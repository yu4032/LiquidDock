#ifndef LD_SCREEN_ROTATION_JNI_H
#define LD_SCREEN_ROTATION_JNI_H

/* Only called from RotationHelper.updateRotation's event observer. There is
 * no worker, timer or JNI work in home_margins' currentConfig hot path.
 *
 * Display.getRotation and Display.getSize(Point) are paired before/after the
 * size read: if the framework has not completed the display transaction,
 * decline to publish dimensions for this event. This is intentionally
 * fail-closed, leaving the GridConfig enum as fallback. */
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include "screen_rotation_math.h"

static pthread_mutex_t g_ld_screen_java_mu = PTHREAD_MUTEX_INITIALIZER;
static atomic_int g_ld_screen_java_ready = ATOMIC_VAR_INIT(0);
static JavaVM *g_ld_screen_vm;
static jobject g_ld_screen_display;
static jclass g_ld_screen_point_cls;
static jmethodID g_ld_screen_point_ctor;
static jmethodID g_ld_screen_get_rotation;
static jmethodID g_ld_screen_get_size;
static jfieldID g_ld_screen_point_x;
static jfieldID g_ld_screen_point_y;

static int ld_screen_jni_clear(JNIEnv *env)
{
    if (!(*env)->ExceptionCheck(env)) return 0;
    (*env)->ExceptionClear(env);
    return 1;
}

static int ld_screen_jni_prepare(JNIEnv *env, JavaVM *vm)
{
    if ((*env)->PushLocalFrame(env, 32) != JNI_OK) {
        (void)ld_screen_jni_clear(env);
        return 0;
    }
    int ok = 0;
    jclass at = (*env)->FindClass(env, "android/app/ActivityThread");
    if (!at || ld_screen_jni_clear(env)) goto done;
    jmethodID current = (*env)->GetStaticMethodID(env, at,
                              "currentApplication", "()Landroid/app/Application;");
    if (!current || ld_screen_jni_clear(env)) goto done;
    jobject app = (*env)->CallStaticObjectMethod(env, at, current);
    if (!app || ld_screen_jni_clear(env)) goto done;
    jclass context = (*env)->FindClass(env, "android/content/Context");
    if (!context || ld_screen_jni_clear(env)) goto done;
    jmethodID get_service = (*env)->GetMethodID(env, context, "getSystemService",
                                      "(Ljava/lang/String;)Ljava/lang/Object;");
    if (!get_service || ld_screen_jni_clear(env)) goto done;
    jstring key = (*env)->NewStringUTF(env, "display");
    if (!key || ld_screen_jni_clear(env)) goto done;
    jobject dm = (*env)->CallObjectMethod(env, app, get_service, key);
    if (!dm || ld_screen_jni_clear(env)) goto done;
    jclass dm_class = (*env)->FindClass(env, "android/hardware/display/DisplayManager");
    if (!dm_class || ld_screen_jni_clear(env)) goto done;
    jmethodID get_display = (*env)->GetMethodID(env, dm_class, "getDisplay",
                                       "(I)Landroid/view/Display;");
    if (!get_display || ld_screen_jni_clear(env)) goto done;
    jobject display = (*env)->CallObjectMethod(env, dm, get_display, (jint)0);
    if (!display || ld_screen_jni_clear(env)) goto done;

    jclass display_class = (*env)->FindClass(env, "android/view/Display");
    jclass point_class = (*env)->FindClass(env, "android/graphics/Point");
    if (!display_class || !point_class || ld_screen_jni_clear(env)) goto done;
    jmethodID rotation = (*env)->GetMethodID(env, display_class, "getRotation", "()I");
    jmethodID get_size = (*env)->GetMethodID(env, display_class,
                                           "getSize", "(Landroid/graphics/Point;)V");
    jmethodID ctor = (*env)->GetMethodID(env, point_class, "<init>", "()V");
    jfieldID x = (*env)->GetFieldID(env, point_class, "x", "I");
    jfieldID y = (*env)->GetFieldID(env, point_class, "y", "I");
    if (!rotation || !get_size || !ctor || !x || !y ||
        ld_screen_jni_clear(env)) goto done;

    jobject display_ref = (*env)->NewGlobalRef(env, display);
    jclass point_ref = (jclass)(*env)->NewGlobalRef(env, point_class);
    if (!display_ref || !point_ref || ld_screen_jni_clear(env)) {
        if (display_ref) (*env)->DeleteGlobalRef(env, display_ref);
        if (point_ref) (*env)->DeleteGlobalRef(env, point_ref);
        goto done;
    }
    g_ld_screen_display = display_ref;
    g_ld_screen_point_cls = point_ref;
    g_ld_screen_point_ctor = ctor;
    g_ld_screen_get_rotation = rotation;
    g_ld_screen_get_size = get_size;
    g_ld_screen_point_x = x;
    g_ld_screen_point_y = y;
    g_ld_screen_vm = vm;
    atomic_store_explicit(&g_ld_screen_java_ready, 1, memory_order_release);
    ok = 1;
done:
    (void)ld_screen_jni_clear(env);
    (void)(*env)->PopLocalFrame(env, NULL);
    return ok;
}

