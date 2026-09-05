package com.hellovoid.liquiddock;

import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;


/** Core Launcher hooks for LiquidDock — zero-copy glass, stroke, dock geometry, workstation. */
public class MainHook {

    private static WeakReference<View> oldBgRef = new WeakReference<>(null);
    private static WeakReference<Object> hotSeatsShadowOwnerRef = new WeakReference<>(null);
    private static volatile LiquidDockConfig.Dock nativeShadowConfig;

    private static View oldBg() { return oldBgRef.get(); }
    private static void setOldBg(View view) { oldBgRef = new WeakReference<>(view); }
    private static Object hotSeatsShadowOwner() { return hotSeatsShadowOwnerRef.get(); }
    private static void setHotSeatsShadowOwner(Object hotSeats) {
        if (hotSeats != null) hotSeatsShadowOwnerRef = new WeakReference<>(hotSeats);
    }
    private static float strokeR = 30f;
    private static volatile boolean workstationMode;
    private static volatile boolean workstationModeHookConfirmed;
    private static final java.util.Map<Long, HomeItemPosition> normalLayoutBackup =
            new java.util.HashMap<>();
    private static final java.util.WeakHashMap<View, android.animation.ValueAnimator>
            dockResizeAnimators = new java.util.WeakHashMap<>();

    // ── entry point ──────────────────────────────────────────────────

