package com.hellovoid.liquiddock;

import java.lang.reflect.Method;

/** Enables MIUI's native shade-header gradient blur when its product-tier gate is disabled. */
final class SystemUiHeaderGradientBlurHook {
    private static final String TAG = "[DC][SystemUiHeaderGradientBlur]";
    private static final String SYSTEM_UI_GRADIENT_BLUR_PROPERTY =
            "persist.sys.background_blur_status_default";
    private static final Api102HookDomain DOMAIN =
            Api102HookDomain.forProcess("systemui.header.gradient.blur");

    private static boolean installed;

    private SystemUiHeaderGradientBlurHook() {}

    static synchronized void install(boolean enabled) {
        if (!enabled || installed) return;
        try {
            DOMAIN.begin();
            Class<?> systemPropertiesClass =
                    Class.forName("android.os.SystemProperties", false, null);
            Method getBoolean = HookUtil.findMethodExact(
                    systemPropertiesClass,
                    "getBoolean",
                    new Class<?>[]{String.class, boolean.class});
            DOMAIN.hook(getBoolean, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length > 0
                        && SYSTEM_UI_GRADIENT_BLUR_PROPERTY.equals(args[0])) {
                    return true;
                }
                return chain.proceed(args);
            });
            DOMAIN.commit();
            installed = true;
            Api101Bridge.log(TAG + " enabled property=" + SYSTEM_UI_GRADIENT_BLUR_PROPERTY);
        } catch (Throwable error) {
            if (DOMAIN.state() == Api102HookDomain.State.INSTALLING
                    || DOMAIN.state() == Api102HookDomain.State.BLOCKED) {
                installed = !DOMAIN.abort();
            } else if (DOMAIN.state() == Api102HookDomain.State.ACTIVE) {
                installed = !DOMAIN.stop();
            }
            Api101Bridge.log(TAG + " hook unavailable; preserving SystemUI behavior", error);
        }
    }
}
