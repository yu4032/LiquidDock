/* 跨 TU 共用的持久化启动日志。
 * logcat 环形缓冲在开机高负载下几分钟就滚掉，卡机复盘时经常"证据蒸发"。
 * 实现见 liquiddock_launcher.c（唯一一份，避免多 TU 各自持 fd）。
 *
 * 记录内容：native_init 是否执行 / 进程门控结果 / device hook 是否装上 /
 * 各 hook 实际装了哪些目标 / 配置接受还是拒绝。
 */
#ifndef LIQUIDDOCK_BOOTLOG_H
#define LIQUIDDOCK_BOOTLOG_H

void ld_blog(const char *fmt, ...) __attribute__((format(printf, 1, 2)));

#endif
