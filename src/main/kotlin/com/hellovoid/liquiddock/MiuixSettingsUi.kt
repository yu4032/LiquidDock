package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.PrismalGlassSurface
import com.styropyr0.prismal.drawPlainPrismalGlass
import com.styropyr0.prismal.components.PrismalGlassBottomTab
import com.styropyr0.prismal.components.PrismalGlassBottomTabs
import com.styropyr0.prismal.components.PrismalGlassToggle
import com.styropyr0.prismal.components.PrismalGlassSlider
import com.styropyr0.prismal.effects.colorControls
import com.styropyr0.prismal.effects.prismalBlur
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalMergedSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val LiquidDockPreferenceMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

private val LocalSettingsScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }
private val LocalPrismalSurfaceBackdrop = staticCompositionLocalOf<PrismalBackdrop?> { null }

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
    bottomBar: @Composable (PrismalBackdrop) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backgroundLayer = rememberPrismalGlassLayer()
    val screenLayer = rememberPrismalGlassLayer()
    val overlayBackdrop = rememberPrismalMergedSource(backgroundLayer, screenLayer)
    val background = MiuixTheme.colorScheme.background
    val primary = MiuixTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            background,
                            primary.copy(alpha = 0.08f),
                            background,
                        ),
                    ),
                )
                .prismalGlassLayer(backgroundLayer),
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                val density = LocalDensity.current
                val collapsed by remember(scrollBehavior) {
                    derivedStateOf { scrollBehavior.state.collapsedFraction >= (1f / 3f) }
                }
                val inlineExpandedActions = !showBack
                Box(modifier = Modifier.fillMaxWidth()) {
                    // The blur itself is rendered separately from TopAppBar content, then alpha-masked
                    // from opaque at the status-bar edge to transparent at the lower edge.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Black,
                                            0.58f to Color.Black,
                                            0.82f to Color.Black.copy(alpha = 0.42f),
                                            1f to Color.Transparent,
                                        ),
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                            .drawPlainPrismalGlass(
                                backdrop = overlayBackdrop,
                                shape = { PrismalRoundedRectangle(0.dp) },
                                effects = {
                                    prismalBlur(with(density) { 14.dp.toPx() })
                                    colorControls(saturation = 1.18f)
                                },
                                onDrawSurface = {
                                    drawRect(MiuixTheme.colorScheme.surface.copy(alpha = 0.30f))
                                },
                            ),
                    )

                    TopAppBar(
                        title = title,
                        largeTitle = if (inlineExpandedActions) " " else title,
                        color = Color.Transparent,
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
                        actions = {
                            if (!inlineExpandedActions || collapsed) {
                                actions()
                            }
                        },
                    )

                    if (inlineExpandedActions && !collapsed) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                color = MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.title1,
                                maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                content = actions,
                            )
                        }
                    }
                }
            },
            bottomBar = { bottomBar(overlayBackdrop) },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .prismalGlassLayer(screenLayer),
            ) {
                CompositionLocalProvider(
                    LocalSettingsScrollBehavior provides scrollBehavior,
                    LocalPrismalSurfaceBackdrop provides backgroundLayer,
                ) {
                    content(padding)
                }
            }
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current
    if (backdrop == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content,
        )
        return
    }

    PrismalGlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = { PrismalRoundedRectangle(24.dp) },
        onClick = onClick,
        blurRadius = 12.dp,
        tint = MiuixTheme.colorScheme.surface,
        tintAlpha = 0.26f,
        saturation = 1.4f,
        refractionHeightPx = 16f,
        refractionAmountPx = 22f,
        chromaticAberration = 0.35f,
        depthEffect = true,
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

@Composable
internal fun LiquidDockGlassToggleRow(
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        insideMargin = LiquidDockPreferenceMargin,
        endActions = {
            if (backdrop != null) {
                PrismalGlassToggle(
                    selected = { checked },
                    onSelect = { next ->
                        if (enabled) onCheckedChange(next)
                    },
                    backdrop = backdrop,
                )
            }
        },
        onClick = {
            if (enabled) onCheckedChange(!checked)
        },
    )
}

@Composable
internal fun LiquidDockGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalPrismalSurfaceBackdrop.current ?: return
    PrismalGlassSlider(
        value = { value },
        onValueChange = { next ->
            if (enabled) onValueChange(next)
        },
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        backdrop = backdrop,
        modifier = modifier.alpha(if (enabled) 1f else 0.42f),
    )
}

private class BottomTabSelectionGate {
    var lastUserDispatchAt: Long = 0L
    var programmaticTarget: Int? = null
    var clearJob: Job? = null
}

@Composable
internal fun LiquidDockGlassNavigationBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    labels: List<String>,
    icons: List<ImageVector>,
    backdrop: PrismalBackdrop,
    modifier: Modifier = Modifier,
) {
    val selectedIndexState = rememberUpdatedState(selectedIndex)
    val onSelectedState = rememberUpdatedState(onSelected)
    val selectedIndexProvider = remember { { selectedIndexState.value } }
    val gate = remember { BottomTabSelectionGate() }
    val scope = rememberCoroutineScope()

    val dispatchUserSelection = remember {
        { index: Int ->
            val now = android.os.SystemClock.uptimeMillis()
            if (
                index != selectedIndexState.value &&
                now - gate.lastUserDispatchAt >= 140L
            ) {
                gate.lastUserDispatchAt = now
                gate.programmaticTarget = index
                onSelectedState.value(index)
                gate.clearJob?.cancel()
                gate.clearJob = scope.launch {
                    delay(360L)
                    if (gate.programmaticTarget == index) {
                        gate.programmaticTarget = null
                    }
                }
            }
        }
    }
    val dispatchPrismalSelection = remember {
        { index: Int ->
            // External clicks already moved the authoritative selection. Ignore Prismal's
            // completion callback for that same animation so it cannot recursively requeue Pager.
            if (
                gate.programmaticTarget == null &&
                index != selectedIndexState.value
            ) {
                onSelectedState.value(index)
            }
        }
    }

    Box(modifier = modifier) {
        PrismalGlassBottomTabs(
            selectedTabIndex = selectedIndexProvider,
            onTabSelected = dispatchPrismalSelection,
            backdrop = backdrop,
            tabsCount = labels.size,
            modifier = Modifier.fillMaxWidth(),
            tintDropletContent = false,
            dropletContentTint = MiuixTheme.colorScheme.primary,
        ) {
            // Keep Prismal's hit targets and drag/spring machinery, but intentionally leave
            // the sampled tab content empty. Upstream records content a second time into
            // tabsBackdrop; drawing labels there causes a refracted duplicate during long press.
            labels.indices.forEach { index ->
                PrismalGlassBottomTab(
                    onClick = { dispatchUserSelection(index) },
                ) {}
            }
        }

        // Draw labels/icons exactly once, above Prismal's glass layers. With no pointer modifier
        // this visual overlay does not steal click/drag events from the empty Prismal tab targets.
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val selected = selectedIndex == index
                val contentColor = if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantActions
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = icons[index],
                        contentDescription = label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = label,
                        color = contentColor,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
