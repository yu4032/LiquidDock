package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/** Final architecture gate: production code must use explicit try/require reflection contracts. */
public class HookUtilArchitectureContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Set<String> BOOT_FRAMEWORK_STATIC_STRING_CLASSES =
            Set.of("android.app.ActivityThread");
    private static final Pattern STRING_STATIC_INVOCATION = Pattern.compile(
            "HookUtil\\.(?:tryInvokeStatic|requireInvokeStatic)\\s*\\(\\s*\"([^\"]+)\"");

    @Test public void productionCallSitesDoNotUseLegacySilentInvocation() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    ::iterator) {
                String source = Files.readString(file);
                if (source.contains("HookUtil.invoke(") || source.contains("HookUtil.invokeStatic(")) {
                    offenders.add(MAIN.relativize(file).toString());
                }
            }
        }
        assertTrue("legacy silent reflection call sites remain: " + offenders, offenders.isEmpty());
    }

    @Test public void vendorStaticInvocationCannotUseHookUtilClassNameResolution() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    ::iterator) {
                String source = Files.readString(file);
                Matcher matcher = STRING_STATIC_INVOCATION.matcher(source);
                while (matcher.find()) {
                    String className = matcher.group(1);
                    if (!BOOT_FRAMEWORK_STATIC_STRING_CLASSES.contains(className)) {
                        offenders.add(MAIN.relativize(file) + ": " + className);
                    }
                }
            }
        }
        assertTrue("vendor/private static invocation must use a Class<?> resolved by the target "
                        + "process ClassLoader; only audited boot/framework string paths are allowed: "
                        + offenders,
                offenders.isEmpty());
    }

    @Test public void legacyCompatibilityApiIsRemoved() throws Exception {
        String source = Files.readString(MAIN.resolve("HookUtil.java"));
        assertFalse(source.contains("public static Object invoke("));
        assertFalse(source.contains("public static Object invokeStatic("));
        assertFalse(source.contains("findMethodBestMatch("));
        assertFalse(source.contains("Temporary compatibility API"));
    }
}
