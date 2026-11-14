#pragma once

#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

static atomic_int g_os4_sync_requested = 0;
static atomic_uintptr_t g_ld_jvm_ptr = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    atomic_store(&g_ld_jvm_ptr, (uintptr_t)vm);
    return JNI_VERSION_1_6;
}

static JavaVM *ld_find_vm_from_maps(void)
{
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return NULL;
    char line[512];
    uintptr_t map_base = 0;
    char path[256] = {0};
    while (fgets(line, sizeof line, fp)) {
        unsigned long long start = 0, end = 0, off = 0;
        char perms[8] = {0};
        if (sscanf(line, "%llx-%llx %7s %llx %*s %*s %255s",
                   &start, &end, perms, &off, path) == 5 &&
            off == 0 && strstr(path, "/libandroid_runtime.so")) {
            map_base = (uintptr_t)start;
            break;
        }
    }
    fclose(fp);
    if (!map_base || !path[0]) {
        ld_blog("OS4 auto-sync: maps miss libandroid_runtime.so");
        return NULL;
    }

    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        ld_blog("OS4 auto-sync: open %s failed", path);
        return NULL;
    }
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size < (off_t)sizeof(Elf64_Ehdr)) {
        close(fd);
        return NULL;
    }
    const unsigned char *base = mmap(NULL, (size_t)st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (base == MAP_FAILED) {
        ld_blog("OS4 auto-sync: mmap %s failed", path);
        return NULL;
    }

    const Elf64_Ehdr *eh = (const Elf64_Ehdr *)base;
    uintptr_t fn_get_vms = 0;
    uintptr_t fn_ar_vm = 0;
    if (eh->e_ident[EI_CLASS] == ELFCLASS64 && eh->e_shoff > 0 &&
        eh->e_shoff + (size_t)eh->e_shnum * sizeof(Elf64_Shdr) <= (size_t)st.st_size) {
        const Elf64_Shdr *sh = (const Elf64_Shdr *)(base + eh->e_shoff);
        for (uint16_t i = 0; i < eh->e_shnum; i++) {
            if (sh[i].sh_type != SHT_DYNSYM || sh[i].sh_link >= eh->e_shnum) continue;
            const Elf64_Shdr *str_sh = &sh[sh[i].sh_link];
            if (sh[i].sh_offset + sh[i].sh_size > (size_t)st.st_size ||
                str_sh->sh_offset + str_sh->sh_size > (size_t)st.st_size) continue;
            const Elf64_Sym *syms = (const Elf64_Sym *)(base + sh[i].sh_offset);
            const char *strs = (const char *)(base + str_sh->sh_offset);
            size_t n = sh[i].sh_size / sizeof(Elf64_Sym);
            for (size_t k = 0; k < n; k++) {
                if (syms[k].st_name >= str_sh->sh_size || syms[k].st_value == 0) continue;
                const char *name = strs + syms[k].st_name;
                if (strcmp(name, "JNI_GetCreatedJavaVMs") == 0) {
                    fn_get_vms = map_base + (uintptr_t)syms[k].st_value;
                } else if (strcmp(name, "AndroidRuntimeGetJavaVM") == 0) {
                    fn_ar_vm = map_base + (uintptr_t)syms[k].st_value;
                }
            }
            if (fn_get_vms) break;
        }
    }
    munmap((void *)base, (size_t)st.st_size);
    JavaVM *vm = NULL;
    if (fn_get_vms) {
        typedef jint (*get_vms_fn)(JavaVM **, jsize, jsize *);
        jsize count = 0;
        jint rc = ((get_vms_fn)fn_get_vms)(&vm, 1, &count);
        if (rc == JNI_OK && count > 0 && vm) return vm;
        vm = NULL;
    }
    if (fn_ar_vm) {
        typedef JavaVM *(*ar_get_vm_fn)(void);
        vm = ((ar_get_vm_fn)fn_ar_vm)();
    }
    return vm;
}

