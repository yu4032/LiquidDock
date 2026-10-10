package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalHighlightProfile;

/** Immutable, typed runtime configuration. All defaults and unit semantics live here so
 * hooks and renderers do not need to know JSON keys. */
final class LiquidDockConfig {
    final boolean enabled;
    final boolean debugLog;
    final Grid grid;
    final Dock dock;
    final Divider divider;
    final Glass glass;
    final Workstation workstation;
    final Recents recents;
    final Animation animation;

    static LiquidDockConfig load() { return new LiquidDockConfig(ConfigReader.load()); }

    static LiquidDockConfig from(ConfigReader reader) { return new LiquidDockConfig(reader); }

    private LiquidDockConfig(ConfigReader c) {
        enabled = c.b(ConfigSchema.Core.ENABLED.name(),
                ConfigSchema.Core.ENABLED.runtimeFallback());
        debugLog = c.b(ConfigSchema.Debug.LOGGING.name(),
                ConfigSchema.Debug.LOGGING.runtimeFallback());
        grid = new Grid(c);
        dock = new Dock(c);
        divider = new Divider(c);
        glass = new Glass(c);
        workstation = new Workstation(c);
        recents = new Recents(c);
        animation = new Animation(c);
    }

    static final class Animation {
        final int workspaceVisibilityMs, dockIconRevealMs, pressInMs, pressOutMs,
                dockResizeMs, shortcutPopupDismissFadeMs, securityCenterExitFadeMs;

        Animation(ConfigReader c) {
            workspaceVisibilityMs = duration(c, ConfigSchema.Animation.WORKSPACE_VISIBILITY);
            dockIconRevealMs = duration(c, ConfigSchema.Animation.DOCK_ICON_REVEAL);
            pressInMs = duration(c, ConfigSchema.Animation.PRESS_IN);
            pressOutMs = duration(c, ConfigSchema.Animation.PRESS_OUT);
            dockResizeMs = duration(c, ConfigSchema.Animation.DOCK_RESIZE);
            shortcutPopupDismissFadeMs =
                    duration(c, ConfigSchema.Animation.SHORTCUT_POPUP_DISMISS_FADE);
            securityCenterExitFadeMs =
                    duration(c, ConfigSchema.Animation.SECURITY_CENTER_EXIT_FADE);
        }

        private static int duration(ConfigReader c,
                com.hellovoid.liquiddock.config.ConfigKey<Integer> key) {
            return Math.max(0, Math.min(2000, c.i(key.name(), key.runtimeFallback())));
        }
    }

    static final class Recents {
        final int backgroundBlurPercent;
        final boolean disableWallpaperDimming;

        Recents(ConfigReader c) {
            backgroundBlurPercent = Math.max(0, Math.min(100, c.i(
                    ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.name(),
                    ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.runtimeFallback())));
            disableWallpaperDimming = c.b(
                    ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.name(),
                    ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.runtimeFallback());
        }
    }

    static final class Grid {
        final boolean enabled, iconSizeEnabled, widgetHorizontalStretch;
        final int columns, rows, iconSizePercent;
        final float landscapeHorizontal, landscapeTop, landscapeBottom, landscapeRowGap;
        final float portraitHorizontal, portraitTop, portraitBottom, portraitRowGap;
        final float splitHorizontalOffset;
        final float landscapeIndicatorY, portraitIndicatorY;

