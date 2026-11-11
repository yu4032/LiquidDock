package com.hellovoid.liquiddock

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * OS4 GUI release status is not the same as OS4 native-hook eligibility.
 *
 * READY       - independent SystemUI/third-party/GUI behavior or a hub
 * TESTING     - can be opened and configured during the OS4 port, not validated
 * UNAVAILABLE - not being ported yet: visible, grayed out, and non-clickable
 *
 * The TESTING classification deliberately does not authorize native injection.
 * Promote to READY only after OS4 device-level functional acceptance.
 */
internal enum class PagePortState {
    READY, TESTING, UNAVAILABLE,
}

internal class SettingsAvailability(private val os4Launcher: Boolean) {
    fun status(page: Page): PagePortState = when {
        !os4Launcher -> PagePortState.READY
        page in OS4_READY_PAGES -> PagePortState.READY
        page in OS4_TESTING_PAGES -> PagePortState.TESTING
        else -> PagePortState.UNAVAILABLE
    }

    fun allows(page: Page): Boolean = status(page) != PagePortState.UNAVAILABLE
    fun isOs4Launcher(): Boolean = os4Launcher

    companion object {
        // No Launcher AOT hook is counted as production-ready at this stage.
        // Hub pages are accessible because unsupported child destinations are
        // separately classified; independent process hooks need no port.
        private val OS4_READY_PAGES = setOf(
            Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub,
            Page.ThemePalette, Page.Data, Page.About,
            Page.GlassComponents, Page.GlassMenus,
            Page.HandleMenuCustomization, Page.HandleMenuAll, Page.HandleMenuEdge,
            Page.ThirdPartyApps, Page.Gboard, Page.GboardAll, Page.GboardEdge,
            Page.SearchboxEdge, Page.SecurityCenterSidebar, Page.SecurityCenterEdge,
            Page.Animation, Page.AnimationPopups, Page.AnimationSystem, Page.AnimationGui,
        )

        // Available for iterative GUI/port testing, but they are not claims of
        // functional OS4 support. The native bootstrap currently installs no hooks.
        // Keep the set narrow; enroll other pages as their implementation begins.
        private val OS4_TESTING_PAGES = setOf(
            Page.Grid, Page.GridBasics, Page.GridLandscape, Page.GridPortrait,
            Page.Dock, Page.DockBehavior, Page.DockGeometry,
            Page.Liquid, Page.LiquidMaterial, Page.LiquidRefraction,
            Page.LiquidColor, Page.LiquidLighting, Page.LiquidShadow,
            Page.LiquidSampling, Page.GlassEdge,
            Page.GlassIcons, Page.GlassWidgets, Page.WidgetComponents,
        )

        /** Detect the AOT desktop independently of its release and SDK version. */
        fun detect(context: Context): SettingsAvailability {
            val isAot = runCatching {
                val info = context.packageManager.getApplicationInfo("com.miui.home", 0)
                (info.flags and ApplicationInfo.FLAG_HAS_CODE) == 0
            }.getOrDefault(false)
            return SettingsAvailability(isAot)
        }
    }
}

internal val LocalSettingsAvailability = staticCompositionLocalOf { SettingsAvailability(false) }
