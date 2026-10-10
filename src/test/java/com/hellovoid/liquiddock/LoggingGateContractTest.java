package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;

/** Production logging contract: diagnostics obey the user debug switch. */
public class LoggingGateContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void api101DiagnosticsAreFailClosedBehindDebugSwitch() throws Exception {
        String source = Files.readString(MAIN.resolve("Api101Bridge.java"));
        assertTrue(source.contains("private static volatile boolean debugLogging;"));
        assertTrue(source.contains("if (!debugLogging) return;"));
        assertTrue(source.contains("public static boolean isDebugLoggingEnabled()"));
        assertTrue(source.contains("public static void errorAlways(String message, Throwable error)"));
    }

    @Test public void moduleOwnsProcessDebugGateInitialization() throws Exception {
        String source = Files.readString(MAIN.resolve("ModuleMain.java"));
        assertTrue("module load must initialize the process debug gate",
                source.contains("Api101Bridge.init(this);\n"
                        + "        refreshDebugLogging();"));
        assertTrue("package readiness must refresh the process debug gate",
                source.contains("public void onPackageReady(@NonNull PackageReadyParam param) {\n"
                        + "        refreshDebugLogging();"));
        assertFalse("Launcher must not run removed configuration migration",
                source.contains("ConfigMigration.migrateAtProcessStart()"));
    }

    @Test public void directAndroidInfoAndErrorLogsStayInAuditedGatedOwners() throws Exception {
        Set<String> infoAllowlist = Set.of(
                "Api101Bridge.java",
                "LiquidDockApp.java",
                "SideSlideHoldDiagnostics.java");
        Set<String> errorAllowlist = Set.of(
                "Api101Bridge.java",
                "SideSlideHoldDiagnostics.java");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    ::iterator) {
                String name = file.getFileName().toString();
                String source = Files.readString(file);
                if ((source.contains("Log.v(") || source.contains("Log.d(")
                        || source.contains("Log.i(")) && !infoAllowlist.contains(name)) {
                    offenders.add(name + ": INFO/DEBUG");
                }
                if (source.contains("Log.e(") && !errorAllowlist.contains(name)) {
                    offenders.add(name + ": ERROR");
                }
                if (source.contains("System.out.")
                        || source.contains("System.err.")
                        || source.contains(".printStackTrace(")) {
                    offenders.add(name + ": stdout/stderr");
                }
            }
        }
        assertTrue("unaudited direct diagnostic logging remains: " + offenders,
                offenders.isEmpty());
    }

    @Test public void directAndroidWarnLogsAreOnlyExplicitOperationalFailures() throws Exception {
        Set<String> warningAllowlist = Set.of(
                "LiquidDockApp.java");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    ::iterator) {
                String source = Files.readString(file);
                if (!source.contains("Log.w(")) continue;
                String relative = MAIN.relativize(file).toString().replace('\\', '/');
                if (!warningAllowlist.contains(relative)) offenders.add(relative);
            }
        }
        assertTrue("new unconditional warnings require explicit review: " + offenders,
                offenders.isEmpty());
    }

    @Test public void sideSlideSpecialTagIsAlsoGated() throws Exception {
        String source = Files.readString(MAIN.resolve("SideSlideHoldDiagnostics.java"));
        assertTrue(source.contains("if (!Api101Bridge.isDebugLoggingEnabled()) return;"));
        assertTrue(source.contains("Log.i(LOGCAT_TAG, message);"));
        assertTrue(source.contains("Log.e(LOGCAT_TAG, message, error);"));
    }

    @Test public void settingsProcessInfoLogsUseItsLocalDebugPreference() throws Exception {
        String source = Files.readString(MAIN.resolve("LiquidDockApp.java"));
        assertTrue(source.contains("ConfigSchema.Debug.LOGGING.name()"));
        assertTrue(source.contains("ConfigSchema.Debug.LOGGING.runtimeFallback()"));
        assertTrue(source.contains("private void debugLog(String message)"));
        assertFalse(source.contains("Log.i(\"LiquidDock\", \"seeded "));
    }
}
