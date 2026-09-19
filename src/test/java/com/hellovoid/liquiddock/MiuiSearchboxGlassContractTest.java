package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class MiuiSearchboxGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(Path path) throws Exception {
        return SourceContractText.read(path);
    }

    @Test
    public void searchboxUsesStableSemanticAnchorsAndExistingGlassBackend() throws Exception {
        String module = read(MAIN.resolve("ModuleMain.java"));
        String registry = read(MAIN.resolve("ThirdPartyGlassAdapterRegistry.java"));
        String hook = read(MAIN.resolve("MiuiSearchboxGlassHook.java"));
        String session = read(MAIN.resolve("MiuiSearchboxGlassSession.java"));
        String geometry = read(MAIN.resolve("MiuiSearchboxGlassGeometry.java"));
        String view = read(MAIN.resolve("MiuiSearchboxGlassView.java"));
        String prefs = read(MAIN.resolve("MiuiSearchboxGlassPreferences.java"));
        String profiles = read(MAIN.resolve("ThirdPartyGlassProfiles.java"));
        String request = read(MAIN.resolve("PassBlurBindRequest.java"));
        String bridge = read(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String authority = read(MAIN.resolve("MiuiSearchboxPassBlurContinuousAuthority.java"));
        String material = read(MAIN.resolve("MiuiSearchboxVendorMaterial.java"));
        String settings = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GboardSettingsPages.kt"));
        String scope = Files.readString(Path.of("src/main/resources/META-INF/xposed/scope.list"));

        assertTrue(scope.contains("com.android.quicksearchbox"));
        assertTrue(module.contains("ThirdPartyGlassAdapterRegistry.handles(packageName)"));
        assertTrue(module.contains("ThirdPartyGlassAdapterRegistry.install(packageName, classLoader)"));
        assertTrue(registry.contains("com.android.quicksearchbox"));
        assertTrue(registry.contains("miui.searchbox"));
        assertTrue(registry.contains("MiuiSearchboxPassBlurContinuousAuthority.install()"));
        assertTrue(registry.contains("MiuiSearchboxGlassHook.install(classLoader)"));
        assertTrue(hook.contains("com.android.quicksearchbox.SearchActivity"));
        assertTrue(hook.contains("com.android.quicksearchbox.ui.SearchActivityBackground"));
        assertTrue(hook.contains("com.android.quicksearchbox.util.BlurTransition"));
        assertTrue(hook.contains("search_activity_view_background"));
        assertTrue(hook.contains("MiuiSearchboxGlassPreferences.resolve(reader, config.glass)"));

        // Backdrop capture remains one-shot and root-owned.
        assertTrue(session.contains("implements RootPassBlurBackend.Consumer"));
        assertTrue(session.contains("View sourceRoot = root.getRootView()"));
        assertTrue(session.contains("PassBlurBindRequest.miuiSearchbox(sourceRoot)"));
        assertTrue(session.contains("MiuiSearchboxSnapshotState"));
        assertTrue(session.contains("snapshotState.beginCapture()"));
        assertTrue(session.contains("!snapshotState.acceptFreshFrame()"));
        assertTrue(session.contains("setUpdatesEnabled(false, \"searchbox-snapshot-latched\")"));

        // The output Surface is fixed in screen space but must sit directly below Searchbox content,
        // matching ShortcutPopupGlassCoordinator instead of living at DecorView index 0.
        assertTrue(hook.contains("activity.findViewById(android.R.id.content)"));
        assertTrue(hook.contains("contentParent.indexOfChild(contentRoot)"));
        assertTrue(hook.contains("outputHost.addView(glassView, contentIndex"));
        assertFalse(hook.contains("outputHost.addView(glassView, 0"));
        assertTrue(hook.contains("ViewTreeObserver.OnPreDrawListener"));
        assertTrue(hook.contains("session.updateGeometry()"));
        assertTrue(hook.contains("ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(view.contains("Stable full-screen"));

        // Geometry must follow the current animated Searchbox position while backdrop stays fixed.
        assertTrue(session.contains("MiuiSearchboxGlassGeometry.capture(\n                sourceRoot, root, cornerRadius)"));
        assertFalse(geometry.contains("cumulativeTranslation("));
        assertFalse(geometry.contains("settledCoordinate("));

        // Prismal is redrawn into a full-screen transparent output instead of moving a cropped result.
        assertTrue(session.contains("prismalRenderer.beginGlassFrame()"));
        assertTrue(session.contains("prismalRenderer.drawGlass("));
        assertTrue(session.contains("presentFull("));
        assertFalse(session.contains("presentCropped("));
        assertTrue(session.contains("ThirdPartyPrismalParams.apply(baseParams, appearance)"));
        assertTrue(session.contains("appearance.captureScalePercent"));
        assertTrue(session.contains("appearance.renderFps"));

        // SearchActivityBackground inherits the stable BackdropBlurRelativeLayout API. Ownership
        // transfers only after Prismal presents, and failure restores the same binder symmetrically.
        assertTrue(hook.contains("backgroundClass.getMethod(\"setBlurEnabled\", Boolean.TYPE)"));
        assertTrue(hook.contains("MiuiSearchboxVendorMaterial.release(background, blurEnabledMethod)"));
        assertTrue(hook.contains("MiuiSearchboxVendorMaterial.restore(background, blurEnabledMethod)"));
        assertTrue(material.contains("Method blurEnabledMethod"));
        assertTrue(material.contains("blurEnabledMethod.invoke(background, Boolean.FALSE)"));
        assertTrue(material.contains("blurEnabledMethod.invoke(background, Boolean.TRUE)"));
        assertFalse(material.contains("background.getClass().getMethod("));
        assertFalse(material.contains("getDeclaredFields("));
        assertFalse(material.contains("getDeclaredConstructors("));

        assertTrue(request.contains("MIUI_SEARCHBOX_EXTRA_EXCLUSIONS = {\"MiuiSearchboxGlassView\"}"));
        assertTrue(bridge.contains("domain == PassBlurDomain.MIUI_SEARCHBOX"));
        assertTrue(bridge.contains("MiuiSearchboxPassBlurContinuousAuthority.claim"));
        assertTrue(bridge.contains("MiuiSearchboxPassBlurContinuousAuthority.release"));
        assertTrue(bridge.contains("MiuiSearchboxPassBlurContinuousAuthority.setUpdatesEnabled("));
        assertTrue(authority.contains("SetPassBlurSurface"));
        assertTrue(authority.contains("setUpdateTextureFlag"));
        assertTrue(authority.contains("boolean updatesEnabled"));
        assertTrue(authority.contains("args[1] = Boolean.valueOf(claim.updatesEnabled)"));

        assertTrue(prefs.contains("liquid_miui_searchbox_blur"));
        assertTrue(prefs.contains("liquid_miui_searchbox_tint_r"));
        assertTrue(prefs.contains("liquid_miui_searchbox_tint_g"));
        assertTrue(prefs.contains("liquid_miui_searchbox_tint_b"));
        assertTrue(prefs.contains("liquid_miui_searchbox_tint_alpha"));
        assertTrue(profiles.contains("third_party_glass."));
        assertTrue(settings.contains("MiuiSearchboxGlassPreferences.BLUR_KEY"));
        assertTrue(settings.contains("MiuiSearchboxGlassPreferences.TINT_RED_KEY"));
        assertTrue(settings.contains("MiuiSearchboxGlassPreferences.TINT_GREEN_KEY"));
        assertTrue(settings.contains("MiuiSearchboxGlassPreferences.TINT_BLUE_KEY"));
        assertTrue(settings.contains("MiuiSearchboxGlassPreferences.TINT_ALPHA_KEY"));
        assertTrue(settings.contains("恢复继承全局外观"));
        assertTrue(settings.contains("MIUI 搜索主界面液态玻璃"));
        assertTrue(settings.contains("搜索框自身背景保持原样"));
        assertFalse(hook.contains("getDeclaredFields("));
        assertFalse(hook.contains("getDeclaredConstructors("));
        assertFalse(hook.contains("search_input_bg_hf"));
    }
}
