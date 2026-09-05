package com.hellovoid.liquiddock;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Testing-architecture gate for production-source readers.
 *
 * <p>Every Java test is scanned. Production source/config inspection is denied by default.
 * Audited static API/architecture contracts are explicit exceptions; historical non-runtime
 * source readers outside this migration are explicit debt and may only shrink. Runtime behavior
 * must be exercised through typed production state/policy APIs.
 */
public class RuntimeBehaviorTestPolicyContractTest {
    private static final Path TEST_ROOT = Path.of("src/test/java");

    /**
     * Source inspection is permitted here only for static vendor/API/architecture/schema bans.
     * These files must not use source ordering or method slicing to prove runtime behavior.
     */
    private static final Set<String> STATIC_SOURCE_ALLOWLIST = Set.of(
            "DockMirrorShortcutReflectionContractTest.java",
            "DockShadowArchitectureTest.java",
            "FolderDragOverlayContractTest.java",
            "FolderPressInteractionContractTest.java",
            "HookUtilArchitectureContractTest.java",
            "LauncherGlassStaticBoundaryTest.java",
            "LauncherGlassVendorMaterialSuppressionContractTest.java",
            "LauncherMamlBackgroundRuleExecutorContractTest.java",
            "LauncherWallpaperFreshnessHookContractTest.java",
            "LauncherWidgetBackgroundControllerContractTest.java",
            "LauncherWidgetTransitionWiringContractTest.java",
            "SystemUiHomeTransitionWiringContractTest.java",
            "UserFacingPreferenceSchemaTest.java",
            "WidgetRemoteViewsRootBackgroundContractTest.java",
            "WidgetSystemUiReflectionContractTest.java",
            "WorkstationDockGeometryContractTest.java");

    /**
     * Grandfathered source readers outside ownership/freshness/animation/recovery migration.
     * This exact list is intentionally visible and must only shrink; new files are never added to
     * make the gate green. When one of these tests is migrated, remove its entry in the same change.
     */
    private static final Set<String> LEGACY_SOURCE_DEBT = Set.of(
            "GlassConfigGenerationContractTest.java",
            "HomeGridOrientationMemoryHookContractTest.java",
            "HomeGridProfileOverlayContractTest.java",
            "Miuix307EdgeOverscanContractTest.java",
            "PrismalModuleBoundaryContractTest.java",
            "PrismalOfficialParityV3Test.java",
            "RestartBoundSettingsContractTest.java",
            "WidgetBackgroundRankingUiContractTest.java",
            "WidgetComponentDiscoveryContractTest.java",
            "WidgetComponentSelectionContractTest.java",
            "WidgetMamlRenderTreeDiscoveryContractTest.java",
            "WorkspaceDropRuleHookContractTest.java",
            "WorkstationAllAppsHookContractTest.java");

    @Test
    public void productionSourceInspectionIsExplicitAndRuntimeBehaviorUsesTypedApis()
            throws Exception {
        List<String> violations = new ArrayList<>();
        Set<String> seenStaticReaders = new HashSet<>();
        Set<String> seenDebtReaders = new HashSet<>();

        try (Stream<Path> paths = Files.walk(TEST_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("RuntimeBehaviorTestPolicyContractTest.java"))
                    .forEach(path -> inspect(path, violations, seenStaticReaders, seenDebtReaders));
        }

        Set<String> staleDebt = new HashSet<>(LEGACY_SOURCE_DEBT);
        staleDebt.removeAll(seenDebtReaders);
        if (!staleDebt.isEmpty()) {
            violations.add("LEGACY_SOURCE_DEBT contains migrated/non-reader files; shrink the list: "
                    + staleDebt);
        }

        if (!violations.isEmpty()) {
            fail("Production source inspection is default-deny. Runtime ownership/freshness/"
                    + "animation/recovery must use typed production state/policy APIs; static "
                    + "inspection requires explicit audit and legacy debt may only shrink:\n  "
                    + String.join("\n  ", violations));
        }
    }

    private static void inspect(
            Path path,
            List<String> violations,
            Set<String> seenStaticReaders,
            Set<String> seenDebtReaders) {
        final String source;
        try {
            source = Files.readString(path);
        } catch (IOException error) {
            violations.add(path + " (unreadable: " + error + ")");
            return;
        }

        if (!isProductionSourceReader(source)) return;

        String name = path.getFileName().toString();
        if (STATIC_SOURCE_ALLOWLIST.contains(name)) {
            seenStaticReaders.add(name);
            rejectStaticRuntimeProof(path, source, violations);
            return;
        }
        if (LEGACY_SOURCE_DEBT.contains(name)) {
            seenDebtReaders.add(name);
            return;
        }

        violations.add(path + " (new/unclassified production source reader)");
    }

    private static boolean isProductionSourceReader(String source) {
        boolean productionReference = source.contains("src/main/java")
                || source.contains("src/main/kotlin")
                || source.contains("src/main/AndroidManifest.xml")
                || source.contains("build.gradle")
                || source.contains("settings.gradle");
        boolean readsText = source.contains("Files.readString(")
                || source.contains("Files.readAllBytes(");
        return productionReference && readsText;
    }

    private static void rejectStaticRuntimeProof(
            Path path, String source, List<String> violations) {
        if (source.contains("indexOf(")) {
            violations.add(path + " (static allowlist may not prove runtime order with indexOf)");
        }
        if (source.contains("substring(")) {
            violations.add(path + " (static allowlist may not prove runtime behavior by slicing)");
        }
    }
}
