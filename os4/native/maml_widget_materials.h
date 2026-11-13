#ifndef LD_MAML_WIDGET_MATERIALS_H
#define LD_MAML_WIDGET_MATERIALS_H

#include "dart_heap.h"
#include "lib_base.h"
#include "native_api.h"
#include "sha256_region.h"
#include "maml_widget_materials_fingerprints.h"

#include <dlfcn.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>

#define LD_WIDGET_FILES "/data/user_de/0/com.miui.home/files/"
#define LD_WIDGET_MAP LD_WIDGET_FILES "liquiddock-widget-overrides.conf"
#define LD_WIDGET_INVENTORY LD_WIDGET_FILES "liquiddock-widget-sources.txt"
#define LD_WIDGET_SHADOWS LD_WIDGET_FILES "liquiddock-widget-shadows/"
#define LD_WIDGET_MAX 64
#define LD_WIDGET_PATH 512

struct ld_widget_map_entry {
    char source_hash[65], shadow_hash[65];
    char source[LD_WIDGET_PATH], shadow[LD_WIDGET_PATH];
    unsigned char verified;
};

static void (*g_maml_init_original)(const void *);
static struct ld_widget_map_entry g_widget_map[LD_WIDGET_MAX];
static size_t g_widget_map_count;
static int g_widget_map_loaded, g_widget_map_valid;
static int g_widget_map_exists;
static dev_t g_widget_map_dev;
static ino_t g_widget_map_ino;
static off_t g_widget_map_size;
static time_t g_widget_map_mtime;
static long g_widget_map_mtime_ns;
static char g_widget_sources[LD_WIDGET_MAX][LD_WIDGET_PATH];
static size_t g_widget_source_count;
static int g_widget_inventory_reset;
static pthread_mutex_t g_widget_materials_lock=PTHREAD_MUTEX_INITIALIZER;

static int ld_maml_hex_valid(const char *s)
{
    if (!s || strlen(s)!=64) return 0;
    for (size_t i=0;i<64;i++)
        if (!((s[i]>='0'&&s[i]<='9')||(s[i]>='a'&&s[i]<='f'))) return 0;
    return 1;
}

static void ld_maml_hex(const unsigned char digest[32], char out[65])
{
    static const char d[]="0123456789abcdef";
    for (size_t i=0;i<32;i++) { out[2*i]=d[digest[i]>>4]; out[2*i+1]=d[digest[i]&15]; }
    out[64]=0;
}

static int ld_widget_source_valid(const char *path)
{
    static const char *const roots[]={
        "/data/user/0/com.miui.home/files/maml/res/",
        "/data/user_de/0/com.miui.home/files/maml/res/"
    };
    if (!path || strlen(path)>=LD_WIDGET_PATH) return 0;
    int under=0;
    for(size_t i=0;i<2;i++) if(!strncmp(path,roots[i],strlen(roots[i]))) under=1;
    if(!under) return 0;
    for(const char *p=path;*p;) {
        while(*p=='/') ++p;
        const char *start=p;
        while(*p && *p!='/') {
            if(*p=='\\' || (unsigned char)*p<0x20 || *p=='\t') return 0;
            ++p;
        }
        size_t part=(size_t)(p-start);
        if((part==1 && start[0]=='.') || (part==2 && start[0]=='.' && start[1]=='.')) return 0;
    }
    const char *base=strrchr(path,'/');
    if(!base || strncmp(base+1,"widget_",7)) return 0;
    base+=8;
    return base[0]>='1' && base[0]<='9' && base[1]=='x'
        && base[2]>='1' && base[2]<='9' && base[3]=='\0';
}

static int ld_widget_hash(const char *path, char hex[65])
{
    struct stat lst,st; unsigned char digest[32],block[65536];
    if(!path || lstat(path,&lst)!=0 || !S_ISREG(lst.st_mode)) return 0;
    int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);
    if(fd<0 || fstat(fd,&st)!=0 || !S_ISREG(st.st_mode) || st.st_size<=0
            || st.st_dev!=lst.st_dev || st.st_ino!=lst.st_ino) { if(fd>=0) close(fd); return 0; }
    struct ld_sha256 sha; ld_sha256_init(&sha);
    for(;;) {
        ssize_t n=read(fd,block,sizeof block);
        if(n<0) { close(fd); return 0; }
        if(!n) break;
        ld_sha256_update(&sha,block,(size_t)n);
    }
    close(fd); ld_sha256_final(&sha,digest);
    ld_maml_hex(digest,hex); return 1;
}