static void *ld_os4_sync_thread(void *arg)
{
    (void)arg;
    JavaVM *vm = (JavaVM *)atomic_load(&g_ld_jvm_ptr);
    if (!vm) {
        typedef JavaVM *(*ar_get_vm_fn)(void);
        typedef jint (*get_vms_fn)(JavaVM **, jsize, jsize *);
        ar_get_vm_fn ar_get_vm = NULL;
        get_vms_fn get_vms = NULL;
        const char *rt_names[] = {
            "libandroid_runtime.so",
            "/system/lib64/libandroid_runtime.so",
            "libart.so",
            "/apex/com.android.art/lib64/libart.so",
            NULL
        };
        for (int i = 0; rt_names[i] && !ar_get_vm && !get_vms; i++) {
            void *h = dlopen(rt_names[i], RTLD_NOLOAD | RTLD_NOW);
            if (!h) continue;
            ar_get_vm = (ar_get_vm_fn)dlsym(h, "AndroidRuntimeGetJavaVM");
            if (!ar_get_vm) ar_get_vm = (ar_get_vm_fn)dlsym(h, "_ZN7android14AndroidRuntime9getJavaVMEv");
            if (!ar_get_vm) get_vms = (get_vms_fn)dlsym(h, "JNI_GetCreatedJavaVMs");
        }
        if (!ar_get_vm) ar_get_vm = (ar_get_vm_fn)dlsym(RTLD_DEFAULT, "AndroidRuntimeGetJavaVM");
        if (!get_vms) get_vms = (get_vms_fn)dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs");
        jsize count = 0;
        for (int i = 0; i < 25; i++) {
            vm = (JavaVM *)atomic_load(&g_ld_jvm_ptr);
            if (vm) break;
            vm = ld_find_vm_from_maps();
            if (vm) break;
            if (ar_get_vm) {
                vm = ar_get_vm();
                if (vm) break;
            }
            if (get_vms && get_vms(&vm, 1, &count) == JNI_OK && vm && count > 0) break;
            vm = NULL;
            usleep(200000);
        }
    }
    if (!vm) {
        ld_blog("OS4 auto-sync: JavaVM unavailable");
        return NULL;
    }
    JNIEnv *env = NULL;
    int attached = 0;
    jint env_rc = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
    if (env_rc == JNI_EDETACHED) {
        if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) {
            ld_blog("OS4 auto-sync: AttachCurrentThread failed");
            return NULL;
        }
        attached = 1;
    } else if (env_rc != JNI_OK || !env) {
        ld_blog("OS4 auto-sync: GetEnv failed rc=%d", env_rc);
        return NULL;
    }

    jclass at_cls = (*env)->FindClass(env, "android/app/ActivityThread");
    jmethodID cur_app_mid = at_cls
        ? (*env)->GetStaticMethodID(env, at_cls, "currentApplication", "()Landroid/app/Application;")
        : NULL;
    jobject app = NULL;
    for (int i = 0; i < 25 && cur_app_mid; i++) {
        app = (*env)->CallStaticObjectMethod(env, at_cls, cur_app_mid);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); app = NULL; }
        if (app) break;
        usleep(200000);
    }
    if (!app) {
        ld_blog("OS4 auto-sync: currentApplication unavailable");
        goto done;
    }

    jclass ctx_cls = (*env)->FindClass(env, "android/content/Context");
    jmethodID get_cr_mid = ctx_cls
        ? (*env)->GetMethodID(env, ctx_cls, "getContentResolver", "()Landroid/content/ContentResolver;")
        : NULL;
    jobject cr = get_cr_mid ? (*env)->CallObjectMethod(env, app, get_cr_mid) : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); cr = NULL; }

    jclass uri_cls = (*env)->FindClass(env, "android/net/Uri");
    jmethodID parse_mid = uri_cls
        ? (*env)->GetStaticMethodID(env, uri_cls, "parse", "(Ljava/lang/String;)Landroid/net/Uri;")
        : NULL;
    jstring uri_str = (*env)->NewStringUTF(env, "content://com.hellovoid.liquiddock.os4sync");
    jobject uri = (parse_mid && uri_str)
        ? (*env)->CallStaticObjectMethod(env, uri_cls, parse_mid, uri_str)
        : NULL;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); uri = NULL; }

    jclass cr_cls = cr ? (*env)->GetObjectClass(env, cr) : NULL;
    jmethodID call_mid = cr_cls
        ? (*env)->GetMethodID(env, cr_cls, "call",
              "(Landroid/net/Uri;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;")
        : NULL;
    jstring method_str = (*env)->NewStringUTF(env, "sync");
    if (cr && uri && call_mid && method_str) {
        ld_blog("OS4 auto-sync: calling content://com.hellovoid.liquiddock.os4sync");
        jobject res = (*env)->CallObjectMethod(env, cr, call_mid, uri, method_str, NULL, NULL);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            ld_blog("OS4 auto-sync: ContentResolver.call threw exception");
        } else {
            ld_blog("OS4 auto-sync: ContentResolver.call completed res=%p", res);
            if (res) (*env)->DeleteLocalRef(env, res);
        }
    } else {
        ld_blog("OS4 auto-sync: JNI lookup failed cr=%p uri=%p call=%p", cr, uri, call_mid);
    }

    if (method_str) (*env)->DeleteLocalRef(env, method_str);
    if (cr_cls) (*env)->DeleteLocalRef(env, cr_cls);
    if (uri) (*env)->DeleteLocalRef(env, uri);
    if (uri_str) (*env)->DeleteLocalRef(env, uri_str);
    if (uri_cls) (*env)->DeleteLocalRef(env, uri_cls);
    if (cr) (*env)->DeleteLocalRef(env, cr);
    if (ctx_cls) (*env)->DeleteLocalRef(env, ctx_cls);
    (*env)->DeleteLocalRef(env, app);

done:
    if (at_cls) (*env)->DeleteLocalRef(env, at_cls);
    if (attached) (*vm)->DetachCurrentThread(vm);
    return NULL;
}

static inline void ld_request_os4_sync_async(void)
{
    int expected = 0;
    if (!atomic_compare_exchange_strong(&g_os4_sync_requested, &expected, 1)) return;
    pthread_t tid;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (pthread_create(&tid, &attr, ld_os4_sync_thread, NULL) == 0) {
        ld_blog("OS4 auto-sync: background trigger scheduled");
    } else {
        ld_blog("OS4 auto-sync: pthread_create failed");
    }
    pthread_attr_destroy(&attr);
}
