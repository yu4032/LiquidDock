#!/usr/bin/env python3
"""Pin the OS4 native backend to an installed Launcher build.

The Launcher AOT library is stripped, but it keeps a full symbol table in the
XZ-compressed ``.gnu_debugdata`` section. Dart function names are stable across
HyperOS 4 launcher releases; only addresses move. This tool reads the vendor
libraries from ``--vendor-dir`` (libapp.so, libapp_launcher.so,
libhyper_os_flutter.so), resolves every hook target by Dart/C++ symbol name,
and regenerates the pinned headers under ``os4/native`` plus a profile JSON.

Hook *site* offsets are reused from the previous pin: for this Launcher family
the function bodies differ only in relocation words (BL/ADRP/B), so the site
of an inline hook keeps its offset inside the function. Targets whose body or
size changed materially are listed as unsupported and compiled out.

Usage:
    python3 os4/tools/pin_launcher_build.py \
        --vendor-dir /path/to/extracted/lib/arm64-v8a \
        --profile turner-8.01.02.8604
"""

import argparse
import hashlib
import json
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]


def sections(data):
    shoff = struct.unpack_from('<Q', data, 0x28)[0]
    shentsize = struct.unpack_from('<H', data, 0x3a)[0]
    shnum = struct.unpack_from('<H', data, 0x3c)[0]
    shstrndx = struct.unpack_from('<H', data, 0x3e)[0]
    out = []
    for index in range(shnum):
        pos = shoff + index * shentsize
        name, kind, flags, addr, offset, size, link, info, align, ent = \
            struct.unpack_from('<IIQQQQIIQQ', data, pos)
        out.append(dict(name=name, kind=kind, offset=offset, size=size,
                        link=link, addr=addr))
    table = out[shstrndx]
    for sec in out:
        raw = data[table['offset'] + sec['name']:table['offset'] + table['size']]
        sec['sname'] = raw.split(b'\0')[0].decode()
    return out


