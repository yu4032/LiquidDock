#include "process_gate.h"

#include <stdint.h>
#include <android/log.h>

/* Native Xposed contract; injection ABI is isolated from feature implementation. */
typedef struct {
    uint32_t version;
    uint32_t reserved;
    int (*hook)(void *target, void *replacement, void **backup);
} Os4NativeApi;
typedef void (*Os4LibraryCallback)(const char *name, void *handle);

/*
 * Read-only bootstrap: intentionally no inline hooks, asset patches,
 * DB mutation, root shell, force-tablet logic, or configuration polling.
 * AOT targets may be installed only after separate full-binary attestation.
 */
__attribute__((visibility("default")))
Os4LibraryCallback native_init(const Os4NativeApi *api) {
    if (!os4_is_launcher_process()) return (Os4LibraryCallback)0;
    if (api == 0 || api->version != 2 || api->hook == 0) {
        __android_log_print(ANDROID_LOG_WARN, "LiquidDock4",
                            "Rejected unknown native injection ABI");
        return (Os4LibraryCallback)0;
    }
    __android_log_print(ANDROID_LOG_INFO, "LiquidDock4",
                        "OS4 read-only bootstrap loaded; no hooks installed");
    return (Os4LibraryCallback)0;
}
