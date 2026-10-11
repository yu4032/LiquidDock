package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.CompoundButton;

import java.util.WeakHashMap;

/**
 * Native Canvas translation of GuiOnTouchPrismalToggle's physical layout and
 * local chrome. Source: 64x28dp translucent track (#787878 -> #34C759,
 * #30D158 dark), 40x24dp GLASS CAPSULE thumb, 20dp travel, 1.5x pressed scale.
 *
 * The real Prismal module samples a backdrop and renders lens/blur on touch.
 * A foreign Settings Canvas has no legitimate backdrop shader; therefore
 * this renderer preserves Prismal's geometry, transparent surface, rim,
 * touch spring and chromatic/specular styling, but makes no false claim to
 * backdrop sampling or true transmitted refraction.
 *
 * No Compose, App Startup, bitmap, PixelCopy or ScreenCapture. If both drawing
 * paths fail, original MIUIX onDraw executes unchanged.
 */
final class SystemSettingsNativeSwitchPainter {
    private static final String TAG = "[DC][SettingsSwitch]";
    private static final WeakHashMap<CompoundButton, State> STATES = new WeakHashMap<>();
    private static boolean agslUnavailableLogged;
    private static int loggedDraws;

    // Prismal-style SDF capsule surface and edge optics. Track and moving
    // glass capsule are intentionally distinct, rather than a small circular
    // opaque thumb. AGSL does not depend on Prismal's resource registry.
    static final String CHROME_AGSL = """
            uniform float2 uSize;
            uniform float uDensity;
            uniform float uFit;
            uniform float uFraction;
            uniform float uPressed;
            uniform float uVelocity;
            uniform float uDark;
            uniform float uEnabled;
            uniform float uRtl;

            float sdCapsule(float2 p, float2 hs) {
                // hs.y is half-height and capsule's corner radius.
                float2 q = float2(abs(p.x) - (hs.x - hs.y), p.y);
                return length(float2(max(q.x, 0.0), q.y)) - hs.y;
            }
            float aaCover(float d, float a) {
                return 1.0 - smoothstep(-a, a, d);
            }

            half4 main(float2 frag) {
                float den = max(uDensity, 0.1);
                float fit = max(uFit, 0.05);
                float2 p = (frag - uSize * 0.5) / fit;
                float aa = max(0.7, 0.85 / fit);
                float fraction = clamp(uFraction, 0.0, 1.0);
                float pressed = clamp(uPressed, 0.0, 1.0);
                float2 trackHS = float2(32.0, 14.0) * den;
                float td = sdCapsule(p, trackHS);
                float trackMask = aaCover(td, aa);
                float trackEdge = (1.0 - smoothstep(0.0, 2.6 * den, abs(td)))
                    * trackMask;
                float top = clamp(0.5 - p.y / (28.0 * den), 0.0, 1.0);
                float3 grey = float3(0.471, 0.471, 0.471);
                float3 accent = mix(float3(0.204, 0.780, 0.349),
                                    float3(0.188, 0.820, 0.345), uDark);
                float3 trackColor = mix(grey, accent, fraction);
                // Preserve Prismal Compose's alpha (.20 light / .36 dark)
                // instead of the prior solid-filled pale track.
                float offAlpha = mix(0.20, 0.36, uDark);
                float trackAlpha = mix(offAlpha, 1.0, fraction) * trackMask;
                // Physical edge reflection: thin bright top + darker lower
                // continuation with a soft grazing highlight, no flat outline.
                float reflection = trackEdge * pow(top, 1.9);
                trackColor = mix(trackColor, float3(0.99, 1.0, 1.0),
                                 reflection * (0.13 + pressed * 0.11));
                trackColor *= 1.0 - (1.0 - top) * trackEdge * 0.085;

                // Prismal: 40x24dp horizontal glass capsule, translating
                // 2dp..22dp over a 64dp track, not a 24dp round knob.
                float dir = mix(1.0, -1.0, uRtl);
                float tx = mix(-10.0, 10.0, fraction) * den * dir;
                float sx = 1.0 + 0.5 * pressed +
                    clamp(uVelocity * 0.75, -0.2, 0.2);
                float sy = 1.0 + 0.5 * pressed -
                    clamp(uVelocity * 0.25, -0.2, 0.2);
                float2 local = (p - float2(tx, 0.0)) /
                               max(float2(sx, sy), float2(0.2));
                float2 thumbHS = float2(20.0, 12.0) * den;
                float dd = sdCapsule(local, thumbHS);
                float thumbMask = aaCover(dd, aa);
                float thumbRim = 1.0 -
                    smoothstep(0.0, 2.25 * den, abs(dd));
                float innerRim = (1.0 - smoothstep(-3.0 * den, -0.6 * den, dd))
                    * thumbMask;
                // Separate luminance of the glass thumb (Prismal idle
                // surfaceColor=white at .75) from the translucent track.
                float north = clamp((thumbHS.y - local.y) /
                             (2.0 * thumbHS.y), 0.0, 1.0);
                float lensHeight = sqrt(max(0.0,
                    1.0 - pow(clamp(length(local) /
                    max(thumbHS.x, 1.0), 0.0, 1.0), 2.0)));
                float3 glass = float3(0.965, 0.978, 0.995);
                glass *= mix(0.90, 1.0, lensHeight);
                glass = mix(glass, float3(1.0), 0.12 * pow(north, 2.0));
                // Ambient specular is mostly visible under contact:
                // PrismalSpecular.Ambient alpha=pressProgress.
                float2 glintAt = float2(-0.42, -0.65) * thumbHS;
                float2 glintDelta = (local - glintAt) /
                                    max(thumbHS, float2(1.0));
                float glint = exp(-dot(glintDelta, glintDelta) * 6.0);
                glass += float3(0.20) * glint * pressed;
                glass += float3(0.095, 0.103, 0.115)
                    * thumbRim * north * (0.44 + 0.56 * pressed);
                glass -= float3(0.048, 0.055, 0.060)
                    * innerRim * (1.0 - north);
                // Approximate small chromatic fringe only in the optical
                // edge; no faked background distortion or image sampling.
                float chroma = 0.28 * thumbRim *
                    clamp(abs(local.x) / max(thumbHS.x, 1.0), 0.0, 1.0);
                glass.r += chroma * 0.07;
                glass.b += chroma * 0.11;
                glass = clamp(glass, 0.0, 1.0);

                float shadowD = dd / max(sx, sy);
                float shadow = (1.0 - smoothstep(0.0, 4.0 * den,
                                   max(shadowD, 0.0))) *
                               (1.0 - thumbMask) * 0.07;
                float thumbA = thumbMask *
                    mix(0.75, 0.91, pressed);
                float behindA = max(trackAlpha, shadow * trackMask);
                float3 behind = mix(trackColor, float3(0.13, 0.16, 0.19),
                                    shadow * trackMask);
                float opacity = thumbA + behindA * (1.0 - thumbA);
                float3 rgb = (glass * thumbA + behind * behindA *
                              (1.0 - thumbA)) / max(opacity, 0.001);
                opacity *= mix(0.40, 1.0, uEnabled);
                return half4(half3(rgb), half(opacity));
            }
            """;

