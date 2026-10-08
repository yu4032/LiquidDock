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
    public void api102HotReloadFailsClosedAfterAnyHookDomainStarts() throws Exception {
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        assertTrue(entry.contains("onHotReloading(@NonNull HotReloadingParam param)"));
        assertTrue(entry.contains("onHotReloaded(@NonNull HotReloadedParam param)"));
        assertTrue(entry.contains("if (activeProcessLifecycle)"));
        assertTrue(entry.contains("return false;"));
        assertTrue(entry.contains("param.setSavedInstanceState(loadedProcessName)"));
        assertTrue(entry.contains("Api101Bridge.init(this)"));
        assertTrue(entry.contains("activeProcessLifecycle = true;"));
        assertFalse(entry.contains("param.getOldHookHandles().forEach(handle -> handle.unhook())"));
    }
}