        Grid(ConfigReader c) {
            enabled = c.b(ConfigSchema.Grid.ENABLED.name(),
                    ConfigSchema.Grid.ENABLED.runtimeFallback());
            columns = Math.max(2, Math.min(10, c.i(
                    ConfigSchema.Grid.COLUMNS.name(),
                    ConfigSchema.Grid.COLUMNS.runtimeFallback())));
            rows = Math.max(2, Math.min(6, c.i(
                    ConfigSchema.Grid.ROWS.name(),
                    ConfigSchema.Grid.ROWS.runtimeFallback())));
            iconSizeEnabled = c.b(ConfigSchema.Grid.ICON_SIZE_ENABLED.name(),
                    ConfigSchema.Grid.ICON_SIZE_ENABLED.runtimeFallback());
            iconSizePercent = Math.max(Launcher450IconSizePolicy.MIN_PERCENT,
                    Math.min(Launcher450IconSizePolicy.MAX_PERCENT, c.i(
                            ConfigSchema.Grid.ICON_SIZE_PERCENT.name(),
                            ConfigSchema.Grid.ICON_SIZE_PERCENT.runtimeFallback())));
            widgetHorizontalStretch = c.b(
                    ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH.name(),
                    ConfigSchema.Grid.WIDGET_HORIZONTAL_STRETCH.runtimeFallback());
            landscapeHorizontal = c.f(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE.name(),
                    ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE.runtimeFallback());
            landscapeTop = c.f(ConfigSchema.Grid.LANDSCAPE_TOP_DISTANCE.name(),
                    ConfigSchema.Grid.LANDSCAPE_TOP_DISTANCE.runtimeFallback());
            landscapeBottom = c.f(ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE.name(),
                    ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE.runtimeFallback());
            portraitHorizontal = c.f(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE.name(),
                    ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE.runtimeFallback());
            portraitTop = c.f(ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE.name(),
                    ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE.runtimeFallback());
            portraitBottom = c.f(ConfigSchema.Grid.PORTRAIT_BOTTOM_DISTANCE.name(),
                    ConfigSchema.Grid.PORTRAIT_BOTTOM_DISTANCE.runtimeFallback());
            splitHorizontalOffset = c.f(ConfigSchema.Grid.SPLIT_HORIZONTAL_OFFSET.name(),
                    ConfigSchema.Grid.SPLIT_HORIZONTAL_OFFSET.runtimeFallback());
            landscapeRowGap = c.f(ConfigSchema.Grid.LANDSCAPE_ROW_GAP.name(), 0f);
            portraitRowGap = c.f(ConfigSchema.Grid.PORTRAIT_ROW_GAP.name(), 0f);
            landscapeIndicatorY = c.f(ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y.name(),
                    ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y.runtimeFallback());
            portraitIndicatorY = c.f(ConfigSchema.Grid.PORTRAIT_INDICATOR_Y.name(),
                    ConfigSchema.Grid.PORTRAIT_INDICATOR_Y.runtimeFallback());
        }
    }

    static final class Dock {
        final boolean enabled, resizeAnimation, smoothResizeAnimation, dimensionsDp;
        final float widthOffset, heightOffset, spacing, bottomOffset;
        final int blurRadius;
        final boolean cornersDp, squircle, fillDiff, strokeEnabled, strokeShadow, shadowEnabled;
        final float cornerOffset, blurCornerOffset, squircleCp, squircleStrokeWidth,
                squircleStrokeOffset, strokeWidth, standardStrokeWidth;
        final int strokeR, strokeG, strokeB, strokeAlpha;
        final float strokeShadowRadius, shadowRadius, shadowSize, shadowY;
        final int strokeShadowAlpha, shadowAlpha;

