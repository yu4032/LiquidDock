/* native_api.h — LSPosed / HyperOS Runtime native module 的注入契约（唯一定义点）
 *
 * 布局来自 libhyperceiler_launcher.so 反汇编（docs/os4-hook-rewrite-plan.md §1.4），
 * 与宿主运行时共享，**一字不可改**：
 *   version +0x00   pad +0x04   hook +0x08（返回 0 == 成功）
 *
 * native_init 的返回值是 per-library-load 回调（LSPosed NativeOnModuleLoaded 约定）：
 * name = 加载的库路径，handle = dlopen 句柄。返回 NULL 表示不注册回调。
 */
#ifndef LIQUIDDOCK_NATIVE_API_H
#define LIQUIDDOCK_NATIVE_API_H

#include <stdint.h>

struct native_api {
    uint32_t version;
    uint32_t pad;
    int (*hook)(void *target, void *replace, void **backup);
};

typedef void (*on_library_loaded_t)(const char *name, void *handle);

/* 模块唯一导出（exports.map）。必须是 C 符号、默认可见性。 */
on_library_loaded_t native_init(struct native_api *api);

#endif /* LIQUIDDOCK_NATIVE_API_H */
