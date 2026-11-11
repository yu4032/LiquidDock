"""Package an OS4 native backend into a freshly built LiquidDock APK.

This produces an unsigned APK; signing is a separate step.

16KB page alignment (Android 15+):
  - 所有 STORED（未压缩）条目在 zip 内的**数据偏移**必须按 16KB 对齐，
    这样它们可以被直接 mmap；`.so` 另需 ELF 的 PT_LOAD p_align=0x4000（由链接参数保证）。
  - build-tools 29 的 zipalign 没有 -P（16KB）选项，且它的 -p 会重算 extra 字段、
    把这里算好的对齐破坏掉 —— 所以**不再调用 zipalign**，对齐全部在这里完成。
  - 16KB 是 4KB 的整数倍，因此同时满足 4KB 页设备。
"""
import argparse
from pathlib import Path
import struct
import zipfile

PAGE_ALIGN = 16384          # .so 需要的页对齐（16KB）
MIN_ALIGN = 4               # 其余未压缩条目只需 4 字节（resources.arsc / dex / png）
ALIGN_EXTRA_ID = 0xD935     # 与 AOSP ZipAlign 相同的 padding extra field ID
LIB = 'lib/arm64-v8a/libliquiddock4.so'


def _align_extra(pos: int, name_len: int, align: int) -> bytes:
    """构造 extra field，使 (pos + 30 + name_len + len(extra)) % align == 0。"""
    base = pos + 30 + name_len          # 本地头 30 字节 + 文件名
    total = (-base) % align
    if total < 4:                       # 至少容纳 id(2) + size(2)
        total += align
    data_len = total - 4
    return struct.pack('<HH', ALIGN_EXTRA_ID, data_len) + b'\x00' * data_len


def _align_for(name: str) -> int:
    """只有 .so 需要页对齐；其余未压缩条目 4 字节即可。"""
    return PAGE_ALIGN if name.endswith('.so') else MIN_ALIGN


def _write_aligned(dst: zipfile.ZipFile, info: zipfile.ZipInfo, data: bytes) -> None:
    """写一个条目；STORED 条目按需对齐数据偏移。"""
    if info.compress_type == zipfile.ZIP_STORED:
        fp = dst.fp
        if fp is None:
            raise RuntimeError('zip is not open for writing')
        info.extra = _align_extra(fp.tell(), len(info.filename.encode()), _align_for(info.filename))
    dst.writestr(info, data)


def package(base: Path, native: Path, output: Path) -> None:
    if output.resolve() in (base.resolve(), native.resolve()):
        raise ValueError('output must differ from inputs')

    replacements = {
        LIB: native.read_bytes(),
        'META-INF/xposed/native_init.list': b'libliquiddock4.so\n',
    }

    with zipfile.ZipFile(base) as src, zipfile.ZipFile(output, 'w') as dst:
        for info in src.infolist():
            name = info.filename
            if name in replacements:
                continue                      # 稍后统一写（.so 放最后）
            if name.startswith('META-INF/') and (
                name.endswith(('.SF', '.RSA', '.DSA', '.EC')) or name == 'META-INF/MANIFEST.MF'
            ):
                continue                      # 旧签名
            _write_aligned(dst, info, src.read(name))

        # 非 .so 的替换项（压缩写入，无需对齐）
        for name, data in replacements.items():
            if name == LIB:
                continue
            zi = zipfile.ZipInfo(name)
            zi.compress_type = zipfile.ZIP_DEFLATED
            dst.writestr(zi, data)

        # .so 最后写，ZIP_STORED + 16KB 对齐
        zi = zipfile.ZipInfo(LIB)
        zi.compress_type = zipfile.ZIP_STORED
        _write_aligned(dst, zi, replacements[LIB])

        # ---- 全量校验：.so 16KB 对齐，其余 STORED 4 字节对齐 ----
        print('[align] 校验 STORED 条目：')
        bad = 0
        for z in dst.infolist():
            if z.compress_type != zipfile.ZIP_STORED:
                continue
            data_off = z.header_offset + 30 + len(z.filename.encode()) + len(z.extra)
            need = _align_for(z.filename)
            ok = (data_off % need == 0)
            if z.filename.endswith('.so') or not ok:
                print(f'  {"OK " if ok else "BAD"} {z.filename} data={data_off} need={need} mod={data_off % need}')
            if not ok:
                bad += 1
        if bad:
            raise AssertionError(f'{bad} 个 STORED 条目对齐不足')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('base', type=Path)
    parser.add_argument('native', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    package(args.base, args.native, args.output)
