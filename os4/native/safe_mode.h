/* 安全模式：桌面短时间内连续启动（≈连续崩溃）达阈值后，禁用模块**全部** hook，
 * 直到用户手动退出。
 *
 * 两个文件通道：
 *   内部（launcher 自己的 DE 目录）——计数与最终裁决，/sdcard 不可用时也能守；
 *   外部（模块 app 的 external files）——app 无需 root 即可读；用户退出时写 .exit，
 *   下次启动据此清掉内部标记并恢复正常。
 */
#ifndef LD_SAFE_MODE_H
#define LD_SAFE_MODE_H
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include "bootlog.h"

#define LD_SM_DIR  "/data/user_de/0/com.miui.home/files"
#define LD_SM_FLAG LD_SM_DIR "/liquiddock-safe-mode.flag"
#define LD_SM_LOG  LD_SM_DIR "/liquiddock-boots.log"

#define LD_SM_EXT_DIR  "/sdcard/Android/media/com.hellovoid.liquiddock"
#define LD_SM_EXT_FLAG LD_SM_EXT_DIR "/safe_mode.flag"
#define LD_SM_EXT_EXIT LD_SM_EXT_DIR "/safe_mode.exit"
#define LD_SM_EXT_RESTART LD_SM_EXT_DIR "/restart.request"

#define LD_SM_WINDOW 20   /* 统计窗口（秒） */
#define LD_SM_LIMIT  8    /* 窗口内的启动次数上限，含本次 */

static int ld_sm_exists(const char *path) { return access(path, F_OK) == 0; }

static void ld_sm_touch(const char *path, int on)
{
    if (on) {
        mkdir(LD_SM_EXT_DIR, 0770);
        FILE *f = fopen(path, "w");
        if (f) { fputs("1\n", f); fclose(f); }
    } else {
        unlink(path);
    }
}


/* 常驻看门狗线程：不做任何 hook，只轮询两个请求文件。
 *   safe_mode.exit  -> 清掉所有标记后自杀，系统会重新拉起桌面（退出安全模式）
 *   restart.request -> 直接自杀，桌面重启后重新装 hook（重启作用域，无需 root）
 * 崩溃安全模式下同样运行：它是线程不是 hook，不会把桌面写崩。 */
static void *ld_sm_watchdog(void *arg)
{
    (void)arg;
    for (;;) {
        sleep(1);
        if (ld_sm_exists(LD_SM_EXT_EXIT)) {
            unlink(LD_SM_FLAG);
            unlink(LD_SM_LOG);
            unlink(LD_SM_EXT_FLAG);
            unlink(LD_SM_EXT_EXIT);
            unlink(LD_SM_EXT_RESTART);
            ld_blog("safe mode: exit requested -> restart home");
            kill(getpid(), SIGKILL);
        } else if (ld_sm_exists(LD_SM_EXT_RESTART)) {
            unlink(LD_SM_EXT_RESTART);
            ld_blog("restart requested -> restart home");
            kill(getpid(), SIGKILL);
        }
    }
    return NULL;
}

static void ld_sm_start_watchdog(void)
{
    static int started = 0;
    if (started) return;
    started = 1;
    pthread_t t;
    if (pthread_create(&t, NULL, ld_sm_watchdog, NULL) == 0) pthread_detach(t);
}

/* 返回 1 = 安全模式，调用方必须跳过所有 hook 安装。 */
static int ld_safe_mode_tick(void)
{
    /* 用户在 app 里点了退出：清掉一切标记，恢复正常。 */
    if (ld_sm_exists(LD_SM_EXT_EXIT)) {
        unlink(LD_SM_FLAG);
        unlink(LD_SM_LOG);
        unlink(LD_SM_EXT_FLAG);
        unlink(LD_SM_EXT_EXIT);
        ld_blog("safe mode: exited by user");
        return 0;
    }
    if (ld_sm_exists(LD_SM_FLAG)) {
        ld_blog("safe mode: active, all hooks disabled");
        if (!ld_sm_exists(LD_SM_EXT_FLAG)) ld_sm_touch(LD_SM_EXT_FLAG, 1); /* 外置通道丢失时补写 */
        return 1;
    }
    /* 正常启动：清掉可能残留的 UI 标记，保证横幅状态与内部状态永远一致。 */
    if (ld_sm_exists(LD_SM_EXT_FLAG)) unlink(LD_SM_EXT_FLAG);
    time_t now = time(NULL);
    if (now < 1600000000) return 0;   /* 时钟未同步，不做统计，避免误判 */

    long stamps[32];
    int n = 0;
    FILE *fp = fopen(LD_SM_LOG, "r");
    if (fp) {
        long t;
        while (n < 32 && fscanf(fp, "%ld", &t) == 1)
            if (t > now - LD_SM_WINDOW && t <= now) stamps[n++] = t;
        fclose(fp);
    }
    if (n + 1 >= LD_SM_LIMIT) {
        ld_sm_touch(LD_SM_FLAG, 1);
        ld_sm_touch(LD_SM_EXT_FLAG, 1);
        ld_blog("safe mode: %d starts within %ds -> enabled", n + 1, LD_SM_WINDOW);
        return 1;
    }
    stamps[n++] = now;
    FILE *out = fopen(LD_SM_LOG, "w");
    if (out) {
        for (int i = 0; i < n; i++) fprintf(out, "%ld\n", stamps[i]);
        fclose(out);
    }
    return 0;
}

#endif /* LD_SAFE_MODE_H */
