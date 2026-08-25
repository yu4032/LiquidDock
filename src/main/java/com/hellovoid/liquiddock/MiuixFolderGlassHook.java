package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    private static final Map<View, Integer> ORIGINAL_COVERED_VISIBILITY =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Map<Drawable, Integer>> ORIGINAL_LARGE_FOLDER_PAINT_ALPHA =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ViewGroup, WeakReference<View>> CLAIMED_FOLDER_COVERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ViewGroup, View.OnAttachStateChangeListener> FOLDER_ATTACH_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ViewGroup, Boolean> FOLDER_RECOVERY_PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static WeakReference<ViewGroup> openedFolderOwner = new WeakReference<>(null);
    private static WeakReference<LauncherGlassStaticNode> openedFolderSink = new WeakReference<>(null);
    private static boolean folderStatusDispatcherInstalled;
    private static boolean nativeFolderMaterialsCovered;
    private static boolean installed;

    private MiuixFolderGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
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
                    if (!isFolderLiveEnabled(material)) {
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    }
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
                setNativeFolderMaterialsCovered(true);
                LauncherGlassSceneController.setFolderCoveredForAll(true);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hookMethod(classLoader, dispatcher, "dispatchFolderClose", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                setNativeFolderMaterialsCovered(false);
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

        // FolderIcon4x4NormalBackgroundDrawable intentionally implements Drawable.setAlpha(int)
        // as a no-op. Reassert the same internal Paint alpha=0 that Launcher uses for native blur
        // immediately before each large-folder draw, so async background refresh and drag-state
        // drawables cannot restore the fallback plate behind our glass.
        Class<?> folderIcon2x2 = Class.forName(
                "com.miui.home.launcher.folder.FolderIcon2x2", false, classLoader);
        Method drawChild = HookUtil.findMethodExact(folderIcon2x2, "drawChild",
                new Class<?>[]{Canvas.class, View.class, Long.TYPE});
        HookUtil.hook(drawChild, chain -> {
            Object owner = chain.getThisObject();
            if (owner instanceof ViewGroup && GlassRuntimeState.isLargeFolderEnabled()) {
                try {
                    View material = resolveFolderMaterial((ViewGroup) owner);
                    if (material != null && claimedSink(material) != null) {
                        suppressLargeFolderDrawablePaint(material);
                    }
                } catch (Throwable ignored) {}
            }
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });

        // Do not rewrite ImageView.setImageDrawable here. HyperOS large-folder drag enter replaces
        // LauncherFolder2x2IconImageView's drawable with FolderIcon4x4DefaultBackgroundDrawable and
        // immediately casts getDrawable() back to that concrete type. Keep vendor drawable identity
        // intact and suppress only its internal Paint; the shared glass geometry continues to follow
        // the original material View.

        HookUtil.hook(HookUtil.findMethodExact(folderIcon, "onOpen", new Class<?>[0]), chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            if (chain.getThisObject() instanceof ViewGroup) {
                setOwnerSuppressed((ViewGroup) chain.getThisObject(), true);
            }
            return result;
        });
        HookUtil.hook(HookUtil.findMethodExact(folderIcon, "onClose", new Class<?>[0]), chain ->
                chain.proceed(chain.getArgs().toArray(new Object[0])));

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
            if (!isFolderLiveEnabled(material)) return;
            int width = material.getWidth();
            int height = material.getHeight();
            if (width <= 0 || height <= 0) return;
            int[] location = new int[2];
            material.getLocationOnScreen(location);
            float x = (event.getRawX() - location[0]) / width;
            float y = 1f - (event.getRawY() - location[1]) / height;
            sink.setPressInteraction(owner.isPressed(), x, y);
        } catch (Throwable error) {
            MainHook.log(TAG + " press bridge failed: " + error);
        }
    }

    private static void setOwnerSuppressed(ViewGroup owner, boolean suppressed) {
        if (owner == null) return;
        if (!folderStatusDispatcherInstalled) {
            setNativeFolderMaterialsCovered(suppressed);
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
            releaseFolderCover(icon);
            try {
                View material = resolveFolderMaterial(icon);
                if (material != null) releaseMaterialOwnership(material);
            } catch (Throwable ignored) {}
            return;
        }
        observeFolderIconAttach(icon, glassConfig);
        try {
            View value = resolveFolderMaterial(icon);
            if (value != null) {
                if (!isFolderLiveEnabled(value)) {
                    releaseMaterialOwnership(value);
                    releaseFolderCover(icon);
                    FOLDER_RECOVERY_PENDING.remove(icon);
                    return;
                }
                LauncherGlassStaticNode sink = attachMaterial(value, glassConfig);
                if (sink != null) {
                    syncLargeFolderCover(icon, glassConfig);
                    if (openedFolderOwner.get() == icon) {
                        openedFolderSink = new WeakReference<>(sink);
                        sink.setSuppressedByFolderOpen(true);
                    }
                } else {
                    releaseFolderCover(icon);
                }
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
                    if (!isFolderLiveEnabled(material)) {
                        releaseMaterialOwnership(material);
                        releaseFolderCover(current);
                        FOLDER_RECOVERY_PENDING.remove(current);
                        return;
                    }
                    sink = attachMaterial(material, glassConfig);
                    if (sink != null) {
                        syncLargeFolderCover(current, glassConfig);
                        if (openedFolderOwner.get() == current) {
                            openedFolderSink = new WeakReference<>(sink);
                            sink.setSuppressedByFolderOpen(true);
                        }
                    } else {
                        releaseFolderCover(current);
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
                    if (value != null && isFolderLiveEnabled(value)) {
                        LauncherGlassStaticNode sink = claimedSink(value);
                        if (sink != null) sink.requestLifecycleRefresh();
                    }
                } catch (Throwable ignored) {}
            }
        };
        FOLDER_ATTACH_LISTENERS.put(icon, listener);
        icon.addOnAttachStateChangeListener(listener);
    }

    private static void syncLargeFolderCover(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        if (icon == null) return;
        if (icon.getClass().getName().contains("FolderIcon1x1")
                || glassConfig == null || !GlassRuntimeState.isLargeFolderEnabled()
                || !LauncherGlassHierarchy.isWorkspace(icon)) {
            releaseFolderCover(icon);
            return;
        }
        Object value = HookUtil.invoke(icon, "getCover");
        if (!(value instanceof View)) return;
        View cover = (View) value;
        WeakReference<View> previousReference = CLAIMED_FOLDER_COVERS.put(
                icon, new WeakReference<>(cover));
        View previous = previousReference != null ? previousReference.get() : null;
        if (previous != null && previous != cover) {
            restoreCoveredVisibility(previous);
            restoreMaterial(previous);
        }
        makeMaterialTransparent(cover);
        coverNativeViewIfNeeded(cover);
    }

    private static void releaseFolderCover(ViewGroup icon) {
        if (icon == null) return;
        WeakReference<View> reference = CLAIMED_FOLDER_COVERS.remove(icon);
        View cover = reference != null ? reference.get() : null;
        if (cover != null) {
            restoreCoveredVisibility(cover);
            restoreMaterial(cover);
        }
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

    private static boolean isSmallFolderIcon(ViewGroup icon) {
        if (icon == null) return false;
        return icon.getClass().getName().contains("FolderIcon1x1");
    }

    private static boolean isFolderLiveEnabled(View material) {
        if (material == null || !GlassRuntimeState.isEnabled()) return false;
        return isSmallFolderMaterial(material)
                ? GlassRuntimeState.isSmallFolderEnabled()
                : GlassRuntimeState.isLargeFolderEnabled();
    }

    private static boolean isFolderStyleEnabled(
            View material, LiquidDockConfig.Glass glassConfig) {
        return isFolderLiveEnabled(material);
    }

    private static LauncherGlassStaticNode attachMaterial(
            View material, LiquidDockConfig.Glass glassConfig) {
        if (material == null || !GlassRuntimeState.isEnabled()
                || !LauncherGlassHierarchy.isWorkspace(material)) return null;
        boolean smallFolder = isSmallFolderMaterial(material);
        GlassComponentStyle style = glassConfig != null
                ? (smallFolder ? glassConfig.smallFolderStyle : glassConfig.largeFolderStyle)
                : new GlassComponentStyle(true, 0f, 0f);
        if (!isFolderLiveEnabled(material)) {
            releaseMaterialOwnership(material);
            return null;
        }

        LauncherGlassStaticNode existing = claimedSink(material);
        if (existing != null) {
            clearVendorBlur(material);
            makeMaterialTransparent(material);
            suppressLargeFolderDrawablePaint(material);
            coverNativeViewIfNeeded(material);
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
            suppressLargeFolderDrawablePaint(material);
            coverNativeViewIfNeeded(material);
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

    static void onRuntimeSmallFolderGlassDisabled() {
        releaseFolderStyleOwnership(true);
    }

    static void onRuntimeLargeFolderGlassDisabled() {
        releaseFolderStyleOwnership(false);
    }

    private static void releaseFolderStyleOwnership(boolean smallFolder) {
        for (View material : new ArrayList<>(CLAIMED.keySet())) {
            if (material == null || isSmallFolderMaterial(material) != smallFolder) continue;
            releaseMaterialOwnership(material);
        }
        if (!smallFolder) {
            for (ViewGroup icon : new ArrayList<>(CLAIMED_FOLDER_COVERS.keySet())) {
                releaseFolderCover(icon);
            }
        }
        for (ViewGroup icon : new ArrayList<>(FOLDER_RECOVERY_PENDING.keySet())) {
            if (isSmallFolderIcon(icon) == smallFolder) FOLDER_RECOVERY_PENDING.remove(icon);
        }
        ViewGroup opened = openedFolderOwner.get();
        if (opened != null && isSmallFolderIcon(opened) == smallFolder) {
            openedFolderOwner = new WeakReference<>(null);
            openedFolderSink = new WeakReference<>(null);
        }
    }

    private static void releaseMaterialOwnership(View material) {
        if (material == null) return;
        LauncherGlassStaticNode sink = claimedSink(material);
        if (sink != null) sink.dispose();
        CLAIMED.remove(material);
        restoreCoveredVisibility(material);
        restoreMaterial(material);
    }

    static void onRuntimeGlassDisabled() {
        setNativeFolderMaterialsCovered(false);
        for (View material : new ArrayList<>(CLAIMED.keySet())) {
            releaseMaterialOwnership(material);
        }
        CLAIMED.clear();
        ORIGINAL_LARGE_FOLDER_PAINT_ALPHA.clear();
        for (ViewGroup icon : new ArrayList<>(CLAIMED_FOLDER_COVERS.keySet())) {
            releaseFolderCover(icon);
        }
        CLAIMED_FOLDER_COVERS.clear();
        for (ViewGroup icon : new ArrayList<>(FOLDER_ATTACH_LISTENERS.keySet())) {
            View.OnAttachStateChangeListener listener = FOLDER_ATTACH_LISTENERS.remove(icon);
            if (listener != null) icon.removeOnAttachStateChangeListener(listener);
        }
        FOLDER_RECOVERY_PENDING.clear();
        openedFolderOwner = new WeakReference<>(null);
        openedFolderSink = new WeakReference<>(null);
    }

    private static void setNativeFolderMaterialsCovered(boolean covered) {
        nativeFolderMaterialsCovered = covered;
        if (!covered) {
            for (Map.Entry<View, Integer> entry
                    : new ArrayList<>(ORIGINAL_COVERED_VISIBILITY.entrySet())) {
                View view = entry.getKey();
                Integer original = entry.getValue();
                if (view != null && original != null) view.setVisibility(original);
            }
            ORIGINAL_COVERED_VISIBILITY.clear();
            return;
        }
        for (View material : new ArrayList<>(CLAIMED.keySet())) {
            coverNativeViewIfNeeded(material);
        }
        for (WeakReference<View> reference
                : new ArrayList<>(CLAIMED_FOLDER_COVERS.values())) {
            coverNativeViewIfNeeded(reference != null ? reference.get() : null);
        }
    }

    private static void coverNativeViewIfNeeded(View view) {
        if (!nativeFolderMaterialsCovered || view == null) return;
        if (!ORIGINAL_COVERED_VISIBILITY.containsKey(view)) {
            ORIGINAL_COVERED_VISIBILITY.put(view, view.getVisibility());
        }
        view.setVisibility(View.INVISIBLE);
    }

    private static void restoreCoveredVisibility(View view) {
        if (view == null) return;
        Integer original = ORIGINAL_COVERED_VISIBILITY.remove(view);
        if (original != null) view.setVisibility(original);
    }

    private static void restoreMaterial(View material) {
        if (material == null) return;
        restoreLargeFolderDrawablePaint(material);
        if (material instanceof ImageView) {
            Integer originalAlpha = ORIGINAL_IMAGE_ALPHA.remove(material);
            if (originalAlpha != null) ((ImageView) material).setImageAlpha(originalAlpha);
        } else {
            Drawable original = ORIGINAL_BACKGROUND.remove(material);
            if (original != null) material.setBackground(original);
        }
    }

    private static boolean isLargeFolderBackgroundDrawable(Drawable drawable) {
        if (drawable == null) return false;
        String name = drawable.getClass().getName();
        return name.endsWith("FolderIcon4x4NormalBackgroundDrawable")
                || name.endsWith("FolderIcon4x4DefaultBackgroundDrawable");
    }

    private static void suppressLargeFolderDrawablePaint(View material) {
        if (!GlassRuntimeState.isLargeFolderEnabled()
                || !(material instanceof ImageView) || isSmallFolderMaterial(material)) return;
        Drawable drawable = ((ImageView) material).getDrawable();
        if (!isLargeFolderBackgroundDrawable(drawable)) return;
        Object paintValue = HookUtil.invoke(drawable, "getPaint");
        if (!(paintValue instanceof Paint)) return;
        Paint paint = (Paint) paintValue;
        synchronized (ORIGINAL_LARGE_FOLDER_PAINT_ALPHA) {
            Map<Drawable, Integer> originals = ORIGINAL_LARGE_FOLDER_PAINT_ALPHA.get(material);
            if (originals == null) {
                originals = new WeakHashMap<>();
                ORIGINAL_LARGE_FOLDER_PAINT_ALPHA.put(material, originals);
            }
            if (!originals.containsKey(drawable)) {
                originals.put(drawable, paint.getAlpha());
            }
        }
        if (paint.getAlpha() != 0) {
            paint.setAlpha(0);
            drawable.invalidateSelf();
        }
    }

    private static void restoreLargeFolderDrawablePaint(View material) {
        Map<Drawable, Integer> originals;
        synchronized (ORIGINAL_LARGE_FOLDER_PAINT_ALPHA) {
            originals = ORIGINAL_LARGE_FOLDER_PAINT_ALPHA.remove(material);
        }
        if (originals == null) return;
        for (Map.Entry<Drawable, Integer> entry : originals.entrySet()) {
            Drawable drawable = entry.getKey();
            Integer alpha = entry.getValue();
            if (drawable == null || alpha == null) continue;
            Object paintValue = HookUtil.invoke(drawable, "getPaint");
            if (!(paintValue instanceof Paint)) continue;
            ((Paint) paintValue).setAlpha(alpha);
            drawable.invalidateSelf();
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