    private SystemSettingsNativeSwitchPainter() {}

    private static final class State {
        final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint trackFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint thumbFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF track = new RectF();
        final RectF thumb = new RectF();
        RuntimeShader shader;
        boolean shaderUnsupported;
    }

    static boolean paint(Canvas canvas, CompoundButton original) {
        if (canvas == null || original == null || original.getWidth() < 8
                || original.getHeight() < 8 || !original.isAttachedToWindow()) return false;
        try {
            State state = STATES.get(original);
            if (state == null) {
                state = new State();
                STATES.put(original, state);
            }
            SystemSettingsPrismalSwitchMotion.State motion =
                    SystemSettingsPrismalSwitchMotion.forView(original);
            boolean dark = (original.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int w = original.getWidth();
            int h = original.getHeight();
            float density = Math.max(0.5f,
                    original.getResources().getDisplayMetrics().density);
            // Native MIUIX widgets can be narrower/shorter than Prismal's
            // Compose layout. Preserve proportions by fitting the 64x28dp
            // source as a WHOLE instead of changing its component geometry.
            float fit = Math.min(1f, Math.min(
                    w / (66f * density), h / (30f * density)));
            fit = Math.max(0.05f, fit);
            boolean rtl = original.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            if (canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= 33
                    && !state.shaderUnsupported) {
                try {
                    if (state.shader == null) {
                        state.shader = new RuntimeShader(CHROME_AGSL);
                        state.shaderPaint.setShader(state.shader);
                    }
                    state.shader.setFloatUniform("uSize", (float) w, (float) h);
                    state.shader.setFloatUniform("uDensity", density);
                    state.shader.setFloatUniform("uFit", fit);
                    state.shader.setFloatUniform("uFraction", motion.fraction);
                    state.shader.setFloatUniform("uPressed", motion.press);
                    state.shader.setFloatUniform("uVelocity", motion.velocityShape);
                    state.shader.setFloatUniform("uDark", dark ? 1f : 0f);
                    state.shader.setFloatUniform("uEnabled", original.isEnabled() ? 1f : 0f);
                    state.shader.setFloatUniform("uRtl", rtl ? 1f : 0f);
                    canvas.drawRect(0f, 0f, w, h, state.shaderPaint);
                    if (loggedDraws++ < 5) {
                        Api101Bridge.log(TAG + " Prismal geometry AGSL rendered "
                                + "track=64x28dp thumb=40x24dp native="
                                + w + "x" + h);
                    }
                    return true;
                } catch (Throwable error) {
                    state.shaderUnsupported = true;
                    state.shaderPaint.setShader(null);
                    if (!agslUnavailableLogged) {
                        agslUnavailableLogged = true;
                        Api101Bridge.log(TAG + " Prismal AGSL unavailable; "
                                + "Canvas geometry fallback " + error.getClass().getName()
                                + " " + error.getMessage());
                    }
                }
            }
            drawCanvasFallback(canvas, state, w, h, density, fit,
                    motion.fraction, motion.press, motion.velocityShape,
                    dark, rtl, original.isEnabled());
            if (loggedDraws++ < 5) {
                Api101Bridge.log(TAG + " Prismal geometry Canvas fallback rendered "
                        + "track=64x28dp thumb=40x24dp");
            }
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " Prismal native painting aborted; "
                    + "restoring MIUIX: " + error.getClass().getName()
                    + " " + error.getMessage());
            return false;
        }
    }

