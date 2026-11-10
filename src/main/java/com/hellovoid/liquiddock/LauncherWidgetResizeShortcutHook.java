package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Adds a reversible size action to the Launcher's native widget shortcut menu. */
final class LauncherWidgetResizeShortcutHook {
    private static final String TAG = "[DC][Widget2x2]";
    private static final String OEM = "com.miui.home.launcher.";
    private static boolean installed;

    private final WeakHashMap<Object, View.OnClickListener> actions = new WeakHashMap<>();
    private final Class<?> itemClass;
    private final Method getLauncher;
    private final Method getCell;
    private final Method getDragContent;
    private final Method isOccupied;
    private final Method updateOccupancy;
    private final Method saveResize;
    private final Method hideMenu;
    private final Method getWidgetItems;
    private final Method setWidgetItems;
    private final Method setTitle;
    private final Constructor<?> newAction;

    private LauncherWidgetResizeShortcutHook(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> launcher = Class.forName(OEM + "Launcher", false, loader);
        Class<?> application = Class.forName(OEM + "Application", false, loader);
        Class<?> workspace = Class.forName(OEM + "BaseWorkspace", false, loader);
        Class<?> cell = Class.forName(OEM + "CellLayout", false, loader);
        Class<?> model = Class.forName(OEM + "LauncherModel", false, loader);
        itemClass = Class.forName(OEM + "ItemInfo", false, loader);

        getLauncher = method(application, "getLauncher");
        getCell = method(workspace, "getCellLayoutById", long.class);
        isOccupied = method(cell, "isCellOccupiedExceptItem",
                int.class, int.class, int.class, int.class, itemClass);
        updateOccupancy = method(cell, "updateCellOccupiedMarks",
                View.class, itemClass, boolean.class, boolean.class);
        saveResize = method(model, "resizeItemInDatabase", Context.class, itemClass,
                int.class, int.class, int.class, int.class);
        hideMenu = method(launcher, "hideShortcutMenuWithoutAnim");

        Class<?> layer = Class.forName(OEM + "ShortcutMenuLayer", false, loader);
        Class<?> dragView = Class.forName(OEM + "DragView", false, loader);
        getDragContent = method(dragView, "getContent");
        Class<?> items = Class.forName(OEM + "shortcuts.AllShortcutMenuItems", false, loader);
        Class<?> action = Class.forName(
                OEM + "shortcuts.SystemShortcutMenuItem$SizeAdaptShortcutMenuItem", false, loader);
        getWidgetItems = method(items, "getWidgetShortcutMenuItems");
        setWidgetItems = method(items, "setWidgetShortcutMenuItems", List.class);
        setTitle = method(action, "setShortTitle", CharSequence.class);
        newAction = action.getConstructor();
        Method query = method(layer, "onQuerySucceed", itemClass, items,
                float[].class, boolean.class, int[].class, int.class);

        // Each added row owns its listener. Launcher-created size actions keep their native listener.
        HookUtil.hook(method(action, "getOnClickListener"), chain -> {
            View.OnClickListener listener = actions.get(chain.getThisObject());
            return listener != null ? listener : chain.proceed();
        });
        HookUtil.hook(query, chain -> {
            try {
                addAction(chain.getThisObject(), chain.getArg(0), chain.getArg(1));
            } catch (ReflectiveOperationException | RuntimeException error) {
                MainHook.log(TAG + " could not add resize shortcut: " + error);
            }
            return chain.proceed();
        });
    }

