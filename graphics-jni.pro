# graphics-core registers all of these annotated bindings in JNI_OnLoad. Its conditional
# consumer rules can stop matching once R8 inlines EGLExt/SyncFenceCompat facades.
# Retain the JNI contract even when those Kotlin facade classes disappear.
-keep @androidx.graphics.utils.JniVisible class *
-keepclasseswithmembers class * {
    @androidx.graphics.utils.JniVisible *;
}