/* Returns the launcher display size paired with the event's Surface
 * rotation. A failed/lagging read is UNKNOWN and never reuses old dimensions. */
static struct ld_screen_dimensions ld_screen_query_framework_dimensions(int event_rotation)
{
    struct ld_screen_dimensions result = {0, 0, -1};
    JavaVM *vm = g_ld_screen_vm;
    if (!vm) vm = (JavaVM *)atomic_load_explicit(&g_ld_jvm_ptr, memory_order_acquire);
    if (!vm) vm = ld_find_vm_from_maps();
    if (!vm) return result;

    JNIEnv *env = NULL;
    int attached = 0;
    jint status = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) return result;
        attached = 1;
    } else if (status != JNI_OK || !env) return result;

    if (!atomic_load_explicit(&g_ld_screen_java_ready, memory_order_acquire)) {
        pthread_mutex_lock(&g_ld_screen_java_mu);
        if (!atomic_load_explicit(&g_ld_screen_java_ready, memory_order_relaxed))
            (void)ld_screen_jni_prepare(env, vm);
        pthread_mutex_unlock(&g_ld_screen_java_mu);
    }

    if (atomic_load_explicit(&g_ld_screen_java_ready, memory_order_acquire) &&
        (*env)->PushLocalFrame(env, 8) == JNI_OK) {
        jint before = (*env)->CallIntMethod(env, g_ld_screen_display,
                                           g_ld_screen_get_rotation);
        if (!ld_screen_jni_clear(env)) {
            jobject point = (*env)->NewObject(env, g_ld_screen_point_cls,
                                               g_ld_screen_point_ctor);
            if (point && !ld_screen_jni_clear(env)) {
                (*env)->CallVoidMethod(env, g_ld_screen_display,
                                      g_ld_screen_get_size, point);
                if (!ld_screen_jni_clear(env)) {
                    jint width = (*env)->GetIntField(env, point, g_ld_screen_point_x);
                    jint height = (*env)->GetIntField(env, point, g_ld_screen_point_y);
                    jint after = (*env)->CallIntMethod(env, g_ld_screen_display,
                                                         g_ld_screen_get_rotation);
                    if (!ld_screen_jni_clear(env) &&
                        before == event_rotation && after == event_rotation) {
                        result.width = width;
                        result.height = height;
                        result.rotation = event_rotation;
                    }
                }
            }
        }
        (void)ld_screen_jni_clear(env);
        (void)(*env)->PopLocalFrame(env, NULL);
    } else {
        (void)ld_screen_jni_clear(env);
    }

    if (attached) (*vm)->DetachCurrentThread(vm);
    return result;
}
#endif
