package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

/** Unit checks for the rooted scope-restart script and its explicit results. */
public class HookScopeRestartShellTest {
    @Test
    public void scriptRestartsOnlySelectedExactHookProcessNames() {
        String script = HookScopeRestartShell.buildScript(Set.of(
                HookScopeRestartShell.HOME,
                HookScopeRestartShell.SECURITY_CENTER,
                HookScopeRestartShell.GBOARD
        ));
        assertTrue(script.contains("am force-stop com.miui.home"));
        assertTrue(script.contains("restart_running com.miui.securitycenter com.miui.securitycenter:ui"));
        assertTrue(script.contains("restart_running com.google.android.inputmethod.latin com.google.android.inputmethod.latin"));
        assertFalse(script.contains("restart_running com.android.systemui com.android.systemui"));
        assertFalse(script.contains("restart_running com.android.quicksearchbox com.android.quicksearchbox"));
        assertFalse(script.contains("am force-stop com.miui.securitycenter"));
        assertTrue(script.contains("if [ -z \"$before\" ]; then report \"$scope\" NOT_RUNNING"));
        assertTrue(script.contains("if ! kill -TERM $before"));
        assertTrue(script.contains("if kill -0 \"$old\""));
        assertTrue(script.contains("current=\"$(pidof \"$process\""));
        assertTrue(script.contains("kill -KILL \"$old\""));
        assertTrue(script.contains("if [ \"$i\" -eq 15 ]"));
    }

    @Test
    public void systemUiUsesDirectKillAndRequiresNewPidInsteadOfOnlyTermination() {
        String script = HookScopeRestartShell.buildScript(Set.of(HookScopeRestartShell.SYSTEM_UI));
        assertTrue(script.contains("restart_systemui() {"));
        assertTrue(script.contains("restart_systemui\n"));
        assertTrue(script.contains("before_ui=\"$(pidof com.android.systemui"));
        assertTrue(script.contains("if ! kill -KILL $before_ui"));
        assertTrue(script.contains("after_ui=\"$(pidof com.android.systemui"));
        assertTrue(script.contains("old_alive"));
        assertTrue(script.contains("report com.android.systemui RESTARTED"));
        assertTrue(script.contains("report com.android.systemui FAILED"));
        assertFalse(script.contains("restart_running com.android.systemui com.android.systemui"));
        assertFalse(script.contains("am force-stop com.android.systemui"));
    }

    @Test
    public void noProcessDoesNotCountAsRestartedAndMissingResultIsFailure() {
        Set<String> selected = Set.of(
                HookScopeRestartShell.HOME,
                HookScopeRestartShell.SECURITY_CENTER,
                HookScopeRestartShell.SEARCH
        );
        Map<String, String> results = HookScopeRestartShell.parseResults(selected,
                "su: ready\n" +
                "LDRESTART|com.miui.home|RESTARTED\n" +
                "LDRESTART|com.miui.securitycenter|NOT_RUNNING\n");
        assertEquals(HookScopeRestartShell.RESTARTED,
                results.get(HookScopeRestartShell.HOME));
        assertEquals(HookScopeRestartShell.NOT_RUNNING,
                results.get(HookScopeRestartShell.SECURITY_CENTER));
        assertEquals(HookScopeRestartShell.FAILED,
                results.get(HookScopeRestartShell.SEARCH));
    }

    @Test
    public void onlyWhitelistedScopeAndOutcomeTokensCanBeParsed() {
        Set<String> selected = Set.of(HookScopeRestartShell.SYSTEM_UI);
        Map<String, String> results = HookScopeRestartShell.parseResults(selected,
                "LDRESTART|com.android.systemui|RESTARTED\n" +
                "LDRESTART|system|RESTARTED\n" +
                "LDRESTART|com.android.systemui|FAKE_STATUS\n");
        assertEquals(1, results.size());
        assertEquals(HookScopeRestartShell.RESTARTED,
                results.get(HookScopeRestartShell.SYSTEM_UI));
        assertThrows(IllegalArgumentException.class,
                () -> HookScopeRestartShell.buildScript(Set.of("system")));
    }

    @Test
    public void terminatedButNotYetRespawnedIsDistinctFromRestarted() {
        Map<String, String> results = HookScopeRestartShell.parseResults(
                Set.of(HookScopeRestartShell.GBOARD),
                "LDRESTART|com.google.android.inputmethod.latin|STOPPED\n");
        assertEquals(HookScopeRestartShell.STOPPED,
                results.get(HookScopeRestartShell.GBOARD));
    }
}
