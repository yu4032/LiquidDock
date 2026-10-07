package com.hellovoid.liquiddock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.overScrollVertical

internal val LiquidDockPreferenceMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

private val LocalSettingsScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }

@Composable
internal fun LiquidDockTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
    MiuixTheme(controller = controller, content = content)
}

@Composable
internal fun LiquidDockSettingsScaffold(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                titlePadding = 20.dp,
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        CompositionLocalProvider(LocalSettingsScrollBehavior provides scrollBehavior) {
            content(padding)
        }
    }
}

@Composable
internal fun LiquidDockSettingsPage(
    padding: PaddingValues,
    extraBottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = LocalSettingsScrollBehavior.current
    var modifier = Modifier
        .fillMaxSize()
        .overScrollVertical()
    if (scrollBehavior != null) {
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + extraBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun LiquidDockSectionTitle(title: String) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 4.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
internal fun LiquidDockSectionCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = content)
    }
}

@Composable
internal fun LiquidDockActionRow(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(22.dp),
            )
        },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
            )
        },
        insideMargin = LiquidDockPreferenceMargin,
        onClick = onClick,
    )
}
