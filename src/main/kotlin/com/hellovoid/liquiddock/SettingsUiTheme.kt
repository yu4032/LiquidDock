package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** One observable private preference, shared across all settings Activities. */
@Composable
internal fun rememberSettingsUiPalette(preferences: SharedPreferences): MutableState<SettingsUiPalette> {
    val palette = remember(preferences) {
        mutableStateOf(SettingsUiPalette.fromStored(
            preferences.getString(SETTINGS_UI_THEME_PALETTE, SettingsUiPalette.SYSTEM.persistedValue),
        ))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
            if (key == null || key == SETTINGS_UI_THEME_PALETTE) {
                palette.value = SettingsUiPalette.fromStored(
                    source.getString(SETTINGS_UI_THEME_PALETTE, SettingsUiPalette.SYSTEM.persistedValue),
                )
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return palette
}

/** Rebuild the native MIUIX Monet palette only when the selected seed changes. */
@Composable
internal fun rememberSettingsUiThemeController(palette: SettingsUiPalette): ThemeController =
    remember(palette) {
        ThemeController(
            ColorSchemeMode.MonetSystem,
            keyColor = palette.seedArgb?.let { Color(it) },
        )
    }

private fun paletteLabel(palette: SettingsUiPalette): Int = when (palette) {
    SettingsUiPalette.SYSTEM -> R.string.theme_palette_system
    SettingsUiPalette.BLUE -> R.string.theme_palette_blue
    SettingsUiPalette.PURPLE -> R.string.theme_palette_purple
    SettingsUiPalette.PINK -> R.string.theme_palette_pink
    SettingsUiPalette.RED -> R.string.theme_palette_red
    SettingsUiPalette.ORANGE -> R.string.theme_palette_orange
    SettingsUiPalette.GREEN -> R.string.theme_palette_green
}

@Composable
internal fun SettingsUiPalettePage(
    padding: PaddingValues,
    selected: SettingsUiPalette,
    onSelect: (SettingsUiPalette) -> Unit,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_theme_palette),
        stringResource(R.string.theme_palette_summary),
    ) {
        SettingsUiPalette.values().forEachIndexed { index, palette ->
            if (index > 0) ModernListDivider()
            val chosen = palette == selected
            val swatch = palette.seedArgb?.let { Color(it) } ?: MiuixTheme.colorScheme.primary
            BasicComponent(
                title = stringResource(paletteLabel(palette)),
                summary = if (chosen) stringResource(R.string.theme_palette_selected) else null,
                insideMargin = ModernPreferenceMargin,
                endActions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(23.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    1.dp,
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                    CircleShape,
                                ),
                        )
                        if (chosen) {
                            Text("✓", color = MiuixTheme.colorScheme.primary)
                        }
                    }
                },
                onClick = { onSelect(palette) },
            )
        }
    }
}
