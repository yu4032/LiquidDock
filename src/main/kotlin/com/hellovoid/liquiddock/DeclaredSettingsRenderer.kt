package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.hellovoid.liquiddock.config.ConfigKey
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.SmallTitle

internal data class SettingsFocusRequest(val page: Page, val key: String, val sequence: Int)

@Composable
internal fun DeclaredSettingsPage(
    padding: PaddingValues,
    meta: SettingsMeta,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    focus: SettingsFocusRequest?,
    open: (Page) -> Unit,
) {
    val state = rememberLazyListState()
    val focusGlow = remember(meta.page) { Animatable(0f) }
    var generation by remember(meta.page) { mutableIntStateOf(0) }
    val revisions = LocalSettingsPreferenceRevisions.current
    fun matches(requirements: List<Pair<ConfigKey<Boolean>, Boolean>>) = requirements.all { (config, expected) ->
        revisions[config.name()]
        prefs.getBoolean(config.name(), config.uiDefault()) == expected
    }
    // Read revisions here so dependencies and inheritance summaries update even
    // when only a child switch wrote the preference.
    val active = revisions.let { masterEnabled && matches(meta.requires) }
    val request = focus?.takeIf { it.page == meta.page }
    LaunchedEffect(meta.page, request) {
        focusGlow.snapTo(0f)
        val index = request?.let { meta.indexOfKey(it.key) } ?: -1
        if (index < 0) return@LaunchedEffect
        // Position addressing works before a lazy row exists. Wait for that
        // specific stable item, then calculate in the lazy viewport coordinates.
        state.scrollToItem(index)
        snapshotFlow { state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == meta.blocks[index].id } }
            .first { it != null }
        withFrameNanos { }
        val layout = state.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.key == meta.blocks[index].id }
            ?: return@LaunchedEffect
        state.scrollBy(settingsCenterScrollDelta(item.offset, item.size,
            layout.viewportStartOffset, layout.viewportEndOffset))
        repeat(3) {
            focusGlow.animateTo(0.38f, tween(120))
            focusGlow.animateTo(0f, tween(240))
        }
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("settings-list:${meta.page.name}").semantics { testTagsAsResourceId = true },
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(meta.blocks, key = { it.id }) { block ->
            when (block) {
                is SettingsBlock.Info -> PageHeader(meta.title, block.text)
                is SettingsBlock.Header -> SmallTitle(block.title)
                is SettingsBlock.Link -> SettingsCard {
                    ArrowPreference(block.title, block.summary, enabled = active,
                        onClick = { open(block.destination) })
                }
                is SettingsBlock.ResetEdge -> {
                    val hasOverrides = revisions.let { generation.let {
                        GlassEdgeEffects.KEYS.any { config ->
                            val key = GlassEdgeEffects.key(block.scope, config)
                            revisions[key]
                            prefs.contains(key) || prefs.contains("${key}_tenths")
                        }
                    } }
                    SettingsCard {
                        ArrowPreference("恢复继承全局光照参数", "仅清除该组件的覆盖参数，其他玻璃不受影响",
                            enabled = active && hasOverrides, onClick = {
                                val editor = prefs.edit()
                                GlassEdgeEffects.KEYS.forEach { config ->
                                    val key = GlassEdgeEffects.key(block.scope, config)
                                    editor.remove(key).remove("${key}_tenths")
                                }
                                editor.apply()
                                generation++
                            })
                    }
                }
                is SettingsBlock.Row -> {
                    val focused = request?.key == block.key
                    Box(Modifier.fillMaxWidth().testTag("setting:${meta.page.name}:${block.key}").drawWithContent {
                        drawContent()
                        if (focused && focusGlow.value > 0f) {
                            val inset = 14.dp.toPx()
                            drawRoundRect(
                                color = Color.White.copy(alpha = focusGlow.value),
                                topLeft = Offset(inset, 0f),
                                size = Size((size.width - inset * 2).coerceAtLeast(0f), size.height),
                                cornerRadius = CornerRadius(20.dp.toPx()),
                            )
                        }
                    }) {
                        SettingsCard {
                            val enabled = active && matches(block.requires)
                            if (block.scope == null) {
                                block.number?.let { IntSetting(prefs, it, enabled) }
                                block.toggle?.let { BooleanSetting(prefs, it, block.title, block.summary, enabled) }
                            } else {
                                block.number?.let { spec ->
                                    ScopedGlassSlider(prefs = prefs, key = block.key,
                                        fallback = spec.config, title = block.title, unit = spec.unit,
                                        enabled = enabled, generation = generation,
                                        decimal = spec.isDecimal)
                                }
                                block.toggle?.let { config ->
                                    val revision = revisions[block.key] ?: 0
                                    var value by remember(block.key, revision, generation, revisions[config.name()]) {
                                        mutableStateOf(prefs.getBoolean(block.key,
                                            prefs.getBoolean(config.name(), config.uiDefault())))
                                    }
                                    SwitchPreference(checked = value, onCheckedChange = {
                                        value = it
                                        prefs.edit().putBoolean(block.key, it).apply()
                                        generation++
                                    }, title = block.title,
                                        summary = (if (prefs.contains(block.key)) "独立设置 · " else "继承全局 · ") + block.summary,
                                        enabled = enabled)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
