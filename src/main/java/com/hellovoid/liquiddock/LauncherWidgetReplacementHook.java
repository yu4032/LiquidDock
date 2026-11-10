package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps picker compatibility separate from the workspace footprint of an enlarged widget. */
final class LauncherWidgetReplacementHook {
    private static final String OEM = "com.miui.home.launcher.";
    private static final String TAG = "[DC][WidgetReplace]";
    private static final String SPAN_X = "widget_span_x";
    private static final String SPAN_Y = "widget_span_y";
    private static final int APP_WIDGET = 4;
    private static final int MAML_WIDGET = 19;

    private LauncherWidgetReplacementHook() {}

    static void install(ClassLoader loader) {
        try {
            Class<?> launcher = Class.forName(OEM + "Launcher", false, loader);
            Class<?> item = Class.forName(OEM + "ItemInfo", false, loader);
            Class<?> widget = Class.forName(OEM + "MIUIWidgetBasicInfo", false, loader);
            Class<?> workspace = Class.forName(OEM + "Workspace", false, loader);
            Class<?> provider = Class.forName(OEM + "LauncherAppWidgetProviderInfo", false, loader);
            Class<?> manager = Class.forName(OEM + "common.WidgetManagerUtils", false, loader);
            Class<?> handler = Class.forName(OEM + "widget.MIUIWidgetReplaceHandler", false, loader);
            Method open = method(manager, "openReplacePage", launcher, widget);
            Method cloneItem = method(item, "clone");
            Method replace = method(handler, "replaceWidget", launcher, Bundle.class);
            Method apply = method(handler, "lambda$replaceWidget$0",
                    workspace, widget, widget, launcher);
            Method findMaml = method(handler, "findMaMlByWidgetId", launcher, int.class);
            Method findAppWidget = method(handler, "findWidgetByWidgetId", launcher, int.class);
            Method providerWidgetId = method(provider, "getAppWidgetId");
            Map<Object, String> expandedReplacements = new WeakHashMap<>();

            HookUtil.hook(open, chain -> {
                Object logicalInfo;
                try {
                    Object current = chain.getArg(1);
                    logicalInfo = expandedIdentity(current) != null ? cloneItem.invoke(current) : null;
                    if (logicalInfo != null) {
                        HookUtil.setIntField(logicalInfo, "spanX", 1);
                        HookUtil.setIntField(logicalInfo, "spanY", 1);
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    MainHook.log(TAG + " picker scope unavailable: " + error);
                    logicalInfo = null;
                }
                return logicalInfo == null ? chain.proceed()
                        : chain.proceed(new Object[]{chain.getArg(0), logicalInfo});
            });

            HookUtil.hook(replace, chain -> {
                Object owner = chain.getArg(0);
                Bundle selection = (Bundle) chain.getArg(1);
                Object original = null;
                String identity = null;
                Method find = null;
                int id = -1;
                try {
                    if (selection != null && selection.getInt(SPAN_X) == 1
                            && selection.getInt(SPAN_Y) == 1) {
                        int type = selection.getInt("replace_widget_type", -1);
                        id = selection.getInt("replace_widget_id", -1);
                        find = type == MAML_WIDGET ? findMaml
                                : type == APP_WIDGET ? findAppWidget : null;
                        if (find != null && id >= 0) {
                            Object candidate = find.invoke(null, owner, id);
                            identity = expandedIdentity(candidate);
                            if (identity != null) original = candidate;
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    MainHook.log(TAG + " replacement source unavailable: " + error);
                }
                if (original == null) return chain.proceed();

                // The picker sends the provider's size. Pass the occupied size to
                // Launcher's placement code while keeping the provider configuration.
                Bundle placement = new Bundle(selection);
                preserveConfigSpan(placement, "widget_config_span_x");
                preserveConfigSpan(placement, "widget_config_span_y");
                placement.putInt(SPAN_X, 2);
                placement.putInt(SPAN_Y, 2);
                Object result = chain.proceed(new Object[]{owner, placement});
                try {
                    // Launcher removes the old item before posting the add callback.
                    if (find.invoke(null, owner, id) == null) expandedReplacements.put(original, identity);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    MainHook.log(TAG + " could not track replacement: " + error);
                }
                return result;
            });

            HookUtil.hook(apply, chain -> {
                Object previous = chain.getArg(1);
                String previousIdentity = expandedReplacements.remove(previous);
                if (previousIdentity == null) return chain.proceed();
                Object incoming = chain.getArg(2);
                Object owner = chain.getArg(3);
                // The provider branch creates a new model object and clears the provider's id.
                int id = -1;
                if (provider.isInstance(incoming)) {
                    try {
                        id = (Integer) providerWidgetId.invoke(incoming);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        MainHook.log(TAG + " replacement provider ID unavailable: " + error);
                    }
                }
                Object result = chain.proceed();
                try {
                    LauncherWidgetProportionalResizeHook.clearExpanded((Context) owner, previous, previousIdentity);
                    Object replacement = id >= 0 ? findAppWidget.invoke(null, owner, id) : incoming;
                    if (LauncherWidgetProportionalResizeHook.isExpanded(replacement)) {
                        Object host = HookUtil.getField(replacement, "hostView");
                        if (host instanceof View) {
                            View wrapper = (View) host;
                            String identity = LauncherWidgetProportionalResizeHook
                                    .widgetIdentity(wrapper, replacement);
                            if (identity != null) {
                                LauncherWidgetProportionalResizeHook.setExpanded(
                                        wrapper, replacement, identity, true);
                            }
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    MainHook.log(TAG + " could not restore replacement scale: " + error);
                }
                return result;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " enlarged widget replacement unavailable: " + error);
        }
    }

    private static String expandedIdentity(Object info) throws ReflectiveOperationException {
        if (!LauncherWidgetProportionalResizeHook.isExpanded(info)) return null;
        Object host = HookUtil.getField(info, "hostView");
        if (!(host instanceof View)) return null;
        View wrapper = (View) host;
        String identity = LauncherWidgetProportionalResizeHook.widgetIdentity(wrapper, info);
        return LauncherWidgetProportionalResizeHook.isMarked(wrapper, info, identity) ? identity : null;
    }

    private static void preserveConfigSpan(Bundle placement, String key) {
        if (!placement.containsKey(key)) placement.putInt(key, 1);
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        return HookUtil.findMethodExact(owner, name, parameters);
    }
}
