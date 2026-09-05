package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Task-5 architecture gate for optional Widget/MAML/SystemUI vendor reflection. */
public class WidgetSystemUiReflectionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final String[] TARGETS = new String[]{
            "LauncherWidgetDarkContentAdapter.java",
            "LauncherMamlBackgroundRuleExecutor.java",
            "LauncherWidgetComponentSelectionExecutor.java",
            "LauncherWidgetComponentDiscovery.java",
            "LauncherWidgetTransitionCoordinator.java",
            "LauncherWidgetTransitionHook.java",
            "SystemUiKeyguardGoneRuntime.java",
            "SystemUiKeyguardGoneSource.java",
            "SystemUiHomeTransitionRuntime.java",
            "SystemUiHomeTransitionSource.java"
    };

    @Test public void widgetAndSystemUiPathsUseExplicitOptionalReflection() throws Exception {
        for (String name : TARGETS) {
            String source = Files.readString(MAIN.resolve(name));
            assertFalse(name + " still uses silent instance invocation",
                    source.contains("HookUtil.invoke("));
            assertFalse(name + " still uses silent static invocation",
                    source.contains("HookUtil.invokeStatic("));
        }

        for (String name : new String[]{
                "LauncherWidgetDarkContentAdapter.java",
                "LauncherMamlBackgroundRuleExecutor.java",
                "LauncherWidgetComponentSelectionExecutor.java"}) {
            String source = read(name);
            assertTrue(name + " helper must call tryInvoke",
                    source.contains("HookUtil.tryInvoke(target, methodName, args)"));
            assertTrue(name + " helper must inspect success",
                    source.contains("if (!result.succeeded())"));
        }

        String keyguardRuntime = read("SystemUiKeyguardGoneRuntime.java");
        String keyguardSource = read("SystemUiKeyguardGoneSource.java");
        String homeRuntime = read("SystemUiHomeTransitionRuntime.java");
        String homeSource = read("SystemUiHomeTransitionSource.java");
        for (String source : new String[]{keyguardRuntime, keyguardSource, homeRuntime, homeSource}) {
            assertTrue(source.contains("HookUtil.tryInvokeStatic("));
            assertTrue(source.contains("\"android.app.ActivityThread\", \"currentApplication\""));
            assertTrue(source.contains("applicationResult.succeeded()"));
        }

        String discovery = read("LauncherWidgetComponentDiscovery.java");
        assertTrue(discovery.contains("HookUtil.tryInvoke(root, \"findElement\", name)"));
        assertTrue(discovery.contains("namedTargetResult.succeeded()"));

        String transition = read("LauncherWidgetTransitionHook.java");
        assertTrue(transition.contains(
                "HookUtil.tryInvoke(target, \"getAnimTargetContainerView\")"));
        assertTrue(transition.contains("containerResult.succeeded()"));
    }

    @Test public void mamlMutationsAreSuccessGatedAndRollbackPartialClaims() throws Exception {
        String rules = read("LauncherMamlBackgroundRuleExecutor.java");
        assertTrue(rules.contains("private static boolean invokeOptionalMutation("));
        assertTrue(rules.contains("if (!invokeOptionalMutation(target, \"show\", false))"));
        assertTrue(rules.contains("restoreElements(elementClaims, appliedCount);"));
        int hide = rules.indexOf("if (!invokeOptionalMutation(target, \"show\", false))");
        int claim = rules.indexOf("CLAIMS.put(host, new Claim(root, elementClaims));");
        assertTrue("MAML claim must be stored only after all hide mutations succeed",
                hide >= 0 && claim > hide);

        String selections = read("LauncherWidgetComponentSelectionExecutor.java");
        assertTrue(selections.contains("private static boolean invokeOptionalMutation("));
        assertTrue(selections.contains("if (invokeOptionalMutation(target, \"show\", false))"));
        int mutation = selections.indexOf("if (invokeOptionalMutation(target, \"show\", false))");
        int addClaim = selections.indexOf("claims.add(new MamlClaim(target, originalShow));", mutation);
        assertTrue("failed component hide must not create a false MAML claim",
                mutation >= 0 && addClaim > mutation);
    }

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }
}
