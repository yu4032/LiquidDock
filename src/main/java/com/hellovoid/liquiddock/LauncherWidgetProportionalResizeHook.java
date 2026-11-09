package com.hellovoid.liquiddock;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Long-pressing a 1x1 AppWidget opens the stock Launcher resize handles.
 * Cell occupancy, collision handling, drag gestures and database commits stay OEM-owned.
 * Only a widget-specific presentation marker enables proportional 2x2 content scaling.
 */
final class LauncherWidgetProportionalResizeHook {
    private static final String TAG = "[DC][Widget2x2]";
    private static final String PREFIX = "widget_scale_";
    private static final String PREFS = "liquiddock_widget_scale";

    private static final WeakHashMap<View, Boolean> OBSERVED = new WeakHashMap<>();
    private static Class<?> appClass;
    private static Class<?> dragLayerClass;
    private static Class<?> widgetViewClass;
    private static Class<?> cellLayoutClass;
    private static Class<?> itemInfoClass;
    private static Class<?> hostViewClass;
    private static Method nativeAddResizeFrame;
    private static boolean installed;

    private LauncherWidgetProportionalResizeHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) return false;
        try {
            appClass = Class.forName("com.miui.home.launcher.Application", false, loader);
            itemInfoClass = Class.forName("com.miui.home.launcher.ItemInfo", false, loader);
            hostViewClass = Class.forName(
                    "com.miui.home.launcher.LauncherAppWidgetHostView", false, loader);
            widgetViewClass = Class.forName("com.miui.home.launcher.LauncherWidgetView",
                    false, loader);
            cellLayoutClass = Class.forName("com.miui.home.launcher.CellLayout",
                    false, loader);
            dragLayerClass = Class.forName("com.miui.home.launcher.DragLayer",
                    false, loader);

            // SuperDraglayer.addResizeFrame delegates to its registered, live
            // mWidgetResizeDragController (also the touch controller). Calling
            // DragLayer.getWidgetResizeDragController instead constructs a different
            // controller that does NOT receive drag-layer touch events.
            Class<?> superDragLayer = Class.forName(
                    "com.miui.home.launcher.SuperDraglayer", false, loader);
            nativeAddResizeFrame = HookUtil.findMethodExact(superDragLayer,
                    "addResizeFrame",
                    new Class<?>[]{itemInfoClass, widgetViewClass, cellLayoutClass});
            Class<?> agentClass = Class.forName(
                    "com.miui.home.launcher.OnLongClickAgent", false, loader);
            Method setListener = HookUtil.findMethodExact(agentClass,
                    "setOnLongClickListener",
                    new Class<?>[]{View.OnLongClickListener.class});

            // The LauncherAppWidgetHostView owns this agent, and OEM long-press
            // recognition remains untouched. Only its final callback is redirected.
            // Original long-press behavior remains available while editing.
            HookUtil.hook(setListener, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length != 1 || !(args[0] instanceof View.OnLongClickListener)) {
                    return chain.proceed(args);
                }
                Object client;
                try {
                    client = HookUtil.getField(chain.getThisObject(), "mClientView");
                } catch (Throwable ignored) {
                    return chain.proceed(args);
                }
                if (!(client instanceof View) || !hostViewClass.isInstance(client)) {
                    return chain.proceed(args);
                }
                View host = (View) client;
                View.OnLongClickListener original = (View.OnLongClickListener) args[0];
                args[0] = (View.OnLongClickListener) source -> {
                    if (tryOpenNativeResizeFrame(host)) return true;
                    return original.onLongClick(source);
                };
                return chain.proceed(args);
            });

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
            try {
                Class<?> infoClass = Class.forName(
                        "com.miui.home.launcher.LauncherAppWidgetInfo", false, loader);
                Class<?> launcherClass = Class.forName(
                        "com.miui.home.launcher.Launcher", false, loader);
                Method bind = HookUtil.findMethodExact(infoClass, "onBindAppWidget",
                        new Class<?>[]{launcherClass, widgetViewClass});
                HookUtil.hook(bind, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object widget = chain.getArg(1);
                    if (widget instanceof View) {
                        View view = (View) widget;
                        view.post(() -> observeIfMarked(view));
                    }
                    return result;
                });
            } catch (Throwable ignored) {
                // Some OEM builds bind before the host is attached; attach/setWidget
                // paths remain available for restoring the per-widget presentation.
            }
            installed = true;
            MainHook.log(TAG + " direct native resize integration installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " native resize API unavailable; no behavior changed: " + error);
            return false;
        }
    }

    private static Object widget(Object info) {
        if (info == null || !itemInfoClass.isInstance(info)) return null;
        try {
            Object host = HookUtil.getField(info, "hostView");
            return widgetViewClass.isInstance(host) ? host : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static AppWidgetProviderInfo provider(Object widget) {
        try {
            Object result = HookUtil.requireInvoke(widget, "getAppWidgetInfo");
            return result instanceof AppWidgetProviderInfo
                    ? (AppWidgetProviderInfo) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean eligible(Object info) {
        Object host = widget(info);
        if (!(host instanceof View) || provider(host) == null) return false;
        try {
            return WidgetProportionalScalePolicy.eligible(
                    HookUtil.getIntField(info, "spanX"),
                    HookUtil.getIntField(info, "spanY"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Returns false before any UI change when this is not a supported 1x1
     * widget, so the OEM long-press listener retains ownership.
     */
    private static boolean tryOpenNativeResizeFrame(View touchedHost) {
        View wrapper = ancestor(touchedHost, widgetViewClass);
        if (wrapper == null || !wrapper.isAttachedToWindow()) return false;
        Object info = wrapper.getTag();
        if (!eligible(info) || widget(info) != wrapper) return false;
        View cell = ancestor(wrapper, cellLayoutClass);
        if (cell == null) return false;

        try {
            Object launcher = HookUtil.requireInvokeStatic(appClass, "getLauncher");
            if (launcher == null) return false;
            Object editing = HookUtil.tryInvoke(launcher, "isInEditing").value();
            if (Boolean.TRUE.equals(editing)) return false;

            Object layer = HookUtil.requireInvoke(launcher, "getDragLayer");
            if (!dragLayerClass.isInstance(layer)) return false;
            // This exact controller must also belong to the OEM touch dispatch.
            if (HookUtil.getField(layer, "mWidgetResizeDragController") == null) return false;
            AppWidgetProviderInfo appProvider = provider(wrapper);
            Object vendorInfo = HookUtil.requireInvoke(info, "getLauncherProviderInfo");
            if (appProvider == null || vendorInfo == null) return false;

            int originalResizeMode = appProvider.resizeMode;
            int originalMinX = HookUtil.getIntField(vendorInfo, "minSpanX");
            int originalMinY = HookUtil.getIntField(vendorInfo, "minSpanY");
            int originalMaxX = HookUtil.getIntField(vendorInfo, "maxSpanX");
            int originalMaxY = HookUtil.getIntField(vendorInfo, "maxSpanY");

            // Values are copied into AppWidgetResizeFrame during construction.
            // Restore shared provider metadata on every exit path.
            try {
                appProvider.resizeMode = AppWidgetProviderInfo.RESIZE_BOTH;
                HookUtil.setIntField(vendorInfo, "minSpanX", 1);
                HookUtil.setIntField(vendorInfo, "minSpanY", 1);
                HookUtil.setIntField(vendorInfo, "maxSpanX", Math.max(2, originalMaxX));
                HookUtil.setIntField(vendorInfo, "maxSpanY", Math.max(2, originalMaxY));
                // The stock DragLayer owns both the overlay and touch controller.
                HookUtil.requireInvoke(layer, "clearAllResizeFrames");
                nativeAddResizeFrame.invoke(layer, info, wrapper, cell);
            } finally {
                appProvider.resizeMode = originalResizeMode;
                HookUtil.setIntField(vendorInfo, "minSpanX", originalMinX);
                HookUtil.setIntField(vendorInfo, "minSpanY", originalMinY);
                HookUtil.setIntField(vendorInfo, "maxSpanX", originalMaxX);
                HookUtil.setIntField(vendorInfo, "maxSpanY", originalMaxY);
            }

            saveMarker(wrapper.getContext(), info, appProvider);
            observeIfMarked(wrapper);
            MainHook.log(TAG + " opened stock resize frame for 1x1 widget");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " stock resize entry failed: " + error);
            return false;
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

    private static String identity(AppWidgetProviderInfo provider) {
        return provider != null && provider.provider != null
                ? provider.provider.flattenToString() : "";
    }

    private static void saveMarker(Context context, Object info, AppWidgetProviderInfo provider) {
        String key = markerKey(info);
        if (key != null && !identity(provider).isEmpty()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(key, identity(provider)).apply();
        }
    }

    private static boolean isMarked(View wrapper, Object info) {
        String key = markerKey(info);
        if (key == null) return false;
        AppWidgetProviderInfo infoProvider = provider(wrapper);
        return !identity(infoProvider).isEmpty()
                && identity(infoProvider).equals(wrapper.getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key, ""));
    }

    private static void observeIfMarked(View widget) {
        Object tag = widget.getTag();
        if (tag == null || !isMarked(widget, tag) || OBSERVED.containsKey(widget)) return;
        OBSERVED.put(widget, Boolean.TRUE);
        WeakReference<View> reference = new WeakReference<>(widget);
        ViewTreeObserver.OnPreDrawListener listener = new ViewTreeObserver.OnPreDrawListener() {
            private boolean scaled;
            @Override public boolean onPreDraw() {
                View wrapper = reference.get();
                if (wrapper == null) return true;
                Object info = wrapper.getTag();
                if (info == null || !isMarked(wrapper, info)) {
                    // The provider may be replaced while this host stays attached.
                    if (scaled) {
                        Object lastHost = HookUtil.tryInvoke(wrapper, "getHostView").value();
                        if (lastHost instanceof View) {
                            ((View) lastHost).setScaleX(1f);
                            ((View) lastHost).setScaleY(1f);
                        }
                        scaled = false;
                    }
                    return true;
                }
                Object hostObject = HookUtil.tryInvoke(wrapper, "getHostView").value();
                if (!(hostObject instanceof View)) return true;
                View content = (View) hostObject;
                int sx, sy;
                try {
                    sx = HookUtil.getIntField(info, "spanX");
                    sy = HookUtil.getIntField(info, "spanY");
                } catch (Throwable ignored) {
                    return true;
                }
                if (sx != 2 || sy != 2) {
                    if (scaled) {
                        content.setScaleX(1f);
                        content.setScaleY(1f);
                        scaled = false;
                    }
                    return true;
                }
                View cell = ancestor(wrapper, cellLayoutClass);
                if (cell == null) return true;
                try {
                    int cellW = (Integer) HookUtil.requireInvoke(cell, "getCellWidth");
                    int cellH = (Integer) HookUtil.requireInvoke(cell, "getCellHeight");
                    int outerW = wrapper.getWidth();
                    int outerH = wrapper.getHeight();
                    if (cellW <= 0 || cellH <= 0 || outerW <= 0 || outerH <= 0) {
                        return true;
                    }
                    // The wrapper is native-sized after the 2x2 CellLayout commit.
                    // Derive the target from its current content box, not the child's
                    // previous measured size: that size may already be scaled from
                    // the prior orientation or before a Launcher re-layout.
                    int leftInset = wrapper.getPaddingLeft();
                    int rightInset = wrapper.getPaddingRight();
                    int topInset = wrapper.getPaddingTop();
                    int bottomInset = wrapper.getPaddingBottom();
                    ViewGroup.LayoutParams params = content.getLayoutParams();
                    if (params instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams margins =
                                (ViewGroup.MarginLayoutParams) params;
                        leftInset += Math.max(0, margins.leftMargin);
                        rightInset += Math.max(0, margins.rightMargin);
                        topInset += Math.max(0, margins.topMargin);
                        bottomInset += Math.max(0, margins.bottomMargin);
                    }
                    WidgetProportionalScalePolicy.Frame frame =
                            WidgetProportionalScalePolicy.fit(leftInset, topInset,
                                    outerW - leftInset - rightInset,
                                    outerH - topInset - bottomInset, cellW, cellH);
                    if (frame == null) return true;
                    boolean sizeChanged = content.getWidth() != frame.width
                            || content.getHeight() != frame.height
                            || content.getLeft() != frame.left
                            || content.getTop() != frame.top;
                    if (sizeChanged) {
                        int wSpec = View.MeasureSpec.makeMeasureSpec(
                                frame.width, View.MeasureSpec.EXACTLY);
                        int hSpec = View.MeasureSpec.makeMeasureSpec(
                                frame.height, View.MeasureSpec.EXACTLY);
                        content.measure(wSpec, hSpec);
                        content.layout(frame.left, frame.top,
                                frame.left + frame.width, frame.top + frame.height);
                    }
                    if (!scaled || content.getScaleX() != frame.scale
                            || content.getScaleY() != frame.scale) {
                        content.setPivotX(frame.width / 2f);
                        content.setPivotY(frame.height / 2f);
                        content.setScaleX(frame.scale);
                        content.setScaleY(frame.scale);
                    }
                    scaled = true;
                } catch (Throwable error) {
                    // A changing vendor layout should retain its native rendering.
                    if (scaled) {
                        content.setScaleX(1f);
                        content.setScaleY(1f);
                        scaled = false;
                    }
                }
                return true;
            }
        };
        View.OnAttachStateChangeListener lifetime = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) {
                if (v.getViewTreeObserver().isAlive()) {
                    v.getViewTreeObserver().removeOnPreDrawListener(listener);
                }
                v.removeOnAttachStateChangeListener(this);
                OBSERVED.remove(v);
            }
        };
        widget.addOnAttachStateChangeListener(lifetime);
        widget.getViewTreeObserver().addOnPreDrawListener(listener);
    }
}
