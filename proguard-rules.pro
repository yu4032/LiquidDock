# This class is accessed via a narrow reflection bridge inside the Settings
# process to bind Prismal's AGSL sources to LiquidDock's APK assets.
# Without keep, R8 may rename its Kotlin object singleton and bind() method.
-keep class com.styropyr0.prismal.internal.PrismalAgslAssets {
    public static final com.styropyr0.prismal.internal.PrismalAgslAssets INSTANCE;
    public final void bind(android.content.res.AssetManager);
}
