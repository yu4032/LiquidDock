package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Replaces MIUI desktop FolderIcon advanced material with output-only shared glass sinks. */
final class MiuixFolderGlassHook {
    private static final String TAG = "[DC][FolderGlass]";
    private static final String ITEM_ICON = "com.miui.home.launcher.ItemIcon";
    private static final String FOLDER_ICON = "com.miui.home.launcher.FolderIcon";
    private static final int MAX_STARTUP_RECOVERY_FRAMES = 24;
    private static final Map<View, WeakReference<LauncherGlassStaticNode>> CLAIMED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> ORIGINAL_IMAGE_ALPHA =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Drawable> ORIGINAL_BACKGROUND =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ViewGroup, View.OnAttachStateChangeListener> FOLDER_ATTACH_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ViewGroup, Boolean> FOLDER_RECOVERY_PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static WeakReference<ViewGroup> openedFolderOwner = new WeakReference<>(null);
    private static WeakReference<LauncherGlassStaticNode> openedFolderSink = new WeakReference<>(null);
    private static boolean folderStatusDispatcherInstalled;
    private static boolean installed;

    private MiuixFolderGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled
                || !runtimeConfig.glass.folderEnabled) {
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        folderStatusDispatcherInstalled = installFolderStatusDispatcherHooks(classLoader);
        try {
            Class<?> itemIcon = Class.forName(ITEM_ICON, false, classLoader);
            Class<?> folderIconType = Class.forName(FOLDER_ICON, false, classLoader);
            Method setIconImageView = HookUtil.findMethodExact(itemIcon, "setIconImageView",
                    new Class<?>[]{Drawable.class, android.graphics.Bitmap.class});
            HookUtil.hook(setIconImageView, chain -> {
                Object icon = chain.getThisObject();
                boolean folder = icon instanceof ViewGroup && folderIconType.isInstance(icon);
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (folder) attachFromFolderIcon((ViewGroup) icon, glassConfig);
                return result;
            });

            installFolderOpenCloseHooks(classLoader);
            observeFolderVariantConstructors(classLoader, glassConfig);

            Class<?> utilities = Class.forName(
                    "com.miui.home.launcher.common.BlurUtilities", false, classLoader);
            Class<?>[] params = new Class<?>[]{View.class, int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, int.class, int.class};
            HookUtil.hook(HookUtil.findMethodExact(utilities, "setFolderIconBlur", params), chain -> {
                Object target = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (target instanceof View) {
                    View material = (View) target;
                    LauncherGlassStaticNode sink = claimedSink(material);
                    if (sink == null) sink = attachMaterial(material, glassConfig);
                    if (sink != null) {
                        clearVendorBlur(material);
                        return null;
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            installed = true;
            MainHook.log(TAG + " shared FolderIcon glass hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    private static void observeFolderVariantConstructors(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        String[] variants = {"com.miui.home.launcher.folder.FolderIcon1x1",
                "com.miui.home.launcher.folder.FolderIcon2x2"};
        for (String variant : variants) {
            try {
                Class<?> type = Class.forName(variant, false, classLoader);
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    HookUtil.hook(constructor, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (owner instanceof ViewGroup) attachFromFolderIcon((ViewGroup) owner, glassConfig);
                        return result;
                    });
                }
            } catch (Throwable ignored) {}
        }
    }

    static void reconcileExistingView(View view, LiquidDockConfig.Glass glassConfig) {
        if (!(view instanceof ViewGroup) || glassConfig == null) return;
        String name = view.getClass().getName();
        if (name.endsWith(".FolderIcon") || name.contains("FolderIcon1x1")
                || name.contains("FolderIcon2x2")) {
            attachFromFolderIcon((ViewGroup) view, glassConfig);
        }
    }

    private static boolean installFolderStatusDispatcherHooks(ClassLoader classLoader) {
        String dispatcher =
                "com.miui.home.launcher.dock.v3.dependencies.FolderStatusServiceImpl";
        try {
            HookUtil.hookMethod(classLoader, dispatcher, "dispatchFolderOpen", chain -> {
                LauncherGlassSceneController.setFolderCoveredForAll(true);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hookMethod(classLoader, dispatcher, "dispatchFolderClose", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                LauncherGlassSceneController.setFolderCoveredForAll(false);
                return result;
            });
            MainHook.log(TAG + " semantic FolderStatus dispatcher hooks installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " semantic FolderStatus dispatcher unavailable; using FolderIcon fallback: "
                    + error);
            return false;
        }
    }

    private static void installFolderOpenCloseHooks(ClassLoader classLoader) throws Exception {
        Class<?> folderIcon = Class.forName(FOLDER_ICON, false, classLoader);
        // FolderIcon's own dispatch path is sufficient on this HyperOS build. Observe only that
        // declaration so we do not add process-wide touch-hook overhead.
        Method dispatchTouchEvent = folderIcon.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        dispatchTouchEvent.setAccessible(true);
        HookUtil.hook(dispatchTouchEvent, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            Object result = chain.proceed(args);
            Object owner = chain.getThisObject();
            if (owner instanceof ViewGroup && args.length > 0 && args[0] instanceof MotionEvent) {
                updateFolderPressAfterDispatch((ViewGroup) owner, (MotionEvent) args[0]);
            }
            return result;
        });

        // Do not rewrite ImageView.setImageDrawable here. HyperOS large-folder drag enter replaces
        // LauncherFolder2x2IconImageView's drawable with FolderIcon4x4DefaultBackgroundDrawable and
        // immediately casts getDrawable() back to that concrete type. Folder material suppression is
        // instead applied with ImageView image alpha, which survives vendor drawable replacement
        // without changing View alpha (the shared glass geometry still follows the material View).

        HookUtil.hook(HookUtil.findMethodExact(folderIcon, "onOpen", new Class<?>[0]), chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            if (chain.getThisObject() instanceof ViewGroup) {
                setOwnerSuppressed((ViewGroup) chain.getThisObject(), true);
            }
            return result;
        });
        HookUtil.hook(HookUtil.findMethodExact(folderIcon, "onClose", new Class<?>[0]), chain -> {
            // onClose is emitted when the close animation starts. Keep the source icon hidden
            // until Folder.onClose's completion Runnable fires, otherwise the source plate is
            // duplicated during the animation.
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });

        Class<?> folder = Class.forName("com.miui.home.launcher.Folder", false, classLoader);
        HookUtil.hook(HookUtil.findMethodExact(folder, "onClose",
                new Class<?>[]{Boolean.TYPE, Runnable.class}), chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            Runnable originalCompletion = args.length > 1 && args[1] instanceof Runnable
                    ? (Runnable) args[1] : null;
            args[1] = (Runnable) () -> {
                restoreOpenedFolderOwner();
                if (originalCompletion != null) originalCompletion.run();
            };
            return chain.proceed(args);
        });
    }

    private static void updateFolderPressAfterDispatch(ViewGroup owner, MotionEvent event) {
        if (owner == null || event == null) return;
        LauncherGlassStaticNode sink = resolveOwnerSink(owner);
        if (sink == null) return;
        try {
            Object value = HookUtil.getField(owner, "mIconImageView");
            if (!(value instanceof View)) return;
            View material = (View) value;
            int width = material.getWidth();
            int height = material.getHeight();
            if (width <= 0 || height <= 0) return;
            int[] location = new int[2];
            material.getLocationOnScreen(location);
            float x = (event.getRawX() - location[0]) / width;
            // Android local Y grows downward; Prismal glow coordinates grow upward.
            float y = 1f - (event.getRawY() - location[1]) / height;
            sink.setPressInteraction(owner.isPressed(), x, y);
        } catch (Throwable error) {
            MainHook.log(TAG + " press bridge failed: " + error);
        }
    }

    private static void setOwnerSuppressed(ViewGroup owner, boolean suppressed) {
        if (owner == null) return;
        if (!folderStatusDispatcherInstalled) {
            LauncherGlassSceneController.setWorkspaceCovered(owner, suppressed);
        }
        if (!suppressed) {
            LauncherGlassStaticNode sink = resolveOwnerSink(owner);
            if (sink != null) sink.setSuppressedByFolderOpen(false);
            if (openedFolderOwner.get() == owner) {
                openedFolderOwner = new WeakReference<>(null);
                openedFolderSink = new WeakReference<>(null);
            }
            return;
        }

        ViewGroup previousOwner = openedFolderOwner.get();
        LauncherGlassStaticNode previousSink = openedFolderSink.get();
        if (previousOwner != null && previousOwner != owner && previousSink != null) {
            previousSink.setSuppressedByFolderOpen(false);
        }

        openedFolderOwner = new WeakReference<>(owner);
        LauncherGlassStaticNode sink = resolveOwnerSink(owner);
        openedFolderSink = new WeakReference<>(sink);
        if (sink != null) {
            sink.resetPressInteraction(false);
            sink.setSuppressedByFolderOpen(true);
        }
    }

    private static void restoreOpenedFolderOwner() {
        LauncherGlassStaticNode sink = openedFolderSink.get();
        ViewGroup owner = openedFolderOwner.get();
        if (owner != null && !folderStatusDispatcherInstalled) {
            LauncherGlassSceneController.setWorkspaceCovered(owner, false);
        }
        openedFolderOwner = new WeakReference<>(null);
        openedFolderSink = new WeakReference<>(null);
        if (sink != null) sink.setSuppressedByFolderOpen(false);
    }

    private static LauncherGlassStaticNode resolveOwnerSink(ViewGroup owner) {
        try {
            Object value = HookUtil.getField(owner, "mIconImageView");
            return value instanceof View ? claimedSink((View) value) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void attachFromFolderIcon(ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        if (icon == null) return;
        if (!GlassRuntimeState.isEnabled() || !LauncherGlassHierarchy.isWorkspace(icon)) {
            try {
                View material = resolveFolderMaterial(icon);
                if (material != null) {
                    LauncherGlassStaticNode old = claimedSink(material);
                    if (old != null) old.dispose();
                    CLAIMED.remove(material);
                    restoreMaterial(material);
                }
            } catch (Throwable ignored) {}
            return;
        }
        observeFolderIconAttach(icon, glassConfig);
        try {
            View value = resolveFolderMaterial(icon);
            if (value != null) {
                LauncherGlassStaticNode sink = attachMaterial(value, glassConfig);
                if (sink != null && openedFolderOwner.get() == icon) {
                    openedFolderSink = new WeakReference<>(sink);
                    sink.setSuppressedByFolderOpen(true);
                }
                // Launcher restart can call setIconImageView after FolderIcon is attached but
                // before its real ViewRoot/Surface is stable. Adding an attach listener at that
                // point does not replay onViewAttachedToWindow, so recover on later UI frames.
                if (sink == null && icon.isAttachedToWindow()
                        && isFolderStyleEnabled(value, glassConfig)) {
                    scheduleFolderRecovery(icon, glassConfig, 0);
                }
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " material resolve failed: " + error);
        }
    }

    private static void scheduleFolderRecovery(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig, int attempt) {
        if (!GlassRuntimeState.isEnabled() || icon == null) {
            if (icon != null) FOLDER_RECOVERY_PENDING.remove(icon);
            return;
        }
        if (attempt == 0) {
            synchronized (FOLDER_RECOVERY_PENDING) {
                if (FOLDER_RECOVERY_PENDING.containsKey(icon)) return;
                FOLDER_RECOVERY_PENDING.put(icon, Boolean.TRUE);
            }
        }
        if (attempt >= MAX_STARTUP_RECOVERY_FRAMES) {
            FOLDER_RECOVERY_PENDING.remove(icon);
            MainHook.log(TAG + " startup recovery exhausted for "
                    + icon.getClass().getSimpleName());
            return;
        }
        WeakReference<ViewGroup> iconRef = new WeakReference<>(icon);
        icon.postOnAnimation(() -> {
            ViewGroup current = iconRef.get();
            if (current == null || !current.isAttachedToWindow()) {
                if (current != null) FOLDER_RECOVERY_PENDING.remove(current);
                return;
            }
            LauncherGlassStaticNode sink = null;
            View material = null;
            try {
                material = resolveFolderMaterial(current);
                if (material != null) {
                    sink = attachMaterial(material, glassConfig);
                    if (sink != null && openedFolderOwner.get() == current) {
                        openedFolderSink = new WeakReference<>(sink);
                        sink.setSuppressedByFolderOpen(true);
                    }
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " startup material recovery failed: " + error);
            }
            if (sink == null && material != null && isFolderStyleEnabled(material, glassConfig)
                    && attempt < MAX_STARTUP_RECOVERY_FRAMES) {
                scheduleFolderRecovery(current, glassConfig, attempt + 1);
            } else {
                FOLDER_RECOVERY_PENDING.remove(current);
            }
        });
    }

    private static void observeFolderIconAttach(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || icon == null
                || FOLDER_ATTACH_LISTENERS.containsKey(icon)) return;
        WeakReference<ViewGroup> iconRef = new WeakReference<>(icon);
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                ViewGroup folder = iconRef.get();
                if (folder == null) return;
                // DragContainer -> desktop reparent does not necessarily call setIconImageView or
                // setFolderIconBlur again. Recover the material on the first normal UI frame.
                folder.postOnAnimation(() -> {
                    ViewGroup current = iconRef.get();
                    if (current != null && current.isAttachedToWindow()) {
                        attachFromFolderIcon(current, glassConfig);
                    }
                });
            }

            @Override public void onViewDetachedFromWindow(View v) {
                ViewGroup folder = iconRef.get();
                if (folder == null) return;
                try {
                    View value = resolveFolderMaterial(folder);
                    if (value != null) {
                        LauncherGlassStaticNode sink = claimedSink(value);
                        if (sink != null) sink.requestLifecycleRefresh();
                    }
                } catch (Throwable ignored) {}
            }
        };
        FOLDER_ATTACH_LISTENERS.put(icon, listener);
        icon.addOnAttachStateChangeListener(listener);
    }

    private static View resolveFolderMaterial(ViewGroup folder) {
        if (folder == null) return null;
        boolean small = folder.getClass().getName().contains("FolderIcon1x1");
        String[] fields = small
                ? new String[]{"mImageView", "mIconImageView"}
                : new String[]{"mIconImageView", "mImageView"};
        for (String field : fields) {
            try {
                Object value = HookUtil.getField(folder, field);
                if (value instanceof View) return (View) value;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean isSmallFolderMaterial(View material) {
        View cursor = material;
        while (cursor != null) {
            if (cursor.getClass().getName().contains("FolderIcon1x1")) return true;
            android.view.ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static boolean isFolderStyleEnabled(
            View material, LiquidDockConfig.Glass glassConfig) {
        if (glassConfig == null) return true;
        return isSmallFolderMaterial(material)
                ? glassConfig.smallFolderStyle.enabled
                : glassConfig.largeFolderStyle.enabled;
    }

    private static LauncherGlassStaticNode attachMaterial(
            View material, LiquidDockConfig.Glass glassConfig) {
        if (material == null || !GlassRuntimeState.isEnabled()
                || !LauncherGlassHierarchy.isWorkspace(material)) return null;
        boolean smallFolder = isSmallFolderMaterial(material);
        GlassComponentStyle style = glassConfig != null
                ? (smallFolder ? glassConfig.smallFolderStyle : glassConfig.largeFolderStyle)
                : new GlassComponentStyle(true, 0f, 0f);
        if (!style.enabled) {
            LauncherGlassStaticNode existing = claimedSink(material);
            if (existing != null) existing.dispose();
            CLAIMED.remove(material);
            restoreMaterial(material);
            return null;
        }

        LauncherGlassStaticNode existing = claimedSink(material);
        if (existing != null) {
            clearVendorBlur(material);
            makeMaterialTransparent(material);
            MiuixLauncherDragOverlayHook.observeStaticNode(existing);
            return existing;
        }
        float nativeRadius = readMaterialRadius(material);
        float fallbackRadius = Math.min(Math.max(1, material.getWidth()),
                Math.max(1, material.getHeight())) * 0.22f;
        float density = material.getResources().getDisplayMetrics().density;
        float radius = LauncherGlassCornerRadiusPolicy.resolve(
                style.cornerRadiusDp, density, nativeRadius, fallbackRadius);
        LauncherGlassStaticNode sink = LauncherGlassStaticNode.attachFolderMaterial(
                material, smallFolder, radius, glassConfig);
        if (sink != null) {
            CLAIMED.put(material, new WeakReference<>(sink));
            MiuixLauncherDragOverlayHook.observeStaticNode(sink);
            clearVendorBlur(material);
            makeMaterialTransparent(material);
            MainHook.log(TAG + " FolderIcon material joined shared static launcher compositor");
        }
        return sink;
    }

    private static LauncherGlassStaticNode claimedSink(View material) {
        WeakReference<LauncherGlassStaticNode> reference = CLAIMED.get(material);
        return reference != null ? reference.get() : null;
    }

    private static float readMaterialRadius(View material) {
        if (material == null) return Float.NaN;
        try {
            Field field = findField(material.getClass(), "mCornerRadius");
            field.setAccessible(true);
            Object value = field.get(material);
            if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
        } catch (Throwable ignored) {}
        try {
            Field field = findField(material.getClass(), "mBackground");
            field.setAccessible(true);
            Object value = field.get(material);
            if (value instanceof GradientDrawable) {
                return Math.max(0f, ((GradientDrawable) value).getCornerRadius());
            }
        } catch (Throwable ignored) {}
        Drawable drawable = material.getBackground();
        if (drawable instanceof GradientDrawable) {
            return Math.max(0f, ((GradientDrawable) drawable).getCornerRadius());
        }
        return Float.NaN;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static void onRuntimeGlassDisabled() {
        for (View material : new ArrayList<>(CLAIMED.keySet())) {
            LauncherGlassStaticNode sink = claimedSink(material);
            if (sink != null) sink.dispose();
            restoreMaterial(material);
        }
        CLAIMED.clear();
        for (ViewGroup icon : new ArrayList<>(FOLDER_ATTACH_LISTENERS.keySet())) {
            View.OnAttachStateChangeListener listener = FOLDER_ATTACH_LISTENERS.remove(icon);
            if (listener != null) icon.removeOnAttachStateChangeListener(listener);
        }
        FOLDER_RECOVERY_PENDING.clear();
        openedFolderOwner = new WeakReference<>(null);
        openedFolderSink = new WeakReference<>(null);
    }

    private static void restoreMaterial(View material) {
        if (material == null) return;
        if (material instanceof ImageView) {
            Integer originalAlpha = ORIGINAL_IMAGE_ALPHA.remove(material);
            if (originalAlpha != null) ((ImageView) material).setImageAlpha(originalAlpha);
        } else {
            Drawable original = ORIGINAL_BACKGROUND.remove(material);
            if (original != null) material.setBackground(original);
        }
    }

    private static boolean isTransparentColorDrawable(Drawable drawable) {
        return drawable instanceof ColorDrawable
                && ((ColorDrawable) drawable).getColor() == Color.TRANSPARENT;
    }

    private static void makeMaterialTransparent(View material) {
        if (material instanceof ImageView) {
            ImageView image = (ImageView) material;
            if (!ORIGINAL_IMAGE_ALPHA.containsKey(material)) {
                ORIGINAL_IMAGE_ALPHA.put(material, image.getImageAlpha());
            }
            image.setImageAlpha(0);
        } else {
            Drawable current = material.getBackground();
            if (!ORIGINAL_BACKGROUND.containsKey(material) && current != null
                    && !isTransparentColorDrawable(current)) {
                ORIGINAL_BACKGROUND.put(material, current);
            }
            material.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static void clearVendorBlur(View material) {
        LauncherGlassVendorMaterialSuppressor.claimFolderMaterial(material);
        MiBlurBridge.clearContentBlur(material);
        try { View.class.getMethod("clearMiBackgroundBlendColor").invoke(material); }
        catch (Throwable ignored) {}
    }
}