static int ld_widget_read_string(uintptr_t req,size_t len_off,size_t ptr_off,char out[LD_WIDGET_PATH])
{
    uint32_t n=0; uintptr_t ptr=0; out[0]=0;
    if(!ld_read_heap(req+len_off,&n,4)||!ld_read_heap(req+ptr_off,&ptr,8)||n>=LD_WIDGET_PATH||(!ptr&&n)) return 0;
    if(n && !ld_read_heap(ptr,out,n)) return 0;
    if(n && memchr(out,'\0',n)) return 0;
    out[n]=0;
    return 1;
}

static int ld_widget_map_load(void)
{
    struct stat lst,st;
    if(lstat(LD_WIDGET_MAP,&lst)!=0) {
        g_widget_map_loaded=1; g_widget_map_exists=0; g_widget_map_valid=0; g_widget_map_count=0;
        return 0;
    }
    if(!S_ISREG(lst.st_mode) || lst.st_size<=0 || lst.st_size>65536) {
        g_widget_map_loaded=1; g_widget_map_exists=1; g_widget_map_valid=0; g_widget_map_count=0;
        g_widget_map_dev=lst.st_dev; g_widget_map_ino=lst.st_ino; g_widget_map_size=lst.st_size;
        g_widget_map_mtime=lst.st_mtime; g_widget_map_mtime_ns=lst.st_mtim.tv_nsec;
        return 0;
    }
    if(g_widget_map_loaded && g_widget_map_exists && g_widget_map_dev==lst.st_dev
            && g_widget_map_ino==lst.st_ino && g_widget_map_size==lst.st_size
            && g_widget_map_mtime==lst.st_mtime && g_widget_map_mtime_ns==lst.st_mtim.tv_nsec)
        return g_widget_map_valid;
    g_widget_map_loaded=1; g_widget_map_exists=1; g_widget_map_valid=0; g_widget_map_count=0;
    int fd=open(LD_WIDGET_MAP,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);
    if(fd<0 || fstat(fd,&st)!=0 || !S_ISREG(st.st_mode) || st.st_size<=0 || st.st_size>65536
            || st.st_dev!=lst.st_dev || st.st_ino!=lst.st_ino) { if(fd>=0) close(fd); return 0; }
    FILE *f=fdopen(fd,"rb"); if(!f) { close(fd); return 0; }
    char line[1200]; size_t total=0; int header=0,ended=0,ok=1;
    if(!fgets(line,sizeof line,f)) ok=0;
    else { total=strlen(line); header=!strcmp(line,"version=1\n"); }
    if(!header) ok=0;
    while(ok && fgets(line,sizeof line,f)) {
        size_t n=strlen(line); total+=n;
        if(total>65536 || n==0 || line[n-1]!='\n' || ended) { ok=0; break; }
        line[n-1]=0;
        if(!strcmp(line,"end=1")) { ended=1; break; }
        if(g_widget_map_count>=LD_WIDGET_MAX) { ok=0; break; }
        char *a=line,*b=strchr(a,'\t'); if(!b){ok=0;break;} *b++=0;
        char *c=strchr(b,'\t'); if(!c){ok=0;break;} *c++=0;
        char *d=strchr(c,'\t'); if(!d){ok=0;break;} *d++=0;
        if(strchr(d,'\t') || !ld_maml_hex_valid(a)||!ld_maml_hex_valid(b)
                || !ld_widget_source_valid(c) || strlen(d)>=LD_WIDGET_PATH) { ok=0; break; }
        char expected[LD_WIDGET_PATH];
        int wrote=snprintf(expected,sizeof expected,LD_WIDGET_SHADOWS "%s.zip",b);
        if(wrote<0 || (size_t)wrote>=sizeof expected || strcmp(expected,d)) {ok=0;break;}
        for(size_t i=0;i<g_widget_map_count;i++) if(!strcmp(g_widget_map[i].source,c)){ok=0;break;}
        if(!ok) break;
        struct ld_widget_map_entry *e=&g_widget_map[g_widget_map_count++];
        memcpy(e->source_hash,a,65); memcpy(e->shadow_hash,b,65);
        memcpy(e->source,c,strlen(c)+1); memcpy(e->shadow,d,strlen(d)+1);
    }
    if(!ended || ferror(f)) ok=0;
    if(!ended && !feof(f)) ok=0;
    if(ended && fgetc(f)!=EOF) ok=0;
    struct stat after;
    if(fstat(fileno(f),&after)!=0 || after.st_size!=st.st_size || after.st_mtime!=st.st_mtime
            || after.st_mtim.tv_nsec!=st.st_mtim.tv_nsec) ok=0;
    fclose(f);
    g_widget_map_dev=st.st_dev; g_widget_map_ino=st.st_ino; g_widget_map_size=st.st_size;
    g_widget_map_mtime=st.st_mtime; g_widget_map_mtime_ns=st.st_mtim.tv_nsec;
    g_widget_map_valid=ok; return ok;
}

