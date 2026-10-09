package com.hellovoid.liquiddock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.preference.PreferenceManager
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class SearchboxSettingsActivity : SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
            var showAllParameters by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showAllParameters) { showAllParameters = false }
            val prefs = remember { PreferenceManager.getDefaultSharedPreferences(this) }
            val uiPrefs = remember { getSharedPreferences(SETTINGS_UI_PREFS, MODE_PRIVATE) }
            val glassEnabled by remember {
                mutableStateOf(uiPrefs.getBoolean(SETTINGS_UI_GLASS_ENABLED, true))
            }
            val masterEnabled by remember {
                mutableStateOf(
                    prefs.getBoolean(
                        ConfigSchema.Core.ENABLED.name(),
                        ConfigSchema.Core.ENABLED.uiDefault(),
                    ),
                )
            }
            MiuixTheme(controller = controller) {
                ModernSettingsScaffold(
                    title = if (showAllParameters) "系统搜索 · 全部参数"
                        else getString(R.string.page_searchbox),
                    glassEnabled = glassEnabled,
                    showBack = true,
                    backLabel = getString(R.string.action_back),
                    onBack = {
                        if (showAllParameters) showAllParameters = false else finish()
                    },
                    actions = {
                        ModernTopActionButton(
                            text = getString(R.string.action_restart_searchbox),
                            onClick = {
                                restartPackageProcess(
                                    "com.android.quicksearchbox",
                                    "系统搜索",
                                )
                            },
                        )
                    },
                ) { padding ->
                    if (showAllParameters) {
                        ScopedGlassSettingsPage(
                            padding, prefs, masterEnabled,
                            ScopedGlassOptics.SEARCHBOX, "系统搜索",
                        )
                    } else {
                        SearchboxSettingsPage(
                            padding = padding,
                            prefs = prefs,
                            masterEnabled = masterEnabled,
                            onOpenAll = { showAllParameters = true },
                        )
                    }
                }
            }
        }
    }
}