        Dock(ConfigReader c) {
            enabled = c.b(ConfigSchema.Dock.ENABLED.name(),
                    ConfigSchema.Dock.ENABLED.runtimeFallback());
            resizeAnimation = c.b(ConfigSchema.Dock.RESIZE_ANIMATION.name(),
                    ConfigSchema.Dock.RESIZE_ANIMATION.runtimeFallback());
            smoothResizeAnimation = c.b(ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.name(),
                    ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.runtimeFallback());
            dimensionsDp = true;
            widthOffset = c.f(ConfigSchema.Dock.WIDTH_OFFSET.name(),
                    ConfigSchema.Dock.WIDTH_OFFSET.runtimeFallback());
            heightOffset = c.f(ConfigSchema.Dock.HEIGHT_OFFSET.name(),
                    ConfigSchema.Dock.HEIGHT_OFFSET.runtimeFallback());
            spacing = c.f(ConfigSchema.Dock.SPACING.name(),
                    ConfigSchema.Dock.SPACING.runtimeFallback());
            bottomOffset = c.f(ConfigSchema.Dock.BOTTOM_OFFSET.name(),
                    ConfigSchema.Dock.BOTTOM_OFFSET.runtimeFallback());
            blurRadius = c.i(ConfigSchema.Dock.BLUR_RADIUS.name(),
                    ConfigSchema.Dock.BLUR_RADIUS.runtimeFallback());
            cornersDp = true;
            cornerOffset = c.f(ConfigSchema.Dock.CORNER_OFFSET.name(),
                    ConfigSchema.Dock.CORNER_OFFSET.runtimeFallback());
            blurCornerOffset = c.f(ConfigSchema.Dock.BLUR_CORNER_OFFSET.name(),
                    ConfigSchema.Dock.BLUR_CORNER_OFFSET.runtimeFallback());
            squircle = c.b(ConfigSchema.Dock.SQUIRCLE.name(),
                    ConfigSchema.Dock.SQUIRCLE.runtimeFallback());
            fillDiff = c.b(ConfigSchema.Dock.FILL_DIFF.name(),
                    ConfigSchema.Dock.FILL_DIFF.runtimeFallback());
            strokeEnabled = c.b(ConfigSchema.Dock.STROKE_ENABLED.name(),
                    ConfigSchema.Dock.STROKE_ENABLED.runtimeFallback());
            squircleCp = c.i(ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT.name(),
                    ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT.runtimeFallback()) / 100f;
            squircleStrokeWidth = c.f(ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.name(),
                    ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.runtimeFallback());
            squircleStrokeOffset = c.f(ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET.name(),
                    ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET.runtimeFallback());
            strokeWidth = c.f(ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH.name(),
                    ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH.runtimeFallback());
            standardStrokeWidth = c.f(ConfigSchema.Dock.STANDARD_STROKE_WIDTH.name(),
                    ConfigSchema.Dock.STANDARD_STROKE_WIDTH.runtimeFallback());
            strokeR = channel(c.i(ConfigSchema.Dock.STROKE_RED.name(),
                    ConfigSchema.Dock.STROKE_RED.runtimeFallback()));
            strokeG = channel(c.i(ConfigSchema.Dock.STROKE_GREEN.name(),
                    ConfigSchema.Dock.STROKE_GREEN.runtimeFallback()));
            strokeB = channel(c.i(ConfigSchema.Dock.STROKE_BLUE.name(),
                    ConfigSchema.Dock.STROKE_BLUE.runtimeFallback()));
            strokeAlpha = channel(c.i(ConfigSchema.Dock.STROKE_ALPHA.name(),
                    ConfigSchema.Dock.STROKE_ALPHA.runtimeFallback()));
            strokeShadow = c.b(ConfigSchema.Dock.STROKE_SHADOW.name(),
                    ConfigSchema.Dock.STROKE_SHADOW.runtimeFallback());
            strokeShadowRadius = c.f(ConfigSchema.Dock.STROKE_SHADOW_RADIUS.name(),
                    ConfigSchema.Dock.STROKE_SHADOW_RADIUS.runtimeFallback());
            strokeShadowAlpha = channel(c.i(ConfigSchema.Dock.STROKE_SHADOW_ALPHA.name(),
                    ConfigSchema.Dock.STROKE_SHADOW_ALPHA.runtimeFallback()));
            shadowEnabled = c.b(ConfigSchema.Dock.SHADOW_ENABLED.name(),
                    ConfigSchema.Dock.SHADOW_ENABLED.runtimeFallback());
            shadowRadius = c.f(ConfigSchema.Dock.SHADOW_RADIUS.name(),
                    ConfigSchema.Dock.SHADOW_RADIUS.runtimeFallback());
            shadowSize = c.f(ConfigSchema.Dock.SHADOW_SIZE.name(),
                    ConfigSchema.Dock.SHADOW_SIZE.runtimeFallback());
            shadowAlpha = channel(c.i(ConfigSchema.Dock.SHADOW_ALPHA.name(),
                    ConfigSchema.Dock.SHADOW_ALPHA.runtimeFallback()));
            shadowY = c.f(ConfigSchema.Dock.SHADOW_Y.name(),
                    ConfigSchema.Dock.SHADOW_Y.runtimeFallback());
        }
    }

