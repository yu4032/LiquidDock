package com.hellovoid.liquiddock

import android.os.Bundle
import androidx.activity.compose.setContent
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
            val prefs = remember { PreferenceManager.getDefaultSharedPreferences(this) }
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
                    title = getString(R.string.page_searchbox),
                    showBack = true,
                    backLabel = getString(R.string.action_back),
                    onBack = { finish() },
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
                    SearchboxSettingsPage(
                        padding = padding,
                        prefs = prefs,
                        masterEnabled = masterEnabled,
                    )
                }
            }
        }
    }
}
