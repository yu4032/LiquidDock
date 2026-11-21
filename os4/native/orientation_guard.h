#ifndef LD_ORIENTATION_GUARD_H
#define LD_ORIENTATION_GUARD_H

/*
 * Hook-free framework rotation for the primary Launcher display.
 * The sole authority is Display.getRotation() normalized with Xiaomi
 * Display.getInstallOrientation(). A Dart grid enum is only a config match,
 * never a source of truth. Missing framework data means UNKNOWN.
 */
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>

#define LD_ORIENT_UNKNOWN (-1)
#define LD_ORIENT_MAX_AGE_NS UINT64_C(250000000)

/* Packed atomic: low byte = normalized rotation + 1, upper bits = generation. */
static atomic_uint_fast64_t g_ld_orient_word = ATOMIC_VAR_INIT(0);
static atomic_uint_fast64_t g_ld_orient_sample_ns = ATOMIC_VAR_INIT(0);
static atomic_int g_ld_orient_reader_started = ATOMIC_VAR_INIT(0);
static atomic_int g_ld_orient_java_ready = ATOMIC_VAR_INIT(0);
static JavaVM *g_ld_orient_vm;
static jobject g_ld_orient_display;
static jmethodID g_ld_orient_get_rotation;
static jmethodID g_ld_orient_get_install;

static inline int ld_orientation_normalize(int rotation, int install)
{
    if ((unsigned)rotation > 3u || (unsigned)install > 3u) return LD_ORIENT_UNKNOWN;
    return (rotation + install) & 3;
}

static inline uint64_t ld_orientation_now_ns(void)
{
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) return 0;
    return (uint64_t)ts.tv_sec * UINT64_C(1000000000) + (uint64_t)ts.tv_nsec;
}

static void ld_orientation_publish(int normalized)
{
    if ((unsigned)normalized > 3u) return;
    uint64_t prev = atomic_load_explicit(&g_ld_orient_word, memory_order_relaxed);
    uint64_t next;
    do {
        uint64_t encoded = (uint64_t)(normalized + 1);
        if ((prev & 0xffu) == encoded) break;
        next = (((prev >> 8) + 1) << 8) | encoded;
    } while (!atomic_compare_exchange_weak_explicit(&g_ld_orient_word, &prev, next,
                memory_order_release, memory_order_relaxed));
    atomic_store_explicit(&g_ld_orient_sample_ns, ld_orientation_now_ns(), memory_order_release);
}

static inline uint64_t ld_orientation_generation(void)
{
    return atomic_load_explicit(&g_ld_orient_word, memory_order_acquire) >> 8;
}

static inline int ld_orientation_guard_rotation(void)
{
    uint64_t now = ld_orientation_now_ns();
    uint64_t stamp = atomic_load_explicit(&g_ld_orient_sample_ns, memory_order_acquire);
    if (!now || !stamp || now < stamp || now - stamp > LD_ORIENT_MAX_AGE_NS)
        return LD_ORIENT_UNKNOWN;
    uint64_t word = atomic_load_explicit(&g_ld_orient_word, memory_order_acquire);
    unsigned encoded = (unsigned)(word & 0xffu);
    return encoded >= 1 && encoded <= 4 ? (int)encoded - 1 : LD_ORIENT_UNKNOWN;
}

static inline int ld_orientation_guard_direction(void)
{
    int rotation = ld_orientation_guard_rotation();
    return rotation < 0 ? LD_ORIENT_UNKNOWN : (rotation & 1);
}

/* A non-active Dart grid must not be treated as the display's active direction. */
static inline int ld_orientation_guard_for_grid(int grid_enum)
{
    int direction = ld_orientation_guard_direction();
    return direction >= 0 && direction == grid_enum ? direction : LD_ORIENT_UNKNOWN;
}

static inline int ld_orientation_guard_generation_matches(uint64_t generation)
{
    return generation != 0 && generation == ld_orientation_generation()
           && ld_orientation_guard_rotation() >= 0;
}

static inline int ld_orientation_jni_clear(JNIEnv *env)
{
    if (!(*env)->ExceptionCheck(env)) return 0;
    (*env)->ExceptionClear(env);
    return 1;
}