static int ld_widget_inventory_add(const char *path)
{
    if(!ld_widget_source_valid(path)) return 0;
    for(size_t i=0;i<g_widget_source_count;i++) if(!strcmp(g_widget_sources[i],path)) return 1;
    if(g_widget_source_count>=LD_WIDGET_MAX) return 0;
    size_t n=strlen(path);
    if(!g_widget_inventory_reset) {
        int reset_fd=open(LD_WIDGET_INVENTORY,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC|O_NOFOLLOW,0600);
        if(reset_fd<0) return 0;
        struct stat reset_st; int reset_ok=fstat(reset_fd,&reset_st)==0 && S_ISREG(reset_st.st_mode);
        if(close(reset_fd)!=0) reset_ok=0;
        if(!reset_ok) return 0;
        g_widget_inventory_reset=1;
    }
    int append_fd=open(LD_WIDGET_INVENTORY,O_WRONLY|O_APPEND|O_CLOEXEC|O_NOFOLLOW);
    if(append_fd<0) return 0;
    struct stat append_st;
    if(fstat(append_fd,&append_st)!=0 || !S_ISREG(append_st.st_mode)) { close(append_fd); return 0; }
    FILE *f=fdopen(append_fd,"ab"); if(!f) { close(append_fd); return 0; }
    int ok=fwrite(path,1,n,f)==n && fputc('\n',f)!=EOF && fflush(f)==0;
    if(fclose(f)!=0) ok=0;
    if(ok) memcpy(g_widget_sources[g_widget_source_count++],path,n+1);
    return ok;
}

static const char *ld_widget_match(const char *source)
{
    if(!ld_widget_map_load()) { ld_blog("MAML override mapping unavailable/invalid"); return NULL; }
    for(size_t i=0;i<g_widget_map_count;i++) {
        struct ld_widget_map_entry *e=&g_widget_map[i];
        if(strcmp(e->source,source)) continue;
        if(!e->verified) {
            char source_hash[65],shadow_hash[65];
            int source_ok=ld_widget_hash(e->source,source_hash);
            int shadow_ok=ld_widget_hash(e->shadow,shadow_hash);
            if(!source_ok || !shadow_ok || strcmp(source_hash,e->source_hash) || strcmp(shadow_hash,e->shadow_hash)) {
                ld_blog("MAML override hash rejection source_read=%d shadow_read=%d",source_ok,shadow_ok); return NULL;
            }
            e->verified=1;
        }
        return e->shadow;
    }
    return NULL;
}

