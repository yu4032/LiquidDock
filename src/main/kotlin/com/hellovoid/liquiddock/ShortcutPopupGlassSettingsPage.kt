package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle

/** Both native menu surfaces use the same settings cells but independent scoped keys. */
@Composable
internal fun ShortcutPopupGlassSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    onOpenAll: () -> Unit,
    onOpenEdge: () -> Unit,
    searchFocus: SettingsFocusRequest? = null,
) = ScopedMenuGlassSettingsPage(
    padding, prefs, masterEnabled, onOpenAll, onOpenEdge, searchFocus,
    scope = ScopedGlassOptics.POPUP,
    titleRes = R.string.page_shortcut_popup,
    description = "独立调整桌面长按快捷菜单的玻璃外观；未单独设置的参数继承全局液态玻璃。",
    toggleKey = ConfigSchema.Glass.SHORTCUT_POPUP_GLASS,
    toggleTitle = "启用快捷菜单玻璃",
    toggleDescription = "替换长按图标时的菜单背景；已有 Hook 时实时生效，首次加载缺失的 Hook 需重启桌面",
    blurTitle = "菜单背景模糊",
    cornerRadiusKey = ConfigSchema.Glass.SHORTCUT_POPUP_CORNER_RADIUS,
)

@Composable
internal fun HandleMenuGlassSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    onOpenAll: () -> Unit,
    onOpenEdge: () -> Unit,
    searchFocus: SettingsFocusRequest? = null,
) = ScopedMenuGlassSettingsPage(
    padding, prefs, masterEnabled, onOpenAll, onOpenEdge, searchFocus,
    scope = ScopedGlassOptics.HANDLE_MENU,
    titleRes = R.string.page_handle_menu,
    description = "独立调整应用顶部控制器弹出菜单的玻璃；未单独设置的参数继承全局液态玻璃。",
    toggleKey = ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS,
    toggleTitle = "启用应用顶部菜单玻璃",
    toggleDescription = "替换应用顶部控制器展开的分屏、小窗菜单；已有 Hook 时实时生效，首次加载缺失的 Hook 需重启系统界面",
    blurTitle = "顶部菜单背景模糊",
    cornerRadiusKey = ConfigSchema.Glass.HANDLE_MENU_CORNER_RADIUS,
)

@Composable
private fun ScopedMenuGlassSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    onOpenAll: () -> Unit,
    onOpenEdge: () -> Unit,
    searchFocus: SettingsFocusRequest?,
    scope: String,
    titleRes: Int,
    description: String,
    toggleKey: ConfigKey<Boolean>,
    toggleTitle: String,
    toggleDescription: String,
    blurTitle: String,
    cornerRadiusKey: ConfigKey<Int>,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var menuEnabled by remember(scope) {
        mutableStateOf(prefs.getBoolean(toggleKey.name(), toggleKey.uiDefault()))
    }
    val controlsEnabled = masterEnabled && liquidEnabled && menuEnabled
    var resetGeneration by remember(scope) { mutableIntStateOf(0) }
    var appearanceRevision by remember(scope) { mutableIntStateOf(0) }

    val basicKeys = listOf(
        ConfigSchema.Glass.BLUR,
        ConfigSchema.Glass.TINT_RED,
        ConfigSchema.Glass.TINT_GREEN,
        ConfigSchema.Glass.TINT_BLUE,
        ConfigSchema.Glass.TINT_ALPHA,
    )
    val hasAppearanceOverride = appearanceRevision.let {
        basicKeys.any { config ->
            val key = ScopedGlassOptics.key(scope, config)
            prefs.contains(key) || prefs.contains("${key}_tenths")
        }
    }
    fun resetAppearance() {
        val editor = prefs.edit()
        basicKeys.forEach { config ->
            val key = ScopedGlassOptics.key(scope, config)
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
            key = ScopedGlassOptics.key(scope, config),
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

    val page = if (scope == ScopedGlassOptics.POPUP)
        Page.ShortcutPopupCustomization else Page.HandleMenuCustomization
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(searchFocus?.sequence, searchFocus?.key) {
        val index = searchFocus?.key?.let { customDenseFocusIndex(page, it) }
        if (index != null) listState.animateScrollToItem(index)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "shortcut-header") {
            PageHeader(
                stringResource(titleRes),
                description,
            )
        }
        item(key = "shortcut-function-title") { SmallTitle("功能") }
        item(key = "shortcut-function") {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    toggleKey,
                    toggleTitle,
                    toggleDescription,
                    masterEnabled && liquidEnabled,
                ) { menuEnabled = it }
                if (scope == ScopedGlassOptics.POPUP) {
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
        }
        item(key = "menu-corner-title") { SmallTitle("圆角") }
        item(key = "menu-corner-radius") {
            SettingsCard {
                IntSetting(
                    prefs,
                    IntSpec(cornerRadiusKey, "菜单圆角", "dp",
                        summary = "小于 0 跟随系统圆角，0 为直角；重新打开菜单生效"),
                    controlsEnabled,
                )
            }
        }
        item(key = "shortcut-color-title") { SmallTitle("玻璃染色（保留背景纹理）") }
        item(key = "shortcut-color") {
            SettingsCard {
                appearance(ConfigSchema.Glass.TINT_RED, "红")
                appearance(ConfigSchema.Glass.TINT_GREEN, "绿")
                appearance(ConfigSchema.Glass.TINT_BLUE, "蓝")
            }
        }
        item(key = "shortcut-color-alpha") {
            SettingsCard {
                appearance(ConfigSchema.Glass.TINT_ALPHA, "染色强度")
            }
        }
        item(key = "shortcut-blur-title") { SmallTitle("模糊") }
        item(key = "shortcut-blur") {
            SettingsCard {
                appearance(ConfigSchema.Glass.BLUR, blurTitle, "px")
            }
        }
        item(key = "shortcut-reset-title") { SmallTitle("外观恢复") }
        item(key = "shortcut-reset") {
            SettingsCard {
                ArrowPreference(
                    title = "恢复继承全局外观",
                    summary = "只清除当前菜单的颜色与模糊覆盖，不影响独立光学参数",
                    enabled = controlsEnabled && hasAppearanceOverride,
                    onClick = { resetAppearance() },
                )
            }
        }
        item(key = "shortcut-edge") {
            SettingsCard {
                ArrowPreference(
                    title = "阴影",
                    summary = "Prismal 外阴影与光学描边可独立调节",
                    enabled = controlsEnabled,
                    onClick = onOpenEdge,
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
