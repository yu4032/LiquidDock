package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Positions only the Workstation launchpad AllAppsWorkspace indicator.
 *
 * AllAppsWorkspace can inherit Launcher Workspace/ScreenView: the generic HOME
 * indicator hook must never control it. This hook binds directly to the vendor
 * launchpad class and captures its actual indicator translation after layout,
 * without modifying either cell geometry or any system-wide View property.
 */
final class WorkstationAllAppsIndicatorHook {
    private static final String ALL_APPS_WORKSPACE =
            "com.miui.home.launcher.laptop.launchpad.AllAppsWorkspace";
    private static final String PROBE_TAG = "[DC][AllAppsIndicatorProbe]";
    // Debug-only, bounded per Launcher process: avoid per-frame I/O or an unbounded logcat.
    private static int logBudget = 150;
    private static int targetCallbacks;
    private static int otherCallbacks;
    private static int missingViews;
    private static int preDrawChecks;
    private static int hierarchyDumps;
    private static final WeakHashMap<View, Float> BASE_Y = new WeakHashMap<>();
    private static final WeakHashMap<View, PositionGuard> GUARDS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> UPDATING =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile HomeGridWorkstationGeometryConfig config =
            HomeGridWorkstationGeometryConfig.NONE;
    private static volatile Class<?> targetClass;

    private WorkstationAllAppsIndicatorHook() {}

    static boolean isTargetWorkspace(Object owner) {
        if (owner == null) return false;
        Class<?> type = targetClass;
        return type != null ? type.isInstance(owner)
                : ALL_APPS_WORKSPACE.equals(owner.getClass().getName());
    }

