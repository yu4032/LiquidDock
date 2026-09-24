package com.hellovoid.liquiddock;

import android.view.View;

/** Domain-explicit input for a native PassBlur producer binding. */
final class PassBlurBindRequest {
    private static final String[] NO_EXTRA_EXCLUSIONS = new String[0];
    private static final String[] DOCK_EXTRA_EXCLUSIONS = {"DockAssistantView"};
    private static final String[] MIUI_SEARCHBOX_EXTRA_EXCLUSIONS = {"MiuiSearchboxGlassView"};
    private static final String[] RECENTS_CAPSULE_EXTRA_EXCLUSIONS = {"RecentsCapsuleGlassSinkView"};
    private static final String[] SYSTEMUI_HANDLE_MENU_EXTRA_EXCLUSIONS =
            {"SystemUiHandleMenuGlassOutputView", "TextureView"};

    private final View host;
    private final PassBlurDomain domain;
    private final float requestedScale;
    private final float nativeScale;
    private final String[] extraExclusions;

    private PassBlurBindRequest(
            View host,
            PassBlurDomain domain,
            float requestedScale,
            String[] extraExclusions) {
        this.host = host;
        this.domain = domain;
        this.requestedScale = requestedScale;
        this.nativeScale = PassBlurBindPolicy.nativeScale(domain, requestedScale);
        this.extraExclusions = extraExclusions.clone();
    }

    static PassBlurBindRequest launcherWorkspace(View host, float requestedScale) {
        return new PassBlurBindRequest(
                host,
                PassBlurDomain.LAUNCHER_WORKSPACE,
                requestedScale,
                NO_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest launcherDialog(View authoritativeRoot) {
        return new PassBlurBindRequest(
                authoritativeRoot,
                PassBlurDomain.LAUNCHER_DIALOG,
                1.0f,
                NO_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest shortcutPopup(View authoritativeRoot) {
        return new PassBlurBindRequest(
                authoritativeRoot,
                PassBlurDomain.SHORTCUT_POPUP,
                1.0f,
                NO_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest dragOverlay(View authoritativeRoot) {
        return new PassBlurBindRequest(
                authoritativeRoot,
                PassBlurDomain.DRAG_OVERLAY,
                1.0f,
                NO_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest dock(View host, float requestedScale) {
        return new PassBlurBindRequest(
                host,
                PassBlurDomain.DOCK,
                requestedScale,
                DOCK_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest securityCenter(View authoritativeRoot) {
        return new PassBlurBindRequest(
                authoritativeRoot,
                PassBlurDomain.SECURITY_CENTER,
                1.0f,
                NO_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest gboardFloating(View authoritativeRoot) {
        return new PassBlurBindRequest(
                authoritativeRoot,
                PassBlurDomain.GBOARD_FLOATING,
                1.0f,
                NO_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest miuiSearchbox(View authoritativeRoot) {
        return new PassBlurBindRequest(
                authoritativeRoot,
                PassBlurDomain.MIUI_SEARCHBOX,
                1.0f,
                MIUI_SEARCHBOX_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest recentsCapsule(View authoritativeRoot) {
        return new PassBlurBindRequest(
                authoritativeRoot,
                PassBlurDomain.RECENTS_CAPSULE,
                1.0f,
                RECENTS_CAPSULE_EXTRA_EXCLUSIONS);
    }

    static PassBlurBindRequest systemUiHandleMenu(View authoritativeRoot) {
        return new PassBlurBindRequest(
                authoritativeRoot,
                PassBlurDomain.SYSTEMUI_HANDLE_MENU,
                1.0f,
                SYSTEMUI_HANDLE_MENU_EXTRA_EXCLUSIONS);
    }

    View host() {
        return host;
    }

    PassBlurDomain domain() {
        return domain;
    }

    float nativeScale() {
        return nativeScale;
    }

    float requestedScale() {
        return requestedScale;
    }

    String[] extraExclusions() {
        return extraExclusions.clone();
    }
}
