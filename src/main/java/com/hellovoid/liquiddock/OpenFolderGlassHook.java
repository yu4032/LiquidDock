package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Native folder lifecycle adapter; rendering and ownership stay in the coordinator. */
final class OpenFolderGlassHook {
    private static final Set<Method> DRAW_HOOKS = new HashSet<>();
    private static final Map<Class<?>, Boolean> DRAW_OBSERVATION = new HashMap<>();
    private static boolean installed;
    private static boolean nativeCloseBackgroundCallbackInstalled;
    private OpenFolderGlassHook() {}

    static boolean isInstalled() { return installed; }

    static void install(ClassLoader loader, LiquidDockConfig config) {
        if (installed || config == null || !config.enabled || !config.glass.enabled) return;
        try {
            Class<?> cling = Class.forName("com.miui.home.launcher.FolderCling", false, loader);
            Class<?> folder = Class.forName("com.miui.home.launcher.Folder", false, loader);
            Class<?> info = Class.forName("com.miui.home.launcher.FolderInfo", false, loader);
            Class<?> launcher = Class.forName("com.miui.home.launcher.Launcher", false, loader);
            Class<?> anim = Class.forName("com.miui.home.launcher.folder.FolderAnimController", false, loader);
            Class<?> icon = Class.forName("com.miui.home.launcher.FolderIcon", false, loader);
            Class<?> grid = Class.forName("com.miui.home.launcher.FolderGridView", false, loader);
            HookUtil.hook(HookUtil.findMethodExact(anim, "setIconBgAnim",
                    new Class<?>[]{boolean.class, icon, grid, int[].class, int[].class, int.class}), chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                safe(() -> OpenFolderGlassCoordinator.onAnimationSpec(Boolean.TRUE.equals(args[0]),
                        (View) args[1], (View) args[2],
                        (int[]) args[3], (int[]) args[4], ((Number) args[5]).intValue()));
                Object result = chain.proceed(args);
                if (!Boolean.TRUE.equals(args[0])) {
                    safe(OpenFolderGlassCoordinator::onClosingAnimationMaterialUpdated);
                }
                return result;
            });
            // The OEM background spring finishes independently of Folder.onClose.
            // Observe its actual completion rather than releasing glass with
            // the content fade or the early Dock dispatchFolderClose event.
            try {
                HookUtil.hook(HookUtil.findMethodExact(anim, "closeFolderAnim",
                        new Class<?>[]{icon, Runnable.class}), chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Runnable original = args[1] instanceof Runnable ? (Runnable) args[1] : null;
                    long nativeSerial = MiuixFolderGlassHook.activeNativeCloseSerial();
                    long glassSerial = OpenFolderGlassCoordinator.activeClosingSerial();
                    args[1] = (Runnable) () -> {
                        try {
                            if (original != null) original.run();
                        } finally {
                            MiuixFolderGlassHook.onNativeBackgroundCloseFinished(nativeSerial);
                            safe(() -> OpenFolderGlassCoordinator.onNativeBackgroundCloseFinished(glassSerial));
                        }
                    };
                    return chain.proceed(args);
                });
                nativeCloseBackgroundCallbackInstalled = true;
            } catch (Throwable error) {
                // No background-completion hook: keep the original content
                // completion as the only release boundary to avoid leaks.
                MainHook.log("[DC][OpenFolderGlass] native close spring boundary unavailable: " + error);
            }
            // Capture source-excluded wallpaper at touch-down without
            // delaying native folder opening for an EGL/PassBlur callback.
            // Accessibility / keyboard entry can bypass FolderIcon ACTION_DOWN.
            HookUtil.hook(HookUtil.findMethodExact(launcher, "openFolder",
                    new Class<?>[]{info, View.class}), chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                FolderDockVisibilityOwner.rememberLauncher(chain.getThisObject());
                if (args.length > 1 && args[1] instanceof ViewGroup) {
                    safe(() -> OpenFolderGlassCoordinator.ensurePrepared((ViewGroup) args[1], args[0]));
                }
                return chain.proceed(args);
            });
            HookUtil.hook(HookUtil.findMethodExact(cling, "addFolderAnimIcon", new Class<?>[]{info, boolean.class}), chain -> {
                safe(() -> OpenFolderGlassCoordinator.onAnimationHost((View) chain.getThisObject()));
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(HookUtil.findMethodExact(cling, "open", new Class<?>[0]), chain -> {
                try {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    // FolderCling.open is earlier than the late native
                    // FolderStatusServiceImpl event: cover the real Dock
                    // hierarchy now, then let the OEM state event run normally.
                    FolderDockVisibilityOwner.onFolderOpened();
                    safe(() -> OpenFolderGlassCoordinator.onOpening((View) chain.getThisObject()));
                    return result;
                } catch (Throwable error) {
                    OpenFolderGlassCoordinator.releaseAll();
                    throw error;
                }
            });
            HookUtil.hook(HookUtil.findMethodExact(cling, "onFolderOpenAnimationFinish", new Class<?>[0]), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                safe(() -> OpenFolderGlassCoordinator.onOpenFinished((View) chain.getThisObject()));
                return result;
            });
            HookUtil.hook(HookUtil.findMethodExact(cling, "close", new Class<?>[]{boolean.class}), chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                boolean nativeSpring = nativeCloseBackgroundCallbackInstalled
                        && args.length > 0 && Boolean.TRUE.equals(args[0]);
                MiuixFolderGlassHook.onNativeCloseStarted(nativeSpring);
                safe(() -> OpenFolderGlassCoordinator.onClosing(
                        (View) chain.getThisObject(), nativeSpring));
                Object result = chain.proceed(args);
                // Release at the native close boundary, including versions
                // that omit the semantic FolderStatusServiceImpl callback.
                FolderDockVisibilityOwner.onFolderClosed();
                return result;
            });
            HookUtil.hook(HookUtil.findMethodExact(folder, "onClose", new Class<?>[]{boolean.class, Runnable.class}), chain -> {
                long serial = OpenFolderGlassCoordinator.closeSerial((View) chain.getThisObject());
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Runnable completion = args[1] instanceof Runnable ? (Runnable) args[1] : null;
                Runnable moduleCompletion = OpenFolderGlassCoordinator.closeCompletion(serial);
                args[1] = (Runnable) () -> {
                    safe(moduleCompletion);
                    if (completion != null) completion.run();
                };
                return chain.proceed(args);
            });
            Class<?> blur = Class.forName("com.miui.home.launcher.common.BlurUtilities", false, loader);
            Class<?>[] signature = {View.class, int.class, int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, int.class};
            HookUtil.hook(HookUtil.findMethodExact(blur, "setFolderIconBlur", signature), chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (!LauncherWidgetNativeViewBlurGate.isRestoringFolder() && args[0] instanceof View) {
                    safe(() -> OpenFolderGlassCoordinator.rememberNativeBlur((View) args[0], args));
                }
                return result;
            });
            installed = true;
            MainHook.log("[DC][OpenFolderGlass] native open/close hooks installed");
        } catch (Throwable error) {
            MainHook.log("[DC][OpenFolderGlass] native adapter unavailable: " + error);
        }
    }

    static synchronized boolean observeDrawable(Drawable drawable) {
        Class<?> type = drawable.getClass();
        Boolean cached = DRAW_OBSERVATION.get(type);
        if (cached != null) return cached;
        try {
            Method draw = type.getMethod("draw", Canvas.class);
            if (Modifier.isAbstract(draw.getModifiers())) {
                DRAW_OBSERVATION.put(type, false);
                return false;
            }
            if (!DRAW_HOOKS.contains(draw)) {
                HookUtil.hook(draw, chain -> {
                    if (chain.getThisObject() instanceof Drawable
                            && OpenFolderGlassCoordinator.shouldSuppress((Drawable) chain.getThisObject())) return null;
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                });
                DRAW_HOOKS.add(draw);
            }
            DRAW_OBSERVATION.put(type, true);
            return true;
        } catch (Throwable error) {
            DRAW_OBSERVATION.put(type, false);
            MainHook.log("[DC][OpenFolderGlass] material draw unavailable: " + error);
            return false;
        }
    }

    private static void safe(Runnable callback) {
        try { callback.run(); }
        catch (Throwable error) {
            MainHook.log("[DC][OpenFolderGlass] adapter fallback: " + error);
            OpenFolderGlassCoordinator.releaseAll();
        }
    }
}