/* Runs on the dedicated attached worker; local refs are released every attempt. */
static int ld_orientation_init_java(JNIEnv *env, JavaVM *vm)
{
    int ok = 0;
    if ((*env)->PushLocalFrame(env, 32) < 0) return 0;
    jclass thread_cls = (*env)->FindClass(env, "android/app/ActivityThread");
    if (!thread_cls || ld_orientation_jni_clear(env)) goto done;
    jmethodID current_app = (*env)->GetStaticMethodID(env, thread_cls,
            "currentApplication", "()Landroid/app/Application;");
    if (!current_app || ld_orientation_jni_clear(env)) goto done;
    jobject app = (*env)->CallStaticObjectMethod(env, thread_cls, current_app);
    if (ld_orientation_jni_clear(env) || !app) goto done;
    jclass ctx_cls = (*env)->FindClass(env, "android/content/Context");
    if (!ctx_cls || ld_orientation_jni_clear(env)) goto done;
    jmethodID get_service = (*env)->GetMethodID(env, ctx_cls,
            "getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;");
    if (!get_service || ld_orientation_jni_clear(env)) goto done;
    jstring key = (*env)->NewStringUTF(env, "display");
    if (!key) goto done;
    jobject manager = (*env)->CallObjectMethod(env, app, get_service, key);
    if (ld_orientation_jni_clear(env) || !manager) goto done;
    jclass manager_cls = (*env)->FindClass(env, "android/hardware/display/DisplayManager");
    if (!manager_cls || ld_orientation_jni_clear(env)) goto done;
    jmethodID get_display = (*env)->GetMethodID(env, manager_cls,
            "getDisplay", "(I)Landroid/view/Display;");
    if (!get_display || ld_orientation_jni_clear(env)) goto done;
    jobject display = (*env)->CallObjectMethod(env, manager, get_display, (jint)0);
    if (ld_orientation_jni_clear(env) || !display) goto done;
    jclass display_cls = (*env)->FindClass(env, "android/view/Display");
    if (!display_cls || ld_orientation_jni_clear(env)) goto done;
    jmethodID get_rotation = (*env)->GetMethodID(env, display_cls, "getRotation", "()I");
    if (!get_rotation || ld_orientation_jni_clear(env)) goto done;
    jmethodID get_install = (*env)->GetMethodID(env, display_cls,
            "getInstallOrientation", "()I");
    if (!get_install || ld_orientation_jni_clear(env)) goto done;
    jobject global = (*env)->NewGlobalRef(env, display);
    if (!global) goto done;
    g_ld_orient_display = global;
    g_ld_orient_get_rotation = get_rotation;
    g_ld_orient_get_install = get_install;
    g_ld_orient_vm = vm;
    atomic_store_explicit(&g_ld_orient_java_ready, 1, memory_order_release);
    ok = 1;
done:
    (void)(*env)->PopLocalFrame(env, NULL);
    return ok;
}

static int ld_orientation_sample(JNIEnv *env)
{
    if (!atomic_load_explicit(&g_ld_orient_java_ready, memory_order_acquire)) return -1;
    jint rotation = (*env)->CallIntMethod(env, g_ld_orient_display, g_ld_orient_get_rotation);
    if (ld_orientation_jni_clear(env)) return -1;
    jint install = (*env)->CallIntMethod(env, g_ld_orient_display, g_ld_orient_get_install);
    if (ld_orientation_jni_clear(env)) return -1;
    int normalized = ld_orientation_normalize(rotation, install);
    if (normalized < 0) return -1;
    ld_orientation_publish(normalized);
    return normalized;
}

/* Synchronous framework check at the rare layout transaction/commit boundary. */
static int ld_orientation_guard_refresh_now(void)
{
    if (!atomic_load_explicit(&g_ld_orient_java_ready, memory_order_acquire))
        return LD_ORIENT_UNKNOWN;
    JavaVM *vm = g_ld_orient_vm;
    if (!vm) return LD_ORIENT_UNKNOWN;
    JNIEnv *env = NULL;
    int attached = 0;
    jint rc = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) return LD_ORIENT_UNKNOWN;
        attached = 1;
    } else if (rc != JNI_OK || !env) {
        return LD_ORIENT_UNKNOWN;
    }
    int rotation = ld_orientation_sample(env);
    if (attached) (*vm)->DetachCurrentThread(vm);
    return rotation < 0 ? LD_ORIENT_UNKNOWN : (rotation & 1);
}

static void *ld_orientation_reader(void *unused)
{
    (void)unused;
    JavaVM *vm = NULL;
    for (int attempt = 0; attempt < 200 && !vm; ++attempt) {
        vm = (JavaVM *)atomic_load(&g_ld_jvm_ptr);
        if (!vm) vm = ld_find_vm_from_maps();
        if (!vm) usleep(50000);
    }
    if (!vm) {
        ld_blog("orientation guard: JVM unavailable; modifications fail closed");
        return NULL;
    }
    JNIEnv *env = NULL;
    if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK || !env) return NULL;
    for (int attempt = 0; attempt < 200 && !atomic_load(&g_ld_orient_java_ready); ++attempt) {
        if (ld_orientation_init_java(env, vm)) break;
        usleep(50000);
    }
    if (!atomic_load(&g_ld_orient_java_ready)) {
        ld_blog("orientation guard: Display or install orientation unavailable");
        (*vm)->DetachCurrentThread(vm);
        return NULL;
    }
    ld_blog("orientation guard: framework Display ready; no orientation hook");
    int last = LD_ORIENT_UNKNOWN;
    for (;;) {
        int rotation = ld_orientation_sample(env);
        if (rotation >= 0 && rotation != last) {
            ld_blog("orientation guard: normalized rotation=%d direction=%d generation=%llu",
                    rotation, rotation & 1, (unsigned long long)ld_orientation_generation());
            last = rotation;
        }
        usleep(16000);
    }
    return NULL;
}

static void ld_orientation_guard_start(void)
{
    int expected = 0;
    if (!atomic_compare_exchange_strong(&g_ld_orient_reader_started, &expected, 1))
        return;
    pthread_t thread;
    if (pthread_create(&thread, NULL, ld_orientation_reader, NULL) == 0)
        pthread_detach(thread);
    else {
        atomic_store(&g_ld_orient_reader_started, 0);
        ld_blog("orientation guard: unable to start framework reader");
    }
}
#endif
