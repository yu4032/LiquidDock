package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/** Final architecture gate: production code must use explicit try/require reflection contracts. */
public class HookUtilArchitectureContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Pattern GENERIC_STATIC_CLASS_NAME_API = Pattern.compile(
            "\\b(?:tryInvokeStatic|requireInvokeStatic)\\s*\\(\\s*String\\b",
            Pattern.DOTALL);

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

    @Test public void genericStaticClassNameInvocationApiIsRemoved() throws Exception {
        String source = Files.readString(MAIN.resolve("HookUtil.java"));
        assertFalse("generic String class-name static invocation can bypass the target process "
                        + "ClassLoader; vendor callers must pass a resolved Class<?> instead",
                GENERIC_STATIC_CLASS_NAME_API.matcher(source).find());
        assertTrue("boot/framework exception must stay narrow and named",
                source.contains("tryInvokeActivityThreadCurrentApplication()")
                        && source.contains("\"android.app.ActivityThread\"")
                        && source.contains("\"currentApplication\""));
    }

    @Test public void productionClassForNameAlwaysNamesItsLoader() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> Files.isRegularFile(path)
                            && path.toString().endsWith(".java")
                            && !path.getFileName().toString().equals("HookUtil.java"))
                    ::iterator) {
                String source = Files.readString(file);
                int from = 0;
                while (true) {
                    int call = source.indexOf("Class.forName", from);
                    if (call < 0) break;
                    int open = source.indexOf('(', call + "Class.forName".length());
                    if (open < 0) break;
                    int close = matchingCloseParen(source, open);
                    if (close < 0) {
                        offenders.add(MAIN.relativize(file) + ": malformed Class.forName call");
                        break;
                    }
                    String arguments = source.substring(open + 1, close);
                    if (topLevelCommaCount(arguments) < 2) {
                        offenders.add(MAIN.relativize(file) + ": "
                                + compact(arguments));
                    }
                    from = close + 1;
                }
            }
        }
        assertTrue("production Class.forName must explicitly name the target process ClassLoader; "
                        + "the audited boot/framework exception lives only in HookUtil: " + offenders,
                offenders.isEmpty());
    }

    @Test public void legacyCompatibilityApiIsRemoved() throws Exception {
        String source = Files.readString(MAIN.resolve("HookUtil.java"));
        assertFalse(source.contains("public static Object invoke("));
        assertFalse(source.contains("public static Object invokeStatic("));
        assertFalse(source.contains("findMethodBestMatch("));
        assertFalse(source.contains("Temporary compatibility API"));
    }

    private static int matchingCloseParen(String source, int open) {
        int nested = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = open + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') {
                quoted = true;
            } else if (c == '(') {
                nested++;
            } else if (c == ')') {
                if (nested == 0) return i;
                nested--;
            }
        }
        return -1;
    }

    private static int topLevelCommaCount(String arguments) {
        int commas = 0;
        int nested = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') quoted = true;
            else if (c == '(') nested++;
            else if (c == ')') nested--;
            else if (c == ',' && nested == 0) commas++;
        }
        return commas;
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
