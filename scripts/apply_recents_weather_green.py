from pathlib import Path

R = Path('src/main/java/com/hellovoid/liquiddock')

def patch(name, pairs):
    p = R / name
    s = p.read_text()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(f'missing anchor {name}: {old[:100]!r}')
        s = s.replace(old, new, 1)
    p.write_text(s)

patch('LauncherGlassRecentsHook.java', [
    ('package com.hellovoid.liquiddock;\n\n',
     'package com.hellovoid.liquiddock;\n\nimport android.os.Handler;\nimport android.os.Looper;\n\n'),
    ('    private static final String RECENTS_DISPATCHER =\n            "com.miui.home.recents.RecentsServiceDispatcher";\n    private static boolean installed;\n',
     '''    private static final String RECENTS_DISPATCHER =\n            "com.miui.home.recents.RecentsServiceDispatcher";\n    // Live Launcher 4.50 logs show wallpaper scale can still be returning to 1.0 for ~500 ms\n    // after onRecentViewHide. Keep one extra frame-budget margin before accepting a fresh scene.\n    private static final long RECENTS_WALLPAPER_SETTLE_MS = 600L;\n    private static boolean installed;\n    private static Handler mainHandler;\n    private static long recentsReturnToken;\n'''),
    ('    static void install(ClassLoader classLoader, LiquidDockConfig config) {\n        if (installed || config == null || !config.enabled || !config.glass.enabled) return;\n        try {\n',
     '    static void install(ClassLoader classLoader, LiquidDockConfig config) {\n        if (installed || config == null || !config.enabled || !config.glass.enabled) return;\n        mainHandler = new Handler(Looper.getMainLooper());\n        try {\n'),
    ('            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewShow", chain -> {\n                // Hide Workspace glass before the vendor starts dispatching the visible Recents state.\n                LauncherGlassSceneController.setRecentsCoveredForAll(true);\n                return chain.proceed(chain.getArgs().toArray(new Object[0]));\n            });\n',
     '''            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewShow", chain -> {\n                // Cancel any delayed HOME release before covering the scene again. Clearing the\n                // settle flag while COVERED cannot schedule a capture.\n                recentsReturnToken++;\n                LauncherGlassSceneController.setRecentsCoveredForAll(true);\n                LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);\n                return chain.proceed(chain.getArgs().toArray(new Object[0]));\n            });\n'''),
    ('                LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn();\n                LauncherGlassSceneController.setRecentsCoveredForAll(false);\n                return result;\n',
     '''                LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn();\n\n                // onRecentViewHide precedes MiuiWallpaperSurfaceAnimation.IDLE on the pure\n                // HOME -> Recents -> HOME path. Arm the capture barrier before uncovering HOME,\n                // then release it after the observed wallpaper scale-to-1.0 tail has settled.\n                long token = ++recentsReturnToken;\n                LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(true);\n                LauncherGlassSceneController.setRecentsCoveredForAll(false);\n                Handler handler = mainHandler;\n                if (handler != null) {\n                    handler.postDelayed(() -> {\n                        if (token != recentsReturnToken) return;\n                        LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);\n                        MainHook.log(TAG + " Recents wallpaper settle released token=" + token);\n                    }, RECENTS_WALLPAPER_SETTLE_MS);\n                } else {\n                    LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);\n                }\n                return result;\n'''),
])

patch('LauncherGlassSceneController.java', [
    ('    private static boolean vendorUnlockTransitionPending;\n',
     '    private static boolean vendorUnlockTransitionPending;\n    private static boolean vendorRecentsWallpaperSettlePending;\n'),
    ('    private boolean unlockTransitionPending;\n',
     '    private boolean unlockTransitionPending;\n    private boolean recentsWallpaperSettlePending;\n'),
    ('        created.unlockTransitionPending = vendorUnlockTransitionPending;\n',
     '        created.unlockTransitionPending = vendorUnlockTransitionPending;\n        created.recentsWallpaperSettlePending = vendorRecentsWallpaperSettlePending;\n'),
    ('    static void setHomeTransitionPendingForAll(boolean pending) {\n',
     '''    static void setRecentsWallpaperSettlePendingForAll(boolean pending) {\n        ArrayList<LauncherGlassSceneController> snapshot;\n        synchronized (LauncherGlassSceneController.class) {\n            vendorRecentsWallpaperSettlePending = pending;\n            snapshot = new ArrayList<>(BY_ROOT.values());\n        }\n        for (LauncherGlassSceneController controller : snapshot) {\n            if (controller != null) controller.setRecentsWallpaperSettlePending(pending);\n        }\n    }\n\n    static void setHomeTransitionPendingForAll(boolean pending) {\n'''),
    ('    private boolean isPresentationPending() {\n        return homeTransitionPending || unlockTransitionPending;\n    }\n',
     '    private boolean isPresentationPending() {\n        return homeTransitionPending || unlockTransitionPending || recentsWallpaperSettlePending;\n    }\n'),
    ('    private void setHomeTransitionPending(boolean pending) {\n',
     '''    private void setRecentsWallpaperSettlePending(boolean pending) {\n        boolean wasPending = isPresentationPending();\n        recentsWallpaperSettlePending = pending;\n        onPresentationPendingChanged(wasPending, isPresentationPending(), "recents-wallpaper");\n    }\n\n    private void setHomeTransitionPending(boolean pending) {\n'''),
])