def symbols(data):
    """Map symbol name -> list of (vaddr, size) from .gnu_debugdata."""
    import lzma
    secs = sections(data)
    debug = next((s for s in secs if s['sname'] == '.gnu_debugdata'), None)
    if debug is None:
        return {}
    mini = lzma.decompress(data[debug['offset']:debug['offset'] + debug['size']],
                           format=lzma.FORMAT_XZ)
    msecs = sections(mini)
    symtab = next((s for s in msecs if s['kind'] == 2), None)
    if symtab is None:
        return {}
    strings = msecs[symtab['link']]
    blob = mini[strings['offset']:strings['offset'] + strings['size']]
    out = {}
    for index in range(symtab['size'] // 24):
        pos = symtab['offset'] + index * 24
        name_off, info, other, shndx, value, size = struct.unpack_from('<IBBHQQ', mini, pos)
        if not name_off or not value:
            continue
        end = blob.find(b'\0', name_off)
        name = blob[name_off:end].decode('utf-8', 'replace')
        out.setdefault(name, []).append((value, size))
    return out


def load_segments(data):
    phoff = struct.unpack_from('<Q', data, 0x20)[0]
    phentsize = struct.unpack_from('<H', data, 0x36)[0]
    phnum = struct.unpack_from('<H', data, 0x38)[0]
    out = []
    for index in range(phnum):
        pos = phoff + index * phentsize
        kind, flags, offset, vaddr, paddr, filesz, memsz, align = \
            struct.unpack_from('<IIQQQQQQ', data, pos)
        out.append((kind, flags, offset, vaddr, filesz))
    return out


def file_offset(segments, vaddr):
    for kind, flags, offset, base, filesz in segments:
        if kind == 1 and base <= vaddr < base + filesz:
            return offset + (vaddr - base)
    return None


def single_symbol(table, name):
    hits = table.get(name)
    if not hits:
        raise SystemExit(f'symbol missing: {name}')
    if len(hits) > 1:
        raise SystemExit(f'symbol ambiguous: {name} -> {hits}')
    return hits[0]


def read_body(binary, segments, vaddr, size):
    offset = file_offset(segments, vaddr)
    if offset is None or offset + size > len(binary):
        raise SystemExit(f'vaddr {hex(vaddr)} +{size} outside the image file')
    return binary[offset:offset + size]


def render_words(name, body):
    words = struct.unpack('<' + str(len(body) // 4) + 'I', body)
    lines = [f'static const uint32_t {name}[] = {{']
    for index in range(0, len(words), 6):
        chunk = words[index:index + 6]
        lines.append('    ' + ', '.join(f'0x{word:08x}u' for word in chunk) + ',')
    lines.append('};')
    return '\n'.join(lines)


def write_header(filename, guard, text):
    path = ROOT / 'os4/native' / filename
    path.write_text(f'#ifndef {guard}\n#define {guard}\n#include <stdint.h>\n\n{text}\n\n#endif\n')


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--vendor-dir', type=pathlib.Path, required=True)
    parser.add_argument('--profile', required=True)
    args = parser.parse_args()
    vendor = args.vendor_dir
    libapp = (vendor / 'libapp.so').read_bytes()
    launcher = (vendor / 'libapp_launcher.so').read_bytes()
    engine_path = vendor / 'libhyper_os_flutter.so'
    engine = engine_path.read_bytes() if engine_path.exists() else None
    app_segments = load_segments(libapp)
    rust_segments = load_segments(launcher)
    app_syms = symbols(libapp)
    rust_syms = symbols(launcher)
    engine_syms = symbols(engine) if engine else {}

    profile = {
        'launcher_elf_sha256': sha256(libapp),
        'launcher_elf_size': len(libapp),
        'rust_elf_sha256': sha256(launcher),
        'rust_elf_size': len(launcher),
        'targets': [],
        'unsupported': [],
    }
    if engine:
        profile['engine_sha256'] = sha256(engine)
        profile['engine_size'] = len(engine)

    def pin(targets, filename, guard, prefix_lines, kind='app'):
        symtab = app_syms if kind == 'app' else rust_syms
        segments = app_segments if kind == 'app' else rust_segments
        binary = libapp if kind == 'app' else launcher
        blocks = []
        for symbol, array, size in targets:
            vaddr, sym_size = single_symbol(symtab, symbol)
            body = read_body(binary, segments, vaddr, size)
            blocks.append(f'/* {symbol} @ {hex(vaddr)} size {size} */')
            blocks.append(render_words(array, body))
            profile['targets'].append({
                'name': symbol, 'vaddr': hex(vaddr), 'size': size,
                'sha256': sha256(body), 'elf': 'libapp.so' if kind == 'app' else 'libapp_launcher.so',
            })
        write_header(filename, guard, '\n'.join(prefix_lines) + '\n\n' + '\n\n'.join(blocks))

    # layout_pad row/col handler
    pad_symbol = 'PadRowColHandler.handle'
    pad_vaddr, pad_size = single_symbol(app_syms, pad_symbol)
    pin([(pad_symbol, 'ld_pad_rowcol_words', 344)], 'layout_fingerprints.h',
        'LD_LAYOUT_FINGERPRINTS_H',
        [f'#define LD_PAD_ROWCOL_VADDR {hex(pad_vaddr)}u',
         '#define LD_PAD_ROWCOL_SIZE 344u'])

    layout_targets = [
        ('LauncherModel.deleteItemDatabase', 'ld_delete_item_words', 132),
        ('RustLibApiImpl.sse_encode_list_item_info', 'ld_save_batch_words', 224),
        ('LauncherModel.updateItemDatabase', 'ld_save_single_words', 132),
        ('ItemDataProcessor.processItems', 'ld_load_items_words', 9476),
        ('LayoutDropRuleForSwapPlaces.isLegalXY', 'ld_widget_lattice_words', 96),
        ('GridController._filterActiveGridConfig', 'ld_filter_words', 728),
        ('GridOccupancyController._processScreenList', 'ld_process_screen_words', 2192),
        ('CellLayoutGetxController._usesNoVacantMode', 'ld_no_vacant_words', 48),
    ]
    defs = []
    for symbol, array, size in layout_targets:
        vaddr, _ = single_symbol(app_syms, symbol)
        macro = 'LD_' + array.replace('ld_', '').replace('_words', '').upper() + '_VADDR'
        defs.append(f'#define {macro} {hex(vaddr)}u')
    pin(layout_targets, 'layout_runtime_fingerprints.h',
        'LD_LAYOUT_RUNTIME_FINGERPRINTS_H', defs)

    orientation_vaddr, _ = single_symbol(app_syms, 'GridOccupancyController.onOrientationChanged')
    pin([('GridOccupancyController.onOrientationChanged', 'ld_orientation_words', 1336)],
        'rotation_fingerprint.h', 'LD_ROTATION_FINGERPRINT_H',
        [f'#define LD_ORIENTATION_VADDR {hex(orientation_vaddr)}u'])

    widget_start, _ = single_symbol(app_syms, 'WidgetContainerWidget.widgetContainerWidth')
    pin([('WidgetContainerWidget.widgetContainerWidth', 'ld_home_widget_words', 608)],
        'home_widget_fingerprints.h', 'LD_HOME_WIDGET_FINGERPRINTS_H',
        [f'#define LD_HOME_WIDGET_FUNCTION_OFFSET {hex(widget_start)}u',
         '#define LD_HOME_WIDGET_FUNCTION_SIZE 0x260u',
         f'#define LD_HOME_WIDGET_HOOK_OFFSET {hex(widget_start + 0x4c)}u',
         f'#define LD_HOME_WIDGET_ALLOC_OFFSET {hex(widget_start + 0x1c4)}u'])

    dock_specs = [
        ('_HotseatWidgetPadState._recomputeDockBgParams', 'ld_dock_background_words', 1856, 0x420, 'BACKGROUND'),
        ('_HotseatWidgetPadState._defaultIconSize', 'ld_dock_spacing_words', 148, 0x80, 'SPACING'),
        ('HotseatLayoutCalculator.calculate', 'ld_dock_icon_padding_body', 588, 0x180, 'ICON_PADDING'),
        ('HotSeatsConstants2.calcScaledIconSize', 'ld_dock_function_size_body', 236, 0x5c, 'FUNCTION_SIZE'),
        ('GridController.hotSeatsMarginBottom', 'ld_dock_bottom_body', 108, 0x58, 'BOTTOM'),
    ]
    dock_defs = []
    dock_targets = []
    for symbol, array, size, site, macro in dock_specs:
        vaddr, _ = single_symbol(app_syms, symbol)
        dock_targets.append((symbol, array, size))
        dock_defs.append(f'#define LD_DOCK_{macro}_START {hex(vaddr)}u')
        dock_defs.append(f'#define LD_DOCK_{macro}_SITE {hex(vaddr + site)}u')
    # The container-height window changed materially on this build.
    dock_defs += ['#define LD_DOCK_CONTAINER_HEIGHT_START 0u',
                  '#define LD_DOCK_CONTAINER_HEIGHT_SITE 0u',
                  'static const uint32_t ld_dock_container_height_body[] = {0};']
    profile['unsupported'].append('DockBoundsReporter._flushPending')
    pin(dock_targets, 'dock_geometry_fingerprints.h', 'LD_DOCK_GEOMETRY_FINGERPRINTS_H', dock_defs)

    glass_start, _ = single_symbol(app_syms, '_ImageFilter.__initGlassV7$Method$FfiNative')
    pin([('_ImageFilter.__initGlassV7$Method$FfiNative', 'ld_glass_filter_words', 1208)],
        'glass_filter_fingerprints.h', 'LD_GLASS_FILTER_FINGERPRINTS_H',
        [f'#define LD_GLASS_FILTER_START {hex(glass_start)}u',
         f'#define LD_GLASS_FILTER_SITE {hex(glass_start + 0x408)}u'])

    rust_symbol = ('_RNvNtNtNtCs8Fq59dB8uNi_12app_launcher8launcher4data23launcher_model_internal'
                   '44check_database_data_orientation_when_loading')
    if rust_symbol in rust_syms:
        vaddr, size = rust_syms[rust_symbol][0]
        window = min(size, 6400)
        body = read_body(launcher, rust_segments, vaddr, window)
        write_header('rust_orientation_fingerprint.h', 'LD_RUST_ORIENTATION_FINGERPRINT_H',
                     f'#define LD_RUST_ORIENTATION_VADDR {hex(vaddr)}u\n'
                     f'#define LD_RUST_ORIENTATION_SIZE {window}u\n\n'
                     + render_words('ld_rust_orientation_words', body))
        profile['targets'].append({'name': rust_symbol, 'vaddr': hex(vaddr),
                                   'size': window, 'sha256': sha256(body),
                                   'elf': 'libapp_launcher.so'})
    else:
        profile['unsupported'].append('check_database_data_orientation_when_loading')

    # Unsupported on this build: bodies or entry windows changed materially.
    # The native backend compiles these installs out via LD_PIN_* flags.
    for name, macro in [('GridController.padIndicatorSpacing', 'HOME_INDICATOR'),
                        ('ComputePipeline.compute', 'HOME_PIPELINE')]:
        profile['unsupported'].append(name)
        write_header('home_options_fingerprints.h' if macro == 'HOME_INDICATOR'
                     else 'home_geometry_fingerprints.h',
                     f'LD_{macro}_FINGERPRINTS_H',
                     f'#define LD_{macro}_FUNCTION_OFFSET 0u\n'
                     f'#define LD_{macro}_FUNCTION_SIZE 0u\n'
                     f'#define LD_{macro}_HOOK_OFFSET 0u\n'
                     f'#define LD_{macro}_BODY_SHA256 ""\n'
                     f'static const uint32_t ld_{macro.lower()}_words[] = {{0}};\n'
                     f'/* {name}: entry window changed on this Launcher build */')

    # Engine body used by the optional strict native-glass check.
    if engine:
        engine_symbol = next((name for name in engine_syms
                              if 'initGlassV7' in name and 'FfiDispatcher' in name
                              and name.endswith('4CallEPNS_13DartWrappableEdddiiddddS4_ddddddS4_ddS4_dddddddddiididdddd')), None)
        if engine_symbol is None:
            engine_symbol = next(name for name in engine_syms
                                 if 'initGlassV7' in name and 'FfiDispatcher' in name)
        vaddr, size = single_symbol(engine_syms, engine_symbol)
        body = read_body(engine, load_segments(engine), vaddr, min(size, 2560))
        words = ','.join(f'0x{byte:02x}' for byte in body)
        (ROOT / 'os4/native/native_glass_fingerprints.h').write_text(
            '#ifndef LD_NATIVE_GLASS_FINGERPRINTS_H\n#define LD_NATIVE_GLASS_FINGERPRINTS_H\n'
            '/* Captured ImageFilter::initGlassV7 body for optional strict safety checks. */\n'
            f'#define LD_NATIVE_GLASS_ENGINE_VADDR {hex(vaddr)}u\n'
            f'static const unsigned char ld_native_glass_engine_words[]={{{words}}};\n'
            '#endif\n')
        profile['targets'].append({'name': engine_symbol, 'vaddr': hex(vaddr),
                                   'size': len(body), 'sha256': sha256(body),
                                   'elf': 'libhyper_os_flutter.so'})

    out = ROOT / 'os4/profiles' / f'{args.profile}.json'
    out.write_text(json.dumps(profile, indent=2) + '\n')
    print(f'pinned {len(profile["targets"])} targets for {args.profile}; '
          f'{len(profile["unsupported"])} unsupported: {profile["unsupported"]}')


if __name__ == '__main__':
    sys.exit(main())
