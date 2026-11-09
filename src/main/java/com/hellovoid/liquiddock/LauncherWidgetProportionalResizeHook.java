package com.hellovoid.liquiddock;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Restores the proportional presentation of widgets expanded from 1x1. */
final class LauncherWidgetProportionalResizeHook {
    private static final String TAG = "[DC][Widget2x2]";
    private static final String PREFIX = "widget_scale_";
    private static final String PREFS = "liquiddock_widget_scale";

    private static final WeakHashMap<View, Boolean> OBSERVED = new WeakHashMap<>();
    private static Class<?> widgetViewClass;
    private static Class<?> cellLayoutClass;
    private static Method getHostView;
    private static Class<?> mamlWidgetClass;
    private static Class<?> mamlInfoClass;
    private static Method getMamlHostView;
    private static Method getMamlProduct;
    private static Method setMamlScale;
    private static Method setMamlTranslate;
    private static Field mamlRoot;
    private static Method getMamlRootScale;
    private static Method putMamlVariable;
    private static Method requestMamlUpdate;
    private static WidgetGrid widgetGrid;
    private static final WeakHashMap<View, MamlPresentation> MAML_PRESENTATIONS =
            new WeakHashMap<>();
    private static Method getAppWidgetInfo;
    private static Method getCellWidth;
    private static Method getCellHeight;
    private static boolean installed;

    private LauncherWidgetProportionalResizeHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) return false;
        try {
            widgetViewClass = Class.forName("com.miui.home.launcher.LauncherWidgetView",
                    false, loader);
            cellLayoutClass = Class.forName("com.miui.home.launcher.CellLayout", false, loader);
            getHostView = HookUtil.findMethodExact(widgetViewClass, "getHostView", new Class<?>[0]);
            getAppWidgetInfo = HookUtil.findMethodExact(
                    widgetViewClass, "getAppWidgetInfo", new Class<?>[0]);
            getCellWidth = HookUtil.findMethodExact(cellLayoutClass, "getCellWidth", new Class<?>[0]);
            getCellHeight = HookUtil.findMethodExact(cellLayoutClass, "getCellHeight", new Class<?>[0]);

            // Concrete OEM widget lifecycle hooks only, never global View hooks.
            Method attached = HookUtil.findMethodExact(widgetViewClass,
                    "onAttachedToWindow", new Class<?>[0]);
            Method setWidget = HookUtil.findMethodExact(widgetViewClass,
                    "setWidget", new Class<?>[]{AppWidgetHostView.class});
            HookUtil.hook(attached, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof View) {
                    View widget = (View) chain.getThisObject();
                    widget.post(() -> observeIfMarked(widget));
                }
                return result;
            });
            HookUtil.hook(setWidget, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof View) {
                    View widget = (View) chain.getThisObject();
                    widget.post(() -> observeIfMarked(widget));
                }
                return result;
            });
            installMaml(loader);
            installed = true;
            MainHook.log(TAG + " proportional widget presentation installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " native resize API unavailable; no behavior changed: " + error);
            return false;
        }
    }

    private static void installMaml(ClassLoader loader) {
        try {
            String base = "com.miui.home.launcher.";
            Class<?> widget = Class.forName(base + "maml.MaMlWidgetView", false, loader);
            Class<?> info = Class.forName(base + "maml.MaMlWidgetInfo", false, loader);
            Class<?> host = Class.forName(base + "maml.MaMlHostView", false, loader);
            getMamlHostView = HookUtil.findMethodExact(widget, "getHostView", new Class<?>[0]);
            getMamlProduct = HookUtil.findMethodExact(info, "getProductId", new Class<?>[0]);
            Class<?> mamlView = host.getSuperclass();
            setMamlScale = HookUtil.findMethodExact(mamlView, "setScale",
                    new Class<?>[]{float.class, int.class, int.class});
            setMamlTranslate = HookUtil.findMethodExact(mamlView, "setTranslate",
                    new Class<?>[]{float.class, float.class});
            mamlRoot = HookUtil.findField(mamlView, "mRoot");
            Class<?> root = mamlRoot.getType();
            getMamlRootScale = HookUtil.findMethodExact(root, "getScale", new Class<?>[0]);
            putMamlVariable = HookUtil.findMethodExact(mamlView, "putVariableNumber",
                    new Class<?>[]{String.class, double.class});
            requestMamlUpdate = HookUtil.findMethodExact(root, "requestUpdate", new Class<?>[0]);
            widgetGrid = new WidgetGrid(loader);

            Method setMaml = HookUtil.findMethodExact(widget, "setMaMl", new Class<?>[]{host});
            Method layout = host.getDeclaredMethod("onLayout",
                    boolean.class, int.class, int.class, int.class, int.class);
            Method touch = mamlView.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            HookUtil.hook(layout, chain -> {
                Object result = chain.proceed();
                updateMamlPresentation((View) chain.getThisObject());
                return result;
            });
            // Launcher 4.50 posts a second size correction after layout and resume.
            // Reapply the provider viewport in that same callback, after its native write.
            try {
                Method ensureSize = HookUtil.findMethodExact(
                        mamlView, "lambda$ensureViewSize$2", new Class<?>[0]);
                HookUtil.hook(ensureSize, chain -> {
                    Object result = chain.proceed();
                    updateMamlPresentation((View) chain.getThisObject());
                    return result;
                });
            } catch (NoSuchMethodException ignored) {
                // Other Launcher versions may post the correction under a different callback.
                try {
                    Method ensureSize = HookUtil.findMethodExact(
                            mamlView, "ensureViewSize", new Class<?>[0]);
                    HookUtil.hook(ensureSize, chain -> {
                        Object result = chain.proceed();
                        View view = (View) chain.getThisObject();
                        if (host.isInstance(view)) view.post(() -> updateMamlPresentation(view));
                        return result;
                    });
                } catch (NoSuchMethodException noDeferredCorrection) {
                    // Layout is the only known size publication on this version.
                }
            }
            HookUtil.hook(touch, chain -> {
                MamlPresentation presentation = MAML_PRESENTATIONS.get(chain.getThisObject());
                Matrix inverse = presentation == null ? null : presentation.inverse;
                if (inverse == null) return chain.proceed();
                MotionEvent event = MotionEvent.obtain((MotionEvent) chain.getArg(0));
                try {
                    event.transform(inverse);
                    return chain.proceed(new Object[]{event});
                } finally {
                    event.recycle();
                }
            });
            HookUtil.hook(setMaml, chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                view.post(() -> observeIfMarked(view));
                return result;
            });
            mamlInfoClass = info;
            mamlWidgetClass = widget;
        } catch (Throwable error) {
            MainHook.log(TAG + " MAML widget presentation unavailable: " + error);
        }
    }

    static String widgetIdentity(View wrapper, Object info) throws ReflectiveOperationException {
        if (widgetViewClass.isInstance(wrapper)) {
            AppWidgetProviderInfo provider = (AppWidgetProviderInfo) getAppWidgetInfo.invoke(wrapper);
            return provider == null || provider.provider == null
                    ? null : provider.provider.flattenToString();
        }
        if (mamlWidgetClass != null && mamlWidgetClass.isInstance(wrapper)
                && mamlInfoClass.isInstance(info)) {
            String product = (String) getMamlProduct.invoke(info);
            if (product == null || product.isEmpty()) return null;
            return "maml:" + product + ":" + HookUtil.getIntField(info, "gadgetId");
        }
        return null;
    }

    static void setExpanded(View wrapper, Object info, String identity, boolean expanded) {
        String key = markerKey(info);
        if (key == null) return;
        android.content.SharedPreferences.Editor editor = wrapper.getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (expanded) {
            editor.putString(key, identity).apply();
            observeIfMarked(wrapper);
        } else {
            editor.remove(key).apply();
        }
    }

    private static View ancestor(View view, Class<?> clazz) {
        for (View cursor = view; cursor != null;) {
            if (clazz.isInstance(cursor)) return cursor;
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static String markerKey(Object info) {
        try {
            long id = HookUtil.getLongField(info, "id");
            return id >= 0 ? PREFIX + id : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isMarked(View wrapper, Object info) {
        if (wrapper == null || info == null) return false;
        try {
            return isMarked(wrapper, info, widgetIdentity(wrapper, info));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }

    static boolean isMarked(View wrapper, Object info, String identity) {
        String key = markerKey(info);
        return key != null && identity != null && identity.equals(wrapper.getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null));
    }

    private static View hostView(View wrapper) {
        try {
            Method method = widgetViewClass.isInstance(wrapper) ? getHostView : getMamlHostView;
            return (View) method.invoke(wrapper);
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    /** Mirrors LauncherWidgetContainerView's measurement grid selection. */
    private static final class WidgetGrid {
        final Method findUsedGrid, getController, suggestedGrid, getCellSize, getWidgetSize;

        WidgetGrid(ClassLoader loader) throws ReflectiveOperationException {
            String prefix = "com.miui.home.launcher.grid.";
            Class<?> utils = Class.forName(prefix + "GridUtils", false, loader);
            Class<?> controller = Class.forName(prefix + "GridController", false, loader);
            Class<?> config = Class.forName(prefix + "GridConfig", false, loader);
            findUsedGrid = HookUtil.findMethodExact(utils, "findParentUsedGridConfig",
                    new Class<?>[]{View.class});
            getController = HookUtil.findMethodExact(controller, "getInstance", new Class<?>[0]);
            suggestedGrid = HookUtil.findMethodExact(
                    controller, "getSuggestFullScreenGridConfig", new Class<?>[0]);
            getCellSize = HookUtil.findMethodExact(config, "getCellSize", new Class<?>[0]);
            getWidgetSize = HookUtil.findMethodExact(config, "getWidgetSize",
                    new Class<?>[]{int.class});
        }

        int oneCellSize(View host) throws ReflectiveOperationException {
            if (!(host.getParent() instanceof View)) return 0;
            Object used = findUsedGrid.invoke(null, host.getParent());
            Object controller = getController.invoke(null);
            Object suggested = controller == null ? null : suggestedGrid.invoke(controller);
            if (used == null) return 0;
            Object measurementGrid = suggested != null
                    && (Integer) getCellSize.invoke(suggested) <= (Integer) getCellSize.invoke(used)
                    ? suggested : used;
            return (Integer) getWidgetSize.invoke(measurementGrid, 1);
        }
    }

    private static void updateMamlPresentation(View host) {
        MamlPresentation presentation = MAML_PRESENTATIONS.get(host);
        if (presentation != null) presentation.update(host);
    }

    private static final class MamlPresentation {
        final WeakReference<View> wrapper;
        Matrix inverse;
        private float scale = 1f, x, y;

        MamlPresentation(View wrapper) {
            this.wrapper = new WeakReference<>(wrapper);
        }

        void update(View host) {
            View owner = wrapper.get();
            Object info = owner == null ? null : owner.getTag();
            try {
                if (!isExpanded(info) || !isMarked(owner, info)) {
                    setTransform(host, 1f, 0f, 0f);
                    MAML_PRESENTATIONS.remove(host);
                    return;
                }
                Object root = mamlRoot.get(host);
                if (root == null) return;
                int source = widgetGrid.oneCellSize(host);
                if (source <= 0) return;
                int parentInsetX = 0, parentInsetY = 0;
                if (host.getParent() instanceof View) {
                    View container = (View) host.getParent();
                    parentInsetX = Math.max(0, container.getWidth() - host.getWidth());
                    parentInsetY = Math.max(0, container.getHeight() - host.getHeight());
                }
                float rootScale = (Float) getMamlRootScale.invoke(root);
                WidgetProportionalScalePolicy.ContentViewport viewport =
                        WidgetProportionalScalePolicy.contentViewport(
                                host.getWidth(), host.getHeight(),
                                source - parentInsetX, source - parentInsetY, rootScale);
                if (viewport == null) return;

                // MamlView.onLayout publishes the physical host size before this runs.
                // Keep responsive artwork in its original 1x1 coordinate space, then
                // enlarge it once on the inner canvas. Leave the host and its outline alone.
                putMamlVariable.invoke(host, "view_width", viewport.logicalWidth);
                putMamlVariable.invoke(host, "view_height", viewport.logicalHeight);
                setTransform(host, viewport.scale, viewport.left, viewport.top);
                requestMamlUpdate.invoke(root);
            } catch (ReflectiveOperationException | RuntimeException error) {
                MainHook.log(TAG + " MAML content sizing failed: " + error);
                MAML_PRESENTATIONS.remove(host);
            }
        }

        private void setTransform(View host, float nextScale, float left, float top)
                throws ReflectiveOperationException {
            if (scale == nextScale && x == left && y == top) return;
            setMamlScale.invoke(host, nextScale, 0, 0);
            setMamlTranslate.invoke(host, left, top);
            scale = nextScale;
            x = left;
            y = top;
            if (scale == 1f && x == 0f && y == 0f) {
                inverse = null;
            } else {
                inverse = new Matrix();
                inverse.setTranslate(-x, -y);
                inverse.postScale(1f / scale, 1f / scale);
            }
        }
    }

    private static boolean isExpanded(Object info) {
        return info != null && HookUtil.getIntField(info, "spanX") == 2
                && HookUtil.getIntField(info, "spanY") == 2;
    }

    private static void observeIfMarked(View wrapper) {
        Object info = wrapper.getTag();
        if (!isMarked(wrapper, info)) return;
        if (mamlWidgetClass != null && mamlWidgetClass.isInstance(wrapper)) {
            View host = hostView(wrapper);
            if (host == null) return;
            MamlPresentation presentation = MAML_PRESENTATIONS.get(host);
            if (presentation == null) {
                presentation = new MamlPresentation(wrapper);
                MAML_PRESENTATIONS.put(host, presentation);
            }
            presentation.update(host);
            return;
        }
        if (OBSERVED.containsKey(wrapper)) return;
        OBSERVED.put(wrapper, Boolean.TRUE);
        WeakReference<View> reference = new WeakReference<>(wrapper);
        class ContentObserver implements ViewTreeObserver.OnPreDrawListener,
                View.OnAttachStateChangeListener {
            private WeakReference<View> scaledContent = new WeakReference<>(null);

            @Override public boolean onPreDraw() {
                View owner = reference.get();
                if (owner == null) return true;
                try {
                    Object info = owner.getTag();
                    View host = hostView(owner);
                    View content = host instanceof ViewGroup ? ((ViewGroup) host).getChildAt(0) : null;
                    View previous = scaledContent.get();
                    if (previous != null && previous != content) reset(previous);
                    if (!isMarked(owner, info)) {
                        if (previous != null && previous == content) reset(content);
                        stop(owner);
                        return true;
                    }
                    if (content == null) return true;
                    View cell = ancestor(owner, cellLayoutClass);
                    if (cell == null || !isExpanded(info)) {
                        if (previous != null && previous == content) reset(content);
                        return true;
                    }
                    int cellW = (Integer) getCellWidth.invoke(cell);
                    int cellH = (Integer) getCellHeight.invoke(cell);
                    int left = host.getPaddingLeft(), top = host.getPaddingTop();
                    WidgetProportionalScalePolicy.Frame frame = WidgetProportionalScalePolicy.fit(
                            left, top, host.getWidth() - left - host.getPaddingRight(),
                            host.getHeight() - top - host.getPaddingBottom(), cellW, cellH);
                    if (frame == null) return true;
                    if (content.getWidth() != frame.width || content.getHeight() != frame.height
                            || content.getLeft() != frame.left || content.getTop() != frame.top) {
                        content.measure(View.MeasureSpec.makeMeasureSpec(frame.width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(frame.height, View.MeasureSpec.EXACTLY));
                        content.layout(frame.left, frame.top,
                                frame.left + frame.width, frame.top + frame.height);
                    }
                    if (previous != content || content.getScaleX() != frame.scale
                            || content.getScaleY() != frame.scale) {
                        content.setPivotX(frame.width / 2f);
                        content.setPivotY(frame.height / 2f);
                        content.setScaleX(frame.scale);
                        content.setScaleY(frame.scale);
                        scaledContent = new WeakReference<>(content);
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    View previous = scaledContent.get();
                    if (previous != null) reset(previous);
                }
                return true;
            }

            private void reset(View view) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                scaledContent.clear();
            }

            @Override public void onViewAttachedToWindow(View view) {}

            @Override public void onViewDetachedFromWindow(View view) {
                stop(view);
            }

            private void stop(View view) {
                ViewTreeObserver tree = view.getViewTreeObserver();
                if (tree.isAlive()) tree.removeOnPreDrawListener(this);
                view.removeOnAttachStateChangeListener(this);
                OBSERVED.remove(view);
            }
        }
        ContentObserver observer = new ContentObserver();
        wrapper.addOnAttachStateChangeListener(observer);
        wrapper.getViewTreeObserver().addOnPreDrawListener(observer);
    }
}
