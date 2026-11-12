#!/usr/bin/env bash
# Pixel comparison and wall-time experiment on real GLES. Optional ADB_SERIAL runs on device.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
sources=()
for file in prismal/src/main/java/com/hellovoid/prismal/*.java; do
    [[ "$file" == */PrismalRenderer.java ]] || sources+=("$file")
done
javac -d "$test_dir" "${sources[@]}" prismal/src/test/native/ExportInteriorFastPath.java
java -cp "$test_dir" com.hellovoid.prismal.ExportInteriorFastPath "$test_dir"
if [[ $# -gt 0 ]]; then
    serial="$1"
    ndk="${ANDROID_NDK_ROOT:-/mnt/extra/Android/Sdk/ndk/28.2.13676358}"
    compiler="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang"
    "$compiler" -O2 prismal/src/test/native/interior_fast_path_gles_test.c -o "$test_dir/check" -lEGL -lGLESv2 -lm
    remote="/data/local/tmp/liquiddock-interior-gles-$$"
    adb -s "$serial" shell mkdir -p "$remote"
    trap 'adb -s "$serial" shell rm -rf "$remote" >/dev/null 2>&1 || true; rm -rf "$test_dir"' EXIT
    adb -s "$serial" push "$test_dir/check" "$test_dir"/*.glsl "$test_dir"/*.frag "$remote/" >/dev/null
    adb -s "$serial" shell "cd '$remote' && ./check"
else
    cc -O2 prismal/src/test/native/interior_fast_path_gles_test.c -o "$test_dir/check" -lEGL -lGLESv2 -lm
    (cd "$test_dir" && EGL_PLATFORM="${EGL_PLATFORM:-surfaceless}" ./check)
fi
