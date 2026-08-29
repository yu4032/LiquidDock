package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.Test;

/** Packaging and architecture gates for declarative widget background rules. */
public class WidgetBackgroundRulePackagingContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path RULES = Path.of("src/main/resources/widget_background_rules.xml");

    private static final String WEATHER_DEFAULT = "b8006e83-c497-4642-9815-f674b82842b0";
    private static final String WEATHER_LARGE = "c989887f-fa0d-4963-8c57-896c03e37efc";
    private static final String WEATHER_WIDE = "bc0f0cd2-43fd-4323-8061-55a8bc997e1f";
    private static final String WEATHER_PACKAGE = "com.miui.weather2";

    @Test
    public void bundledRuleResourceOwnsKnownWeatherIdentifiers() throws Exception {
        assertTrue(Files.isRegularFile(RULES));
        String xml = Files.readString(RULES);
        assertTrue(xml.contains("<widget-background-rules"));
        assertTrue(xml.contains(WEATHER_DEFAULT));
        assertTrue(xml.contains(WEATHER_LARGE));
        assertTrue(xml.contains(WEATHER_WIDE));
        assertTrue(xml.contains(WEATHER_PACKAGE));
    }

    @Test
    public void productionJavaContainsNoWeatherSpecificIdentifiers() throws Exception {
        StringBuilder production = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            production.append(Files.readString(path)).append('\n');
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
        }
        String source = production.toString();
        assertFalse(source.contains(WEATHER_DEFAULT));
        assertFalse(source.contains(WEATHER_LARGE));
        assertFalse(source.contains(WEATHER_WIDE));
        assertFalse(source.contains(WEATHER_PACKAGE));
    }

    @Test
    public void widgetOwnershipUsesFacadeAndFutureCustomizationIsTracked() throws Exception {
        String controller = Files.readString(MAIN.resolve("LauncherWidgetBackgroundController.java"));
        String rootHook = Files.readString(MAIN.resolve("LauncherMamlRootLoadedHook.java"));
        String staticHook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String todo = Files.readString(Path.of("TODO.md"));

        assertTrue(controller.contains("LauncherGlassVendorMaterialSuppressor.claimWidgetMaterial(host)"));
        assertTrue(controller.contains("LauncherMamlBackgroundRuleExecutor.claim(host)"));
        assertTrue(controller.contains("LauncherMamlBackgroundRuleExecutor.release(host)"));
        assertTrue(rootHook.contains("LauncherWidgetBackgroundController.claimLoadedMamlRoot"));
        assertTrue(staticHook.contains("LauncherWidgetBackgroundController.claim(host)"));
        assertTrue(staticHook.contains("LauncherWidgetBackgroundController.release(host)"));
        assertTrue(todo.contains("Widget background hide rules 用户自定义化（后期）"));
        assertTrue(todo.contains("hide-element"));
        assertTrue(todo.contains("diagnostic-only"));
    }
}