static void ld_maml_init_replacement(const void *request)
{
    if(!g_maml_init_original) return;
    if(!request) { g_maml_init_original(request); return; }
    uintptr_t base=(uintptr_t)request;
    char source[LD_WIDGET_PATH],kind[LD_WIDGET_PATH];
    uint32_t id=0;
    if(!ld_read_heap(base,&id,4) || !ld_widget_read_string(base,0x04,0x08,source)
            || !ld_widget_read_string(base,0x30,0x38,kind)) {
        g_maml_init_original(request); return;
    }
    int is_widget=!strncmp(kind,"hyper_widget_",13) && ld_widget_source_valid(source);
    if(is_widget) {
        pthread_mutex_lock(&g_widget_materials_lock);
        (void)ld_widget_inventory_add(source);
        pthread_mutex_unlock(&g_widget_materials_lock);
    }
    const char *shadow=NULL;
    if(is_widget && (g_home_options.widget_overrides || (g_home_options.glass_enabled && g_home_options.glass_widgets))) {
        pthread_mutex_lock(&g_widget_materials_lock);
        shadow=ld_widget_match(source);
        pthread_mutex_unlock(&g_widget_materials_lock);
    }
    if(is_widget) ld_blog("MAML widget id=%u overrides=%d glass=%d redirect=%d entries=%zu",
        id,g_home_options.widget_overrides,g_home_options.glass_widgets,shadow!=NULL,g_widget_map_count);
    if(shadow) {
        unsigned char copy[0x68] __attribute__((aligned(16)));
        if(ld_read_heap(base,copy,sizeof copy)) {
            uint32_t n=(uint32_t)strlen(shadow); uintptr_t p=(uintptr_t)shadow;
            memcpy(copy+0x04,&n,4); memcpy(copy+0x08,&p,8);
            g_maml_init_original(copy); return;
        }
    }
    g_maml_init_original(request);
}

static int ld_maml_hash_path(const char *path,unsigned char digest[32])
{
    struct stat st;
    if(stat(path,&st)!=0 || st.st_size<=0 || !S_ISREG(st.st_mode)) return 0;
    return ld_sha256_region(path,0,(size_t)st.st_size,digest);
}


/* 从 SDK 文件解析 dynsym 里符号的 st_value（= vaddr 偏移，ET_DYN）。
 * 注意：section headers 不在 PT_LOAD 里，内存映射读不到，必须读文件。 */
static uintptr_t ld_maml_symbol_offset(const char *name)
{
    FILE *f = fopen(LD_MAML_SDK_PATH, "rb");
    if(!f) return 0;
    unsigned char eh[64];
    if(fread(eh, 1, sizeof eh, f) != sizeof eh) { fclose(f); return 0; }
    uint64_t shoff; uint16_t shentsize, shnum;
    memcpy(&shoff, eh + 0x28, 8);
    memcpy(&shentsize, eh + 0x3a, 2);
    memcpy(&shnum, eh + 0x3c, 2);
    if(!shoff || !shnum || shentsize < 64 || shnum > 512) { fclose(f); return 0; }
    unsigned char *sh = (unsigned char *)calloc(shnum, shentsize);
    if(!sh) { fclose(f); return 0; }
    if(fseek(f, (long)shoff, SEEK_SET) != 0 ||
       fread(sh, shentsize, shnum, f) != shnum) { free(sh); fclose(f); return 0; }
    uintptr_t found = 0;
    for(uint16_t i = 0; i < shnum && !found; i++) {
        unsigned char *sec = sh + (size_t)i * shentsize;
        uint32_t type; memcpy(&type, sec + 4, 4);
        if(type != 11) continue;                       /* SHT_DYNSYM */
        uint64_t off_v, size_v, ent; uint32_t link;
        memcpy(&off_v, sec + 24, 8);
        memcpy(&size_v, sec + 32, 8);
        memcpy(&link, sec + 40, 4);
        memcpy(&ent, sec + 56, 8);
        if(link >= shnum || ent < 24 || !size_v || size_v / ent > 200000) continue;
        unsigned char *strsec = sh + (size_t)link * shentsize;
        uint64_t stroff, strsize;
        memcpy(&stroff, strsec + 24, 8);
        memcpy(&strsize, strsec + 32, 8);
        if(!strsize || strsize > 16u * 1024u * 1024u) continue;
        char *str = (char *)malloc((size_t)strsize + 1);
        if(!str) continue;
        if(fseek(f, (long)stroff, SEEK_SET) == 0 && fread(str, 1, (size_t)strsize, f) == strsize) {
            str[strsize] = 0;
            unsigned char *sym = (unsigned char *)malloc((size_t)size_v);
            if(sym && fseek(f, (long)off_v, SEEK_SET) == 0 &&
               fread(sym, 1, (size_t)size_v, f) == size_v) {
                for(uint64_t k = 0; k + ent <= size_v; k += ent) {
                    uint32_t st_name; memcpy(&st_name, sym + k, 4);
                    if(!st_name || st_name >= strsize) continue;
                    if(strcmp(str + st_name, name) == 0) {
                        uint64_t value; memcpy(&value, sym + k + 8, 8);
                        found = (uintptr_t)value;
                        break;
                    }
                }
            }
            free(sym);
        }
        free(str);
    }
    free(sh);
    fclose(f);
    return found;
}

