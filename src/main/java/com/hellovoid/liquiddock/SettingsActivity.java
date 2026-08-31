package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.net.Uri;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.hellovoid.liquiddock.config.ConfigCodec;
import com.hellovoid.liquiddock.config.ConfigMigration;
import com.hellovoid.liquiddock.config.PresetManager;

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
        setContentView(R.layout.activity_settings);
        migratePreferences();
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Window w = getWindow();
        // targetSdk 35+ is edge-to-edge: the system/theme owns the status-bar background.
        // Only request icon contrast through the modern insets controller.
        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean night = uiMode == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsController insetsController = w.getInsetsController();
        if (insetsController != null) {
            insetsController.setSystemBarsAppearance(
                    night ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        }
        if (useLegacyPreferenceUi()) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SettingsFragment()).commit();
        }
    }

    protected boolean useLegacyPreferenceUi() { return true; }

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


    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            SettingsActivity activity = (SettingsActivity) requireActivity();
            Preference export = findPreference("export_config");
            if (export != null) export.setOnPreferenceClickListener(pref -> {
                activity.launchExport(); return true;
            });
            Preference importPref = findPreference("import_config");
            if (importPref != null) importPref.setOnPreferenceClickListener(pref -> {
                activity.launchImport(); return true;
            });
            Preference ipadPreset = findPreference("preset_ipad");
            if (ipadPreset != null) ipadPreset.setOnPreferenceClickListener(pref -> {
                applyIpadPreset();
                return true;
            });
            Preference restart = findPreference("restart_launcher");
            if (restart != null) restart.setOnPreferenceClickListener(pref -> {
                activity.restartLauncher();
                return true;
            });
        }

        private void applyIpadPreset() {
            PresetManager.IpadPresetResult result = PresetManager.applyIpad(requireContext(),
                    PreferenceManager.getDefaultSharedPreferences(requireContext()));
            Toast.makeText(requireContext(),
                "iPad preset: spacing " + result.spacing + " px, height "
                    + signed(result.heightOffset) + " px, width " + signed(result.widthOffset)
                    + " px, radius " + signed(result.cornerOffset) + " px, bottom +"
                    + result.bottomOffset + " px",
                Toast.LENGTH_LONG).show();
            ((SettingsActivity) requireActivity()).restartLauncher();
        }

        private static String signed(int value) {
            return value > 0 ? "+" + value : String.valueOf(value);
        }
    }
}
