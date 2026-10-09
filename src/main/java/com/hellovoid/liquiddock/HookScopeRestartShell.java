package com.hellovoid.liquiddock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Restart only known Xposed hook processes and report their actual PID outcome.
 * Shell script output is a bounded, explicit protocol; it is not the exit status
 * of the last shell command (which previously masked skipped restarts).
 */
final class HookScopeRestartShell {
    static final String HOME = "com.miui.home";
    static final String SYSTEM_UI = "com.android.systemui";
    static final String SECURITY_CENTER = "com.miui.securitycenter";
    static final String GBOARD = "com.google.android.inputmethod.latin";
    static final String SEARCH = "com.android.quicksearchbox";

    static final String RESTARTED = "RESTARTED";
    static final String STOPPED = "STOPPED";
    static final String NOT_RUNNING = "NOT_RUNNING";
    static final String FAILED = "FAILED";
    private static final String PREFIX = "LDRESTART|";

    // SystemUI restart immediately switches the device to the lock screen.
    // Complete every other selected restart before that disruptive final
    // action. Launcher should be penultimate so returning HOME cannot prevent
    // other application scopes from restarting.
    private static final String[] ORDER = {
            SECURITY_CENTER, GBOARD, SEARCH, HOME, SYSTEM_UI
    };

    static final Set<String> ALLOWED = Set.of(ORDER);

    private HookScopeRestartShell() {}

    static String buildScript(Set<String> selected) {
        return buildScript(selected, false);
    }

