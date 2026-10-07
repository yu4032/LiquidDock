package com.hellovoid.liquiddock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import com.styropyr0.prismal.PrismalBackdrop
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

internal val ModernPreferenceMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

private val LocalModernScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }

@Composable
internal fun ModernSettingsScaffold(
    title: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (PrismalBackdrop) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val topBarTint = surfaceColor.copy(alpha = 0.88f)
    val miuixBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val prismalBackdrop = rememberPrismalGlassLayer()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier.textureBlur(
                    backdrop = miuixBackdrop,
                    shape = RectangleShape,
                    blurRadius = 22f,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(color = topBarTint)),
                    ),
                ),
            ) {
                TopAppBar(
                    title = title,
                    largeTitle = title,
                    color = Color.Transparent,
                    scrollBehavior = scrollBehavior,
                    titlePadding = 20.dp,
                    navigationIcon = {
                        if (showBack) ModernBackButton(onBack)
                    },
                    actions = actions,
                )
            }
        },
        bottomBar = { bottomBar(prismalBackdrop) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(miuixBackdrop)
                .prismalGlassLayer(prismalBackdrop),
        ) {
            CompositionLocalProvider(LocalModernScrollBehavior provides scrollBehavior) {
                content(padding)
            }
        }
    }
}

@Composable
internal fun ModernSettingsPage(
    padding: PaddingValues,
    overlayBottomBar: Boolean = false,
    bottomExtra: Dp = 28.dp,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = LocalModernScrollBehavior.current
    val bottomPadding = if (overlayBottomBar) {
        bottomExtra
    } else {
        padding.calculateBottomPadding() + bottomExtra
    }
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
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun ModernPageIntro(summary: String?) {
    if (summary.isNullOrBlank()) return
    Text(
        text = summary,
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
internal fun ModernSectionTitle(title: String) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 2.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
internal fun ModernSectionCard(
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
internal fun ModernBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = null,
        )
    }
}

@Composable
internal fun ModernFeatureTile(
    title: String,
    summary: String,
    icon: ImageVector,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconShape = RoundedCornerShape(14.dp)
    Card(
        modifier = modifier,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 14.dp else 17.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                        iconShape,
                    )
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(text = title, fontSize = 17.sp)
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = if (compact) 2 else 3,
                )
            }
        }
    }
}
