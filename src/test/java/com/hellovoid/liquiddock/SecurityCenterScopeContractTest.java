package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

/** Static libxposed package-scope contract for the Security Center integration. */
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
}
