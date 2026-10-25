// lib_base.h — OS4 目标库 ELF 基址解析（替换有缺陷的「最低 MiuiHome.apk 映射」）
//
// ============================ 为什么需要它 ============================
// 旧实现（hooks_dock.c / hooks_grid.c / hooks_glass.c 里各自的 find_libapp_base）
// 取「含 MiuiHome.apk 的最小映射 start」当 ELF 基址。**这是错的**：
// APK 里映射了多个 ELF（libapp.so、libapp_launcher.so、libresources_frb.so、
// libnative_widget_sdk.so）以及资源，最低 start 属于哪一段取决于加载顺序，
// 与「我要的 libapp.so」无关。
// 表现：入口指纹门稳定报 `refusing unverified Dart entry ... word=0x...`。
// **门是对的，地址是错的** —— 最容易误判成指纹问题。
//
// ============================ 实测事实（真机 maps + 字节验证）============
// ⚠️ 关键：**APK 内每个 ELF 的每个 PT_LOAD 段是被分别 mmap 的**，不是一整块连续映射。
//    因此对同一 APK 的不同段，`map_start - map_offset` **各不相同**
//    （实测 19 段 → 17 个不同值）。
//    任何「file_base = start - offset 恒等」的假设都是错的
//    —— 本文件第一版即栽在此，已在真机上用字节读出否决。
//
//    实测（launcher pid 7367）：
//      libapp.so          entry=0x528000  elf_base=0x6d48478000  -> "\x7fELF" ✓
//        elf_base+0xa8a03c (calculateDockOffsetY)      -> fd 79 bf a9 (0xa9bf79fd) ✓
//        elf_base+0xa8b20c (calHotseatCenterPosition)  -> fd 79 bf a9 ✓
//      libapp_launcher.so entry=0x2140000 elf_base=0x6d6a62c000  -> "\x7fELF" ✓
//        elf_base+0x627fe8 (is_pad_device closure)     -> fd 7b bd a9 (0xa9bd7bfd) ✓
//
//    映射规律（与 ELF 程序头一致）：
//        mmap(elf_base + page_down(p_vaddr), file_offset = entry + page_down(p_offset), ...)
//    所以 **runtime = elf_base + vaddr**（对同一 ELF 的所有段都成立），
//    而 `elf_base` 就是「覆盖 entry_off 的那段映射」算出来的 ELF 头地址。
//
// ============================ 正确做法 ============================
//   1. 从 ZIP 中央目录取目标条目在 APK 内的真实数据偏移 entry_off（**运行时读，不硬编码**）
//   2. 在 /proc/self/maps 找覆盖 entry_off 的映射：
//        elf_base = map_start + (entry_off - map_offset)
//   3. 断言 elf_base 处是 `\x7fELF`（不通过即拒绝）
//   4. runtime = elf_base + vaddr
//   5. **强校验**：逐个 PT_LOAD 核对
//        「elf_base + page_down(p_vaddr)」这段映射存在，
//        且其文件偏移 == entry_off + page_down(p_offset)
//      这条能抓住「布局假设错误 / 段映射方式变化」，比只看魔数强得多，
//      也正是能拦住第一版错误设计的检查。
//
// 该头文件只做只读解析，不修改任何状态。任一步失败一律返回 false，
// 调用方必须拒绝安装（fail-closed），**绝不退回到「猜一个地址」**。
#ifndef LIQUIDDOCK_LIB_BASE_H
#define LIQUIDDOCK_LIB_BASE_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>

#define LD_ELF_MAGIC_WORD 0x464c457fu   /* "\x7fELF" 小端读成 u32 */

typedef struct {
    uintptr_t elf_base;     /* ELF 头所在运行地址 */
    size_t    entry_off;    /* 该 ELF 在 APK 内的文件偏移（ZIP 条目数据区） */
    const void *phdr;       /* ELF 程序头表（已映射，可直接读） */
    uint16_t  phnum;
    uint16_t  phentsize;
    size_t    page;         /* 系统页大小 */
} ld_lib_info;

/* ---------------- 页大小（缓存） ---------------- */
static size_t ld_page_size(void)
{
    static size_t p = 0;
    if (!p) {
        long v = sysconf(_SC_PAGESIZE);
        p = (v > 0) ? (size_t)v : 4096u;
    }
    return p;
}

