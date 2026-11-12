#!/usr/bin/env bash
# Optional headless GPU check. Requires JDK 17+, a C compiler, EGL/GLES2 headers/libraries.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
sources=()
for source in prismal/src/main/java/com/hellovoid/prismal/*.java; do
    [[ "$source" == */PrismalRenderer.java ]] || sources+=("$source")
done
javac -d "$test_dir" "${sources[@]}" \
    prismal/src/test/native/ExportCorners.java
java -cp "$test_dir" com.hellovoid.prismal.ExportCorners "$test_dir"
cc prismal/src/test/native/continuous_corners_gles_test.c \
    -o "$test_dir/check" -lEGL -lGLESv2 -lm
EGL_PLATFORM="${EGL_PLATFORM:-surfaceless}" "$test_dir/check" "$test_dir" \
    baseline.frag decorated.frag reconstructed.frag
