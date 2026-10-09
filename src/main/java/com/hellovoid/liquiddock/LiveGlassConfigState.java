package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * API101 Remote Preferences -> frame-coalesced, process-local material configuration.
 * Never reinstalls a Hook, rebinds an EGL context or recreates a PassBlur producer.
 * Only an already-installed renderer is updated; newly acquired sessions read currentGlass().
 */
final class LiveGlassConfigState {
    private static final String TAG = "[DC][LiveConfig]";
    private static Handler mainHandler;
    private static final AtomicBoolean FRAME_PENDING = new AtomicBoolean();
    private static final Set<String> GLASS_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                    ConfigSchema.Glass.ENABLED.name(),
                    ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(),
                    ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.name(),
                    ConfigSchema.Glass.ICON_GLASS.name(),
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),
                    ConfigSchema.Glass.RECENTS_CAPSULE_GLASS.name(),
                    ConfigSchema.Glass.WIDGET_GLASS.name(),
                    ConfigSchema.Glass.WIDGET_DARK_CONTENT.name(),
                    ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(),
                    ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS.name(),
                    ConfigSchema.Glass.ICON_SIZE_OFFSET.name(),
                    ConfigSchema.Glass.ICON_CORNER_RADIUS.name(),
                    ConfigSchema.Glass.WIDGET_SIZE_OFFSET.name(),
                    ConfigSchema.Glass.WIDGET_CORNER_RADIUS.name(),
                    ConfigSchema.Glass.SMALL_FOLDER_SIZE_OFFSET.name(),
                    ConfigSchema.Glass.LARGE_FOLDER_SIZE_OFFSET.name(),
                    ConfigSchema.Glass.BLUR.name(),
                    ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE.name(),
                    ConfigSchema.Glass.PASSBLUR_RENDER_FPS.name(),
                    ConfigSchema.Glass.CHROMATIC.name(),
                    ConfigSchema.Glass.TINT_ALPHA.name(),
                    ConfigSchema.Glass.THICKNESS.name(),
                    ConfigSchema.Glass.IOR.name(),
                    ConfigSchema.Glass.NORMAL_STRENGTH.name(),
                    ConfigSchema.Glass.DOME.name(),
                    ConfigSchema.Glass.LENS_REFRACTION.name(),
                    ConfigSchema.Glass.DEPTH_EFFECT.name(),
                    ConfigSchema.Glass.HIGHLIGHT_WIDTH.name(),
                    ConfigSchema.Glass.TINT_RED.name(),
                    ConfigSchema.Glass.TINT_GREEN.name(),
                    ConfigSchema.Glass.TINT_BLUE.name(),
                    ConfigSchema.Glass.BRIGHTNESS.name(),
                    ConfigSchema.Glass.SPECULAR_SHARPNESS.name(),
                    ConfigSchema.Glass.SPECULAR_STRENGTH.name(),
                    ConfigSchema.Glass.RIM_LIGHT.name(),
                    ConfigSchema.Glass.CAUSTICS.name(),
                    ConfigSchema.Glass.PRISMAL_REFRACTION_INSET.name(),
                    ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE.name(),
                    ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH.name(),
                    ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING.name(),
                    ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF.name(),
                    ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT.name(),
                    ConfigSchema.Glass.PRISMAL_DISPERSION_R.name(),
                    ConfigSchema.Glass.PRISMAL_DISPERSION_B.name(),
                    ConfigSchema.Glass.PRISMAL_VIBRANCY.name(),
                    ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT.name(),
                    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.name(),
                    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX.name(),
                    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH.name(),
                    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY.name(),
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY.name(),
                    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X.name(),
                    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_RED.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_GREEN.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_BLUE.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA.name(),
                    ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS.name(),
                    ConfigSchema.Glass.PRISMAL_TRANSMITTANCE.name(),
                    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X.name(),
                    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y.name(),
                    ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE.name(),
                    ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.name(),
                    ConfigSchema.LauncherHighlight.SKY_HAZE.name(),
                    ConfigSchema.LauncherHighlight.SPECULAR.name(),
                    ConfigSchema.LauncherHighlight.LIT_RIM.name(),
                    ConfigSchema.LauncherHighlight.OPPOSITE_RIM.name(),
                    ConfigSchema.LauncherHighlight.CORNER_RIM.name(),
                    ConfigSchema.LauncherHighlight.FACE_SHEEN.name(),
                    ConfigSchema.LauncherHighlight.PLAIN_HIGHLIGHT.name(),
                    ConfigSchema.LauncherHighlight.CAUSTICS.name(),
                    ConfigSchema.LauncherHighlight.PRESS_GLOW.name(),
                    ConfigSchema.LauncherHighlight.LARGE_SKY_HAZE.name(),
                    ConfigSchema.LauncherHighlight.LARGE_SPECULAR.name(),
                    ConfigSchema.LauncherHighlight.LARGE_LIT_RIM.name(),
                    ConfigSchema.LauncherHighlight.LARGE_OPPOSITE_RIM.name(),
                    ConfigSchema.LauncherHighlight.LARGE_CORNER_RIM.name(),
                    ConfigSchema.LauncherHighlight.LARGE_FACE_SHEEN.name(),
                    ConfigSchema.LauncherHighlight.LARGE_PLAIN_HIGHLIGHT.name(),
                    ConfigSchema.LauncherHighlight.LARGE_CAUSTICS.name(),
                    ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW.name())));
    private static final Set<String> RECENTS_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.name(),
                    ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.name())));

    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static Map<String, ?> lastGlass = Collections.emptyMap();
    private static Map<String, ?> lastRecents = Collections.emptyMap();
    private static volatile LiquidDockConfig.Glass currentGlass;
    private static volatile long generation;

    private LiveGlassConfigState() {}

    static synchronized void initialize(SharedPreferences remote, LiquidDockConfig initial) {
        if (preferences != null && listener != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        }
        preferences = remote;
        mainHandler = new Handler(Looper.getMainLooper());
        currentGlass = initial.glass;
        generation = 0L;
        FRAME_PENDING.set(false);
        Map<String, ?> all = snapshot(remote);
        lastGlass = project(all, GLASS_KEYS);
        lastRecents = project(all, RECENTS_KEYS);
        listener = (prefs, key) -> {
            if (!isLiveKey(key)) return;
            // Listener callbacks may be on a Binder thread; Choreographer must run on main.
            Handler handler = mainHandler;
            if (handler != null) handler.post(LiveGlassConfigState::scheduleFrame);
        };
        if (remote != null) {
            remote.registerOnSharedPreferenceChangeListener(listener);
        }
    }

    static LiquidDockConfig.Glass currentGlass() {
        return currentGlass;
    }

    static long generation() {
        return generation;
    }

    static boolean isLiveKey(String key) {
        if (key == null) return true; // bulk preference change
        if (GLASS_KEYS.contains(key) || RECENTS_KEYS.contains(key)) return true;
        return key.endsWith("_tenths")
                && GLASS_KEYS.contains(key.substring(0, key.length() - "_tenths".length()));
    }

    private static void scheduleFrame() {
        if (!FRAME_PENDING.compareAndSet(false, true)) return;
        Choreographer.getInstance().postFrameCallback(ignored -> flushOnMain());
    }

    private static void flushOnMain() {
        FRAME_PENDING.set(false);
        SharedPreferences prefs = preferences;
        if (prefs == null) return;
        try {
            Map<String, ?> all = snapshot(prefs);
            Map<String, ?> glass = project(all, GLASS_KEYS);
            Map<String, ?> recents = project(all, RECENTS_KEYS);
            boolean glassChanged = !glass.equals(lastGlass);
            boolean recentsChanged = !recents.equals(lastRecents);
            if (!glassChanged && !recentsChanged) return;

            LiquidDockConfig next = LiquidDockConfig.from(new ConfigReader(all));
            if (glassChanged) {
                currentGlass = next.glass;
                lastGlass = glass;
                generation++;
                if (GlassRuntimeState.isEnabled()) {
                    Miuix307ZeroCopyRenderer.sync(next.glass, Math.round(next.glass.blur));
                    Miuix307ZeroCopyRenderer.requestDockSceneRefresh();
                    LauncherGlassStaticNode.applyLiveGlassConfigToAll(next.glass);
                    LauncherGlassSessionRegistry.applyLiveGlassConfigToAll(next.glass);
                    ShortcutPopupGlassCoordinator.onLiveGlassConfigChanged(next.glass);
                    MiuixLauncherStaticGlassHook.onLiveGlassConfigChanged(next.glass);
                }
                MainHook.log(TAG + " glass generation=" + generation
                        + " blur=" + next.glass.blur);
            }
            if (recentsChanged) {
                lastRecents = recents;
                RecentsBackgroundBlurHook.onLiveConfigChanged(next.recents);
                MainHook.log(TAG + " recents blur=" + next.recents.backgroundBlurPercent);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " configuration refresh failed: " + error);
        }
    }

    private static Map<String, ?> snapshot(SharedPreferences prefs) {
        if (prefs == null) return Collections.emptyMap();
        Map<String, ?> result = prefs.getAll();
        return result == null ? Collections.emptyMap() : new HashMap<>(result);
    }

    private static Map<String, ?> project(Map<String, ?> source, Set<String> keys) {
        Map<String, Object> result = new HashMap<>();
        for (String key : keys) {
            if (source.containsKey(key)) result.put(key, source.get(key));
            String tenths = key + "_tenths";
            if (source.containsKey(tenths)) result.put(tenths, source.get(tenths));
        }
        return result;
    }
}