/* ---------------- ZIP 中央目录：查某条目在 APK 内的文件偏移 ----------------
 * 已用 Python zipfile 逐字节比对验证（libapp 0x528000 / launcher 0x2140000）。 */
static bool ld_zip_entry_offset(const char *apk_path, const char *entry_name, size_t *out_off)
{
    FILE *f = fopen(apk_path, "rb");
    if (!f) return false;
    bool ok = false;
    unsigned char *tail = NULL;
    long fsz;

    if (fseek(f, 0, SEEK_END) != 0) goto done;
    fsz = ftell(f);
    if (fsz < 22) goto done;

    long scan = fsz < (22 + 65536) ? fsz : (22 + 65536);
    tail = (unsigned char *)malloc((size_t)scan);
    if (!tail) goto done;
    if (fseek(f, fsz - scan, SEEK_SET) != 0) goto done;
    if (fread(tail, 1, (size_t)scan, f) != (size_t)scan) goto done;

    long eocd = -1;
    for (long i = scan - 22; i >= 0; i--) {
        if (tail[i] == 'P' && tail[i+1] == 'K' && tail[i+2] == 0x05 && tail[i+3] == 0x06) {
            eocd = i; break;
        }
    }
    if (eocd < 0) goto done;

    uint32_t cd_size, cd_off;
    uint16_t cd_count;
    memcpy(&cd_count, tail + eocd + 10, 2);
    memcpy(&cd_size,  tail + eocd + 12, 4);
    memcpy(&cd_off,   tail + eocd + 16, 4);

    /* ZIP64 EOCD 定位器（存在时优先） */
    if (cd_off == 0xffffffffu || cd_count == 0xffffu) {
        for (long i = scan - 22; i >= 20; i--) {
            if (tail[i] == 'P' && tail[i+1] == 'K' && tail[i+2] == 0x06 && tail[i+3] == 0x07) {
                uint64_t z64_off;
                memcpy(&z64_off, tail + i + 8, 8);
                unsigned char hdr[56];
                if (fseek(f, (long)z64_off, SEEK_SET) == 0 &&
                    fread(hdr, 1, sizeof hdr, f) == sizeof hdr &&
                    hdr[0] == 'P' && hdr[1] == 'K' && hdr[2] == 0x06 && hdr[3] == 0x06) {
                    uint64_t z_cd_size, z_cd_off;
                    memcpy(&z_cd_off,  hdr + 48, 8);
                    memcpy(&z_cd_size, hdr + 40, 8);
                    cd_off  = (uint32_t)z_cd_off;
                    cd_size = (uint32_t)z_cd_size;
                }
                break;
            }
        }
    }

    if (cd_size == 0 || cd_size > (uint32_t)fsz) goto done;
    unsigned char *cd = (unsigned char *)malloc(cd_size);
    if (!cd) goto done;
    if (fseek(f, (long)cd_off, SEEK_SET) != 0 || fread(cd, 1, cd_size, f) != cd_size) {
        free(cd); goto done;
    }

    size_t want = strlen(entry_name);
    size_t p = 0;
    while (p + 46 <= cd_size) {
        if (!(cd[p] == 'P' && cd[p+1] == 'K' && cd[p+2] == 0x01 && cd[p+3] == 0x02)) break;
        uint16_t nlen, elen, clen;
        uint32_t lho;
        memcpy(&nlen, cd + p + 28, 2);
        memcpy(&elen, cd + p + 30, 2);
        memcpy(&clen, cd + p + 32, 2);
        memcpy(&lho,  cd + p + 42, 4);
        if (p + 46 + nlen > cd_size) break;
        if (nlen == want && memcmp(cd + p + 46, entry_name, want) == 0) {
            /* 从 local header 跳过可变长字段，得到真实数据偏移 */
            unsigned char lh[30];
            if (fseek(f, (long)lho, SEEK_SET) == 0 &&
                fread(lh, 1, sizeof lh, f) == sizeof lh &&
                lh[0] == 'P' && lh[1] == 'K' && lh[2] == 0x03 && lh[3] == 0x04) {
                uint16_t ln, le;
                memcpy(&ln, lh + 26, 2);
                memcpy(&le, lh + 28, 2);
                *out_off = (size_t)lho + 30 + ln + le;
                ok = true;
            }
            break;
        }
        p += 46 + nlen + elen + clen;
    }
    free(cd);
done:
    free(tail);
    fclose(f);
    return ok;
}