static int ld_maml_install(const struct native_api *api)
{
    if(!api || !api->hook || g_maml_init_original) return g_maml_init_original!=NULL;
    int strict=g_home_options.hook_safety_enabled;
    unsigned char digest[32],body[LD_MAML_INIT_SIZE]; char hex[65]={0};
    /* SDK 摘要门禁已取消：目标按符号解析，SDK 升级不再禁用小组件玻璃。
     * 摘要只记日志供排障；strict 模式仍做函数体逐字校验。 */
    if(ld_maml_hash_path(LD_MAML_SDK_PATH,digest)) {
        ld_maml_hex(digest,hex);
        ld_blog("MAML SDK build %s%s", hex, strcmp(hex,LD_MAML_SDK_SHA)?" (differs from pin)":"");
    }
    /* 先用已加载库的 handle 解析（SDK 常以局部命名空间 dlopen，
     * RTLD_DEFAULT 看不到）；再退 RTLD_DEFAULT；最后才是固定偏移。 */
    uintptr_t target=0, offset=ld_maml_symbol_offset("init_maml");
    if(offset) {
        uintptr_t base=0; char maps[1024]; FILE *f=fopen("/proc/self/maps","r");
        if(f) {
            while(fgets(maps,sizeof maps,f)) {
                unsigned long start=0,off=1; char perms[5];
                if(strstr(maps,LD_MAML_SDK_PATH) &&
                   sscanf(maps,"%lx-%*lx %4s %lx",&start,perms,&off)==3 && off==0) {base=(uintptr_t)start;break;}
            }
            fclose(f);
        }
        target=base+offset;
        ld_blog("MAML init_maml dynsym offset 0x%lx -> %p", (unsigned long)offset, (void *)target);
    }
    if(!target) target=(uintptr_t)dlsym(RTLD_DEFAULT,"init_maml");
    if(!target) {
        /* 固定偏移只对钉过的 SDK 成立：摘要不匹配时绝不用它（陈旧偏移会 hook 错函数）。 */
        if(!hex[0] || strcmp(hex, LD_MAML_SDK_SHA)) {
            ld_blog("MAML init_maml unresolved and SDK unpinned; skip (hook would land elsewhere)");
            return 0;
        }
        uintptr_t base=0; char maps[1024]; FILE *f=fopen("/proc/self/maps","r");
        if(!f) return 0;
        while(fgets(maps,sizeof maps,f)) {
            unsigned long start=0,off=1; char perms[5];
            if(strstr(maps,LD_MAML_SDK_PATH) && sscanf(maps,"%lx-%*lx %4s %lx",&start,perms,&off)==3 && off==0) {base=(uintptr_t)start;break;}
        }
        fclose(f); if(!base) return 0;
        target=base+LD_MAML_INIT_OFFSET;
        ld_blog("MAML target fallback offset 0x%lx -> %p", (unsigned long)LD_MAML_INIT_OFFSET, (void *)target);
    }
    if(!executable_range(target,strict?LD_MAML_INIT_SIZE:16)) {
        ld_blog("MAML target %p not executable (strict=%d)", (void *)target, strict);
        return 0;
    }
    /* 函数体摘要只记日志（门禁已取消）：按符号解析不存在陈旧偏移风险。 */
    if(ld_read_heap(target,body,sizeof body)) {
        struct ld_sha256 sha; ld_sha256_init(&sha); ld_sha256_update(&sha,body,sizeof body); ld_sha256_final(&sha,digest);
        ld_maml_hex(digest,hex);
        ld_blog("MAML init_maml body %s%s", hex, strcmp(hex,LD_MAML_INIT_SHA)?" (differs from pin)":"");
    }
    (void)strict;
    void *backup=NULL; int rc=api->hook((void*)target,(void*)ld_maml_init_replacement,&backup);
    if(rc || !backup) { ld_blog("MAML init hook failed result=%d",rc); return 0; }
    g_maml_init_original=(void(*)(const void*))backup;
    (void)ld_pin_code_pages(target,16);
    ld_blog("MAML widget resource hook installed safety=%d",strict);
    return 1;
}

#endif