    public void install(ClassLoader classLoader) {
        installWorkstationModeGuard(classLoader);
        LiquidDockConfig config = LiquidDockConfig.load();
        WidgetGridSizing.setWidgetAdaptationEnabled(
                WidgetGridSizing.shouldAdaptWidgets(config.grid.enabled, config.grid.widgetAdaptation));
        debugLogging = config.debugLog;
        log("[DC] LiquidDock " + (debugLogging ? "debug logging ON" : "loaded"));
        if (!config.enabled) {
            log("[DC] LiquidDock master switch disabled");
            return;
        }
        DockStrokeRenderer.installNativeHook(classLoader, config.dock);
        installWorkstationDockHooks(classLoader, config.workstation);
        WorkstationDockGeometryHook.install(classLoader, config.workstation);
        if (!config.dock.resizeAnimation)
            installDockResizeAnimationBypass(classLoader, config.dock.smoothResizeAnimation,
                    config.animation.dockResizeMs);
        if (workstationMode)
            log("[DC] workstation active; using isolated workstation parameters");

        LiquidDockConfig.Grid grid = config.grid;
        boolean grid8x4 = grid.enabled, dp = grid.dp, offsets = grid.offsets;
        float gridScale = dp ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int landXBase = dp ? 57 : 160, landYBase = dp ? 28 : 80;
        int portXBase = dp ? 28 : 80, portYBase = dp ? 57 : 160;
        float landLeft = offsets ? grid.landscapeHorizontal : landXBase + grid.landscapeHorizontal;
        float landRight = offsets ? grid.landscapeHorizontal : landXBase + grid.landscapeHorizontal;
        float landTop = offsets ? grid.landscapeTop : landYBase + grid.landscapeTop;
        float landBottom = offsets ? grid.landscapeBottom : landYBase + grid.landscapeBottom;
        float portLeft = offsets ? grid.portraitHorizontal : portXBase + grid.portraitHorizontal;
        float portRight = offsets ? grid.portraitHorizontal : portXBase + grid.portraitHorizontal;
        float portTop = offsets ? grid.portraitTop : portYBase + grid.portraitTop;
        float portBottom = offsets ? grid.portraitBottom : portYBase + grid.portraitBottom;
        float landGap = grid.landscapeRowGap, portGap = grid.portraitRowGap;
        if (!offsets) {
            landLeft -= landXBase; landRight -= landXBase;
            landTop -= landYBase; landBottom -= landYBase;
            portLeft -= portXBase; portRight -= portXBase;
            portTop -= portYBase; portBottom -= portYBase;
            landGap -= dp ? 1 : 3; portGap -= dp ? 1 : 3;
        }
        DockDividerHook.install(classLoader);
        HomeGridHook.install(classLoader, grid8x4,
            Math.round(landLeft * gridScale), Math.round(landRight * gridScale),
            Math.round(landTop * gridScale), Math.round(landBottom * gridScale),
            Math.round(portLeft * gridScale), Math.round(portRight * gridScale),
            Math.round(portTop * gridScale), Math.round(portBottom * gridScale),
            Math.round(landGap * gridScale), Math.round(portGap * gridScale),
            Math.round(grid.landscapeIndicatorY * gridScale),
            Math.round(grid.portraitIndicatorY * gridScale));
        HomeGridHook.setWorkstationHorizontalOffset(Math.round(
                config.workstation.gridHorizontalOffset * gridScale));
        // All Apps controls are absolute edge spacing in dp. They must not inherit the
        // ordinary grid_margins_dp unit switch, otherwise the same spacing setting changes
        // meaning when the normal desktop grid unit mode changes.
        float workstationAllAppsScale = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        HomeGridHook.setWorkstationAllAppsOffsets(
                Math.round(config.workstation.allAppsLandscapeHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsLandscapeTopSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsLandscapeBottomSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitTopSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitBottomSpacing * workstationAllAppsScale));

        boolean dockCustomization = config.dock.enabled;
        // Keep HotSeats itself as the only authority for the whole-Dock native shadow.
        nativeShadowConfig = config.dock;
        installNativeDockShadowOwnership(classLoader);
        installDockShadowSetupHook(classLoader);
        if (config.glass.enabled) {
            if (Miuix307MaterialPipeline.install(classLoader, config)) {
                log("[DC] MiuiX 307 zero-copy material active");
                return;
            }
            log("[DC] MiuiX 307 zero-copy material unavailable; liquid glass disabled");
        }
        if (!dockCustomization) {
            log("[DC] Dock customization disabled; legacy hooks installed inertly");
        }

        // ── Dock customization path; liquid glass is zero-copy-only above ──
        LiquidDockConfig.Dock dock = config.dock;
        log("[DC] init: bl=" + dock.blurRadius + " sq=" + dock.squircle);
        boolean sq = dock.squircle;
        float dockScale = dock.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int wo = Math.round(dock.widthOffset * dockScale);
        int ho = Math.round(dock.heightOffset * dockScale);
        int br = dock.blurRadius;
        float cornerScale = dock.cornersDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int co = Math.round(dock.cornerOffset * cornerScale);
        int blurCo = Math.round(dock.blurCornerOffset * cornerScale);
        int spacing = Math.round(dock.spacing * dockScale);
        ClassLoader cl = classLoader;

        try {
            String hsc = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

            // spacing
            if (spacing != 0) {
                try {
                    Class<?> recyclerView = Class.forName("androidx.recyclerview.widget.RecyclerView", false, cl);
                    Class<?> recyclerState = Class.forName("androidx.recyclerview.widget.RecyclerView$State", false, cl);
                    HookUtil.hookMethod(cl,
                        "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                        "getItemOffsets",
                        chain -> {
                            Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            if (workstationMode || !VisualRuntimeState.isDockCustomizationEnabled()) {
                                return r;
                            }
                            Rect out = (Rect) chain.getArgs().get(0);
                            out.left += spacing;
                            out.right += spacing;
                            return r;
                        }, Rect.class, View.class, recyclerView, recyclerState);

                    Class<?> layoutManager = Class.forName(
                            "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager", false, cl);
                    HookUtil.hookMethod(layoutManager, "updateBackgroundView",
                            new Class<?>[]{FrameLayout.class, int.class, int.class, float.class},
                            chain -> {
                                if (workstationMode || !VisualRuntimeState.isDockCustomizationEnabled()) {
                                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                                }
                                int itemCount = (Integer) HookUtil.requireInvoke(
                                        chain.getThisObject(), "getItemCount");
                                if (itemCount > 0) {
                                    Object[] args = chain.getArgs().toArray(new Object[0]);
                                    args[1] = (Integer) args[1] + spacing * 2 * itemCount;
                                    return chain.proceed(args);
                                }
                                return chain.proceed(chain.getArgs().toArray(new Object[0]));
                            });
                } catch (Throwable e) { log("[DC] spacing hook unavailable: " + e); }
            }

            // setBackgroundWidth: before (width offset) + after (syncAll)
            HookUtil.hookMethod(cl, hsc, "setBackgroundWidth",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (VisualRuntimeState.isDockCustomizationEnabled()
                                && !workstationMode && wo != 0) {
                            args[0] = (int) args[0] + wo;
                        }
                        Object r = chain.proceed(args);
                        syncAll((View) chain.getThisObject());
                        return r;
                    }, int.class);

            // setBackgroundHeight: before (height offset) + after (syncAll)
            HookUtil.hookMethod(cl, hsc, "setBackgroundHeight",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (VisualRuntimeState.isDockCustomizationEnabled()
                                && !workstationMode && ho != 0) {
                            args[0] = (int) args[0] + ho;
                        }
                        Object r = chain.proceed(args);
                        syncAll((View) chain.getThisObject());
                        return r;
                    }, int.class);