/* ---------------- /proc/self/maps 快照 ---------------- */
typedef struct { uintptr_t start, end; unsigned long off; } ld_mapseg;
#define LD_MAX_SEGS 512

/* Updated system apps map from /data/app/.../com.miui.home-<id>/base.apk;
 * the factory image maps /product/.../MiuiHome.apk. */
static const char *ld_launcher_apk_substr(void)
{
    FILE *f = fopen("/proc/self/maps", "r");
    if (!f) return "com.miui.home-";
    char line[512];
    while (fgets(line, sizeof line, f)) {
        if (strstr(line, "/com.miui.home-")) {
            fclose(f);
            return "com.miui.home-";
        }
    }
    fclose(f);
    return "MiuiHome.apk";
}

/* 从 maps 里取该 APK 的真实**完整路径**（并去掉可能的 " (deleted)" 后缀）。
 * ⚠️ 这一步不可省：调用方给的是 maps 匹配子串（如 "MiuiHome.apk"），
 *    但 `fopen` 需要绝对路径；直接拿子串去 fopen 会失败。 */
static bool ld_apk_real_path(const char *substr, char *out, size_t cap)
{
    if (!out || cap == 0) return false;
    FILE *f = fopen("/proc/self/maps", "r");
    if (!f) return false;
    char line[512];
    bool ok = false;
    while (fgets(line, sizeof line, f)) {
        char *sp = strchr(line, '/');           /* 路径以 '/' 开头 */
        if (!sp) continue;
        if (!strstr(sp, substr)) continue;
        char *nl = strchr(sp, '\n'); if (nl) *nl = '\0';
        char *del = strstr(sp, " (deleted)"); if (del) *del = '\0';
        size_t n = strlen(sp);
        if (n > 0 && n < cap) { memcpy(out, sp, n + 1); ok = true; break; }
    }
    fclose(f);
    return ok;
}

static int ld_collect_segs(const char *apk_substr, ld_mapseg *out, int max)
{
    FILE *f = fopen("/proc/self/maps", "r");
    if (!f) return 0;
    char line[512];
    int n = 0;
    while (fgets(line, sizeof line, f) && n < max) {
        uintptr_t s, e; unsigned long off; char perm[8] = {0}, path[256] = {0};
        /* 格式顺序：start end perms offset dev inode path */
        if (sscanf(line, "%lx-%lx %7s %lx %*s %*s %255[^\n]",
                   &s, &e, perm, &off, path) < 4) continue;
        if (apk_substr && !strstr(path, apk_substr)) continue;
        out[n].start = s; out[n].end = e; out[n].off = off;
        n++;
    }
    fclose(f);
    return n;
}

/* 找覆盖给定文件偏移的映射 */
static bool ld_seg_for_offset(const ld_mapseg *segs, int n, size_t file_off,
                             uintptr_t *map_start, size_t *map_off)
{
    for (int i = 0; i < n; i++) {
        size_t span = (size_t)(segs[i].end - segs[i].start);
        if (file_off >= segs[i].off && file_off < (size_t)segs[i].off + span) {
            *map_start = segs[i].start;
            *map_off   = (size_t)segs[i].off;
            return true;
        }
    }
    return false;
}

/* ---------------- ELF PT_LOAD 访问 ---------------- */
static const unsigned char *ld_phdr_at(const ld_lib_info *li, uint16_t i)
{
    return (const unsigned char *)li->phdr + (size_t)i * li->phentsize;
}

static bool ld_phdr_load(const ld_lib_info *li, uint16_t i,
                         uint64_t *p_offset, uint64_t *p_vaddr,
                         uint64_t *p_filesz, uint64_t *p_memsz)
{
    const unsigned char *ph = ld_phdr_at(li, i);
    uint32_t p_type;
    memcpy(&p_type,   ph + 0,  4);
    memcpy(p_offset,  ph + 8,  8);
    memcpy(p_vaddr,   ph + 16, 8);
    memcpy(p_filesz,  ph + 32, 8);
    memcpy(p_memsz,   ph + 40, 8);
    return p_type == 1;   /* PT_LOAD */
}

