# PassBlur native fork work area

This directory is isolated from the LiquidDock APK. The APK's libxposed Java
code cannot execute inside the `surfaceflinger` native process. No module,
modified `libsurfaceflinger.so`, or installer is produced here yet.

The exact analyzed library can be checked offline:

```sh
python native/passblur/verify_binary.py /path/to/original/libsurfaceflinger.so
```

The `Libs` release archive from `yu4032/hyperos-analysis` must have SHA256
`57e25e06f70aa3e3da98dbeb16fbcc6e6e9d2ecd541f96568dc043e91804282f`.
Its `files/libsurfaceflinger.so` must have SHA256
`407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32`.
The manifest also pins 18 function byte ranges and seven disassembled candidate
instructions. Candidate sites are evidence only, not approved patch sites. A mismatch is a different
build and must not be patched with these offsets.
The `PassBlur native guard tests` workflow runs the guard and scheduler tests
on code changes. Its manual `verify-private-release` job additionally fetches
and verifies the real private asset when the repository has a read-scoped
`HYPEROS_ANALYSIS_READ_TOKEN` secret; it fails explicitly if that secret is
missing.

The Phase 1 generation algorithm and a per-job registry keyed by the promise
shared state are implemented in `latest_only_scheduler.hpp`. This avoids
using the cloned closure allocation as identity. It still requires native
adapters at submission, worker entry, pre-queue, completion, and PassBlur
lifecycle. Its C++ test exercises separate targets, both stale gates,
concurrent publication, pointer reuse, and cloned-job lookup:

```sh
g++ -std=c++17 -O2 -Wall -Wextra -Werror -pthread \
  native/passblur/test_latest_only_scheduler.cpp -o /tmp/passblur-scheduler-test
/tmp/passblur-scheduler-test
python -m unittest discover native/passblur -p 'test_*.py'
```

`offline_patch.py` only accepts instruction sites explicitly approved in
`compatibility.json`; currently that list is empty. Accordingly its `apply`
operation deliberately refuses to create a patched library. Once a reviewed
patch plan includes exact original and replacement instruction bytes plus the
full patched SHA256, the tool will verify original identity, executable
segment membership, instruction alignment, target bytes, and all changed
bytes. It writes only a separate offline output file. Its `restore` operation
copies a verified original backup to another path. `apply` will automatically
write `<output>.original` before writing `<output>` once an approved plan
exists:

```sh
python native/passblur/offline_patch.py restore \
  --original /path/to/verified/original/libsurfaceflinger.so \
  --output /path/to/restored/libsurfaceflinger.so
```

Before any integration with a systemless module, resolve the ABI and
lifetime gaps in
[`docs/reverse-engineering/passblur-native-scheduler.md`](../../docs/reverse-engineering/passblur-native-scheduler.md):
identify a safe native loading/patch mechanism, attach a per-PassBlur state
and stable per-job generation across cloned closures, prove early-exit
promise/RefBase/vector cleanup, and prove the late cancel/fence path.
After that, test on an exact-build device with a module that can be disabled
from recovery, keeping the original library byte-for-byte available. No
on-device installation should be attempted from this work area yet.