    /** Divider customization is independent from Dock geometry and unit switches. */
    static final class Divider {
        final boolean enabled;
        final float widthDp, heightPercent, yOffsetDp;
        final int colorR, colorG, colorB, alpha;

        Divider(ConfigReader c) {
            enabled = c.b(ConfigSchema.Divider.ENABLED.name(),
                    ConfigSchema.Divider.ENABLED.uiDefault());
            // The current divider keys store width and Y in raw tenths of dp.
            widthDp = Math.max(0f, c.f(ConfigSchema.Divider.WIDTH_DP.name(), 10f) / 10f);
            heightPercent = clamp(c.f(ConfigSchema.Divider.HEIGHT_SCALE.name(), 60f), 0f, 100f);
            yOffsetDp = c.f(ConfigSchema.Divider.Y_OFFSET_DP.name(), 0f) / 10f;
            colorR = channel(c.i(ConfigSchema.Divider.COLOR_RED.name(), 255));
            colorG = channel(c.i(ConfigSchema.Divider.COLOR_GREEN.name(), 255));
            colorB = channel(c.i(ConfigSchema.Divider.COLOR_BLUE.name(), 255));
            alpha = channel(c.i(ConfigSchema.Divider.ALPHA.name(), 128));
        }
    }

    static final class Glass {
        final boolean enabled, securityCenterEnabled, systemUiHandleMenuEnabled,
                folderEnabled, widgetEnabled, widgetDarkContent, iconEnabled,
                functionalDockIconEnabled, recentsCapsuleEnabled;
        final GlassComponentStyle iconStyle;
        final GlassComponentStyle widgetStyle;
        final GlassComponentStyle smallFolderStyle;
        final GlassComponentStyle largeFolderStyle;
        final boolean prismalShowNormals;
        final PrismalHighlightProfile launcherHighlightProfile, largeSurfaceHighlightProfile;
        final float blur, chromatic, thickness, ior, normalStrength, dome,
        lensRefraction, depthEffect, highlightWidth, brightness,
        specularStrength, rimLight, caustics;
        final float prismalRefractionInset, prismalDisplacementScale, prismalHeightTransitionWidth,
                prismalSminSmoothing, prismalEdgeRefractionFalloff, prismalFresnelReflect,
                prismalDispersionR, prismalDispersionB, prismalVibrancy, prismalPlainHighlight,
                os4EdgeWidthPx, os4ReflectOffsetPx, os4ReflectionStrength, os4ReflectionLighten,
                os4DirectionalAngleRange, os4DirectionalIntensity,
                os4DirectionalOppositeIntensity,
                prismalLightDirX, prismalLightDirY, prismalShadowSoftness, prismalTransmittance,
                prismalBackdropScaleX, prismalBackdropScaleY, prismalParallaxScale;
        final int tintAlpha, tintR, tintG, tintB, specularSharp,
                prismalShadowR, prismalShadowG, prismalShadowB, prismalShadowAlpha;
        final int passBlurCaptureScalePercent, passBlurRenderFps;

