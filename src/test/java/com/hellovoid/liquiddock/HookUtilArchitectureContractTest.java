package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/** Final architecture gate: production code must use explicit try/require reflection contracts. */
public class HookUtilArchitectureContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Pattern GENERIC_STATIC_CLASS_NAME_API = Pattern.compile(
            "\\b(?:tryInvokeStatic|requireInvokeStatic)\\s*\\(\\s*String\\b",
            Pattern.DOTALL);
    private static final Pattern DEFAULT_LOADER_CLASS_FOR_NAME = Pattern.compile(
            "Class\\.forName\\s*\\(\\s*(?:"
                    + "\"(?:\\\\.|[^\"])*\""
                    + "|[^(),\"]+"
                    + "|\\((?:[^()]|\\([^()]*\\))*\\)"
                    + ")*\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern CLASS_ARRAY_FORWARDING_OVERLOAD = Pattern.compile(
            "\\n\\s*static void hookMethod\\(ClassLoader cl, String className,\\s*"
                    + "String methodName,\\s*XposedInterface\\.Hooker callback,\\s*"
                    + "Class<\\?>\\[\\] paramTypeSpecs\\)\\s*\\{\\s*"
                    + "hookMethod\\(cl, className, methodName, callback, "
                    + "\\(Object\\[\\]\\) paramTypeSpecs\\);\\s*\\}",
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
                Matcher matcher = DEFAULT_LOADER_CLASS_FOR_NAME.matcher(source);
                while (matcher.find()) {
                    offenders.add(MAIN.relativize(file) + ": "
                            + matcher.group().replaceAll("\\s+", " ").trim());
                }
            }
        }
        assertTrue("production Class.forName must explicitly name the target process ClassLoader; "
                        + "the audited boot/framework exception lives only in HookUtil: " + offenders,
                offenders.isEmpty());
    }

    @Test public void classArrayForwardingHasAnExactPackageBoundary() throws Exception {
        String source = Files.readString(MAIN.resolve("HookUtil.java"));
        assertTrue("Class<?>[] forwarding must use a package-private exact overload that explicitly "
                        + "forwards the array as Object[] into the existing resolver",
                CLASS_ARRAY_FORWARDING_OVERLOAD.matcher(source).find());
    }

    @Test public void legacyCompatibilityApiIsRemoved() throws Exception {
        String source = Files.readString(MAIN.resolve("HookUtil.java"));
        assertFalse(source.contains("public static Object invoke("));
        assertFalse(source.contains("public static Object invokeStatic("));
        assertFalse(source.contains("findMethodBestMatch("));
        assertFalse(source.contains("Temporary compatibility API"));
    }
}
