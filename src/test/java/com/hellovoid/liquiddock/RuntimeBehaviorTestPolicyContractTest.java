package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Testing-architecture gate for production-source readers.
 *
 * <p>Every Java/Kotlin test is scanned. Production source/config inspection is denied by default.
 * Audited static API/architecture contracts are explicit exceptions. Runtime behavior
 * must be exercised through typed production state/policy APIs.
 */
public class RuntimeBehaviorTestPolicyContractTest {
    private static final List<Path> TEST_ROOTS = List.of(
            Path.of("src/test/java"),
            Path.of("src/test/kotlin"));

    /**
     * Source inspection is permitted here only for static vendor/API/architecture/schema bans.
     * These files must not use source ordering or method slicing to prove runtime behavior.
     */
    private static final Set<String> STATIC_SOURCE_ALLOWLIST = Set.of(
            "ConfigKeyOwnershipContractTest.java",
            "HookUtilArchitectureContractTest.java",
            "R8ReleaseKeepContractTest.java",
            "R8SceneControllerReflectionContractTest.java",
            "UserFacingPreferenceSchemaTest.java");

    @Test
    public void productionSourceInspectionIsExplicitAndRuntimeBehaviorUsesTypedApis()
            throws Exception {
        List<String> violations = new ArrayList<>();

        for (Path testRoot : TEST_ROOTS) {
            if (!Files.isDirectory(testRoot)) continue;
            try (Stream<Path> paths = Files.walk(testRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(RuntimeBehaviorTestPolicyContractTest::isTestSource)
                        .filter(path -> !path.getFileName().toString()
                                .equals("RuntimeBehaviorTestPolicyContractTest.java"))
                        .forEach(path -> inspect(path, violations));
            }
        }

        if (!violations.isEmpty()) {
            fail("Production source inspection is default-deny. Runtime ownership/freshness/"
                    + "animation/recovery must use typed production state/policy APIs; static "
                    + "inspection requires explicit audit:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    public void alternateSourceReaderApisCannotBypassClassification() {
        assertTrue(isProductionSourceReader(
                "Files.readAllLines(Path.of(\"src/main/java/Foo.java\"));"));
        assertTrue(isProductionSourceReader(
                "Files.lines(Path.of(\"src/main/java/Foo.java\"));"));
        assertTrue(isProductionSourceReader(
                "Files.newBufferedReader(Path.of(\"src/main/java/Foo.java\"));"));
        assertTrue(isProductionSourceReader(
                "File(\"src/main/kotlin/Foo.kt\").readText()"));
    }

    private static boolean isTestSource(Path path) {
        String text = path.toString();
        return text.endsWith(".java") || text.endsWith(".kt");
    }

    private static void inspect(
            Path path,
            List<String> violations) {
        final String source;
        try {
            source = Files.readString(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }

        if (!isProductionSourceReader(source)) return;
        String name = path.getFileName().toString();
        if (STATIC_SOURCE_ALLOWLIST.contains(name)) {
            rejectStaticRuntimeProof(name, source, violations);
            return;
        }
        violations.add(name + " reads production source/config without an audited static contract");
    }

    private static boolean isProductionSourceReader(String source) {
        // Default-deny by production/config reference instead of trying to enumerate all reader
        // APIs. That keeps Files.lines/readAllLines/newBufferedReader and Kotlin readText/readLines
        // from becoming trivial bypasses while audited static source contracts remain allowlisted.
        return source.contains("src/main/")
                || source.contains("build.gradle")
                || source.contains("settings.gradle");
    }

    private static void rejectStaticRuntimeProof(
            String name, String source, List<String> violations) {
        if (source.contains("indexOf(")
                || source.contains("substring(")
                || source.contains("split(")
                || source.contains("lastIndexOf(")) {
            violations.add(name + " is allowlisted only for static architecture/API assertions; "
                    + "source slicing/order checks are runtime-behavior proof and must move to "
                    + "typed state/policy tests");
        }
    }
}