        Glass(ConfigReader c) {
            enabled = c.b(ConfigSchema.Glass.ENABLED.name(),
                    ConfigSchema.Glass.ENABLED.runtimeFallback());
            securityCenterEnabled = c.b(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(),
                    ConfigSchema.Glass.SECURITY_CENTER_GLASS.runtimeFallback());
            systemUiHandleMenuEnabled = c.b(
                    ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.name(),
                    ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.runtimeFallback());
            boolean resolvedIconEnabled = c.b(ConfigSchema.Glass.ICON_GLASS.name(),
                    ConfigSchema.Glass.ICON_GLASS.runtimeFallback());
            functionalDockIconEnabled = c.b(
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.runtimeFallback());
            recentsCapsuleEnabled = c.b(
                    ConfigSchema.Glass.RECENTS_CAPSULE_GLASS.name(),
                    ConfigSchema.Glass.RECENTS_CAPSULE_GLASS.runtimeFallback());
            boolean resolvedWidgetEnabled = c.b(ConfigSchema.Glass.WIDGET_GLASS.name(),
                    ConfigSchema.Glass.WIDGET_GLASS.runtimeFallback());
            widgetDarkContent = c.b(ConfigSchema.Glass.WIDGET_DARK_CONTENT.name(),
                    ConfigSchema.Glass.WIDGET_DARK_CONTENT.runtimeFallback());
            boolean resolvedSmallEnabled = c.b(ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(),
                    ConfigSchema.Glass.SMALL_FOLDER_GLASS.runtimeFallback());
            boolean resolvedLargeEnabled = c.b(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_GLASS.runtimeFallback());
            float smallRadius = c.f(ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS.name(),
                    ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS.runtimeFallback());
            float largeRadius = c.f(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS.runtimeFallback());
            iconStyle = new GlassComponentStyle(
                    resolvedIconEnabled || functionalDockIconEnabled,
                    c.f(ConfigSchema.Glass.ICON_SIZE_OFFSET.name(), 0f),
                    c.f(ConfigSchema.Glass.ICON_CORNER_RADIUS.name(), 0f));
            widgetStyle = new GlassComponentStyle(resolvedWidgetEnabled,
                    c.f(ConfigSchema.Glass.WIDGET_SIZE_OFFSET.name(), 0f),
                    c.f(ConfigSchema.Glass.WIDGET_CORNER_RADIUS.name(), 0f));
            smallFolderStyle = new GlassComponentStyle(resolvedSmallEnabled,
                    c.f(ConfigSchema.Glass.SMALL_FOLDER_SIZE_OFFSET.name(), 0f), smallRadius);
            largeFolderStyle = new GlassComponentStyle(resolvedLargeEnabled,
                    c.f(ConfigSchema.Glass.LARGE_FOLDER_SIZE_OFFSET.name(), 0f), largeRadius);
            iconEnabled = resolvedIconEnabled;
            widgetEnabled = widgetStyle.enabled;
            folderEnabled = smallFolderStyle.enabled || largeFolderStyle.enabled;
            launcherHighlightProfile = LauncherHighlightPreferences.read(c);
            largeSurfaceHighlightProfile = LauncherHighlightPreferences.readLargeSurfaces(c);
            blur = c.f(ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.runtimeFallback());
            passBlurCaptureScalePercent = clamp(c.i(
                    ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE.name(),
                    ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE.runtimeFallback()),
                    ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE.minInt(),
                    ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE.maxInt());
            passBlurRenderFps = clamp(c.i(
                    ConfigSchema.Glass.PASSBLUR_RENDER_FPS.name(),
                    ConfigSchema.Glass.PASSBLUR_RENDER_FPS.runtimeFallback()),
                    ConfigSchema.Glass.PASSBLUR_RENDER_FPS.minInt(),
                    ConfigSchema.Glass.PASSBLUR_RENDER_FPS.maxInt());
            // Upstream Prismal uses the human-facing chromatic magnitude directly (for example 8).
            chromatic = c.i(ConfigSchema.Glass.CHROMATIC.name(),
                    ConfigSchema.Glass.CHROMATIC.runtimeFallback());
            tintAlpha = channel(c.i(ConfigSchema.Glass.TINT_ALPHA.name(),
                    ConfigSchema.Glass.TINT_ALPHA.runtimeFallback()));
            thickness = c.f(ConfigSchema.Glass.THICKNESS.name(),
                    ConfigSchema.Glass.THICKNESS.runtimeFallback());
            ior = c.i(ConfigSchema.Glass.IOR.name(), ConfigSchema.Glass.IOR.runtimeFallback()) / 100f;
            normalStrength = c.i(ConfigSchema.Glass.NORMAL_STRENGTH.name(),
                    ConfigSchema.Glass.NORMAL_STRENGTH.runtimeFallback()) / 100f;
            dome = c.i(ConfigSchema.Glass.DOME.name(), ConfigSchema.Glass.DOME.runtimeFallback()) / 100f;
            lensRefraction = clamp(c.f(ConfigSchema.Glass.LENS_REFRACTION.name(), 1.3f),
                    ConfigSchema.Glass.LENS_REFRACTION.minInt(),
                    ConfigSchema.Glass.LENS_REFRACTION.maxInt());
    depthEffect = c.i(ConfigSchema.Glass.DEPTH_EFFECT.name(),
            ConfigSchema.Glass.DEPTH_EFFECT.runtimeFallback()) / 100f;
    highlightWidth = c.i(ConfigSchema.Glass.HIGHLIGHT_WIDTH.name(),
                    ConfigSchema.Glass.HIGHLIGHT_WIDTH.runtimeFallback()) / 100f;
            tintR = channel(c.i(ConfigSchema.Glass.TINT_RED.name(),
                    ConfigSchema.Glass.TINT_RED.runtimeFallback()));
            tintG = channel(c.i(ConfigSchema.Glass.TINT_GREEN.name(),
                    ConfigSchema.Glass.TINT_GREEN.runtimeFallback()));
            tintB = channel(c.i(ConfigSchema.Glass.TINT_BLUE.name(),
                    ConfigSchema.Glass.TINT_BLUE.runtimeFallback()));
            brightness = c.i(ConfigSchema.Glass.BRIGHTNESS.name(),
                    ConfigSchema.Glass.BRIGHTNESS.runtimeFallback()) / 100f;
            specularSharp = Math.max(1, c.i(ConfigSchema.Glass.SPECULAR_SHARPNESS.name(),
                    ConfigSchema.Glass.SPECULAR_SHARPNESS.runtimeFallback()));
            specularStrength = c.i(ConfigSchema.Glass.SPECULAR_STRENGTH.name(),
                    ConfigSchema.Glass.SPECULAR_STRENGTH.runtimeFallback()) / 100f;
            rimLight = c.i(ConfigSchema.Glass.RIM_LIGHT.name(),
                    ConfigSchema.Glass.RIM_LIGHT.runtimeFallback()) / 100f;
            caustics = c.i(ConfigSchema.Glass.CAUSTICS.name(),
                    ConfigSchema.Glass.CAUSTICS.runtimeFallback()) / 100f;

            prismalRefractionInset = c.f(ConfigSchema.Glass.PRISMAL_REFRACTION_INSET.name(),
                    ConfigSchema.Glass.PRISMAL_REFRACTION_INSET.runtimeFallback());
            prismalDisplacementScale = c.i(ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE.name(),
                    ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE.runtimeFallback()) / 100f;
            prismalHeightTransitionWidth = c.f(ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH.name(),
                    ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH.runtimeFallback());
            prismalSminSmoothing = c.f(ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING.name(), 1.8f);
            prismalEdgeRefractionFalloff = c.i(ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF.name(),
                    ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF.runtimeFallback()) / 100f;
            prismalFresnelReflect = c.i(ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT.name(),
                    ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT.runtimeFallback()) / 100f;
            prismalDispersionR = c.i(ConfigSchema.Glass.PRISMAL_DISPERSION_R.name(),
                    ConfigSchema.Glass.PRISMAL_DISPERSION_R.runtimeFallback()) / 100f;
            prismalDispersionB = c.i(ConfigSchema.Glass.PRISMAL_DISPERSION_B.name(),
                    ConfigSchema.Glass.PRISMAL_DISPERSION_B.runtimeFallback()) / 100f;
            prismalVibrancy = c.i(ConfigSchema.Glass.PRISMAL_VIBRANCY.name(),
                    ConfigSchema.Glass.PRISMAL_VIBRANCY.runtimeFallback()) / 100f;
            prismalPlainHighlight = c.i(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.name(),
                    ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.runtimeFallback()) / 100f;
            os4EdgeWidthPx = clamp(c.i(ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.name(),
                    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.runtimeFallback()),
                    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.minInt(),
                    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.maxInt());
            os4ReflectOffsetPx = clamp(c.i(ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.name(),
                    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.runtimeFallback()),
                    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.minInt(),
                    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.maxInt());
            os4ReflectionStrength = clamp(c.i(ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.name(),
                    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.runtimeFallback()),
                    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.minInt(),
                    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.maxInt()) / 100f;
            os4ReflectionLighten = clamp(c.i(ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.name(),
                    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.runtimeFallback()),
                    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.minInt(),
                    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.maxInt()) / 100f;
            os4DirectionalAngleRange = clamp(c.i(
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.runtimeFallback()),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.minInt(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.maxInt()) / 100f;
            os4DirectionalIntensity = clamp(c.i(ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.runtimeFallback()),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.minInt(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.maxInt()) / 100f;
            os4DirectionalOppositeIntensity = clamp(c.i(
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.runtimeFallback()),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.minInt(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.maxInt()) / 100f;
            prismalLightDirX = c.i(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X.name(),
                    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X.runtimeFallback()) / 100f;
            prismalLightDirY = c.i(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y.name(),
                    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y.runtimeFallback()) / 100f;
            prismalShadowR = channel(c.i(ConfigSchema.Glass.PRISMAL_SHADOW_RED.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_RED.runtimeFallback()));
            prismalShadowG = channel(c.i(ConfigSchema.Glass.PRISMAL_SHADOW_GREEN.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_GREEN.runtimeFallback()));
            prismalShadowB = channel(c.i(ConfigSchema.Glass.PRISMAL_SHADOW_BLUE.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_BLUE.runtimeFallback()));
            prismalShadowAlpha = channel(c.i(ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA.runtimeFallback()));
            prismalShadowSoftness = c.i(ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS.runtimeFallback()) / 100f;
            prismalTransmittance = c.i(ConfigSchema.Glass.PRISMAL_TRANSMITTANCE.name(),
                    ConfigSchema.Glass.PRISMAL_TRANSMITTANCE.runtimeFallback()) / 100f;
            prismalBackdropScaleX = c.i(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X.name(),
                    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X.runtimeFallback()) / 100f;
            prismalBackdropScaleY = c.i(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y.name(),
                    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y.runtimeFallback()) / 100f;
            prismalParallaxScale = c.i(ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE.name(),
                    ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE.runtimeFallback()) / 100f;
            prismalShowNormals = c.b(ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.name(),
                    ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.runtimeFallback());
        }
    }

    static final class Workstation {
        final boolean dockEnabled, dimensionsDp;
        final float dockWidthOffset, dockIconGlassCornerRadius, gridHorizontalOffset;
        final float allAppsLandscapeHorizontalOffset;
        final float allAppsLandscapeTopSpacing, allAppsLandscapeBottomSpacing;
        final float allAppsPortraitHorizontalOffset;
        final float allAppsPortraitTopSpacing, allAppsPortraitBottomSpacing;
        final float iconTopOffset, iconBottomOffset;

        Workstation(ConfigReader c) {
            dockEnabled = c.b(ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name(),
                    ConfigSchema.Workstation.DOCK_CUSTOMIZATION.runtimeFallback());
            dimensionsDp = true;
            dockWidthOffset = c.f(ConfigSchema.Workstation.DOCK_WIDTH_OFFSET.name(),
                    ConfigSchema.Workstation.DOCK_WIDTH_OFFSET.runtimeFallback());
            dockIconGlassCornerRadius = c.f(
                    ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS.name(),
                    ConfigSchema.Workstation.DOCK_ICON_GLASS_CORNER_RADIUS.runtimeFallback());
            gridHorizontalOffset = c.f(ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET.name(),
                    ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET.runtimeFallback());
            allAppsLandscapeHorizontalOffset = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET.name(),
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET.runtimeFallback());
            allAppsLandscapeTopSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING.name(),
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING.runtimeFallback());
            allAppsLandscapeBottomSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING.name(),
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING.runtimeFallback());
            allAppsPortraitHorizontalOffset = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET.name(),
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET.runtimeFallback());
            allAppsPortraitTopSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING.name(),
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING.runtimeFallback());
            allAppsPortraitBottomSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING.name(),
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING.runtimeFallback());
            iconTopOffset = c.f(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET.name(),
                    ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET.runtimeFallback());
            iconBottomOffset = c.f(ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET.name(),
                    ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET.runtimeFallback());
        }
    }

    private static int channel(int value) { return clamp(value, 0, 255); }
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
