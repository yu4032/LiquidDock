package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.net.Uri;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Toast;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.hellovoid.liquiddock.config.ConfigCodec;
import com.hellovoid.liquiddock.config.ConfigMigration;

public class SettingsActivity extends AppCompatActivity {
    private static final int WIDGET_HIDDEN_BACKUP_MAX_BYTES = 1024 * 1024;
    private final ActivityResultLauncher<String> exportConfigLauncher =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
            uri -> { if (uri != null) exportCurrentParameters(uri); });
    private final ActivityResultLauncher<String[]> importConfigLauncher =
        registerForActivityResult(new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importParameters(uri); });
    private final ActivityResultLauncher<String> exportWidgetHiddenLauncher =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
            uri -> { if (uri != null) exportWidgetHiddenRules(uri); });
    private final ActivityResultLauncher<String[]> importWidgetHiddenLauncher =
        registerForActivityResult(new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importWidgetHiddenRules(uri); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        migratePreferences();
        Window w = getWindow();
        // Compose installs content after this base onCreate returns. Without the former XML
        // setContentView(), PhoneWindow may not have created DecorView yet, and
        // Window#getInsetsController() dereferences that missing decor on HyperOS.
        WindowInsetsController insetsController = w.getDecorView().getWindowInsetsController();
        // targetSdk 35+ is edge-to-edge: the system/theme owns the status-bar background.
        // Only request icon contrast through the modern insets controller.
        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean night = uiMode == Configuration.UI_MODE_NIGHT_YES;
        if (insetsController != null) {
            insetsController.setSystemBarsAppearance(
                    night ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        }
    }

    private void migratePreferences() {
        ConfigMigration.migrate(this, PreferenceManager.getDefaultSharedPreferences(this));
    }

    void launchExport() {
        exportConfigLauncher.launch("LiquidDock-settings.json");
    }

    void launchImport() {
        importConfigLauncher.launch(new String[]{"application/json", "text/json", "text/plain"});
    }

    void launchWidgetHiddenExport() {
        exportWidgetHiddenLauncher.launch("LiquidDock-widget-hidden.json");
    }

    void launchWidgetHiddenImport() {
        importWidgetHiddenLauncher.launch(
                new String[]{"application/json", "text/json", "text/plain"});
    }

    private void exportCurrentParameters(Uri uri) {
        new Thread(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Unable to open destination");
                JSONObject json = collectParameters(
                    PreferenceManager.getDefaultSharedPreferences(this));
                json.put("_format", "liquiddock-settings");
                json.put("_version", 2);
                out.write((json.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> Toast.makeText(this,
                    "Parameters exported", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                showError("Export failed: " + e.getMessage());
            }
        }).start();
    }

    private void importParameters(Uri uri) {
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IOException("Unable to open selected file");
                byte[] buffer = new byte[4096];
                int count, total = 0;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > 65536) throw new IOException("Config is larger than 64 KiB");
                    out.write(buffer, 0, count);
                }
                JSONObject json = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
                String format = json.optString("_format", "liquiddock-settings");
                if (!"liquiddock-settings".equals(format))
                    throw new IOException("Not a LiquidDock settings file");
                SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(this).edit();
                applyImportedParameters(json, editor);
                if (!editor.commit()) throw new IOException("Unable to save imported settings");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Parameters imported and applied", Toast.LENGTH_LONG).show();
                    restartLauncher();
                    recreate();
                });
            } catch (Exception e) {
                showError("Import failed: " + e.getMessage());
            }
        }).start();
    }

    private void exportWidgetHiddenRules(Uri uri) {
        new Thread(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Unable to open destination");
                SharedPreferences preferences =
                        PreferenceManager.getDefaultSharedPreferences(this);
                Set<String> selected = preferences.getStringSet(
                        WidgetComponentStore.SELECTION_KEY, Set.of());
                Map<String, Object> backup = WidgetHiddenRulesBackup.exportValues(selected);
                JSONObject json = new JSONObject();
                json.put(WidgetHiddenRulesBackup.KEY_FORMAT,
                        backup.get(WidgetHiddenRulesBackup.KEY_FORMAT));
                json.put(WidgetHiddenRulesBackup.KEY_VERSION,
                        backup.get(WidgetHiddenRulesBackup.KEY_VERSION));
                JSONArray selectors = new JSONArray();
                Object values = backup.get(WidgetHiddenRulesBackup.KEY_SELECTORS);
                if (values instanceof Iterable<?>) {
                    for (Object value : (Iterable<?>) values) selectors.put(value);
                }
                json.put(WidgetHiddenRulesBackup.KEY_SELECTORS, selectors);
                out.write((json.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> Toast.makeText(this,
                        "小组件隐藏规则已导出", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                showError("导出小组件隐藏规则失败: " + e.getMessage());
            }
        }).start();
    }

    private void importWidgetHiddenRules(Uri uri) {
        new Thread(() -> {
            try {
                JSONObject json = readJsonDocument(uri, WIDGET_HIDDEN_BACKUP_MAX_BYTES,
                        "小组件隐藏规则文件超过 1 MiB");
                Map<String, Object> values = new LinkedHashMap<>();
                values.put(WidgetHiddenRulesBackup.KEY_FORMAT,
                        json.opt(WidgetHiddenRulesBackup.KEY_FORMAT));
                values.put(WidgetHiddenRulesBackup.KEY_VERSION,
                        json.opt(WidgetHiddenRulesBackup.KEY_VERSION));
                JSONArray array = json.optJSONArray(WidgetHiddenRulesBackup.KEY_SELECTORS);
                if (array == null) {
                    values.put(WidgetHiddenRulesBackup.KEY_SELECTORS, null);
                } else {
                    ArrayList<Object> selectors = new ArrayList<>(array.length());
                    for (int i = 0; i < array.length(); i++) selectors.add(array.opt(i));
                    values.put(WidgetHiddenRulesBackup.KEY_SELECTORS, selectors);
                }

                Set<String> imported = WidgetHiddenRulesBackup.importValues(values);
                SharedPreferences preferences =
                        PreferenceManager.getDefaultSharedPreferences(this);
                if (!WidgetHiddenRulesBackup.replaceSelections(preferences, imported)) {
                    throw new IOException("无法保存小组件隐藏规则");
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "小组件隐藏规则已导入并覆盖当前设置",
                            Toast.LENGTH_LONG).show();
                    restartLauncher();
                });
            } catch (Exception e) {
                showError("导入小组件隐藏规则失败: " + e.getMessage());
            }
        }).start();
    }

    private JSONObject readJsonDocument(Uri uri, int maxBytes, String tooLargeMessage)
            throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Unable to open selected file");
            byte[] buffer = new byte[4096];
            int count, total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) throw new IOException(tooLargeMessage);
                out.write(buffer, 0, count);
            }
            return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private static JSONObject collectParameters(SharedPreferences sp) throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Object> entry : ConfigCodec.exportValues(sp.getAll()).entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json;
    }

    private static void applyImportedParameters(JSONObject json, SharedPreferences.Editor editor) {
        for (Map.Entry<String, Object> entry : ConfigCodec.importValues(jsonToMap(json)).entrySet()) {
            putPreferenceValue(editor, entry.getKey(), entry.getValue());
        }
    }

    private static Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> values = new LinkedHashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            values.put(key, json.opt(key));
        }
        return values;
    }

    private static void putPreferenceValue(SharedPreferences.Editor editor, String key,
                                           Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else {
            throw new IllegalArgumentException("Unsupported preference value for " + key);
        }
    }

    void restartHookScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            Toast.makeText(this, "未选择重启作用域", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!HookScopeRestartShell.ALLOWED.containsAll(scopes)) {
            Toast.makeText(this, "包含不支持的重启作用域", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> selected = Set.copyOf(scopes);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean diagnostic = preferences.getBoolean(
                com.hellovoid.liquiddock.config.ConfigSchema.Debug.LOGGING.name(),
                com.hellovoid.liquiddock.config.ConfigSchema.Debug.LOGGING.runtimeFallback());
        LiquidDockApp.syncToRemote(preferences);
        if (diagnostic) ((LiquidDockApp) getApplication()).logScopedRestart("UI_REQUEST|" + selected);
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder("su")
                        .redirectErrorStream(true)
                        .start();
                try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                    os.writeBytes(HookScopeRestartShell.buildScript(selected, diagnostic));
                    os.flush();
                }
                if (!p.waitFor(45, TimeUnit.SECONDS)) {
                    p.destroy();
                    if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly();
                    throw new IOException("su timed out while restarting selected hook scopes");
                }

                StringBuilder stdout = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("LDRESTART|")) stdout.append(line).append('\n');
                    }
                }
                if (p.exitValue() != 0) {
                    throw new IOException("root shell failed (code " + p.exitValue() + ")");
                }
                Map<String, String> outcomes = HookScopeRestartShell.parseResults(
                        selected, stdout.toString());
                if (diagnostic) ((LiquidDockApp) getApplication()).logScopedRestart(
                        "UI_RESULT|" + outcomes);
                // No confirmation dialog for successful restarts. Only surface
                // real failures, especially when SystemUI did not respawn.
                ArrayList<String> failed = new ArrayList<>();
                for (Map.Entry<String, String> outcome : outcomes.entrySet()) {
                    boolean isSystemUi = HookScopeRestartShell.SYSTEM_UI.equals(outcome.getKey());
                    String state = outcome.getValue();
                    if (HookScopeRestartShell.FAILED.equals(state)
                            || (isSystemUi && !HookScopeRestartShell.RESTARTED.equals(state))) {
                        failed.add(restartScopeDisplayName(outcome.getKey()));
                    }
                }
                if (!failed.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            "以下作用域未能完成重启：" + String.join("、", failed),
                            Toast.LENGTH_LONG).show());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (diagnostic) ((LiquidDockApp) getApplication()).logScopedRestart(
                        "UI_INTERRUPTED|" + e);
                runOnUiThread(() -> Toast.makeText(this,
                        "作用域重启已中断", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                if (diagnostic) ((LiquidDockApp) getApplication()).logScopedRestart(
                        "UI_FAILED|" + e);
                runOnUiThread(() -> Toast.makeText(this,
                        "作用域重启失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static String restartScopeDisplayName(String scope) {
        switch (scope) {
            case HookScopeRestartShell.HOME: return "桌面";
            case HookScopeRestartShell.SYSTEM_UI: return "系统界面";
            case HookScopeRestartShell.SECURITY_CENTER: return "安全中心";
            case HookScopeRestartShell.GBOARD: return "Gboard";
            case HookScopeRestartShell.SEARCH: return "系统搜索";
            default: return scope;
        }
    }

    void restartLauncher() {
        // Configuration is already in API101 Remote Preferences. Root is used only to
        // restart MIUI Home so process-start-only hooks reload their settings.
        LiquidDockApp.syncToRemote(PreferenceManager.getDefaultSharedPreferences(this));
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder("su")
                        .redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")))
                        .redirectError(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")))
                        .start();
                try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                    os.writeBytes("am force-stop com.miui.home && sleep 1 && "
                        + "am start -a android.intent.action.MAIN -c android.intent.category.HOME\nexit\n");
                    os.flush();
                }
                if (!p.waitFor(8, TimeUnit.SECONDS)) {
                    p.destroy();
                    if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly();
                    throw new IOException("su timed out while restarting MIUI Home");
                }
                int exitCode = p.exitValue();
                if (exitCode != 0) throw new IOException("su failed with exit code " + exitCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> Toast.makeText(this,
                        "Launcher restart interrupted", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: "+e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    void restartPackageProcess(String packageName, String displayName) {
        // Third-party app descriptors provide trusted static process names. Validate the value
        // anyway so future entries cannot accidentally turn this shell command into an injection path.
        if (packageName == null || !packageName.matches("[A-Za-z0-9_.:]+")
                || displayName == null || displayName.trim().isEmpty()) {
            Toast.makeText(this, "Invalid app restart target", Toast.LENGTH_SHORT).show();
            return;
        }
        LiquidDockApp.syncToRemote(PreferenceManager.getDefaultSharedPreferences(this));
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder("su")
                        .redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")))
                        .redirectError(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")))
                        .start();
                try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                    os.writeBytes("PIDS=$(pidof " + packageName + "); "
                            + "if [ -z \"$PIDS\" ]; then exit 1; fi; "
                            + "kill -TERM $PIDS\nexit\n");
                    os.flush();
                }
                if (!p.waitFor(8, TimeUnit.SECONDS)) {
                    p.destroy();
                    if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly();
                    throw new IOException("su timed out while restarting " + displayName);
                }
                int exitCode = p.exitValue();
                if (exitCode != 0) {
                    throw new IOException(displayName + " process is not running");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> Toast.makeText(this,
                        displayName + " restart interrupted", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


}
