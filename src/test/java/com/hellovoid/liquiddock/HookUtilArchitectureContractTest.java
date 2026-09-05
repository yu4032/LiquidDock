package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

/** Final architecture gate: production code must use explicit try/require reflection contracts. */
public class HookUtilArchitectureContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

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

    @Test public void genericStaticClassNameInvocationApiIsRemoved() {
        for (Method method : HookUtil.class.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.equals("tryInvokeStatic") && !name.equals("requireInvokeStatic")) continue;
            Class<?>[] parameters = method.getParameterTypes();
            assertTrue("static invocation API must declare a target type", parameters.length > 0);
            assertFalse("generic String class-name static invocation can bypass the target process "
                            + "ClassLoader: " + method,
                    parameters[0].equals(String.class));
        }
    }

    @Test public void legacyCompatibilityApiIsRemoved() throws Exception {
        String source = Files.readString(MAIN.resolve("HookUtil.java"));
        assertFalse(source.contains("public static Object invoke("));
        assertFalse(source.contains("public static Object invokeStatic("));
        assertFalse(source.contains("findMethodBestMatch("));
        assertFalse(source.contains("Temporary compatibility API"));
    }
}