    static void install(ClassLoader loader, HomeGridWorkstationGeometryConfig settings) {
        config = settings == null ? HomeGridWorkstationGeometryConfig.NONE : settings;
        probe("install", "loader=" + (loader == null ? "null" : loader.getClass().getName())
                + " workstation=" + MainHook.isWorkstationMode()
                + " orientationPx landscape=" + config.allAppsLandscapeIndicatorY
                + " portrait=" + config.allAppsPortraitIndicatorY);
        final Class<?> allApps;
        try {
            allApps = Class.forName(ALL_APPS_WORKSPACE, false, loader);
            targetClass = allApps;
            probe("target-class", "resolved " + allApps.getName()
                    + " super=" + (allApps.getSuperclass() == null
                    ? "null" : allApps.getSuperclass().getName()));
            try {
                probe("api", classMembers(allApps));
            } catch (Throwable error) {
                probe("api", "introspection unavailable: " + error);
            }
        } catch (Throwable error) {
            probe("target-class", "NOT FOUND: " + error);
            return;
        }

        // Install independently. A missing vendor indicator-position method must not
        // suppress the onLayout probe; it was previously the source of a silent failure.
        hookBoundary(allApps, "updateIndicatorPositions",
                new Class<?>[]{int.class, boolean.class}, "positions");
        hookBoundary(allApps, "onLayout",
                new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class},
                "layout");
        hookBoundary(allApps, "onAttachedToWindow",
                new Class<?>[0], "attach");
    }

    private static void hookBoundary(
            Class<?> target, String name, Class<?>[] signature, String stage) {
        try {
            Method method = HookUtil.findMethodExact(target, name, signature);
            // Do not install a process-wide View/ViewGroup hook merely to sample
            // launchpad attachment: only hook OEM-defined lifecycle methods.
            if ("attach".equals(stage)
                    && !method.getDeclaringClass().getName().startsWith("com.miui.home.")) {
                probe("hook-" + stage, "skipped framework owner "
                        + method.getDeclaringClass().getName());
                return;
            }
            HookUtil.hook(method, chain -> aroundVendorLayout(chain, stage));
            probe("hook-" + stage, "BOUND " + method.getDeclaringClass().getName()
                    + "#" + name);
        } catch (Throwable error) {
            probe("hook-" + stage, "FAILED " + name + ": " + error);
        }
    }

    private static Object aroundVendorLayout(
            io.github.libxposed.api.XposedInterface.Chain chain, String stage)
            throws Throwable {
        Object owner = chain.getThisObject();
        Object[] args = chain.getArgs().toArray(new Object[0]);
        if (!isTargetWorkspace(owner)) {
            if (MainHook.debugLogging && otherCallbacks++ < 5) {
                probe("other-receiver", stage + " owner="
                        + (owner == null ? "null" : owner.getClass().getName()));
            }
            return chain.proceed(args);
        }
        if (Boolean.TRUE.equals(UPDATING.get())) {
            if (MainHook.debugLogging && targetCallbacks < 12) {
                probe("reentrant", stage + " owner=" + owner.getClass().getName());
            }
            return chain.proceed(args);
        }

        UPDATING.set(Boolean.TRUE);
        try {
            boolean detail = MainHook.debugLogging && targetCallbacks++ < 14;
            View before = indicatorOf(owner);
            if (detail) probe("callback-before", stage
                    + " workstation=" + MainHook.isWorkstationMode()
                    + " owner=" + describe(owner)
                    + " indicator=" + describe(before)
                    + " storedBase=" + baseline(before));
            if (before != null) restoreVendorY(before);
            Object result = chain.proceed(args);
            View after = indicatorOf(owner);
            if (after == null && MainHook.debugLogging && missingViews++ < 5) {
                HookUtil.InvocationResult<Object> getter =
                        HookUtil.tryInvoke(owner, "getScreenIndicator");
                probe("indicator-missing", stage + " getter="
                        + (getter.succeeded()
                        ? describe(getter.value()) : String.valueOf(getter.failure())));
                dumpHierarchy(owner);
            }
            if (after != null) captureVendorY(after);
            if (detail) {
                probe("callback-after", stage + " indicator=" + describe(after)
                        + " storedBase=" + baseline(after));
                if (after == null && owner instanceof View) {
                    ViewParentLog.logAncestors((View) owner);
                }
            }
            return result;
        } finally {
            UPDATING.remove();
        }
    }

    private static String baseline(View indicator) {
        if (indicator == null) return "null";
        synchronized (BASE_Y) {
            return String.valueOf(BASE_Y.get(indicator));
        }
    }

    private static String describe(Object object) {
        if (object == null) return "null";
        if (!(object instanceof View)) return object.getClass().getName();
        View view = (View) object;
        String resource = "";
        try {
            if (view.getId() != View.NO_ID) {
                resource = view.getResources().getResourceEntryName(view.getId());
            }
        } catch (Throwable ignored) {}
        return view.getClass().getName() + "@" + Integer.toHexString(
                System.identityHashCode(view))
                + (resource.isEmpty() ? "" : "#" + resource)
                + " y=" + view.getY() + " ty=" + view.getTranslationY()
                + " top=" + view.getTop() + " h=" + view.getHeight()
                + " shown=" + view.isShown();
    }

    private static String classMembers(Class<?> type) {
        StringBuilder out = new StringBuilder();
        for (Class<?> cursor = type; cursor != null
                && cursor.getName().startsWith("com.miui.home.");
             cursor = cursor.getSuperclass()) {
            out.append(cursor.getSimpleName()).append('{');
            int count = 0;
            for (Method method : cursor.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                if ((name.contains("indicator") || name.contains("page")
                        || name.contains("layout") || name.contains("attach"))
                        && count++ < 14) {
                    out.append(method.getName()).append('(')
                            .append(method.getParameterCount()).append("),");
                }
            }
            for (Field field : cursor.getDeclaredFields()) {
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if ((name.contains("indicator") || name.contains("page"))
                        && count++ < 20) {
                    out.append(field.getName()).append(',');
                }
            }
            out.append("} ");
            if (out.length() > 900) break;
        }
        return out.toString();
    }

    private static void dumpHierarchy(Object owner) {
        if (!(owner instanceof ViewGroup) || hierarchyDumps++ >= 2) return;
        ViewGroup root = (ViewGroup) owner;
        probe("hierarchy-root", describe(root));
        int[] remaining = {24};
        dumpChildren(root, 0, remaining);
    }

    private static void dumpChildren(ViewGroup group, int depth, int[] remaining) {
        if (depth > 4 || remaining[0] <= 0) return;
        for (int i = 0; i < group.getChildCount() && remaining[0] > 0; i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            remaining[0]--;
            probe("hierarchy", "depth=" + depth + " index=" + i + " " + describe(child));
            if (child instanceof ViewGroup) {
                dumpChildren((ViewGroup) child, depth + 1, remaining);
            }
        }
    }

    private static synchronized void probe(String stage, String message) {
        if (!MainHook.debugLogging || logBudget-- <= 0) return;
        MainHook.log(PROBE_TAG + " " + stage + " " + message);
    }

    private static final class ViewParentLog {
        static void logAncestors(View view) {
            android.view.ViewParent parent = view.getParent();
            for (int depth = 0; parent != null && depth < 5; depth++) {
                probe("ancestor", "depth=" + depth + " " + describe(parent));
                parent = parent.getParent();
            }
        }
    }

    private static View indicatorOf(Object owner) {
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(owner, "getScreenIndicator");
        if (result.succeeded() && result.value() instanceof View) {
            return (View) result.value();
        }
        // The launchpad may host the indicator as a direct descendant rather
        // than expose the ScreenView getter. Do not search beyond this workspace.
        return owner instanceof ViewGroup ? findIndicator((ViewGroup) owner, 0) : null;
    }

    private static View findIndicator(ViewGroup parent, int depth) {
        if (depth > 8) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;
            String name = child.getClass().getName();
            if (name.contains("ScreenIndicator") || name.contains("PageIndicator")) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View result = findIndicator((ViewGroup) child, depth + 1);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void restoreVendorY(View indicator) {
        Float base;
        synchronized (BASE_Y) {
            base = BASE_Y.get(indicator);
        }
        if (base != null && indicator.getTranslationY() != base) {
            indicator.setTranslationY(base);
        }
    }

    private static void captureVendorY(View indicator) {
        float vendorY = indicator.getTranslationY();
        synchronized (BASE_Y) {
            BASE_Y.put(indicator, vendorY);
        }
        synchronized (GUARDS) {
            if (!GUARDS.containsKey(indicator)) {
                PositionGuard guard = new PositionGuard(indicator);
                GUARDS.put(indicator, guard);
                indicator.getViewTreeObserver().addOnPreDrawListener(guard);
                indicator.addOnAttachStateChangeListener(guard);
                probe("indicator-bound", describe(indicator)
                        + " orientation=" + indicator.getResources()
                        .getConfiguration().orientation);
            }
        }
        apply(indicator, vendorY);
    }

    private static void apply(View indicator, float vendorY) {
        boolean portrait = indicator.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        HomeGridWorkstationGeometryConfig current = config;
        float y = WorkstationAllAppsIndicatorPolicy.targetY(
                vendorY, MainHook.isWorkstationMode(), true, portrait,
                current.allAppsLandscapeIndicatorY, current.allAppsPortraitIndicatorY);
        float oldY = indicator.getTranslationY();
        if (oldY != y) indicator.setTranslationY(y);
        if (MainHook.debugLogging && (preDrawChecks < 10 || oldY != y
                && preDrawChecks < 24)) {
            preDrawChecks++;
            probe("apply", "portrait=" + portrait + " workstation="
                    + MainHook.isWorkstationMode() + " base=" + vendorY
                    + " target=" + y + " before=" + oldY
                    + " after=" + indicator.getTranslationY()
                    + " landscapePx=" + current.allAppsLandscapeIndicatorY
                    + " portraitPx=" + current.allAppsPortraitIndicatorY
                    + " view=" + describe(indicator));
        }
    }

    private static final class PositionGuard implements
            ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private final WeakReference<View> indicator;

        PositionGuard(View view) {
            indicator = new WeakReference<>(view);
        }

        @Override
        public boolean onPreDraw() {
            View view = indicator.get();
            if (view == null) return true;
            Float baseline;
            synchronized (BASE_Y) {
                baseline = BASE_Y.get(view);
            }
            if (baseline != null) {
                float observed = view.getTranslationY();
                apply(view, baseline);
                if (MainHook.debugLogging && preDrawChecks < 12) {
                    probe("pre-draw", "observed=" + observed + " base=" + baseline
                            + " corrected=" + view.getTranslationY());
                }
            }
            return true;
        }

        @Override
        public void onViewAttachedToWindow(View view) {}

        @Override
        public void onViewDetachedFromWindow(View view) {
            ViewTreeObserver observer = view.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            view.removeOnAttachStateChangeListener(this);
            synchronized (GUARDS) {
                if (GUARDS.get(view) == this) GUARDS.remove(view);
            }
            synchronized (BASE_Y) {
                BASE_Y.remove(view);
            }
            probe("indicator-detached", describe(view));
            indicator.clear();
        }
    }
}
