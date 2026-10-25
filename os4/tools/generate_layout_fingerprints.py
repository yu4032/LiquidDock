#!/usr/bin/env python3
"""Generate the pinned fuxi 8.01.02.8584 AOT function fingerprints."""
import argparse
import hashlib
import json
import pathlib
import struct
import sys

parser = argparse.ArgumentParser()
parser.add_argument("vendor_dir", type=pathlib.Path, help="directory containing extracted ARM64 vendor .so files")
args = parser.parse_args()

root = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / "os4/tools"))
from attest_elf import load_segments

DART_SHA256 = "93e511989202a70d38b8231c3133167b830ca7609b61ba6ad133898737d0e0ef"
RUST_SHA256 = "f70a2d15505abc83bbc2f03cbe9e90b635a8add6d89ae0fef764100aea1c6d8b"


def extract_target(binary, name, vaddr, size):
    matches = [
        (offset + vaddr - base, size)
        for flags, base, offset, filesz in load_segments(binary)
        if flags & 1 and base <= vaddr and vaddr + size <= base + filesz
    ]
    if len(matches) != 1:
        raise ValueError(f"{name}: expected one executable segment, found {len(matches)}")
    offset, _ = matches[0]
    body = binary[offset:offset + size]
    return body, {
        "name": name,
        "vaddr": hex(vaddr),
        "size": size,
        "sha256": hashlib.sha256(body).hexdigest(),
    }