    static String buildScript(Set<String> selected, boolean diagnostic) {
        if (selected == null || selected.isEmpty() || !ALLOWED.containsAll(selected)) {
            throw new IllegalArgumentException("Unsupported restart scope selection");
        }

        StringBuilder script = new StringBuilder();
        if (diagnostic) {
            // One logcat event for every shell milestone, even if launching
            // HOME backgrounds/kills the Settings activity or its su process.
            // The app's existing Debug.LOGGING switch gates this entirely.
            script.append("trace() { /system/bin/log -p i -t LD_SCOPE_RESTART \"$1\" 2>/dev/null || true; }\n");
            script.append("trace \"ROOT_START|pid=$|ppid=$PPID|uid=$(id -u)\"\n");
        }
        script.append("report() { printf 'LDRESTART|%s|%s\\n' \"$1\" \"$2\"; ");
        if (diagnostic) script.append("trace \"RESULT|$1|$2\"; ");
        script.append("}\n");
        script.append("restart_running() {\n");
        script.append("  scope=\"$1\"; process=\"$2\"\n");
        script.append("  before=\"$(pidof \"$process\" 2>/dev/null || true)\"\n");
        script.append("  if [ -z \"$before\" ]; then report \"$scope\" NOT_RUNNING; return; fi\n");
        script.append("  if ! kill -TERM $before 2>/dev/null; then report \"$scope\" FAILED; return; fi\n");
        script.append("  i=0\n");
        script.append("  while [ \"$i\" -lt 40 ]; do\n");
        script.append("    after=\"$(pidof \"$process\" 2>/dev/null || true)\"\n");
        script.append("    old_alive=0\n");
        script.append("    for old in $before; do\n");
        script.append("      if kill -0 \"$old\" 2>/dev/null; then old_alive=1; break; fi\n");
        script.append("    done\n");
        // SIGTERM may be ignored; escalate once, after checking the PID still
        // belongs to the exact selected process (never an unrelated package).
        script.append("    if [ \"$i\" -eq 15 ] && [ \"$old_alive\" -ne 0 ]; then\n");
        script.append("      current=\"$(pidof \"$process\" 2>/dev/null || true)\"\n");
        script.append("      for old in $before; do\n");
        script.append("        case \" $current \" in *\" $old \"*) kill -KILL \"$old\" 2>/dev/null || true ;; esac\n");
        script.append("      done\n");
        script.append("    fi\n");
        script.append("    if [ \"$old_alive\" -eq 0 ]; then\n");
        script.append("      if [ -n \"$after\" ]; then report \"$scope\" RESTARTED; ");
        script.append("else report \"$scope\" STOPPED; fi\n");
        script.append("      return\n");
        script.append("    fi\n");
        script.append("    sleep 0.1; i=$((i+1))\n");
        script.append("  done\n");
        script.append("  report \"$scope\" FAILED\n");
        script.append("}\n");

        // SystemUI is a persistent system process: TERM is not a reliable
        // restart trigger on HyperOS. Use a targeted KILL and wait for a
        // different live PID, independent of which settings page invoked it.
        script.append("restart_systemui() {\n");
        script.append("  before_ui=\"$(pidof com.android.systemui 2>/dev/null || true)\"\n");
        script.append("  if [ -z \"$before_ui\" ]; then report com.android.systemui FAILED; return; fi\n");
        script.append("  if ! kill -KILL $before_ui 2>/dev/null; then report com.android.systemui FAILED; return; fi\n");
        script.append("  i=0\n");
        script.append("  while [ \"$i\" -lt 80 ]; do\n");
        script.append("    after_ui=\"$(pidof com.android.systemui 2>/dev/null || true)\"\n");
        script.append("    old_alive=0\n");
        script.append("    for old in $before_ui; do\n");
        script.append("      if kill -0 \"$old\" 2>/dev/null; then old_alive=1; break; fi\n");
        script.append("    done\n");
        script.append("    if [ \"$old_alive\" -eq 0 ] && [ -n \"$after_ui\" ]; then\n");
        script.append("      for fresh in $after_ui; do\n");
        script.append("        case \" $before_ui \" in *\" $fresh \"*) ;; *) report com.android.systemui RESTARTED; return ;; esac\n");
        script.append("      done\n");
        script.append("    fi\n");
        script.append("    sleep 0.1; i=$((i+1))\n");
        script.append("  done\n");
        script.append("  report com.android.systemui FAILED\n");
        script.append("}\n");

        for (String scope : ORDER) {
            if (!selected.contains(scope)) continue;
            if (diagnostic) {
                String process = SECURITY_CENTER.equals(scope)
                        ? "com.miui.securitycenter:ui" : scope;
                script.append("trace \"STEP|" + scope + "|pid=$(pidof " + process
                        + " 2>/dev/null || true)\"\n");
            }
            if (HOME.equals(scope)) {
                script.append("old_home=\"$(pidof com.miui.home 2>/dev/null || true)\"\n");
                script.append("if ! am force-stop com.miui.home >/dev/null 2>&1; then\n");
                script.append("  report com.miui.home FAILED\n");
                script.append("elif ! am start -a android.intent.action.MAIN ");
                script.append("-c android.intent.category.HOME >/dev/null 2>&1; then\n");
                script.append("  report com.miui.home FAILED\n");
                script.append("else\n");
                script.append("  i=0; new_home=\"\"\n");
                script.append("  while [ \"$i\" -lt 50 ]; do\n");
                script.append("    new_home=\"$(pidof com.miui.home 2>/dev/null || true)\"\n");
                script.append("    if [ -n \"$new_home\" ] && [ \"$new_home\" != \"$old_home\" ]; then break; fi\n");
                script.append("    sleep 0.1; i=$((i+1))\n");
                script.append("  done\n");
                script.append("  if [ -n \"$new_home\" ] && [ \"$new_home\" != \"$old_home\" ]; ");
                script.append("then report com.miui.home RESTARTED; ");
                script.append("else report com.miui.home FAILED; fi\n");
                script.append("fi\n");
            } else if (SYSTEM_UI.equals(scope)) {
                script.append("restart_systemui\n");
            } else if (SECURITY_CENTER.equals(scope)) {
                // Only :ui installs LiquidDock's Security Center hook.
                script.append("restart_running com.miui.securitycenter com.miui.securitycenter:ui\n");
            } else if (GBOARD.equals(scope)) {
                script.append("restart_running com.google.android.inputmethod.latin ");
                script.append("com.google.android.inputmethod.latin\n");
            } else if (SEARCH.equals(scope)) {
                script.append("restart_running com.android.quicksearchbox ");
                script.append("com.android.quicksearchbox\n");
            }
        }
        if (diagnostic) script.append("trace 'ROOT_DONE'\n");
        script.append("exit 0\n");
        return script.toString();
    }

    static Map<String, String> parseResults(Set<String> selected, String stdout) {
        Map<String, String> results = new LinkedHashMap<>();
        for (String scope : ORDER) {
            if (selected.contains(scope)) results.put(scope, FAILED);
        }
        if (stdout == null) return Collections.unmodifiableMap(results);
        for (String line : stdout.split("\\r?\\n")) {
            if (!line.startsWith(PREFIX)) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3 || !results.containsKey(parts[1])) continue;
            String state = parts[2].trim();
            if (RESTARTED.equals(state) || STOPPED.equals(state)
                    || NOT_RUNNING.equals(state) || FAILED.equals(state)) {
                results.put(parts[1], state);
            }
        }
        return Collections.unmodifiableMap(results);
    }
}
