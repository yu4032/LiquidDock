package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** Settings-side UI for exact widget background targets discovered by Launcher 4.50. */
@Composable
internal fun WidgetBackgroundSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val builtInKey = ConfigSchema.Glass.WIDGET_BACKGROUND_BUILTIN_RULES.name()
    val userRulesKey = ConfigSchema.Glass.WIDGET_BACKGROUND_USER_RULES.name()
    var builtInRules by remember {
        mutableStateOf(
            prefs.getBoolean(
                builtInKey,
                ConfigSchema.Glass.WIDGET_BACKGROUND_BUILTIN_RULES.uiDefault(),
            ),
        )
    }
    var encodedRules by remember {
        mutableStateOf(
            prefs.getString(
                userRulesKey,
                ConfigSchema.Glass.WIDGET_BACKGROUND_USER_RULES.uiDefault(),
            ) ?: "",
        )
    }
    var refreshGeneration by remember { mutableStateOf(0) }
    val discovered = remember(refreshGeneration) { loadDiscoveredWidgetBackgrounds() }
    val configuredRules = remember(encodedRules) { WidgetBackgroundUserRuleCodec.decode(encodedRules) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item { SmallTitle("小组件背景隐藏") }
        item {
            SwitchPreference(
                checked = builtInRules,
                onCheckedChange = {
                    builtInRules = it
                    prefs.edit().putBoolean(builtInKey, it).apply()
                },
                title = "使用内置兼容规则",
                summary = "保留 LiquidDock 已验证的小组件背景规则；你的逐项选择始终优先",
                enabled = masterEnabled,
            )
        }
        item {
            ArrowPreference(
                "刷新发现结果",
                summary = "重新读取当前 Launcher 会话实际发现的 MAML 元素和 RemoteViews 背景控件",
                enabled = masterEnabled,
                onClick = { refreshGeneration++ },
            )
        }

        if (discovered.isEmpty()) {
            item {
                Text(
                    "尚未发现可配置的小组件背景。先回到桌面显示目标小组件，再返回此页并刷新。",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        } else {
            discovered.forEach { snapshot ->
                val identity = snapshot.identity()
                item {
                    SmallTitle(widgetIdentityTitle(identity))
                    Text(
                        widgetIdentitySummary(identity),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                items(
                    items = snapshot.targets(),
                    key = { target ->
                        WidgetBackgroundDiscoveryCodec.preferenceKey(identity) + ":" +
                                target.kind().name + ":" + target.name()
                    },
                ) { target ->
                    val checked = configuredRules.any { rule ->
                        rule.matches(identity) &&
                                rule.targetKind() == target.kind() &&
                                rule.target() == target.name()
                    }
                    SwitchPreference(
                        checked = checked,
                        onCheckedChange = { selected ->
                            val next = WidgetBackgroundUserRuleCodec.decode(encodedRules).toMutableList()
                            next.removeAll { rule ->
                                rule.matches(identity) &&
                                        rule.targetKind() == target.kind() &&
                                        rule.target() == target.name()
                            }
                            if (selected) {
                                next.add(
                                    WidgetBackgroundUserRule(
                                        identity,
                                        target.kind(),
                                        target.name(),
                                    ),
                                )
                            }
                            val nextEncoded = WidgetBackgroundUserRuleCodec.encode(next)
                            encodedRules = nextEncoded
                            prefs.edit().putString(userRulesKey, nextEncoded).apply()
                        },
                        title = target.name(),
                        summary = widgetTargetSummary(target),
                        enabled = masterEnabled,
                    )
                }
            }
        }
    }
}

private fun loadDiscoveredWidgetBackgrounds(): List<WidgetBackgroundDiscoverySnapshot> {
    val remote = LiquidDockApp.remotePreferences("widget_discovery") ?: return emptyList()
    return remote.all.values
        .asSequence()
        .filterIsInstance<String>()
        .mapNotNull { WidgetBackgroundDiscoveryCodec.decode(it) }
        .filter { it.targets().isNotEmpty() }
        .sortedWith(
            compareByDescending<WidgetBackgroundDiscoverySnapshot> { it.lastSeenMillis() }
                .thenBy { widgetIdentityTitle(it.identity()) },
        )
        .toList()
}

private fun widgetIdentityTitle(identity: WidgetBackgroundIdentity): String {
    return identity.productId?.takeIf { it.isNotBlank() }
        ?: identity.appPackage?.takeIf { it.isNotBlank() }
        ?: identity.type?.takeIf { it.isNotBlank() }
        ?: "Widget"
}

private fun widgetIdentitySummary(identity: WidgetBackgroundIdentity): String {
    val parts = mutableListOf<String>()
    identity.type?.takeIf { it.isNotBlank() }?.let { parts += it }
    identity.appPackage?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (identity.spanX >= 0 && identity.spanY >= 0) {
        parts += "${identity.spanX}×${identity.spanY}"
    }
    if (identity.configSpanX >= 0 && identity.configSpanY >= 0 &&
        (identity.configSpanX != identity.spanX || identity.configSpanY != identity.spanY)
    ) {
        parts += "config ${identity.configSpanX}×${identity.configSpanY}"
    }
    return if (parts.isEmpty()) "Launcher 4.50 实时发现" else parts.joinToString(" · ")
}

private fun widgetTargetSummary(target: WidgetBackgroundDiscoveryTarget): String {
    val kind = when (target.kind()) {
        WidgetBackgroundUserRule.TargetKind.MAML_ELEMENT -> "MAML 元素"
        WidgetBackgroundUserRule.TargetKind.REMOTE_VIEWS_RESOURCE -> "RemoteViews 背景"
    }
    return target.detail().takeIf { it.isNotBlank() }?.let { "$kind · $it" } ?: kind
}