    static synchronized void install(ClassLoader loader) {
        if (installed || loader == null) return;
        try {
            new LauncherWidgetResizeShortcutHook(loader);
            installed = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " widget resize shortcut unavailable: " + error);
        }
    }

    private void addAction(Object layer, Object menuInfo, Object items)
            throws ReflectiveOperationException {
        if (!itemClass.isInstance(menuInfo) || items == null
                || HookUtil.getLongField(menuInfo, "container") != -100L) return;
        Object launcher = getLauncher.invoke(null);
        if (launcher == null) return;
        Object workspace = HookUtil.getField(launcher, "mWorkspace");
        if (workspace == null) return;
        Object page = getCell.invoke(workspace, HookUtil.getLongField(menuInfo, "screenId"));
        if (!(page instanceof ViewGroup)) return;
        ViewGroup cell = (ViewGroup) page;
        long itemId = HookUtil.getLongField(menuInfo, "id");
        if (itemId < 0) return;
        View widget = findWidget(layer, cell, itemId);
        if (widget == null) return;
        Object info = widget.getTag();
        String identity = LauncherWidgetProportionalResizeHook.widgetIdentity(widget, info);
        if (identity == null) return;
        int span = targetSpan(widget, info, identity);
        if (span == 0) return;

        Object action = newAction.newInstance();
        Context context = widget.getContext();
        setTitle.invoke(action, span == 2
                ? label(context, R.string.widget_resize_expand, "Enlarge to 2×2")
                : label(context, R.string.widget_resize_shrink, "Restore to 1×1"));
        List<?> existing = (List<?>) getWidgetItems.invoke(items);
        List<Object> widgetItems = new ArrayList<>();
        widgetItems.add(action);
        if (existing != null) widgetItems.addAll(existing);
        actions.put(action, ignored -> toggle(launcher, cell, widget, info, identity, span));
        setWidgetItems.invoke(items, widgetItems);
    }

    private View findWidget(Object layer, ViewGroup cell, long id)
            throws ReflectiveOperationException {
        Object anchor = HookUtil.getField(layer, "mAnchorView");
        if (getDragContent.getDeclaringClass().isInstance(anchor)) {
            anchor = getDragContent.invoke(anchor);
        }
        // The native long-press drag temporarily reparents the real widget to DragView.
        if (anchor instanceof View) {
            View view = (View) anchor;
            Object info = view.getTag();
            if (itemClass.isInstance(info) && HookUtil.getLongField(info, "id") == id) return view;
        }
        for (int index = 0; index < cell.getChildCount(); index++) {
            View child = cell.getChildAt(index);
            Object info = child.getTag();
            if (itemClass.isInstance(info) && HookUtil.getLongField(info, "id") == id) return child;
        }
        return null;
    }

    private void toggle(Object launcher, ViewGroup cell, View widget, Object info,
                        String identity, int span) {
        Context context = widget.getContext();
        try {
            if (widget.getTag() != info) return;
            hideMenu.invoke(launcher);
            // The menu's drag preview may still own the widget during its return animation.
            long deadline = SystemClock.uptimeMillis() + 800;
            cell.post(() -> applyWhenReturned(cell, widget, info, identity, span, deadline));
        } catch (ReflectiveOperationException | RuntimeException error) {
            showFailure(context, error);
        }
    }

    private void applyWhenReturned(ViewGroup cell, View widget, Object info,
                                   String identity, int span, long deadline) {
        if (!cell.isAttachedToWindow() || widget.getTag() != info) return;
        if (widget.getParent() == cell) {
            applySize(cell, widget, info, identity, span);
        } else if (SystemClock.uptimeMillis() < deadline) {
            cell.postOnAnimation(() -> applyWhenReturned(cell, widget, info, identity, span, deadline));
        }
    }

    private boolean isCurrentWidget(ViewGroup cell, View widget, Object info,
                                    String identity, int span) throws ReflectiveOperationException {
        return widget.getParent() == cell && widget.isAttachedToWindow()
                && widget.getTag() == info
                && identity.equals(LauncherWidgetProportionalResizeHook.widgetIdentity(widget, info))
                && targetSpan(widget, info, identity) == span;
    }

    private void applySize(ViewGroup cell, View widget, Object info, String identity, int span) {
        Context context = widget.getContext();
        try {
            if (!isCurrentWidget(cell, widget, info, identity, span)) return;
            int x = coordinate(info, "cellX");
            int y = coordinate(info, "cellY");
            Point target = span == 2 ? findSpace(cell, info, x, y) : new Point(x, y);
            if (target == null) {
                notice(context, R.string.widget_resize_no_space, "Not enough space around this widget");
                return;
            }

            // Clear the old footprint before resizeItemInDatabase mutates the same ItemInfo.
            updateOccupancy.invoke(cell, widget, info, true, false);
            try {
                saveResize.invoke(null, context, info, target.x, target.y, span, span);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                int originalSpan = span == 2 ? 1 : 2;
                try {
                    saveResize.invoke(null, context, info, x, y, originalSpan, originalSpan);
                } catch (ReflectiveOperationException | RuntimeException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            } finally {
                updateOccupancy.invoke(cell, widget, info, false, false);
            }
            LauncherWidgetProportionalResizeHook.setExpanded(widget, info, identity, span == 2);
            widget.requestLayout();
        } catch (ReflectiveOperationException | RuntimeException error) {
            showFailure(context, error);
        }
    }

    private Point findSpace(ViewGroup cell, Object info, int x, int y)
            throws ReflectiveOperationException {
        for (int top = y; top >= Math.max(0, y - 1); top--) {
            for (int left = x; left >= Math.max(0, x - 1); left--) {
                if (Boolean.FALSE.equals(isOccupied.invoke(cell, left, top, 2, 2, info))) {
                    return new Point(left, top);
                }
            }
        }
        return null;
    }

    private static int targetSpan(View widget, Object info, String identity) {
        return WidgetProportionalScalePolicy.toggleSpan(
                coordinate(info, "spanX"), coordinate(info, "spanY"),
                LauncherWidgetProportionalResizeHook.isMarked(widget, info, identity));
    }

    private static int coordinate(Object info, String field) {
        return HookUtil.getIntField(info, field);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        return HookUtil.findMethodExact(type, name, parameters);
    }

    private static void showFailure(Context context, Throwable error) {
        MainHook.log(TAG + " widget resize failed: " + error);
        notice(context, R.string.widget_resize_failed, "Could not resize the widget");
    }

    private static void notice(Context context, int resource, String fallback) {
        Toast.makeText(context, label(context, resource, fallback), Toast.LENGTH_SHORT).show();
    }

    private static String label(Context context, int resource, String fallback) {
        try {
            Context module = context.createPackageContext(WidgetComponentStore.MODULE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY);
            return module.getString(resource);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
