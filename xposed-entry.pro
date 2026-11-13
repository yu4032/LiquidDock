# API 102 loads the module entry through META-INF/xposed/java_init.list,
# not through Android's manifest. R8 cannot infer that text-based entry as a
# Java call site. Keep the exact entry class name in every optimized variant.
# Only this loader entry is pinned; all internal hook/rendering code remains
# eligible for R8 shrinking and obfuscation.
-keep class com.hellovoid.liquiddock.ModuleMain { *; }

# Keep R8's entry-list contents consistent if another entry is ever obfuscated.
-adaptresourcefilecontents META-INF/xposed/java_init.list
-dontwarn io.github.libxposed.annotation.**