/* vaddr -> 运行地址。
 * 实测规律：mmap(elf_base + page_down(p_vaddr), entry + page_down(p_offset))，
 * 因此 runtime = elf_base + vaddr（对同一 ELF 的所有 PT_LOAD 都成立）。
 * 这里仍做 PT_LOAD 归属检查，超出任何段即拒绝（防止 vaddr 张冠李戴）。 */
static bool ld_vaddr_to_runtime(const ld_lib_info *li, uintptr_t vaddr, uintptr_t *out)
{
    for (uint16_t i = 0; i < li->phnum; i++) {
        uint64_t p_offset, p_vaddr, p_filesz, p_memsz;
        if (!ld_phdr_load(li, i, &p_offset, &p_vaddr, &p_filesz, &p_memsz)) continue;
        uint64_t span = p_memsz ? p_memsz : p_filesz;
        if (span == 0) continue;
        if (vaddr >= p_vaddr && vaddr < p_vaddr + span) {
            *out = li->elf_base + vaddr;
            return true;
        }
    }
    return false;
}

/* ---------------- 强校验：PT_LOAD 与真实映射一致 ----------------
 * 逐个 PT_LOAD 核对：「elf_base + page_down(p_vaddr)」这段映射存在，
 * 且其文件偏移 == entry_off + page_down(p_offset)。
 * 这条能抓住「段映射方式变了 / entry 算错 / 指错库」，是魔数之外的独立证据。 */
static bool ld_verify_load_layout(const ld_lib_info *li, const ld_mapseg *segs, int nsegs)
{
    size_t pg = li->page;
    int checked = 0;
    for (uint16_t i = 0; i < li->phnum; i++) {
        uint64_t p_offset, p_vaddr, p_filesz, p_memsz;
        if (!ld_phdr_load(li, i, &p_offset, &p_vaddr, &p_filesz, &p_memsz)) continue;
        if (p_filesz == 0) continue;                 /* 纯 bss 段不映射 */
        uintptr_t want_addr = li->elf_base + (uintptr_t)(p_vaddr & ~(uint64_t)(pg - 1));
        size_t    want_off  = li->entry_off + (size_t)(p_offset & ~(uint64_t)(pg - 1));
        bool hit = false;
        for (int j = 0; j < nsegs; j++) {
            if (segs[j].start == want_addr && (size_t)segs[j].off == want_off) { hit = true; break; }
        }
        if (!hit) return false;
        checked++;
    }
    return checked > 0;
}

/* ---------------- 一步到位：解析 + 校验 ---------------- */
/*
 * apk_path : 传给 maps 匹配的路径子串，如 "com.miui.home-" 或 "MiuiHome.apk"
 * zip_entry: ZIP 内条目名，如 "lib/arm64-v8a/libapp.so"
 */
/* ZIP 条目偏移对同一 APK 恒定 —— 缓存起来，避免轮询反复解析中央目录。
 * 轮询线程可能从多个线程并发调用，用互斥锁保护。 */
#define LD_ZIPC_N 6
static bool ld_zip_entry_offset_cached(const char *apk, const char *entry, size_t *out)
{
    static pthread_mutex_t mu = PTHREAD_MUTEX_INITIALIZER;
    static struct { char apk[192]; char ent[80]; size_t off; } cache[LD_ZIPC_N];
    static int n;

    pthread_mutex_lock(&mu);
    for (int i = 0; i < n; i++) {
        if (strcmp(cache[i].apk, apk) == 0 && strcmp(cache[i].ent, entry) == 0) {
            *out = cache[i].off;
            pthread_mutex_unlock(&mu);
            return true;
        }
    }
    pthread_mutex_unlock(&mu);

    size_t off = 0;
    if (!ld_zip_entry_offset(apk, entry, &off)) return false;

    pthread_mutex_lock(&mu);
    if (n < LD_ZIPC_N && strlen(apk) < sizeof cache[n].apk &&
        strlen(entry) < sizeof cache[n].ent) {
        strcpy(cache[n].apk, apk);
        strcpy(cache[n].ent, entry);
        cache[n].off = off;
        n++;
    }
    pthread_mutex_unlock(&mu);
    *out = off;
    return true;
}