    private static void drawCanvasFallback(Canvas canvas, State state,
            int width, int height, float density, float fit, float f,
            float press, float velocity, boolean dark, boolean rtl,
            boolean enabled) {
        final float d = density;
        final int save = canvas.save();
        try {
            canvas.translate(width * 0.5f, height * 0.5f);
            canvas.scale(fit, fit);
            float frac = SystemSettingsPrismalSwitchMotion.clamp(f, 0f, 1f);
            float pressure = SystemSettingsPrismalSwitchMotion.clamp(press, 0f, 1f);
            float alpha = enabled ? 1f : 0.40f;

            state.track.set(-32f*d, -14f*d, 32f*d, 14f*d);
            int offColor = dark ? 0x5c787880 : 0x33787878; // alpha .36/.20
            int onColor = dark ? 0xff30d158 : 0xff34c759;
            state.trackFill.setShader(null);
            state.trackFill.setColor(blendArgb(offColor, onColor, frac));
            state.trackFill.setAlpha(Math.round(
                    ((state.trackFill.getColor() >>> 24) & 0xff) * alpha));
            canvas.drawRoundRect(state.track, 14f*d, 14f*d, state.trackFill);

            state.rim.setShader(null);
            state.rim.setColor(0xb7ffffff);
            state.rim.setAlpha(Math.round(130 * alpha));
            state.rim.setStyle(Paint.Style.STROKE);
            state.rim.setStrokeWidth(Math.max(0.6f, 0.9f*d));
            canvas.drawRoundRect(state.track, 14f*d, 14f*d, state.rim);
            state.rim.setStyle(Paint.Style.FILL);

            float direction = rtl ? -1f : 1f;
            float tx = (-10f + 20f*frac)*d*direction;
            float sx = 1f + 0.5f*pressure
                    + SystemSettingsPrismalSwitchMotion.clamp(
                    velocity*0.75f, -0.2f, 0.2f);
            float sy = 1f + 0.5f*pressure
                    - SystemSettingsPrismalSwitchMotion.clamp(
                    velocity*0.25f, -0.2f, 0.2f);
            int thumbSave = canvas.save();
            canvas.translate(tx, 0f);
            canvas.scale(sx, sy);
            try {
                state.thumb.set(-20f*d, -12f*d, 20f*d, 12f*d);
                state.thumbFill.setColor(0xffffffff);
                state.thumbFill.setAlpha(Math.round((0.75f + 0.16f*pressure)*255f*alpha));
                state.thumbFill.setShader(new LinearGradient(
                        0f, -12f*d, 0f, 12f*d,
                        0xeeffffff, 0xccdce7f2, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(state.thumb, 12f*d, 12f*d, state.thumbFill);
                state.thumbFill.setShader(null);
                state.rim.setColor(0xffffffff);
                state.rim.setAlpha(Math.round((125f + 78f*pressure)*alpha));
                state.rim.setStyle(Paint.Style.STROKE);
                state.rim.setStrokeWidth(Math.max(0.7f, 1.15f*d));
                canvas.drawRoundRect(state.thumb, 12f*d, 12f*d, state.rim);
                state.rim.setStyle(Paint.Style.FILL);
            } finally {
                canvas.restoreToCount(thumbSave);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /** Color interpolation includes transparency, like Compose lerp(Color). */
    private static int blendArgb(int off, int on, float fraction) {
        float f = SystemSettingsPrismalSwitchMotion.clamp(fraction, 0f, 1f);
        int a = Math.round(((off >>> 24) & 255) * (1f-f)
                + ((on >>> 24) & 255) * f);
        int r = Math.round(((off >>> 16) & 255) * (1f-f)
                + ((on >>> 16) & 255) * f);
        int g = Math.round(((off >>> 8) & 255) * (1f-f)
                + ((on >>> 8) & 255) * f);
        int b = Math.round((off & 255) * (1f-f)
                + (on & 255) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
