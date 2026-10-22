package com.hellovoid.liquiddock

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Presentation capability policy for the HyperOS 4 native/AOT Launcher.
 *
 * This is intentionally distinct from binary compatibility: identifying an OS4
 * Launcher means we must hide unsupported pages, not that a native hook can run.
 * Add Launcher pages only after an OS4 backend implementation and device-level
 * behavior verification, never merely after a successful Gradle build.
 */
internal class SettingsAvailability(private val os4Launcher: Boolean) {
    fun allows(page: Page): Boolean = !os4Launcher || page in OS4_AVAILABLE_PAGES

    fun isOs4Launcher(): Boolean = os4Launcher

    companion object {
        // Pages here are app GUI, non-Launcher processes, or mixed hubs with
        // independently guarded Launcher-specific children.
        private val OS4_AVAILABLE_PAGES = setOf(
            Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub,
            Page.ThemePalette, Page.Data, Page.About,
            Page.Liquid, Page.GlassEdge,
            Page.GlassComponents, Page.GlassMenus,
            Page.HandleMenuCustomization, Page.HandleMenuAll, Page.HandleMenuEdge,
            Page.ThirdPartyApps, Page.Gboard, Page.GboardAll, Page.GboardEdge,
            Page.SearchboxEdge, Page.SecurityCenterSidebar, Page.SecurityCenterEdge,
            Page.Animation, Page.AnimationPopups, Page.AnimationSystem, Page.AnimationGui,
        )

        /** Detect the Rust/Dart AOT Launcher family, including unknown OS4 builds. */
        fun detect(context: Context): SettingsAvailability {
            if (Build.VERSION.SDK_INT < 36) return SettingsAvailability(false)
            val isAot = runCatching {
                val info = context.packageManager.getApplicationInfo("com.miui.home", 0)
                val version = context.packageManager
                    .getPackageInfo("com.miui.home", 0).versionName.orEmpty()
                // The research build has hasCode=false and RELEASE-8.* naming.
                // Unknown AOT builds must fail closed in the UI as well.
                (info.flags and ApplicationInfo.FLAG_HAS_CODE) == 0 &&
                    (version.startsWith("RELEASE-8.") || version.isBlank())
            }.getOrDefault(false)
            return SettingsAvailability(isAot)
        }
    }
}

internal val LocalSettingsAvailability = staticCompositionLocalOf { SettingsAvailability(false) }