def render_words(name, body, target):
    if len(body) % 4:
        raise ValueError(f"{name}: body size must be word aligned")
    words = struct.unpack("<" + str(len(body) // 4) + "I", body)
    lines = [f"/* {target['vaddr']} SHA256 {target['sha256']} */", f"static const uint32_t {name}[] = {{"]
    for index in range(0, len(words), 6):
        chunk = words[index:index + 6]
        lines.append("    " + ", ".join(f"0x{word:08x}u" for word in chunk) + ",")
    lines.append("};")
    return "\n".join(lines)


def write_header(filename, guard, body):
    path = root / "os4/native" / filename
    path.write_text(
        f"/* Generated from the SHA-256 pinned fuxi 8584 ELF. */\n"
        f"#ifndef {guard}\n#define {guard}\n#include <stdint.h>\n\n"
        + body
        + f"\n\n#endif\n"
    )


def generate_targets(filename, guard, binary, targets):
    sections = []
    entries = []
    for symbol, name, vaddr, size in targets:
        body, entry = extract_target(binary, symbol, vaddr, size)
        sections.append(render_words(name, body, entry))
        entries.append(entry)
    write_header(filename, guard, "\n\n".join(sections))
    return entries


def generate_hook_header(filename, guard, binary, macros, targets):
    sections = []
    entries = []
    for symbol, name, vaddr, size in targets:
        body, entry = extract_target(binary, symbol, vaddr, size)
        sections.append(render_words(name, body, entry))
        entries.append(entry)
    definitions = [f"#define {name} {value}" for name, value in macros]
    write_header(filename, guard, "\n".join(definitions) + "\n\n" + "\n\n".join(sections))
    return entries


def render_sha_bytes(name, digest):
    raw = bytes.fromhex(digest)
    lines = [f"static const unsigned char {name}[32]={{"]
    for index in range(0, len(raw), 8):
        lines.append("  " + ", ".join(f"0x{byte:02x}" for byte in raw[index:index + 8]) + ",")
    lines.append("};")
    return "\n".join(lines)


def generate_icon_glass_header(binary):
    targets = [
        ("ShortcutIconWidget._addFolderBgAnim closure", "ld_icon_glass_body_sha", 0x1786e18, 796),
        ("ShortCutCommonWidgetMixin.buildBackground closure", "ld_icon_glass_radius_sha", 0x14e9b78, 804),
        ("SfAnimConfig._checkSystemSupport", "ld_icon_glass_flight_sha", 0xa06648, 136),
    ]
    sections = []
    entries = []
    for symbol, name, vaddr, size in targets:
        body, entry = extract_target(binary, symbol, vaddr, size)
        sections.append(f"/* {entry['vaddr']} SHA256 {entry['sha256']} */\n" +
                        render_sha_bytes(name, entry["sha256"]))
        entries.append(entry)
    macros = [
        ("LD_ICON_GLASS_BODY_START", "0x1786e18u"),
        ("LD_ICON_GLASS_BODY_SIZE", "796u"),
        ("LD_ICON_GLASS_RADIUS_START", "0x14e9b78u"),
        ("LD_ICON_GLASS_RADIUS_SIZE", "804u"),
        ("LD_ICON_GLASS_FLIGHT_START", "0xa06648u"),
        ("LD_ICON_GLASS_FLIGHT_SIZE", "136u"),
    ]
    write_header("icon_glass_fingerprints.h", "LD_ICON_GLASS_FINGERPRINTS_H",
                 "\n".join(f"#define {name} {value}" for name, value in macros) +
                 "\n\n" + "\n\n".join(sections))
    return entries


libapp = (args.vendor_dir / "libapp.so").read_bytes()
launcher = (args.vendor_dir / "libapp_launcher.so").read_bytes()
if hashlib.sha256(libapp).hexdigest() != DART_SHA256:
    raise SystemExit("libapp.so SHA-256 does not match fuxi Launcher 8.01.02.8584")
if hashlib.sha256(launcher).hexdigest() != RUST_SHA256:
    raise SystemExit("libapp_launcher.so SHA-256 does not match the attested fuxi Rust library")

pad_body, pad_entry = extract_target(libapp, "PadRowColHandler.handle", 0x1a84cfc, 344)
cell_body, cell_entry = extract_target(libapp, "CellLayoutGetxController.changeCellCount", 0xa532a8, 932)
pad_header = render_words("ld_pad_rowcol_words", pad_body, pad_entry)
write_header("layout_fingerprints.h", "LD_LAYOUT_FINGERPRINTS_H", pad_header)

layout_entries = generate_targets(
    "layout_runtime_fingerprints.h",
    "LD_LAYOUT_RUNTIME_FINGERPRINTS_H",
    libapp,
    [
        ("LauncherModel.deleteItemDatabase", "ld_delete_item_words", 0x918fe0, 132),
        ("RustLibApiImpl.sse_encode_list_item_info", "ld_save_batch_words", 0x8effa4, 224),
        ("LauncherModel.updateItemDatabase", "ld_save_single_words", 0x8e340c, 132),
        ("ItemDataProcessor.processItems", "ld_load_items_words", 0x15e91b4, 9476),
        ("LayoutDropRuleForSwapPlaces.isLegalXY", "ld_widget_lattice_words", 0x18c8f44, 96),
        ("GridController._filterActiveGridConfig", "ld_filter_words", 0x8d39a4, 728),
        ("GridOccupancyController._processScreenList", "ld_process_screen_words", 0x8f02c0, 2192),
        ("CellLayoutGetxController._usesNoVacantMode", "ld_no_vacant_words", 0x8ecc10, 48),
    ],
)
rotation_entries = generate_targets(
    "rotation_fingerprint.h",
    "LD_ROTATION_FINGERPRINT_H",
    libapp,
    [("GridOccupancyController.onOrientationChanged", "ld_orientation_words", 0x8ef670, 1336)],
)
rust_entries = generate_targets(
    "rust_orientation_fingerprint.h",
    "LD_RUST_ORIENTATION_FINGERPRINT_H",
    launcher,
    [("ld_rust_orientation_words", "ld_rust_orientation_words", 0x90ffa0, 0x911890 - 0x90ffa0)],
)

hook_entries = []
indicator_vaddr = 0x15373e4
indicator_size = 0x580
hook_entries += generate_hook_header(
    "home_options_fingerprints.h", "LD_HOME_OPTIONS_FINGERPRINTS_H", libapp,
    [
        ("LD_HOME_INDICATOR_FUNCTION_OFFSET", hex(indicator_vaddr) + "u"),
        ("LD_HOME_INDICATOR_FUNCTION_SIZE", hex(indicator_size) + "u"),
        ("LD_HOME_INDICATOR_HOOK_OFFSET", hex(indicator_vaddr + 0x460) + "u"),
        ("LD_HOME_INDICATOR_BODY_SHA256", "__BODY_SHA256__"),
    ],
    [("GridController.padIndicatorSpacing code window", "ld_home_indicator_words",
      indicator_vaddr, indicator_size)],
)
home_indicator_entry = hook_entries[-1]
home_options = root / "os4/native/home_options_fingerprints.h"
home_options.write_text(home_options.read_text().replace(
    '#define LD_HOME_INDICATOR_BODY_SHA256 __BODY_SHA256__',
    f'#define LD_HOME_INDICATOR_BODY_SHA256 "{home_indicator_entry["sha256"]}"'))

home_pipeline_entries = generate_hook_header(
    "home_geometry_fingerprints.h", "LD_HOME_GEOMETRY_FINGERPRINTS_H", libapp,
    [
        ("LD_HOME_PIPELINE_FUNCTION_OFFSET", "0x199758cu"),
        ("LD_HOME_PIPELINE_FUNCTION_SIZE", "0x36cu"),
        ("LD_HOME_PIPELINE_HOOK_OFFSET", "0x19978c8u"),
        ("LD_HOME_PIPELINE_BODY_SHA256", "__BODY_SHA256__"),
    ],
    [("ComputePipeline.compute", "ld_home_pipeline_words", 0x199758c, 0x36c)],
)
home_pipeline_header = root / "os4/native/home_geometry_fingerprints.h"
home_pipeline_header.write_text(home_pipeline_header.read_text().replace(
    '#define LD_HOME_PIPELINE_BODY_SHA256 __BODY_SHA256__',
    f'#define LD_HOME_PIPELINE_BODY_SHA256 "{home_pipeline_entries[0]["sha256"]}"'))

home_widget_entries = generate_hook_header(
    "home_widget_fingerprints.h", "LD_HOME_WIDGET_FINGERPRINTS_H", libapp,
    [
        ("LD_HOME_WIDGET_FUNCTION_OFFSET", "0x118792cu"),
        ("LD_HOME_WIDGET_FUNCTION_SIZE", "0x260u"),
        ("LD_HOME_WIDGET_HOOK_OFFSET", "0x1187978u"),
        ("LD_HOME_WIDGET_BODY_SHA256", "__BODY_SHA256__"),
        ("LD_HOME_WIDGET_ALLOC_OFFSET", "0x1187af0u"),
    ],
    [("WidgetContainerWidget.widgetContainerWidth", "ld_home_widget_words", 0x118792c, 0x260)],
)
home_widget_header = root / "os4/native/home_widget_fingerprints.h"
home_widget_header.write_text(home_widget_header.read_text().replace(
    '#define LD_HOME_WIDGET_BODY_SHA256 __BODY_SHA256__',
    f'#define LD_HOME_WIDGET_BODY_SHA256 "{home_widget_entries[0]["sha256"]}"'))

dock_specs = [
    ("_HotseatWidgetPadState._recomputeDockBgParams", "ld_dock_background_words", 0x1408534, 0x740),
    ("_HotseatWidgetPadState._defaultIconSize", "ld_dock_spacing_words", 0x9c5890, 0x94),
    ("HotseatLayoutCalculator.calculate", "ld_dock_icon_padding_body", 0x9c5594, 0x24c),
    ("HotSeatsConstants2.calcScaledIconSize", "ld_dock_function_size_body", 0xaa95dc, 0xec),
    ("DockBoundsReporter._flushPending code window", "ld_dock_container_height_body", 0x118dab0, 0x534),
    ("GridController.hotSeatsMarginBottom", "ld_dock_bottom_body", 0xa86e1c, 0x6c),
]
dock_macros = [
    ("LD_DOCK_BACKGROUND_START", "0x1408534u"),
    ("LD_DOCK_BACKGROUND_SITE", "0x1408954u"),
    ("LD_DOCK_SPACING_START", "0x9c5890u"),
    ("LD_DOCK_SPACING_SITE", "0x9c5910u"),
    ("LD_DOCK_ICON_PADDING_START", "0x9c5594u"),
    ("LD_DOCK_ICON_PADDING_SITE", "0x9c5714u"),
    ("LD_DOCK_FUNCTION_SIZE_START", "0xaa95dcu"),
    ("LD_DOCK_FUNCTION_SIZE_SITE", "0xaa9638u"),
    ("LD_DOCK_CONTAINER_HEIGHT_START", "0x118dab0u"),
    ("LD_DOCK_CONTAINER_HEIGHT_SITE", "0x118dc70u"),
    ("LD_DOCK_BOTTOM_START", "0xa86e1cu"),
    ("LD_DOCK_BOTTOM_SITE", "0xa86e74u"),
]
hook_entries += generate_hook_header(
    "dock_geometry_fingerprints.h", "LD_DOCK_GEOMETRY_FINGERPRINTS_H", libapp,
    dock_macros, dock_specs,
)
hook_entries += home_pipeline_entries + home_widget_entries

glass_filter_entries = generate_hook_header(
    "glass_filter_fingerprints.h", "LD_GLASS_FILTER_FINGERPRINTS_H", libapp,
    [
        ("LD_GLASS_FILTER_START", "0x1ba9914u"),
        ("LD_GLASS_FILTER_SITE", "0x1ba9d1cu"),
    ],
    [("_ImageFilter.__initGlassV7$Method$FfiNative", "ld_glass_filter_words", 0x1ba9914, 0x4b8)],
)
hook_entries += glass_filter_entries
hook_entries += generate_icon_glass_header(libapp)

profile_targets = [pad_entry, cell_entry, *layout_entries, *rotation_entries, *hook_entries]
(root / "os4/profiles/fuxi-8.01.02.8584.json").write_text(
    json.dumps({"elf_sha256": DART_SHA256, "targets": profile_targets}, indent=2) + "\n"
)
(root / "os4/profiles/fuxi-8584-rust-layout.json").write_text(
    json.dumps({"elf_sha256": RUST_SHA256, "targets": rust_entries}, indent=2) + "\n"
)
print(f"generated {len(profile_targets)} 8584 Dart function fingerprints and {len(rust_entries)} Rust function fingerprint")
