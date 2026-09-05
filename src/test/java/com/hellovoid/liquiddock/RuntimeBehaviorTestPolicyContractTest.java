package com.hellovoid.liquiddock;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Testing-architecture gate: runtime behavior tests must exercise production-used pure state/policy
 * objects, not infer behavior from production source text or reflective access to package-private
 * test seams. Static architecture/config contract tests are intentionally outside this filename
 * scope.
 */
public class RuntimeBehaviorTestPolicyContractTest {
    private static final Path TEST_ROOT = Path.of("src/test/java");

    @Test
    public void runtimeBehaviorTestsDoNotReadProductionSourceOrReflectIntoState() throws Exception {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(TEST_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(RuntimeBehaviorTestPolicyContractTest::isRuntimeBehaviorTest)
                    .forEach(path -> inspect(path, violations));
        }
        if (!violations.isEmpty()) {
            fail("Runtime behavior tests must use typed production state/policy APIs:\n  "
                    + String.join("\n  ", violations));
        }
    }

    private static boolean isRuntimeBehaviorTest(Path path) {
        String name = path.getFileName().toString();
        if (name.equals("RuntimeBehaviorTestPolicyContractTest.java")) return false;
        return name.endsWith("StateTest.java")
                || name.endsWith("PolicyTest.java")
                || name.endsWith("RecoveryTest.java")
                || name.endsWith("OwnershipTest.java")
                || name.endsWith("AnimationTest.java");
    }

    private static void inspect(Path path, List<String> violations) {
        final String source;
        try {
            source = Files.readString(path);
        } catch (IOException error) {
            violations.add(path + " (unreadable: " + error + ")");
            return;
        }

        reject(path, source, violations, "Files.readString(", "reads source text");
        reject(path, source, violations, "Files.readAllBytes(", "reads source bytes");
        reject(path, source, violations, "src/main/java", "references production source path");
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
