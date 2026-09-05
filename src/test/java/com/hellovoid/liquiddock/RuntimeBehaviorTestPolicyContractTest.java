package com.hellovoid.liquiddock;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Testing-architecture gate: production source inspection is denied by default. Only explicitly
 * audited static contracts may inspect production source/config text. Runtime behavior must be
 * exercised through typed production state/policy APIs instead of source strings or reflection.
 */
public class RuntimeBehaviorTestPolicyContractTest {
    private static final Path TEST_ROOT = Path.of("src/test/java");

    /**
     * Narrow structural exceptions. Adding a file here requires that every source assertion in the
     * class is static R8/Gradle/Manifest/API/architecture validation, never runtime sequencing.
     */
    private static final Set<String> STATIC_SOURCE_ALLOWLIST = Set.of(
            "DockShadowArchitectureTest.java",
            "HookUtilArchitectureContractTest.java",
            "LauncherGlassStaticBoundaryTest.java",
            "PrismalModuleBoundaryContractTest.java",
            "R8ReleaseKeepContractTest.java");

    @Test
    public void productionSourceInspectionIsStaticOnlyAndRuntimeTestsUseTypedApis() throws Exception {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(TEST_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("RuntimeBehaviorTestPolicyContractTest.java"))
                    .forEach(path -> inspect(path, violations));
        }
        if (!violations.isEmpty()) {
            fail("Production source inspection is reserved for audited static contracts; "
                    + "runtime behavior must use typed production state/policy APIs:\n  "
                    + String.join("\n  ", violations));
        }
    }

    private static void inspect(Path path, List<String> violations) {
        final String source;
        try {
            source = Files.readString(path);
        } catch (IOException error) {
            violations.add(path + " (unreadable: " + error + ")");
            return;
        }

        String name = path.getFileName().toString();
        if (STATIC_SOURCE_ALLOWLIST.contains(name)) return;

        boolean productionSourceReference = source.contains("src/main/java")
                || source.contains("src/main/kotlin")
                || source.contains("src/main/AndroidManifest.xml")
                || source.contains("build.gradle")
                || source.contains("settings.gradle");
        if (productionSourceReference
                && (source.contains("Files.readString(") || source.contains("Files.readAllBytes("))) {
            violations.add(path + " (reads production source/config text without static allowlist)");
        }

        reject(path, source, violations, "getDeclaredConstructor(", "reflects constructor");
        reject(path, source, violations, "getDeclaredMethod(", "reflects method");
        reject(path, source, violations, "getDeclaredField(", "reflects field");
        reject(path, source, violations, ".setAccessible(true)", "forces reflective access");
    }

    private static void reject(
            Path path, String source, List<String> violations, String token, String reason) {
        if (source.contains(token)) violations.add(path + " (" + reason + ": " + token + ")");
    }
}
