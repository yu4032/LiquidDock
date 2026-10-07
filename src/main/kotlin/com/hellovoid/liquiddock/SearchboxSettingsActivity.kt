package com.hellovoid.liquiddock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.preference.PreferenceManager
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SearchboxSettingsActivity : SettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { PreferenceManager.getDefaultSharedPreferences(this) }
            val masterEnabled by remember {
                mutableStateOf(
                    prefs.getBoolean(
                        ConfigSchema.Core.ENABLED.name(),
                        ConfigSchema.Core.ENABLED.uiDefault(),
                    ),
                )
            }
            LiquidDockTheme {
                LiquidDockSettingsScaffold(
                    title = getString(R.string.page_searchbox),
                    showBack = true,
                    onBack = { finish() },
                    actions = {
                        TextButton(
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