patch('LauncherMamlBackgroundSuppressor.java', [
    ('    private static final String WEATHER_PRODUCT_ID =\n            "b8006e83-c497-4642-9815-f674b82842b0";\n    private static final String WEATHER_APP_PACKAGE = "com.miui.weather2";\n',
     '''    private static final String WEATHER_PRODUCT_ID =\n            "b8006e83-c497-4642-9815-f674b82842b0";\n    private static final String WEATHER_LARGE_PRODUCT_ID =\n            "c989887f-fa0d-4963-8c57-896c03e37efc";\n    private static final String WEATHER_WIDE_PRODUCT_ID =\n            "bc0f0cd2-43fd-4323-8061-55a8bc997e1f";\n    private static final String WEATHER_APP_PACKAGE = "com.miui.weather2";\n'''),
    ('    private static final String WEATHER_SKY_OWNER_ELEMENT = "skyColor";\n',
     '    private static final String WEATHER_SKY_OWNER_ELEMENT = "skyColor";\n    private static final String WEATHER_BACKGROUND_OWNER_ELEMENT = "background";\n'),
    ('        if (root == null) {\n            MainHook.log(LOG_TAG + identity\n                    + " root=null targetFound=false suppressed=false");\n            return;\n        }\n        Object target = HookUtil.invoke(root, "findElement", WEATHER_SKY_OWNER_ELEMENT);\n        if (target == null) {\n            MainHook.log(LOG_TAG + identity\n                    + " root=" + root.getClass().getSimpleName()\n                    + " target=" + WEATHER_SKY_OWNER_ELEMENT\n                    + " targetFound=false suppressed=false");\n            dumpNamedElementsOnce(productId, root);\n            return;\n        }\n',
     '''        if (root == null) {\n            MainHook.log(LOG_TAG + identity\n                    + " root=null targetFound=false suppressed=false");\n            return;\n        }\n        String ownerElement = resolveWeatherBackgroundOwner(productId);\n        if (ownerElement == null) {\n            release(host);\n            MainHook.log(LOG_TAG + identity\n                    + " root=" + root.getClass().getSimpleName()\n                    + " target=unresolved targetFound=false suppressed=false");\n            dumpNamedElementsOnce(productId, root);\n            return;\n        }\n        Object target = HookUtil.invoke(root, "findElement", ownerElement);\n        if (target == null) {\n            MainHook.log(LOG_TAG + identity\n                    + " root=" + root.getClass().getSimpleName()\n                    + " target=" + ownerElement\n                    + " targetFound=false suppressed=false");\n            dumpNamedElementsOnce(productId, root);\n            return;\n        }\n'''),
    ('        MainHook.log(LOG_TAG + identity\n                + " root=" + root.getClass().getSimpleName()\n                + " target=" + WEATHER_SKY_OWNER_ELEMENT\n                + " targetFound=true suppressed=" + suppressed);\n',
     '        MainHook.log(LOG_TAG + identity\n                + " root=" + root.getClass().getSimpleName()\n                + " target=" + ownerElement\n                + " targetFound=true suppressed=" + suppressed);\n'),
    ('    private static boolean isWeatherIdentity(String productId, String appPackage) {\n        return WEATHER_PRODUCT_ID.equals(productId) || WEATHER_APP_PACKAGE.equals(appPackage);\n    }\n',
     '''    private static boolean isWeatherIdentity(String productId, String appPackage) {\n        return WEATHER_PRODUCT_ID.equals(productId)\n                || WEATHER_LARGE_PRODUCT_ID.equals(productId)\n                || WEATHER_WIDE_PRODUCT_ID.equals(productId)\n                || WEATHER_APP_PACKAGE.equals(appPackage);\n    }\n\n    private static String resolveWeatherBackgroundOwner(String productId) {\n        if (WEATHER_PRODUCT_ID.equals(productId)) return WEATHER_SKY_OWNER_ELEMENT;\n        if (WEATHER_LARGE_PRODUCT_ID.equals(productId)\n                || WEATHER_WIDE_PRODUCT_ID.equals(productId)) {\n            return WEATHER_BACKGROUND_OWNER_ELEMENT;\n        }\n        return null;\n    }\n'''),
])

print('recents/weather GREEN patch applied')
