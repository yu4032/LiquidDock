package com.hellovoid.liquiddock;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Adds an action to the OEM widget shortcut menu and delegates occupancy changes to
 * Launcher WidgetResizeDragController. Fixed-size 1x1 content is then rendered at its
 * original measured size with a uniform transform inside the committed native 2x2 cell.
 *
 * No Favorites DB writes and no replacement menu/resize view.
 */
final class LauncherWidgetProportionalResizeHook {
    private static final String TAG = "[DC][Widget2x2]";
    private static final String PREFIX = "widget_scale_";
    private static final String PREFS = "liquiddock_widget_scale";

    // Weak entries: shortcut items and attached widget views never outlive Launcher.
    private static final WeakHashMap<Object, WeakReference<Object>> MENU_ITEMS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> OBSERVED = new WeakHashMap<>();

    private static Class<?> appClass;
    private static Class<?> editItemClass;
    private static Class<?> dragLayerClass;
    private static Class<?> widgetViewClass;
    private static Class<?> cellLayoutClass;
    private static Class<?> itemInfoClass;
    private static Constructor<?> editItemConstructor;
    private static Method addResizeFrame;
    private static boolean installed;

    private LauncherWidgetProportionalResizeHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) return false;
        try {
            appClass = Class.forName("com.miui.home.launcher.Application", false, loader);
            itemInfoClass = Class.forName("com.miui.home.launcher.ItemInfo", false, loader);
            widgetViewClass = Class.forName("com.miui.home.launcher.LauncherWidgetView",
                    false, loader);
            cellLayoutClass = Class.forName("com.miui.home.launcher.CellLayout",
                    false, loader);
            dragLayerClass = Class.forName("com.miui.home.launcher.DragLayer",
                    false, loader);
            Class<?> controllerClass = Class.forName(
                    "com.miui.home.launcher.WidgetResizeDragController", false, loader);
            addResizeFrame = HookUtil.findMethodExact(controllerClass, "addResizeFrame",
                    new Class<?>[]{itemInfoClass, widgetViewClass, cellLayoutClass});
            // This signature must exist before a custom menu action can be advertised.
            HookUtil.findMethodExact(dragLayerClass, "getWidgetResizeDragController",
                    new Class<?>[]{Class.forName("com.miui.home.launcher.Launcher",
                            false, loader),
                            Class.forName("com.miui.home.launcher.DragController",
                                    false, loader)});
            Class<?> manager = Class.forName(
                    "com.miui.home.launcher.shortcuts.ShortcutMenuManager", false, loader);
            Method getAll = HookUtil.findMethodExact(manager,
                    "getAllShortcutMenuItems", new Class<?>[]{itemInfoClass});
            editItemClass = Class.forName(
                    "com.miui.home.launcher.shortcuts.SystemShortcutMenuItem$EditShortcutMenuItem",
                    false, loader);
            editItemConstructor = editItemClass.getDeclaredConstructor();
            editItemConstructor.setAccessible(true);
            Method getListener = HookUtil.findMethodExact(editItemClass,
                    "getOnClickListener", new Class<?>[0]);

            // Hook a concrete widget class. Never hook ViewGroup globally.
            Method attached = HookUtil.findMethodExact(widgetViewClass,
                    "onAttachedToWindow", new Class<?>[0]);
            Method setWidget = HookUtil.findMethodExact(widgetViewClass, "setWidget",
                    new Class<?>[]{AppWidgetHostView.class});

            HookUtil.hook(getListener, chain -> {
                Object item = chain.getThisObject();
                WeakReference<Object> bound = MENU_ITEMS.get(item);
                if (bound == null) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                return (View.OnClickListener) clicked -> {
                    Object info = bound.get();
                    if (info != null) openNativeEditor(info);
                };
            });

            HookUtil.hook(getAll, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object info = args.length == 1 ? args[0] : null;
                if (result == null || !eligible(info)) return result;
                try {
                    Object original = HookUtil.requireInvoke(
                            result, "getWidgetShortcutMenuItems");
                    if (!(original instanceof List<?>)) return result;
                    Object newItem = editItemConstructor.newInstance();
                    HookUtil.requireInvoke(newItem, "setShortTitle", actionTitle(
                            ((View) widget(info)).getContext()));
                    ArrayList<Object> items = new ArrayList<>((List<?>) original);
                    items.add(newItem);
                    HookUtil.requireInvoke(result, "setWidgetShortcutMenuItems", items);
                    MENU_ITEMS.put(newItem, new WeakReference<>(info));
                } catch (Throwable error) {
                    MainHook.log(TAG + " native menu entry unavailable: " + error);
                }
                return result;
            });

            HookUtil.hook(attached, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof View) {
                    observeIfMarked((View) chain.getThisObject());
                }
                return result;
            });
            HookUtil.hook(setWidget, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof View) {
                    observeIfMarked((View) chain.getThisObject());
                }
                return result;
            });
            installed = true;
            MainHook.log(TAG + " native edit/resize integration installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " native edit API unavailable; feature disabled: " + error);
            return false;
        }
    }

    private static CharSequence actionTitle(Context context) {
        return context.getResources().getConfiguration().locale.getLanguage()
                .equals(Locale.CHINESE.getLanguage())
                ? "等比放大（拖至 2×2）" : "Scale proportionally (drag to 2×2)";
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
        if (!(host instanceof View)) return false;
        AppWidgetProviderInfo provider = provider(host);
        if (provider == null) return false;
        try {
            return WidgetProportionalScalePolicy.eligible(
                    HookUtil.getIntField(info, "spanX"),
                    HookUtil.getIntField(info, "spanY"), provider.resizeMode);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void openNativeEditor(Object info) {
        if (!eligible(info)) return;
        Object target = widget(info);
        if (!(target instanceof View)) return;
        View host = (View) target;
        View cell = ancestor(host, cellLayoutClass);
        if (cell == null) return;
        AppWidgetProviderInfo provider = provider(target);
        if (provider == null) return;

        Object vendorInfo;
        try {
            vendorInfo = HookUtil.requireInvoke(info, "getLauncherProviderInfo");
        } catch (Throwable error) {
            MainHook.log(TAG + " provider sizing metadata unavailable: " + error);
            return;
        }
        if (vendorInfo == null) return;

        int oldMode = provider.resizeMode;
        int oldMaxX;
        int oldMaxY;
        try {
            oldMaxX = HookUtil.getIntField(vendorInfo, "maxSpanX");
            oldMaxY = HookUtil.getIntField(vendorInfo, "maxSpanY");
        } catch (Throwable error) {
            MainHook.log(TAG + " native span limits unavailable: " + error);
            return;
        }
        try {
            Object launcher = HookUtil.requireInvokeStatic(appClass, "getLauncher");
            Object layer = HookUtil.requireInvoke(launcher, "getDragLayer");
            Object dragController = HookUtil.requireInvoke(launcher, "getDragController");
            if (!dragLayerClass.isInstance(layer)) return;
            Object resizeController = HookUtil.requireInvoke(layer,
                    "getWidgetResizeDragController", launcher, dragController);
            if (resizeController == null) return;

            // Only the original resize-frame construction sees the widened limits.
            // The provider information is restored even if a vendor method fails.
            provider.resizeMode = AppWidgetProviderInfo.RESIZE_BOTH;
            HookUtil.setIntField(vendorInfo, "maxSpanX", Math.max(2, oldMaxX));
            HookUtil.setIntField(vendorInfo, "maxSpanY", Math.max(2, oldMaxY));
            try {
                HookUtil.requireInvoke(launcher, "hideShortcutMenuWithoutAnim");
                addResizeFrame.invoke(resizeController, info, target, cell);
            } finally {
                provider.resizeMode = oldMode;
                HookUtil.setIntField(vendorInfo, "maxSpanX", oldMaxX);
                HookUtil.setIntField(vendorInfo, "maxSpanY", oldMaxY);
            }
            saveMarker(host.getContext(), info, provider);
            observeIfMarked(host);
        } catch (Throwable error) {
            MainHook.log(TAG + " could not open native resize handles: " + error);
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