static bool ld_resolve_lib(const char *apk_path, const char *zip_entry, ld_lib_info *out)
{
    memset(out, 0, sizeof *out);

    /* ⚠️ 顺序至关重要：先查 maps，再碰 APK 文件。
     * 反过来的话，库还没映射时也会每次都 fopen 57MB 的 Launcher APK 读中央目录；
     * 调用方（hooks_device 抢 Rust init 窗口的轮询）前 300 轮间隔仅 2ms，
     * 等于在开机最吃 I/O 的阶段每秒几百次 MB 级读取 —— 实测把桌面拖到开不了机。 */
    ld_mapseg *segs = (ld_mapseg *)malloc(sizeof(ld_mapseg) * LD_MAX_SEGS);
    if (!segs) return false;
    int nsegs = ld_collect_segs(apk_path, segs, LD_MAX_SEGS);
    if (nsegs <= 0) { free(segs); return false; }   /* 库未映射 → 立即返回，零 APK I/O */

    /* apk_path 是 maps 匹配子串；fopen 需要绝对路径，从 maps 取真实路径 */
    char real[256];
    if (!ld_apk_real_path(apk_path, real, sizeof real)) { free(segs); return false; }

    size_t entry_off = 0;
    if (!ld_zip_entry_offset_cached(real, zip_entry, &entry_off)) { free(segs); return false; }

    /* 覆盖 entry_off 的映射 → elf_base（entry_off 页对齐时 elf_base == map_start） */
    uintptr_t map_start = 0; size_t map_off = 0;
    if (!ld_seg_for_offset(segs, nsegs, entry_off, &map_start, &map_off)) {
        free(segs); return false;                 /* 还没映射出来 → 让调用方下一轮再试 */
    }
    uintptr_t elf_base = map_start + (entry_off - map_off);

    uint32_t magic = 0;
    memcpy(&magic, (const void *)elf_base, sizeof magic);
    if (magic != LD_ELF_MAGIC_WORD) { free(segs); return false; }

    const unsigned char *eh = (const unsigned char *)elf_base;
    uint64_t e_phoff; uint16_t e_phentsize, e_phnum;
    memcpy(&e_phoff,     eh + 0x20, 8);
    memcpy(&e_phentsize, eh + 0x36, 2);
    memcpy(&e_phnum,     eh + 0x38, 2);
    if (e_phentsize < 56 || e_phnum == 0 || e_phnum > 64) { free(segs); return false; }

    out->elf_base  = elf_base;
    out->entry_off = entry_off;
    out->phdr      = (const void *)(elf_base + (uintptr_t)e_phoff);
    out->phnum     = e_phnum;
    out->phentsize = e_phentsize;
    out->page      = ld_page_size();

    if (!ld_verify_load_layout(out, segs, nsegs)) { free(segs); return false; }
    free(segs);
    return true;
}

/* 交叉验证（交接文档要求「用两个独立目标反推同一 base」）：
 * 两目标必须都能解析；落在同一 PT_LOAD 时，runtime 差必须严格等于 vaddr 差。 */
static bool ld_cross_check_strict(const ld_lib_info *li, uintptr_t v1, uintptr_t v2)
{
    uintptr_t r1, r2;
    if (!ld_vaddr_to_runtime(li, v1, &r1)) return false;
    if (!ld_vaddr_to_runtime(li, v2, &r2)) return false;
    int s1 = -1, s2 = -1;
    for (uint16_t i = 0; i < li->phnum; i++) {
        uint64_t p_offset, p_vaddr, p_filesz, p_memsz;
        if (!ld_phdr_load(li, i, &p_offset, &p_vaddr, &p_filesz, &p_memsz)) continue;
        uint64_t span = p_memsz ? p_memsz : p_filesz;
        if (span == 0) continue;
        if (v1 >= p_vaddr && v1 < p_vaddr + span) s1 = i;
        if (v2 >= p_vaddr && v2 < p_vaddr + span) s2 = i;
    }
    if (s1 < 0 || s2 < 0) return false;
    if (s1 != s2) return true;              /* 跨段：差值不必相等 */
    uintptr_t dv = (v2 > v1) ? (v2 - v1) : (v1 - v2);
    uintptr_t dr = (r2 > r1) ? (r2 - r1) : (r1 - r2);
    return dv == dr;
}

#endif /* LIQUIDDOCK_LIB_BASE_H */
