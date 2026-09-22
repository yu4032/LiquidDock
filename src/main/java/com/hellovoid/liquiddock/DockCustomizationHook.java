package com.hellovoid.liquiddock;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** Owns the legacy/non-zero-copy Dock customization hooks. */
final class DockCustomizationHook {
    private static float strokeRadius = 30f;

    private DockCustomizationHook() {}

    static void install(ClassLoader classLoader, DockInstallConfig config) {
        if (config == null) return;
        MainHook.log("[DC] init: bl=" + config.blurRadius + " sq=" + config.squircle);
        try {
            final String backgroundClass =
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

            if (config.spacingPx != 0) {
                installSpacing(classLoader, config.spacingPx);
            }

            HookUtil.hookMethod(classLoader, backgroundClass, "setBackgroundWidth",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (VisualRuntimeState.isDockCustomizationEnabled()
                                && !WorkstationRuntimeState.isActive()
                                && config.widthOffsetPx != 0) {
                            args[0] = (int) args[0] + config.widthOffsetPx;
                        }
                        Object result = chain.proceed(args);
                        syncAll((View) chain.getThisObject());
                        return result;
                    }, int.class);

            HookUtil.hookMethod(classLoader, backgroundClass, "setBackgroundHeight",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (VisualRuntimeState.isDockCustomizationEnabled()
                                && !WorkstationRuntimeState.isActive()
                                && config.heightOffsetPx != 0) {
                            args[0] = (int) args[0] + config.heightOffsetPx;
                        }
                        Object result = chain.proceed(args);
                        syncAll((View) chain.getThisObject());
                        return result;
                    }, int.class);

            HookUtil.hookMethod(classLoader, backgroundClass, "setBackgroundRadius",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (!WorkstationRuntimeState.isActive()
                                && VisualRuntimeState.isDockCustomizationEnabled()) {
                            View view = (View) chain.getThisObject();
                            float systemRadius = (Float) args[0];
                            if (!animating(view)) {
                                strokeRadius = Math.max(
                                        0f, systemRadius + config.cornerOffsetPx);
                            }
                            args[0] = Math.max(
                                    0f, systemRadius + config.blurCornerOffsetPx);
                        }

                        Object result = chain.proceed(args);
                        View view = (View) chain.getThisObject();
                        syncAll(view);
                        if (config.squircle
                                && VisualRuntimeState.isDockCustomizationEnabled()
                                && !animating(view)) {
                            float radius = (Float) HookUtil.getField(view, "mCornerRadius");
                            if (radius > 0) {
                                view.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                    @Override
                                    public void getOutline(
                                            View target, android.graphics.Outline outline) {
                                        outline.setPath(squirclePath(
                                                new RectF(
                                                        0, 0,
                                                        view.getWidth(),
                                                        view.getHeight()),
                                                radius));
                                    }
                                });
                            }
                        }
                        return result;
                    }, float.class);

            installBlurRadius(classLoader, config.blurRadius);
        } catch (Throwable error) {
            MainHook.log("[DC] init err: " + error);
        }
    }

    static void syncAll(View background) {
        if (background == null) return;
        DockShadowOwnership.rememberBackground(background);
        DockShadowRuntimePolicy.GeometrySync sync =
                DockShadowRuntimePolicy.geometrySync(
                        WorkstationRuntimeState.isActive(), animating(background));
        if (sync == DockShadowRuntimePolicy.GeometrySync.REMEMBER_ONLY) return;
        try {
            DockShadowOwnership.syncDockShadow(background, LiquidDockConfig.load().dock);
        } catch (Throwable error) {
            MainHook.log("[DC] native Dock shadow config sync failed: " + error);
        }
    }

    static float strokeRadius() {
        return strokeRadius;
    }

    private static void installSpacing(ClassLoader classLoader, int spacing) {
        try {
            Class<?> recyclerView = Class.forName(
                    "androidx.recyclerview.widget.RecyclerView", false, classLoader);
            Class<?> recyclerState = Class.forName(
                    "androidx.recyclerview.widget.RecyclerView$State", false, classLoader);
            HookUtil.hookMethod(
                    classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                    "getItemOffsets",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (WorkstationRuntimeState.isActive()
                                || !VisualRuntimeState.isDockCustomizationEnabled()) {
                            return result;
                        }
                        Rect out = (Rect) chain.getArg(0);
                        out.left += spacing;
                        out.right += spacing;
                        return result;
                    },
                    Rect.class, View.class, recyclerView, recyclerState);

            Class<?> layoutManager = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager",
                    false,
                    classLoader);
            HookUtil.hookMethod(layoutManager, "updateBackgroundView",
                    new Class<?>[]{FrameLayout.class, int.class, int.class, float.class},
                    chain -> {
                        if (WorkstationRuntimeState.isActive()
                                || !VisualRuntimeState.isDockCustomizationEnabled()) {
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
        } catch (Throwable error) {
            MainHook.log("[DC] spacing hook unavailable: " + error);
        }
    }

    private static void installBlurRadius(ClassLoader classLoader, int blurRadius) {
        try {
            Class<?> blurUtilities = Class.forName(
                    "com.miui.home.launcher.common.BlurUtilities", false, classLoader);
            HookUtil.hookMethod(
                    blurUtilities,
                    "setBackgroundBlur",
                    new Class<?>[]{View.class, int.class, float[].class, int[][].class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (VisualRuntimeState.isDockCustomizationEnabled()
                                && !WorkstationRuntimeState.isActive()
                                && blurRadius != 100) {
                            args[1] = blurRadius;
                        }
                        return chain.proceed(args);
                    });
        } catch (Throwable ignored) {}
    }

    private static Path squirclePath(RectF rect, float radius) {
        return squirclePath(rect, radius, 0.65f);
    }

    private static Path squirclePath(RectF rect, float radius, float cp) {
        Path path = new Path();
        if (radius <= 1) {
            path.addRect(rect, Path.Direction.CW);
            return path;
        }
        float a = radius;
        float c = a * cp;
        float left = rect.left;
        float top = rect.top;
        float right = rect.right;
        float bottom = rect.bottom;
        path.moveTo(left, top + a);
        path.cubicTo(left, top + a - c, left + a - c, top, left + a, top);
        path.lineTo(right - a, top);
        path.cubicTo(right - a + c, top, right, top + a - c, right, top + a);
        path.lineTo(right, bottom - a);
        path.cubicTo(
                right, bottom - a + c, right - a + c, bottom, right - a, bottom);
        path.lineTo(left + a, bottom);
        path.cubicTo(
                left + a - c, bottom, left, bottom - a + c, left, bottom - a);
        path.close();
        return path;
    }

    private static boolean animating(View view) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(view, "isAnimating");
        return result.succeeded() && Boolean.TRUE.equals(result.value());
    }
}
