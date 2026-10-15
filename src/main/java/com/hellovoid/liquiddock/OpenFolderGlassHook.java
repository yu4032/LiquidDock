package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/** Native folder lifecycle adapter; rendering and ownership stay in the coordinator. */
final class OpenFolderGlassHook {
    private static final Set<Method> DRAW_HOOKS = new HashSet<>();
    private static boolean installed;
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
                return chain.proceed(args);
            });
            // Capture the composed workspace/Dock at touch-down without
            // delaying native folder opening for an EGL/PassBlur callback.
            // Accessibility / keyboard entry can bypass FolderIcon ACTION_DOWN.
            HookUtil.hook(HookUtil.findMethodExact(launcher, "openFolder",
                    new Class<?>[]{info, View.class}), chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length > 1 && args[1] instanceof ViewGroup) {
                    safe(() -> OpenFolderGlassCoordinator.ensurePrepared((ViewGroup) args[1]));
                }
                return chain.proceed(args);
            });
            HookUtil.hook(HookUtil.findMethodExact(cling, "addFolderAnimIcon", new Class<?>[]{info, boolean.class}), chain -> {
                safe(() -> OpenFolderGlassCoordinator.onAnimationHost((View) chain.getThisObject()));
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(HookUtil.findMethodExact(cling, "open", new Class<?>[0]), chain -> {
                safe(() -> OpenFolderGlassCoordinator.onOpening((View) chain.getThisObject()));
                try { return chain.proceed(chain.getArgs().toArray(new Object[0])); }
                catch (Throwable error) { OpenFolderGlassCoordinator.releaseAll(); throw error; }
            });
            HookUtil.hook(HookUtil.findMethodExact(cling, "onFolderOpenAnimationFinish", new Class<?>[0]), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                safe(() -> OpenFolderGlassCoordinator.onOpenFinished((View) chain.getThisObject()));
                return result;
            });
            HookUtil.hook(HookUtil.findMethodExact(cling, "close", new Class<?>[]{boolean.class}), chain -> {
                safe(() -> OpenFolderGlassCoordinator.onClosing((View) chain.getThisObject()));
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
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
        try {
            Method draw = drawable.getClass().getMethod("draw", Canvas.class);
            if (Modifier.isAbstract(draw.getModifiers())) return false;
            if (DRAW_HOOKS.contains(draw)) return true;
            HookUtil.hook(draw, chain -> {
                if (chain.getThisObject() instanceof Drawable
                        && OpenFolderGlassCoordinator.shouldSuppress((Drawable) chain.getThisObject())) return null;
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            DRAW_HOOKS.add(draw);
            return true;
        } catch (Throwable error) {
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
