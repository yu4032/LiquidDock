package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle

/** ShortcutMenu-only controls; the full optics page shares the same scoped keys. */
@Composable
internal fun ShortcutPopupGlassSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    onOpenAll: () -> Unit,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var popupEnabled by remember {
        mutableStateOf(prefs.getBoolean(
            ConfigSchema.Glass.SHORTCUT_POPUP_GLASS.name(),
            ConfigSchema.Glass.SHORTCUT_POPUP_GLASS.uiDefault(),
        ))
    }
    val controlsEnabled = masterEnabled && liquidEnabled && popupEnabled
    var resetGeneration by remember { mutableIntStateOf(0) }
    var appearanceRevision by remember { mutableIntStateOf(0) }

    val basicKeys = listOf(
        ConfigSchema.Glass.BLUR,
        ConfigSchema.Glass.TINT_RED,
        ConfigSchema.Glass.TINT_GREEN,
        ConfigSchema.Glass.TINT_BLUE,
        ConfigSchema.Glass.TINT_ALPHA,
    )
    val hasAppearanceOverride = appearanceRevision.let {
        basicKeys.any { config ->
            val key = ScopedGlassOptics.key(ScopedGlassOptics.POPUP, config)
            prefs.contains(key) || prefs.contains("${key}_tenths")
        }
    }
    fun resetAppearance() {
        val editor = prefs.edit()
        basicKeys.forEach { config ->
            val key = ScopedGlassOptics.key(ScopedGlassOptics.POPUP, config)
            editor.remove(key).remove("${key}_tenths")
        }
        editor.apply()
        resetGeneration++
        appearanceRevision++
    }

    @Composable
    fun appearance(
        config: ConfigKey<Int>,
        title: String,
        unit: String = "",
    ) {
        ScopedGlassSlider(
            prefs = prefs,
            key = ScopedGlassOptics.key(ScopedGlassOptics.POPUP, config),
            fallback = config,
            title = title,
            unit = unit,
            enabled = controlsEnabled,
            generation = resetGeneration,
            decimal = config.storageMode() == ConfigKey.StorageMode.DP_TENTHS,
            // The reset action only needs to wake once when the first override appears.
            // Recompose neither the section nor its other sliders on each drag frame.
            onPersisted = { if (!hasAppearanceOverride) appearanceRevision++ },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "shortcut-header") {
            PageHeader(
                stringResource(R.string.page_shortcut_popup),
                "独立调整桌面长按快捷菜单的玻璃外观；未单独设置的参数继承全局液态玻璃。",
            )
        }
        item(key = "shortcut-function-title") { SmallTitle("功能") }
        item(key = "shortcut-function") {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.SHORTCUT_POPUP_GLASS,
                    "启用快捷菜单玻璃",
                    "替换长按图标时的菜单背景；关闭后恢复原生材质，已有 Hook 时实时生效",
                    masterEnabled && liquidEnabled,
                ) { popupEnabled = it }
                ModernListDivider()
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT,
                    "快捷菜单深色模式适配",
                    "文字与单色图标适配为浅色；保留彩色应用图标",
                    masterEnabled && liquidEnabled,
                )
            }
        }
        item(key = "shortcut-color-title") { SmallTitle("玻璃颜色") }
        item(key = "shortcut-color") {
            SettingsCard {
                appearance(ConfigSchema.Glass.TINT_RED, "红")
                appearance(ConfigSchema.Glass.TINT_GREEN, "绿")
                appearance(ConfigSchema.Glass.TINT_BLUE, "蓝")
                appearance(ConfigSchema.Glass.TINT_ALPHA, "不透明度")
            }
        }
        item(key = "shortcut-blur-title") { SmallTitle("模糊") }
        item(key = "shortcut-blur") {
            SettingsCard {
                appearance(ConfigSchema.Glass.BLUR, "菜单背景模糊", "px")
                ArrowPreference(
                    title = "恢复继承全局外观",
                    summary = "只清除快捷菜单的颜色与模糊覆盖，不影响独立光学参数",
                    enabled = controlsEnabled && hasAppearanceOverride,
                    onClick = { resetAppearance() },
                )
            }
        }
        item(key = "shortcut-optics-title") { SmallTitle("独立参数") }
        item(key = "shortcut-optics") {
            SettingsCard {
                ArrowPreference(
                    title = "全部参数",
                    summary = "折射、厚度、OS4 高光与阴影等独立设置；未调整时继承全局",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = onOpenAll,
                )
            }
        }
    }
}
