package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guardrails for the staged libxposed API 102 experiment. */
public class Api102HotReloadContractTest {
    @Test
    public void api102DependenciesAndMetadataAreExplicitlyExperimental() throws Exception {
        String gradle = Files.readString(Path.of("build.gradle.kts"));
        String metadata = Files.readString(Path.of("src/main/resources/META-INF/xposed/module.prop"));
        String entryList = Files.readString(Path.of("src/main/resources/META-INF/xposed/java_init.list"));
        assertTrue(gradle.contains("compileOnly(\"io.github.libxposed:api:102.0.0\")"));
        assertTrue(gradle.contains("implementation(\"io.github.libxposed:service:102.0.0\")"));
        assertTrue(metadata.contains("minApiVersion=102"));
        assertTrue(metadata.contains("targetApiVersion=102"));
        assertTrue("do not enable reload without EGL/receiver teardown", metadata.contains("autoHotReload=false"));
        assertFalse(metadata.contains("autoHotReload=true"));
        assertTrue(entryList.trim().equals("com.hellovoid.liquiddock.ModuleMain"));
    }

    @Test
    public void keyguardPilotHasAnOwnerScopedFailClosedLifecycle() throws Exception {
        String owner = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SystemUiKeyguardGoneSource.java"));
        String domain = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Api102HookDomain.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Api102HookRegistry.java"));
        assertTrue(owner.contains("Api102HookDomain.forProcess("));
        assertTrue(owner.contains("DOMAIN.begin()"));
        assertTrue(owner.contains("DOMAIN.hook(method, chain ->"));
        assertTrue(owner.contains("DOMAIN.commit()"));
        assertTrue(owner.contains("DOMAIN.abort()"));
        assertTrue(owner.contains("static boolean stopForFutureReload()"));
        assertTrue(owner.contains("DOMAIN.runActiveSideEffect(() -> onTransitionStep(step))"));
        assertTrue(domain.contains("ReentrantReadWriteLock(true)"));
        assertTrue(domain.contains("effectsBarrier.writeLock().tryLock("));
        assertTrue(domain.contains("STOP_DRAIN_TIMEOUT_MS"));
        assertTrue(domain.contains("if (!drained) return false"));
        assertTrue(owner.contains("if (!DOMAIN.isActive()) return;"));
        assertTrue(domain.contains("state = State.BLOCKED"));
        assertTrue(domain.contains("boolean clean = registry.rollback("));
        assertTrue(domain.contains("state = State.ACTIVE"));
        assertTrue(registry.contains(".setId(id)"));
        assertTrue(registry.contains("handle.unhook()"));
        assertTrue(registry.contains("duplicate API 102 hook id"));
        assertTrue(registry.contains("previous.replaceHook(next)"));
        assertFalse(owner.contains("replaceIdentified("));
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        assertFalse(entry.contains("SystemUiKeyguardGoneSource.stopForFutureReload("));
    }

    @Test
    public void legacyHookCreationTracksHandlesWithoutChangingPriorityOrIds() throws Exception {
        String hooks = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HookUtil.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Api102HookRegistry.java"));
        assertTrue(hooks.contains("Api102HookRegistry.registerUnidentified("));
        assertTrue(hooks.contains("hook(Constructor<?> ctor, XposedInterface.Hooker callback)"));
        assertTrue(hooks.contains("hookWithPriority("));
        assertTrue(hooks.contains(".setPriority(priority)"));
        assertTrue(registry.contains("unnamedHandles.add(handle)"));
        assertTrue(registry.contains("handles.size() + unnamedHandles.size()"));
        String dock = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java"));
        String grid = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridDbOrientationHook.java"));
        assertFalse(dock.contains("Api101Bridge.module().hook("));
        assertFalse(grid.contains("Api101Bridge.module().hook("));
        assertTrue(dock.contains("HookUtil.hookWithPriority("));
        assertTrue(grid.contains("HookUtil.hookWithPriority("));
        assertTrue(registry.contains("return complete;"));
    }

    @Test
    public void api102HotReloadFailsClosedAfterAnyHookDomainStarts() throws Exception {
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        assertTrue(entry.contains("onHotReloading(@NonNull HotReloadingParam param)"));
        assertTrue(entry.contains("onHotReloaded(@NonNull HotReloadedParam param)"));
        assertTrue(entry.contains("if (activeProcessLifecycle || Api102HookRegistry.installedCount() != 0)"));
        assertTrue(entry.contains("return false;"));
        assertTrue(entry.contains("param.setSavedInstanceState(loadedProcessName)"));
        assertTrue(entry.contains("Api101Bridge.init(this)"));
        assertTrue(entry.contains("activeProcessLifecycle = true;"));
        assertFalse(entry.contains("param.getOldHookHandles().forEach(handle -> handle.unhook())"));
    }
}
