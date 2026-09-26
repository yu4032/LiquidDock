package com.hellovoid.liquiddock;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Temporary hardware probe for identifying the owner of HyperOS top-edge blurred popups.
 *
 * <p>SystemUIPlugin lives in a child ClassLoader, so this deliberately hooks only the framework
 * View blur APIs shared by both class loaders. It never mutates blur state and logs only views
 * whose ancestry looks notification/island/focus related. Remove this probe after the real owner
 * is confirmed on hardware.</p>
 */
final class SystemUiTopPopupProbe {
    private static final String TAG = "[DC][SystemUiTopPopupProbe]";
    private static final Map<View, String> LAST_STATE = new WeakHashMap<>();
    private static boolean installed;

    private SystemUiTopPopupProbe() {}

    static synchronized boolean install() {
        if (installed) return true;

        int installedHooks = 0;
        installedHooks += hookIntSetter("setMiBackgroundBlurRadius");
        installedHooks += hookIntSetter("setMiBackgroundBlurMode");
        installedHooks += hookIntSetter("setMiViewBlurMode");
        installedHooks += hookBooleanSetter("setPassWindowBlurEnabled");

        installed = installedHooks > 0;
        log("installed hooks=" + installedHooks);
        return installed;
    }

    private static int hookIntSetter(String methodName) {
        try {
            HookUtil.hookMethod(
                    View.class,
                    methodName,
                    new Class<?>[]{int.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (owner instanceof View && args.length == 1 && args[0] instanceof Number) {
                            logCandidate((View) owner, methodName + "="
                                    + ((Number) args[0]).intValue());
                        }
                        return result;
                    });
            return 1;
        } catch (Throwable error) {
            log(methodName + " hook unavailable: " + error);
            return 0;
        }
    }

    private static int hookBooleanSetter(String methodName) {
        try {
            HookUtil.hookMethod(
                    View.class,
                    methodName,
                    new Class<?>[]{boolean.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (owner instanceof View && args.length == 1 && args[0] instanceof Boolean) {
                            logCandidate((View) owner, methodName + "=" + args[0]);
                        }
                        return result;
                    });
            return 1;
        } catch (Throwable error) {
            log(methodName + " hook unavailable: " + error);
            return 0;
        }
    }

    private static void logCandidate(View view, String change) {
        if (view == null || !isInteresting(view)) return;

        String signature = change + "|" + view.getWidth() + "x" + view.getHeight()
                + "|" + Math.round(view.getTranslationX()) + ","
                + Math.round(view.getTranslationY()) + "|" + view.getVisibility();
        synchronized (LAST_STATE) {
            if (signature.equals(LAST_STATE.get(view))) return;
            LAST_STATE.put(view, signature);
        }

        int[] location = new int[2];
        try { view.getLocationOnScreen(location); } catch (Throwable ignored) {}

        View root = view.getRootView();
        String window = describeWindow(root);
        log(change
                + " view=" + describeView(view)
                + " size=" + view.getWidth() + "x" + view.getHeight()
                + " screen=[" + location[0] + "," + location[1] + "]"
                + " translation=[" + view.getTranslationX() + "," + view.getTranslationY() + "]"
                + " alpha=" + view.getAlpha()
                + " shown=" + view.isShown()
                + " bg=" + className(view.getBackground())
                + " root=" + describeView(root)
                + " " + window
                + " chain=" + describeChain(view));
    }

    private static boolean isInteresting(View view) {
        View current = view;
        for (int depth = 0; current != null && depth < 12; depth++) {
            String name = current.getClass().getName().toLowerCase(Locale.ROOT);
            if (name.contains("dynamicisland")
                    || name.contains("notification")
                    || name.contains("headsup")
                    || name.contains("heads_up")
                    || name.contains("focus")
                    || name.contains("prompt")
                    || name.contains("island")
                    || name.contains("decorwindow")) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static String describeChain(View view) {
        StringBuilder out = new StringBuilder();
        View current = view;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (depth > 0) out.append(" <- ");
            out.append(describeView(current));
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return out.toString();
    }

    private static String describeWindow(View root) {
        if (root == null) return "window=<none>";
        ViewGroup.LayoutParams params = root.getLayoutParams();
        if (!(params instanceof WindowManager.LayoutParams)) {
            return "windowParams=" + className(params);
        }
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) params;
        CharSequence title = null;
        try { title = lp.getTitle(); } catch (Throwable ignored) {}
        return "windowTitle=" + String.valueOf(title)
                + " type=" + lp.type
                + " flags=0x" + Integer.toHexString(lp.flags)
                + " layout=" + lp.width + "x" + lp.height;
    }

    private static String describeView(View view) {
        if (view == null) return "<null>";
        return view.getClass().getName() + "#" + resourceEntryName(view);
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return "<no-id>";
        try {
            Resources resources = view.getResources();
            return resources.getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return "0x" + Integer.toHexString(view.getId());
        }
    }

    private static String className(Object value) {
        return value != null ? value.getClass().getName() : "<null>";
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
