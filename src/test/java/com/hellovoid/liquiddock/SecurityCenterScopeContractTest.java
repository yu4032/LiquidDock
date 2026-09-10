package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

/** Static libxposed package-scope and compatibility-name contract for Security Center. */
public class SecurityCenterScopeContractTest {
    @Test
    public void xposedScopeContainsExactlyTheSupportedPackages() throws Exception {
        List<String> lines = Files.readAllLines(
                Path.of("src/main/resources/META-INF/xposed/scope.list"));
        assertEquals(List.of(
                "com.miui.home",
                "com.android.systemui",
                "com.miui.securitycenter"), lines);
    }

    @Test
    public void compatibilitySourcesContainNoVersionSpecificObfuscatedClassLiterals() throws Exception {
        Path root = Path.of("src/main/java/com/hellovoid/liquiddock");
        StringBuilder source = new StringBuilder();
        try (java.util.stream.Stream<Path> paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().startsWith("SecurityCenter"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            source.append(stripJavaComments(
                                    Files.readString(path, StandardCharsets.UTF_8))).append('\n');
                        } catch (java.io.IOException error) {
                            throw new java.io.UncheckedIOException(error);
                        }
                    });
        }
        for (String banned : List.of(
                "ob.e0", "ob.l0", "ja.a", "gq.g", "hq.g", "za.p",
                "newbox.x1", "newbox.y1", "40011320L", "40011355L")) {
            assertFalse("version-specific compatibility literal remains: " + banned,
                    source.toString().contains(banned));
        }
    }

    private static String stripJavaComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
