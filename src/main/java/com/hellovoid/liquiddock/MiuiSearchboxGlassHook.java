package com.hellovoid.liquiddock;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Replaces only the stable SearchActivityBackground blur layer with LiquidDock Prismal glass. */
final class MiuiSearchboxGlassHook {
    private static final String TAG = "[DC][MiuiSearchboxGlass]";
    private static final String SEARCH_ACTIVITY_CLASS = "com.android.quicksearchbox.SearchActivity";
    private static final String BACKGROUND_CLASS =
            "com.android.quicksearchbox.ui.SearchActivityBackground";
    private static final String BLUR_TRANSITION_CLASS =
            "com.android.quicksearchbox.util.BlurTransition";
    private static final String BACKGROUND_ID_NAME = "search_activity_view_background";
    private static final String SEARCHBOX_PACKAGE = "com.android.quicksearchbox";

    private static final WeakHashMap<ViewGroup, State> STATES = new WeakHashMap<>();
    private static boolean installed;

    private MiuiSearchboxGlassHook() {}

    private static final class State implements View.OnAttachStateChangeListener,
            MiuiSearchboxGlassSession.Listener {
        final ViewGroup background;
        final ViewGroup outputHost;
        final int contentIndex;
        final Drawable stockBackground;
        final Method blurEnabledMethod;
        final MiuiSearchboxGlassSession session;
        final MiuiSearchboxGlassView glassView;
        final boolean freshOnResume;
        ViewTreeObserver observer;
        ViewTreeObserver.OnPreDrawListener preDrawListener;
        boolean disposed;

        State(
                ViewGroup background,
                ViewGroup outputHost,
                int contentIndex,
                Method blurEnabledMethod,
                LiquidDockConfig.Glass glassConfig,
                ThirdPartyGlassAppearance appearance,
                float cornerRadius) {
            this.background = background;
            this.outputHost = outputHost;
            this.contentIndex = contentIndex;
            this.blurEnabledMethod = blurEnabledMethod;
            stockBackground = background.getBackground();
            freshOnResume = appearance == null || appearance.freshOnResume;
            session = new MiuiSearchboxGlassSession(
                    background, glassConfig, appearance, cornerRadius, this);
            glassView = new MiuiSearchboxGlassView(background.getContext(), session);
            glassView.setAlpha(0f);
        }

        void attach() {
            background.addOnAttachStateChangeListener(this);
            background.setBackgroundColor(Color.TRANSPARENT);
            outputHost.addView(glassView, contentIndex, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            ViewTreeObserver nextObserver = outputHost.getViewTreeObserver();
            ViewTreeObserver.OnPreDrawListener listener = () -> {
                if (!disposed) session.updateGeometry();
                return true;
            };
            if (nextObserver.isAlive()) {
                nextObserver.addOnPreDrawListener(listener);
                observer = nextObserver;
                preDrawListener = listener;
            }
            background.post(this::refreshVisible);
        }

        void refreshVisible() {
            if (disposed || !background.isAttachedToWindow()) return;
            glassView.setAlpha(0f);
            session.updateGeometry();
            session.reconcileRoot();
            session.requestFreshCapture();
            Api101Bridge.log(TAG + " visible freshness barrier requested");
        }

        void refreshOnResume() {
            if (freshOnResume) refreshVisible();
        }

        @Override
        public void onPresented() {
            if (disposed) return;
            MiuiSearchboxVendorMaterial.release(background, blurEnabledMethod);
            glassView.setAlpha(1f);
            Api101Bridge.log(TAG + " Prismal presented; vendor backdrop binder released");
        }

        @Override
        public void onFailure(Throwable error) {
            Api101Bridge.log(TAG + " render failed; restoring stock material", error);
            dispose(true);
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            if (!disposed) background.post(this::refreshVisible);
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            dispose(false);
        }

        void dispose(boolean restoreStock) {
            if (disposed) return;
            disposed = true;
            background.removeOnAttachStateChangeListener(this);
            if (observer != null && preDrawListener != null) {
                try {
                    if (observer.isAlive()) observer.removeOnPreDrawListener(preDrawListener);
                } catch (Throwable ignored) {}
            }
            observer = null;
            preDrawListener = null;
            synchronized (STATES) {
                if (STATES.get(background) == this) STATES.remove(background);
            }
            glassView.dispose();
            session.shutdown();
            if (restoreStock) {
                MiuiSearchboxVendorMaterial.restore(background, blurEnabledMethod);
                background.setBackground(stockBackground);
            }
        }
    }

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> activityClass = Class.forName(SEARCH_ACTIVITY_CLASS, false, classLoader);
            Class<?> backgroundClass = Class.forName(BACKGROUND_CLASS, false, classLoader);
            Class<?> blurTransitionClass = Class.forName(BLUR_TRANSITION_CLASS, false, classLoader);

            Method setupContentView = activityClass.getDeclaredMethod("setupContentView");
            Method activityResume = Activity.class.getDeclaredMethod("onResume");
            Method dayBlur = backgroundClass.getDeclaredMethod("getBlurStyleDayMode");
            Method nightBlur = backgroundClass.getDeclaredMethod("getBlurStyleNightMode");
            Method addBlur = blurTransitionClass.getDeclaredMethod("addBlur", View.class);
            Method blurEnabledMethod = backgroundClass.getMethod("setBlurEnabled", Boolean.TYPE);

            HookUtil.hook(setupContentView, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof Activity) {
                    attach((Activity) owner, backgroundClass, blurEnabledMethod);
                }
                return result;
            });

            HookUtil.hook(activityResume, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner != null && owner.getClass() == activityClass) {
                    refreshActivity((Activity) owner, backgroundClass);
                }
                return result;
            });

            HookUtil.hook(dayBlur, chain -> isOwned(chain.getThisObject())
                    ? null : chain.proceed(chain.getArgs().toArray(new Object[0])));
            HookUtil.hook(nightBlur, chain -> isOwned(chain.getThisObject())
                    ? null : chain.proceed(chain.getArgs().toArray(new Object[0])));
            HookUtil.hook(addBlur, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length == 1 && isOwned(args[0])) return null;
                return chain.proceed(args);
            });

            installed = true;
            Api101Bridge.log(TAG + " installed using stable SearchActivity/SearchActivityBackground anchors");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " hook unavailable; stock Searchbox blur retained", error);
            return false;
        }
    }

    private static void attach(
            Activity activity,
            Class<?> backgroundClass,
            Method blurEnabledMethod) {
        ConfigReader reader = ConfigReader.load();
        LiquidDockConfig config = LiquidDockConfig.from(reader);
        ThirdPartyGlassAppearance appearance =
                MiuiSearchboxGlassPreferences.resolve(reader, config.glass);
        if (!config.enabled || !config.glass.enabled || !appearance.enabled) return;

        ViewGroup background = resolveBackground(activity, backgroundClass);
        if (background == null) return;

        View contentRoot = activity.findViewById(android.R.id.content);
        if (contentRoot == null || !(contentRoot.getParent() instanceof ViewGroup)) {
            Api101Bridge.log(TAG + " stable content parent unavailable; stock blur retained");
            return;
        }
        ViewGroup contentParent = (ViewGroup) contentRoot.getParent();
        int contentIndex = contentParent.indexOfChild(contentRoot);
        if (contentIndex < 0) {
            Api101Bridge.log(TAG + " stable content index unavailable; stock blur retained");
            return;
        }
        ViewGroup outputHost = contentParent;

        float cornerRadius = resolveCornerRadius(background);
        if (appearance.cornerRadiusOverrideDp >= 0f) {
            cornerRadius = appearance.cornerRadiusOverrideDp
                    * background.getResources().getDisplayMetrics().density;
        }
        synchronized (STATES) {
            State existing = STATES.get(background);
            if (existing != null && !existing.disposed) return;
            State state = new State(
                    background,
                    outputHost,
                    contentIndex,
                    blurEnabledMethod,
                    config.glass,
                    appearance,
                    cornerRadius);
            STATES.put(background, state);
            state.attach();
        }
    }

    private static void refreshActivity(Activity activity, Class<?> backgroundClass) {
        ViewGroup background = resolveBackground(activity, backgroundClass);
        if (background == null) return;
        synchronized (STATES) {
            State state = STATES.get(background);
            if (state != null && !state.disposed) state.refreshOnResume();
        }
    }

    private static ViewGroup resolveBackground(Activity activity, Class<?> backgroundClass) {
        int id = activity.getResources().getIdentifier(
                BACKGROUND_ID_NAME, "id", SEARCHBOX_PACKAGE);
        if (id == 0) {
            Api101Bridge.log(TAG + " stable background resource missing; stock blur retained");
            return null;
        }
        View candidate = activity.findViewById(id);
        if (!(candidate instanceof ViewGroup) || !backgroundClass.isInstance(candidate)) {
            Api101Bridge.log(TAG + " background resource no longer resolves to SearchActivityBackground");
            return null;
        }
        return (ViewGroup) candidate;
    }

    private static boolean isOwned(Object object) {
        if (!(object instanceof ViewGroup)) return false;
        synchronized (STATES) {
            State state = STATES.get((ViewGroup) object);
            return state != null && !state.disposed;
        }
    }

    private static float resolveCornerRadius(View background) {
        int id = background.getResources().getIdentifier("dip_27", "dimen", SEARCHBOX_PACKAGE);
        if (id != 0) {
            try { return background.getResources().getDimension(id); }
            catch (Throwable ignored) {}
        }
        return 27f * background.getResources().getDisplayMetrics().density;
    }
}
