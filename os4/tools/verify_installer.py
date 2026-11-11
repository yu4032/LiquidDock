"""Verify the signed OS4 test APK still contains the exact native library, entrypoint and 16 KiB alignment."""
from __future__ import annotations

import hashlib
from pathlib import Path
import struct
import sys
import zipfile

LIB = "lib/arm64-v8a/libliquiddock4.so"
NATIVE_INIT = "META-INF/xposed/native_init.list"
PAGE = 16384

def verify(apk: Path, library: Path) -> None:
    with apk.open("rb") as stream, zipfile.ZipFile(stream) as zf:
        names = zf.namelist()
        if names.count(LIB) != 1 or names.count(NATIVE_INIT) != 1:
            raise AssertionError("Missing/duplicate OS4 native SO or LSPosed entrypoint")
        if zf.read(NATIVE_INIT) != b"libliquiddock4.so\n":
            raise AssertionError("Native LSPosed init list is incorrect")
        actual = zf.read(LIB)
        expected = library.read_bytes()
        if actual != expected:
            raise AssertionError("Packaged OS4 library differs from CI native build")
        info = zf.getinfo(LIB)
        if info.compress_type != zipfile.ZIP_STORED:
            raise AssertionError("OS4 native library must be uncompressed for mmap")

        for entry in zf.infolist():
            if entry.compress_type != zipfile.ZIP_STORED:
                continue
            stream.seek(entry.header_offset)
            local = stream.read(30)
            if len(local) != 30 or struct.unpack_from("<I", local)[0] != 0x04034B50:
                raise AssertionError(f"Invalid local ZIP header: {entry.filename}")
            name_len, extra_len = struct.unpack_from("<HH", local, 26)
            data_offset = entry.header_offset + 30 + name_len + extra_len
            boundary = PAGE if entry.filename.endswith(".so") else 4
            if data_offset % boundary != 0:
                raise AssertionError(f"{entry.filename} aligned to {data_offset % boundary}/{boundary}")
        print(f"Verified signed APK: {apk}")
        print(f"Native SHA-256: {hashlib.sha256(actual).hexdigest()}")
        print(f"APK SHA-256: {hashlib.sha256(apk.read_bytes()).hexdigest()}")
        print(f"Native entry data offset: {info.header_offset} (local header)")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: verify_installer.py SIGNED_APK NATIVE_SO")
    verify(Path(sys.argv[1]), Path(sys.argv[2]))
