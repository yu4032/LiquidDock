package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

/** Static libxposed package-scope and semantic-name contract for Security Center. */
public class SecurityCenterScopeContractTest {
    private static final Pattern SHORT_QUALIFIED_TYPE = Pattern.compile(
            "(?<![A-Za-z0-9_])(?:[a-z]{2,3}\\.)+(?:[a-z](?:\\d+)?)(?![A-Za-z0-9_])");
    private static final Pattern SHORT_VENDOR_MEMBER = Pattern.compile(
            "\\bTurboLayout\\.[A-Za-z][A-Za-z0-9]{0,2}\\s*\\(");
    private static final Pattern DECOMPILER_FIELD = Pattern.compile(
            "\\bf\\d{3,}[A-Za-z0-9]*\\b");
    private static final Pattern SHORT_VENDOR_TYPE_TAIL = Pattern.compile(
            "\\b(?:newbox|sidebar)\\.[a-z][A-Za-z0-9]{0,2}\\b");
    private static final Pattern COMMENTED_SHORT_VENDOR_METHOD = Pattern.compile(
            "\\bvendor\\s+[A-Za-z][A-Za-z0-9]{0,2}\\s*\\(");

    @Test
    public void xposedScopeContainsExactlyTheSupportedPackages() throws Exception {
        List<String> lines = Files.readAllLines(
                Path.of("src/main/resources/META-INF/xposed/scope.list"));
        assertEquals(List.of(
                "com.miui.home",
                "com.android.systemui",
                "com.miui.securitycenter",
                "com.google.android.inputmethod.latin",
                "com.baidu.input_mi"), lines);
    }

    @Test
    public void securityCenterContractsContainNoDecompilerIdentifiers() throws Exception {
        StringBuilder source = new StringBuilder();
        appendSecurityCenterSources(
                Path.of("src/main/java/com/hellovoid/liquiddock"), source);
        appendSecurityCenterSources(
                Path.of("src/test/java/com/hellovoid/liquiddock"), source);
        appendTreeIfPresent(Path.of("src/test/java/fixture"), source);
        Path architecture = Path.of("ARCHITECTURE.md");
        if (Files.isRegularFile(architecture)) {
            source.append(Files.readString(architecture, StandardCharsets.UTF_8)).append('\n');
        }

        String text = source.toString();
        assertNoMatch("short package-qualified decompiler type", SHORT_QUALIFIED_TYPE, text);
        assertNoMatch("short TurboLayout member authority", SHORT_VENDOR_MEMBER, text);
        assertNoMatch("numbered decompiler field", DECOMPILER_FIELD, text);
        assertNoMatch("short vendor package-tail type", SHORT_VENDOR_TYPE_TAIL, text);
        assertNoMatch("commented short vendor method", COMMENTED_SHORT_VENDOR_METHOD, text);
    }

    private static void appendSecurityCenterSources(Path root, StringBuilder target)
            throws Exception {
        if (!Files.isDirectory(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith("SecurityCenter")) continue;
                if (!name.endsWith(".java") && !name.endsWith(".kt")) continue;
                target.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
    }

    private static void appendTreeIfPresent(Path root, StringBuilder target) throws Exception {
        if (!Files.isDirectory(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".java") && !name.endsWith(".kt")) continue;
                target.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        }
    }

    private static void assertNoMatch(String label, Pattern pattern, String source) {
        assertFalse(label + " found: " + pattern, pattern.matcher(source).find());
    }
}
