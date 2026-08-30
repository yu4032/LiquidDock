package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** API101 injected RemotePreferences are read-only; discovery must return through app IPC. */
public class WidgetBackgroundDiscoveryTransportContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    @Test public void launcherPublisherNeverEditsInjectedRemotePreferences() throws Exception {
        String store = Files.readString(MAIN.resolve("WidgetBackgroundDiscoveryStore.java"));
        assertFalse(store.contains("Api101Bridge.remotePreferences"));
        assertFalse(store.contains("remote.edit()"));
        assertTrue(store.contains("WidgetBackgroundDiscoveryProvider"));
        assertTrue(store.contains("ContentResolver"));
    }

    @Test public void providerPersistsDiscoveryLocallyAndRejectsUnknownCallers() throws Exception {
        Path providerPath = MAIN.resolve("WidgetBackgroundDiscoveryProvider.java");
        assertTrue(Files.exists(providerPath));
        String provider = Files.readString(providerPath);
        String manifest = Files.readString(Path.of("src/main/AndroidManifest.xml"));
        String page = Files.readString(KOTLIN.resolve("WidgetBackgroundSettingsPage.kt"));
        String app = Files.readString(MAIN.resolve("LiquidDockApp.java"));

        assertTrue(provider.contains("com.miui.home"));
        assertTrue(provider.contains("Binder.getCallingUid()"));
        assertTrue(provider.contains("getSharedPreferences"));
        assertTrue(provider.contains("METHOD_PUBLISH"));
        assertTrue(manifest.contains(".WidgetBackgroundDiscoveryProvider"));
        assertTrue(manifest.contains("android:exported=\"true\""));
        assertTrue(app.contains("widgetDiscoveryPreferences"));
        assertTrue(page.contains("LiquidDockApp.widgetDiscoveryPreferences()"));
        assertFalse(page.contains("LiquidDockApp.remotePreferences(\"widget_discovery\")"));
    }
}