            // setBackgroundRadius: complex before + after with squircle
            HookUtil.hookMethod(cl, hsc, "setBackgroundRadius",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (!workstationMode && VisualRuntimeState.isDockCustomizationEnabled()) {
                            View v = (View) chain.getThisObject();
                            float systemRadius = (Float) args[0];
                            if (!animating(v)) strokeR = Math.max(0f, systemRadius + co);
                            args[0] = Math.max(0f, systemRadius + blurCo);
                        }
                        Object r = chain.proceed(args);
                        View v = (View) chain.getThisObject();
                        syncAll(v);
                        if (sq && VisualRuntimeState.isDockCustomizationEnabled()) {
                            if (animating(v)) return r;
                            float radius = (Float) HookUtil.getField(v, "mCornerRadius");
                            if (radius > 0) v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                @Override public void getOutline(View vv, android.graphics.Outline o) {
                                    o.setPath(squirclePath(new RectF(0, 0, v.getWidth(), v.getHeight()), radius));
                                }});
                        }
                        return r;
                    }, float.class);

            // BlurUtilities
            try {
                Class<?> bu = Class.forName("com.miui.home.launcher.common.BlurUtilities", false, cl);
                HookUtil.hookMethod(bu, "setBackgroundBlur",
                        new Class<?>[]{View.class, int.class, float[].class, int[][].class},
                        chain -> {
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            if (VisualRuntimeState.isDockCustomizationEnabled()
                                    && !workstationMode && br != 100) {
                                args[1] = br;
                            }
                            return chain.proceed(args);
                        });
            } catch (Throwable ignored) {}
        } catch (Throwable e) { log("[DC] init err: " + e); }
    }

    // ── helpers ──────────────────────────────────────────────────────

    /**
     * Hook the two HotSeats lifecycle methods that own the stock native shadow. The original
     * methods always remain the renderer; LiquidDock only supplies temporary state while they run.
     */
    private static void installNativeDockShadowOwnership(ClassLoader cl) {
        try {
            Class<?> hotSeatsClass = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeats", false, cl);
            java.lang.reflect.Method showViewShadow =
                    hotSeatsClass.getDeclaredMethod("showViewShadow");
            showViewShadow.setAccessible(true);
            HookUtil.hook(showViewShadow, chain -> {
                Object hotSeats = chain.getThisObject();
                setHotSeatsShadowOwner(hotSeats);
                HotSeatsShadowScope scope = pushConfiguredHotSeatsShadow(hotSeats);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    scope.close();
                }
            });

            try {
                // HyperOS animates the native shadow from HotSeats.setTranslationY. Do not replay
                // another shadow here: any nested showViewShadow call is already intercepted above.
                java.lang.reflect.Method setTranslationY =
                        hotSeatsClass.getDeclaredMethod("setTranslationY", float.class);
                setTranslationY.setAccessible(true);
                HookUtil.hook(setTranslationY, chain -> {
                    setHotSeatsShadowOwner(chain.getThisObject());
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                });
            } catch (Throwable translationError) {
                log("[DC] HotSeats setTranslationY shadow hook unavailable: " + translationError);
            }
            log("[DC] HotSeats native Dock shadow lifecycle hooked");
        } catch (Throwable e) {
            log("[DC] native Dock shadow lifecycle unavailable: " + e);
        }
    }

    private static HotSeatsShadowScope pushConfiguredHotSeatsShadow(Object hotSeats) {
        LiquidDockConfig.Dock dock = currentNativeShadowConfig();
        if (hotSeats == null || !DockShadowRuntimePolicy.shouldApplyTemporaryOverrides(
                workstationMode, VisualRuntimeState.isDockCustomizationEnabled(), dock != null)) {
            return HotSeatsShadowScope.noop();
        }

        float density = hotSeats instanceof View
                ? ((View) hotSeats).getResources().getDisplayMetrics().density
                : android.content.res.Resources.getSystem().getDisplayMetrics().density;
        float scale = dock.dimensionsDp ? density : 1f;
        float radiusPx = Math.min(
                Math.max(0f, dock.shadowRadius * scale),
                Math.max(0f, dock.shadowSize * scale));
        float offsetYPx = dock.shadowY * scale;

        HotSeatsShadowScope scope = new HotSeatsShadowScope(hotSeats);
        scope.overrideNumber("mMiShadowRadius", radiusPx);
        scope.overrideNumber("mMiShadowOffsetY", offsetYPx);

        // Alpha is owned exclusively at the terminal MiShadowUtils.applyViewShadow boundary by
        // DockNativeShadowBridge. Never mutate HotSeats alpha here: HyperOS 4.50 propagates
        // HotSeats.setAlpha() to every Dock child, including the real ShortcutIcon hierarchy.
        return scope;
    }

    private static LiquidDockConfig.Dock currentNativeShadowConfig() {
        LiquidDockConfig.Dock current = nativeShadowConfig;
        if (current != null) return current;
        try {
            current = LiquidDockConfig.load().dock;
            nativeShadowConfig = current;
        } catch (Throwable ignored) {
        }
        return current;
    }

    private static void setNumericField(java.lang.reflect.Field field, Object target, float value)
            throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == float.class || type == Float.class) field.set(target, value);
        else if (type == double.class || type == Double.class) field.set(target, (double) value);
        else if (type == int.class || type == Integer.class) field.set(target, Math.round(value));
        else if (type == long.class || type == Long.class) field.set(target, (long) Math.round(value));
        else if (type == short.class || type == Short.class) field.set(target, (short) Math.round(value));
        else if (type == byte.class || type == Byte.class) field.set(target, (byte) Math.round(value));
        else throw new IllegalArgumentException("not numeric: " + type);
    }

    /** Rebind runtime state by asking HotSeats to execute its own native shadow method. */
    private static void refreshVendorDockShadow() {
        Object hotSeats = hotSeatsShadowOwner();
        if (hotSeats == null) return;
        HookUtil.InvocationResult<Object> refresh = HookUtil.tryInvoke(hotSeats, "showViewShadow");
        if (!refresh.succeeded()) {
            log("[DC] HotSeats native shadow refresh failed: " + refresh.failure());
        }
    }

    private static void installDockShadowSetupHook(ClassLoader cl) {
        try {
            HookUtil.hookMethod(cl, "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        try {
                            LiquidDockConfig current = LiquidDockConfig.load();
                            nativeShadowConfig = current.dock;
                            Object hotSeats = HookUtil.getField(chain.getThisObject(), "mHotSeats");
                            if (hotSeats == null) return r;
                            setHotSeatsShadowOwner(hotSeats);
                            View background = resolveActiveDockBackground(hotSeats);
                            if (background != null) setOldBg(background);
                            refreshVendorDockShadow();
                        } catch (Throwable e) {
                            log("[DC] Dock shadow setup failed: " + e);
                        }
                        return r;
                    });
        } catch (Throwable e) {
            log("[DC] Dock shadow setup hook unavailable: " + e);
        }
    }

    /** Prefer the active theme-aware background and keep BlurBackground2 as compatibility fallback. */
    private static View resolveActiveDockBackground(Object hotSeats) {
        if (hotSeats == null) return null;
        HookUtil.InvocationResult<Object> activeResult =
                HookUtil.tryInvoke(hotSeats, "getHotSeatsBackground");
        if (activeResult.succeeded() && activeResult.value() instanceof View) {
            return (View) activeResult.value();
        }
        try {
            Object compat = HookUtil.getField(hotSeats, "mBlurBackground2");
            return compat instanceof View ? (View) compat : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Geometry callbacks only remember the active background/config; HotSeats owns shadow drawing. */
    static void syncDockShadow(View dockBg, LiquidDockConfig.Dock dock) {
        if (dockBg != null) setOldBg(dockBg);
        if (dock != null) nativeShadowConfig = dock;
    }

    static void onRuntimeDockShadowDisabled() {
        // The parent customization callback runs first when the parent itself is disabled. In that
        // case the current effective state already tells the HotSeats hook to pass vendor values.
        if (!DockShadowRuntimePolicy.shouldRefreshVendorShadow(
                workstationMode, VisualRuntimeState.isDockCustomizationEnabled())) return;
        refreshVendorDockShadow();
    }

    static void onRuntimeDockShadowEnabled() {
        if (!DockShadowRuntimePolicy.shouldRefreshVendorShadow(
                workstationMode, VisualRuntimeState.isDockCustomizationEnabled())) return;
        try {
            nativeShadowConfig = LiquidDockConfig.load().dock;
        } catch (Throwable ignored) {
        }
        refreshVendorDockShadow();
    }

    static void onRuntimeDockCustomizationDisabled() {
        refreshVendorDockShadow();
        DockStrokeRenderer.refreshInstalledFromCurrentConfig();
        View dockBg = oldBg();
        if (dockBg != null) dockBg.postInvalidateOnAnimation();
    }

    private static void installDockResizeAnimationBypass(
            ClassLoader cl, boolean smoothAnimation, int durationMs) {
        try {
            HookUtil.hookMethod(cl,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    "updateBackgroundSize",
                    chain -> {
                        int oldW = 0, oldH = 0; float oldR = 0f;
                        try {
                            oldW = HookUtil.getIntField(chain.getThisObject(), "mWidth");
                            oldH = HookUtil.getIntField(chain.getThisObject(), "mHeight");
                            Object r = HookUtil.getField(chain.getThisObject(), "mCornerRadius");
                            if (r instanceof Float) oldR = (Float) r;
                        } catch (Throwable ignored) {}
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object r = chain.proceed(args);
                        try {
                            Object set = HookUtil.getField(chain.getThisObject(), "animatorSet");
                            if (set instanceof android.animation.Animator)
                                ((android.animation.Animator) set).end();
                            Object radius = HookUtil.getField(chain.getThisObject(), "mViewRadiusAnimator");
                            if (radius instanceof android.animation.Animator)
                                ((android.animation.Animator) radius).end();
                        } catch (Throwable ignored) {}
                        View view = (View) chain.getThisObject();
                        if (smoothAnimation)
                            animateDockGeometryFromPrevious(view, oldW, oldH, oldR, durationMs);
                        else syncAll(view);
                        return r;
                    }, int.class, int.class, float.class);
            log("[DC] Dock resize animation disabled");
        } catch (Throwable e) { log("[DC] Dock resize animation bypass unavailable: " + e); }
    }

    private static void animateDockGeometryFromPrevious(
            View view, int startW, int startH, float startR, int durationMs) {
        try {
            int targetW = HookUtil.getIntField(view, "mWidth");
            int targetH = HookUtil.getIntField(view, "mHeight");
            float targetR = ((Number) HookUtil.getField(view, "mCornerRadius")).floatValue();
            synchronized (dockResizeAnimators) {
                android.animation.ValueAnimator previous = dockResizeAnimators.remove(view);
                if (previous != null) {
                    startW = HookUtil.getIntField(view, "mWidth");
                    startH = HookUtil.getIntField(view, "mHeight");
                    startR = ((Number) HookUtil.getField(view, "mCornerRadius")).floatValue();
                    previous.cancel();
                }
                if (startW == targetW && startH == targetH && Math.abs(startR - targetR) < .01f) {
                    syncAll(view); return;
                }
                final int fromW = startW, fromH = startH;
                final float fromR = startR;
                HookUtil.setIntField(view, "mWidth", fromW);
                HookUtil.setIntField(view, "mHeight", fromH);
                HookUtil.setField(view, "mCornerRadius", fromR);
                android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
                a.setDuration(durationMs);
                a.setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f));
                a.addUpdateListener(animator -> {
                    float t = (Float) animator.getAnimatedValue();
                    HookUtil.setIntField(view, "mWidth", Math.round(fromW + (targetW - fromW) * t));
                    HookUtil.setIntField(view, "mHeight", Math.round(fromH + (targetH - fromH) * t));
                    HookUtil.setField(view, "mCornerRadius", fromR + (targetR - fromR) * t);
                    HookUtil.InvocationResult<Object> measureResult =
                            HookUtil.tryInvoke(view, "triggerMeasure");
                    if (!measureResult.succeeded()) {
                        // Optional on some Launcher builds; requestLayout below is the fallback.
                    }
                    view.requestLayout();
                    syncAll(view);
                });
                a.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        synchronized (dockResizeAnimators) { dockResizeAnimators.remove(view); }
                    }
                });
                dockResizeAnimators.put(view, a);
                a.start();
            }
        } catch (Throwable e) {
            syncAll(view);
            log("[DC] smooth Dock resize failed: " + e);
        }
    }

    private static void installWorkstationDockHooks(ClassLoader cl, LiquidDockConfig.Workstation config) {
        if (!config.dockEnabled) return;
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int iconTopOffset = Math.round(config.iconTopOffset * scale);
        int iconBottomOffset = Math.round(config.iconBottomOffset * scale);
        try {
            Class<?> recyclerView = Class.forName("androidx.recyclerview.widget.RecyclerView", false, cl);
            Class<?> recyclerState = Class.forName("androidx.recyclerview.widget.RecyclerView$State", false, cl);
            HookUtil.hookMethod(cl,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                    "getItemOffsets",
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (workstationMode) {
                            Rect out = (Rect) chain.getArgs().get(0);
                            out.top += iconTopOffset;
                            out.bottom += iconBottomOffset;
                        }
                        return r;
                    }, Rect.class, View.class, recyclerView, recyclerState);
            log("[DC] workstation Dock icon vertical offsets top=" + iconTopOffset + " bottom=" + iconBottomOffset);
        } catch (Throwable e) { log("[DC] workstation Dock icon offset hook unavailable: " + e); }
    }

    private static void installWorkstationModeGuard(ClassLoader cl) {
        boolean detected = false;
        try {
            Class<?> mc = Class.forName("com.miui.home.launcher.allapps.LauncherModeController", false, cl);
            HookUtil.InvocationResult<Object> laptopProbe = HookUtil.tryInvokeStatic(
                    "com.miui.home.launcher.allapps.LauncherModeController", "isLaptopMode");
            Object laptopResult = laptopProbe.succeeded() ? laptopProbe.value() : null;
            if (laptopResult instanceof Boolean) {
                workstationMode = (Boolean) laptopResult;
            } else {
                HookUtil.InvocationResult<Object> dcProbe = HookUtil.tryInvokeStatic(
                        "com.miui.home.launcher.DeviceConfig", "isMingouLaptopPcModeEnabled");
                Object dcResult = dcProbe.succeeded() ? dcProbe.value() : null;
                workstationMode = dcResult instanceof Boolean && (Boolean) dcResult;
            }
            Class<?> sm = Class.forName("com.miui.home.launcher.laptop.LaptopStateManager", false, cl);
            HookUtil.hookMethod(sm, "onLaptopModeChanged", new Class<?>[]{boolean.class},
                    chain -> {
                        boolean entering = (Boolean) chain.getArgs().get(0);
                        workstationModeHookConfirmed = true;
                        if (entering) backupNormalHomeLayout();
                        setWorkstationMode(entering);
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!entering) scheduleNormalLayoutRestore();
                        HomeGridHook.scheduleAllPageRefresh();
                        return r;
                    });
            detected = true;
            log("[DC] workstation guard uses LauncherModeController; active=" + workstationMode);
            // Deferred re-check: isLaptopMode() may return null at early startup;
            // re-query after the Launcher has finished initializing its mode state.
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (workstationModeHookConfirmed) return; // hook already confirmed the state
                try {
                    HookUtil.InvocationResult<Object> recheckResult = HookUtil.tryInvokeStatic(
                            "com.miui.home.launcher.allapps.LauncherModeController", "isLaptopMode");
                    Object recheck = recheckResult.succeeded() ? recheckResult.value() : null;
                    boolean actual = recheck instanceof Boolean && (Boolean) recheck;
                    if (!recheckResult.succeeded() || recheck == null) {
                        HookUtil.InvocationResult<Object> dcResult = HookUtil.tryInvokeStatic(
                                "com.miui.home.launcher.DeviceConfig",
                                "isMingouLaptopPcModeEnabled");
                        if (dcResult.succeeded()) {
                            actual = dcResult.value() instanceof Boolean && (Boolean) dcResult.value();
                        }
                    }
                    if (actual != workstationMode) setWorkstationMode(actual);
                } catch (Throwable ignored) {}
            }, 2000L);
        } catch (Throwable currentApiError) {
            log("[DC] current workstation API unavailable: " + currentApiError);
        }
        if (!detected) try {
            Class<?> dc = Class.forName("com.miui.home.launcher.DeviceConfig", false, cl);
            workstationMode = (Boolean) HookUtil.requireInvokeStatic(
                    "com.miui.home.launcher.DeviceConfig", "isMingouLaptopPcModeEnabled");
            HookUtil.hookMethod(dc, "setMingouLaptopPcModeEnabled", new Class<?>[]{boolean.class},
                    chain -> {
                        setWorkstationMode((Boolean) chain.getArgs().get(0));
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            detected = true;
            log("[DC] workstation guard uses legacy DeviceConfig; active=" + workstationMode);
        } catch (Throwable legacyApiError) {
            log("[DC] legacy workstation API unavailable: " + legacyApiError);
        }
        if (!detected) {
            workstationMode = false;
            log("[DC] ERROR: no supported workstation state API found");
        }
    }

    private static void setWorkstationMode(boolean enabled) {
        workstationMode = enabled;
        HomeGridHook.setWorkstationMode(enabled);
        WorkstationDockGeometryHook.onWorkstationModeChanged(enabled);
        log("[DC] Mingou workstation mode changed=" + enabled);
        // The effective workstation flag is published before this refresh, so entering passes the
        // untouched vendor state and leaving reapplies LiquidDock's temporary native parameters.
        refreshVendorDockShadow();
        View dockBg = oldBg();
        if (!enabled) {
            if (dockBg != null) dockBg.post(() -> {
                dockBg.setAlpha(1f);
                syncAll(dockBg);
            });
            return;
        }
        if (dockBg != null) dockBg.post(() -> {
            // The injected Prismal surface is a child of this vendor material. Keep the host
            // visible while HotSeats owns its native Dock shadow.
            dockBg.setAlpha(1f);
        });
        Miuix307ZeroCopyRenderer.setProducerUpdatesEnabled(
                true, "workstation-mode-enabled");
    }

    private static void backupNormalHomeLayout() {
        normalLayoutBackup.clear();
        View dockBg = oldBg();
        View root = dockBg == null ? null : dockBg.getRootView();
        if (root != null) collectHomeItemPositions(root, false);
        log("[DC] normal 8x4 layout backup items=" + normalLayoutBackup.size());
    }

    private static void scheduleNormalLayoutRestore() {
        View dockBg = oldBg();
        View root = dockBg == null ? null : dockBg.getRootView();
        if (root == null || normalLayoutBackup.isEmpty()) return;
        root.post(() -> restoreNormalLayout(root));
        root.postDelayed(() -> restoreNormalLayout(root), 250L);
        root.postDelayed(() -> restoreNormalLayout(root), 700L);
    }

    private static void restoreNormalLayout(View root) {
        restoreNormalHomeLayout(root);
    }

    private static void collectHomeItemPositions(View view, boolean restore) {
        Object tag = view.getTag();
        if (tag != null) try {
            long id = HookUtil.getLongField(tag, "id");
            if (id >= 0) {
                if (!restore) {
                    normalLayoutBackup.put(id, new HomeItemPosition(
                            HookUtil.getLongField(tag, "screenId"),
                            HookUtil.getIntField(tag, "cellX"), HookUtil.getIntField(tag, "cellY"),
                            HookUtil.getIntField(tag, "spanX"), HookUtil.getIntField(tag, "spanY")));
                } else {
                    HomeItemPosition saved = normalLayoutBackup.get(id);
                    if (saved != null) {
                        HookUtil.setLongField(tag, "screenId", saved.screenId);
                        HookUtil.setIntField(tag, "cellX", saved.cellX);
                        HookUtil.setIntField(tag, "cellY", saved.cellY);
                        HookUtil.setIntField(tag, "spanX", saved.spanX);
                        HookUtil.setIntField(tag, "spanY", saved.spanY);
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collectHomeItemPositions(group.getChildAt(i), restore);
        }
    }

    private static void restoreNormalHomeLayout(View root) {
        if (workstationMode) return;
        collectHomeItemPositions(root, true);
        root.requestLayout();
        root.invalidate();
        log("[DC] normal 8x4 layout restored from backup items=" + normalLayoutBackup.size());
    }

    // ── drawing / sync ───────────────────────────────────────────────

    private static Path squirclePath(RectF r, float rad) { return squirclePath(r, rad, 0.65f); }
    private static Path squirclePath(RectF r, float rad, float cp) {
        Path p = new Path(); if (rad <= 1) { p.addRect(r, Path.Direction.CW); return p; }
        float a = rad, c = a * cp, l = r.left, t = r.top, ri = r.right, b = r.bottom;
        p.moveTo(l, t + a); p.cubicTo(l, t + a - c, l + a - c, t, l + a, t); p.lineTo(ri - a, t);
        p.cubicTo(ri - a + c, t, ri, t + a - c, ri, t + a); p.lineTo(ri, b - a);
        p.cubicTo(ri, b - a + c, ri - a + c, b, ri - a, b); p.lineTo(l + a, b);
        p.cubicTo(l + a - c, b, l, b - a + c, l, b - a); p.close(); return p;
    }

    private static void syncAll(View bg) {
        if (bg == null) return;
        setOldBg(bg);
        DockShadowRuntimePolicy.GeometrySync sync =
                DockShadowRuntimePolicy.geometrySync(workstationMode, animating(bg));
        if (sync == DockShadowRuntimePolicy.GeometrySync.REMEMBER_ONLY) return;
        try {
            syncDockShadow(bg, LiquidDockConfig.load().dock);
        } catch (Throwable e) {
            log("[DC] native Dock shadow config sync failed: " + e);
        }
    }

    static boolean isWorkstationMode() { return workstationMode; }

    // ── logging ──────────────────────────────────────────────────────

    static boolean debugLogging;

    static void log(String s) { if (!debugLogging) return; Api101Bridge.log(s); fileLog(s); }

    private static void fileLog(String s) {
        try {
            String line = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT)
                .format(new java.util.Date()) + " " + s + "\n";
            java.io.File dir = new java.io.File("/sdcard/Download");
            if (!dir.canWrite()) dir = new java.io.File("/data/local/tmp");
            java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(dir, "liquiddock.log"), true);
            out.write(line.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {}
    }

    private static boolean animating(View v) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(v, "isAnimating");
        return result.succeeded() && Boolean.TRUE.equals(result.value());
    }

    // ── data ─────────────────────────────────────────────────────────

    private static final class HotSeatsShadowScope implements AutoCloseable {
        private static final HotSeatsShadowScope NOOP = new HotSeatsShadowScope(null);
        private final Object target;
        private final java.util.ArrayList<ShadowFieldState> fields = new java.util.ArrayList<>();

        HotSeatsShadowScope(Object target) {
            this.target = target;
        }

        static HotSeatsShadowScope noop() {
            return NOOP;
        }

        boolean overrideNumber(String name, float value) {
            if (target == null) return false;
            try {
                java.lang.reflect.Field field = HookUtil.findField(target.getClass(), name);
                Object oldValue = field.get(target);
                setNumericField(field, target, value);
                fields.add(new ShadowFieldState(field, oldValue));
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override public void close() {
            if (target == null) return;
            for (int i = fields.size() - 1; i >= 0; i--) {
                ShadowFieldState state = fields.get(i);
                try { state.field.set(target, state.value); }
                catch (Throwable ignored) {}
            }
            fields.clear();
        }
    }

    private static final class ShadowFieldState {
        final java.lang.reflect.Field field;
        final Object value;

        ShadowFieldState(java.lang.reflect.Field field, Object value) {
            this.field = field;
            this.value = value;
        }
    }

    private static final class HomeItemPosition {
        final long screenId;
        final int cellX, cellY, spanX, spanY;
        HomeItemPosition(long screenId, int cellX, int cellY, int spanX, int spanY) {
            this.screenId = screenId; this.cellX = cellX; this.cellY = cellY;
            this.spanX = spanX; this.spanY = spanY;
        }
    }
}
