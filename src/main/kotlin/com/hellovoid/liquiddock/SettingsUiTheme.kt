package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.isSystemInDarkTheme
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
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
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

/**
 * Keep Android's wallpaper-derived system palette unchanged. For explicit color
 * choices, MIUIX's default TonalSpot desaturates each seed toward neutral gray;
 * use its native Vibrant palette to retain recognizable accent hue/chroma.
 */
@Composable
internal fun rememberSettingsUiThemeController(palette: SettingsUiPalette): ThemeController {
    val dark = isSystemInDarkTheme()
    return remember(palette, dark) {
        ThemeController(
            ColorSchemeMode.MonetSystem,
            keyColor = palette.getSeedArgb(dark)?.let { Color(it) },
            paletteStyle = if (palette == SettingsUiPalette.SYSTEM) {
                ThemePaletteStyle.TonalSpot
            } else {
                ThemePaletteStyle.Vibrant
            },
        )
    }
}

/**
 * Monet produces coherent supporting colors, but even Vibrant uses fixed
 * Material tone steps for primary (40/80), which can look pastel/gray on
 * some themes. Explicit fixed-color choices should show their actual seed
 * for interactive accent roles. Keep background, surfaces, outlines, disabled
 * colors and the entire system-dynamic-color option as MIUIX generates them.
 *
 * The inner MIUIX theme overrides only its color roles. This does not touch
 * Prismal's sampling, shader, layout, motion or tab paint pipeline.
 */
@Composable
internal fun SettingsUiColorTheme(
    controller: ThemeController,
    palette: SettingsUiPalette,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    MiuixTheme(controller = controller) {
        val seed = palette.getSeedArgb(dark)
        if (seed == null) {
            // Wallpaper-derived dynamic color remains exactly the stock MIUIX theme.
            content()
        } else {
            val baseColors = MiuixTheme.colorScheme
            val accent = Color(seed)
            val onAccent = if (accent.luminance() > 0.179f) Color.Black else Color.White
            val vividColors = remember(seed, baseColors.primary, baseColors.background) {
                baseColors.copy(
                    primary = accent,
                    onPrimary = onAccent,
                    primaryVariant = accent,
                    onPrimaryVariant = onAccent,
                    onBackgroundVariant = accent,
                    sliderKeyPoint = accent,
                )
            }
            MiuixTheme(colors = vividColors, content = content)
        }
    }
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
    val dark = isSystemInDarkTheme()
    val systemPaletteController = remember { ThemeController(ColorSchemeMode.MonetSystem) }
    val systemAccent = systemPaletteController.currentColors().primary
    SettingsList(
        padding,
        stringResource(R.string.page_theme_palette),
        stringResource(R.string.theme_palette_summary),
    ) {
        SettingsUiPalette.values().forEachIndexed { index, palette ->
            if (index > 0) ModernListDivider()
            val chosen = palette == selected
            val swatch = palette.getSeedArgb(dark)?.let { Color(it) } ?: systemAccent
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
