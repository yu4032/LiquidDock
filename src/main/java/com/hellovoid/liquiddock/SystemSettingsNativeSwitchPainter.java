package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
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
 * Settings-only rendering of the native MIUIX switch.
 *
 * No ComposeView, App Startup, RenderNode snapshot, bitmap, PixelCopy or
 * external texture. This paints only procedural glass chrome and cannot
 * refract the Settings page behind the switch.
 *
 * An unsupported/failed shader is NOT fatal: a small Canvas gradient renderer
 * preserves visible glass chrome. If native painting cannot succeed the caller
 * runs the original MIUIX onDraw chain instead.
 */
final class SystemSettingsNativeSwitchPainter {
    private static final String TAG = "[DC][SettingsSwitch]";
    private static final WeakHashMap<CompoundButton, State> STATES = new WeakHashMap<>();
    private static boolean agslUnavailableLogged;
    private static int nativePaints;

    // Small local-only AGSL lens/chrome shader. This has no Prismal App Startup,
    // resource registry, image input, vendor overlay or external lifecycle.
    // Coordinates and checked state are uniformly supplied per native widget.
    static final String CHROME_AGSL = """
            uniform float2 uSize;
            uniform float uChecked;
            uniform float uDark;
            uniform float uEnabled;

            float capsule(float2 p, float2 halfSize, float radius) {
                float2 d = abs(p) - halfSize + float2(radius, radius);
                return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
            }

            half4 main(float2 position) {
                float2 size = max(uSize, float2(1.0));
                float2 mid = size * 0.5;
                float2 trackHS = float2(size.x * 0.455, size.y * 0.35);
                float tr = trackHS.y;
                float trackD = capsule(position - mid, trackHS, tr);
                float trackCover = 1.0 - smoothstep(-0.9, 0.9, trackD);
                float trackEdge = (1.0 - smoothstep(0.0, 2.4, abs(trackD))) * trackCover;
                float top = clamp(1.0 - position.y / size.y, 0.0, 1.0);

                float3 off = mix(float3(0.85, 0.87, 0.91),
                                 float3(0.18, 0.20, 0.25), uDark);
                float3 on = mix(float3(0.18, 0.74, 0.65),
                                float3(0.16, 0.58, 0.60), uDark);
                float3 base = mix(off, on, uChecked);
                base += float3(0.075) * top * trackCover;
                base = mix(base, float3(0.98), trackEdge * (0.16 + 0.22 * top));
                float trackAlpha = trackCover *
                    mix(mix(0.83, 0.76, uDark), 0.98, uChecked);

                float thumbR = size.y * 0.292;
                float travel = max(0.0, trackHS.x - trackHS.y - size.y * 0.03);
                float2 thumb = position - (mid + float2(mix(-travel, travel, uChecked), 0.0));
                float thumbD = length(thumb) - thumbR;
                float shadow = 1.0 - smoothstep(1.0, 5.8, thumbD);
                float cover = 1.0 - smoothstep(-0.85, 0.85, thumbD);
                float rim = 1.0 - smoothstep(0.0, 2.1, abs(thumbD));
                float fresnel = pow(clamp(1.0 - length(thumb) / max(thumbR, 1.0), 0.0, 1.0), 0.65);
                float3 thumbColor = mix(
                    float3(0.90, 0.94, 0.99),
                    float3(0.90, 0.96, 0.99), top
                );
                thumbColor -= float3(0.14) * (1.0 - fresnel);
                thumbColor += float3(0.085) * rim * top;
                float3 composite = mix(base, float3(0.04, 0.07, 0.12), shadow * 0.22);
                composite = mix(composite, thumbColor, cover);
                float alpha = max(trackAlpha, shadow * 0.32 * trackCover);
                alpha = max(alpha, cover);
                alpha *= mix(0.40, 1.0, uEnabled);
                return half4(half3(clamp(composite, 0.0, 1.0)), half(clamp(alpha, 0.0, 1.0)));
            }
            """;

    private SystemSettingsNativeSwitchPainter() {}

    private static final class State {
        final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF track = new RectF();
        final RectF knob = new RectF();
        RuntimeShader shader;
        boolean shaderChecked;
        boolean shaderUnsupported;
    }

    static boolean paint(Canvas canvas, CompoundButton original) {
        if (canvas == null || original == null || original.getWidth() < 8
                || original.getHeight() < 8 || !original.isAttachedToWindow()) {
            return false;
        }
        try {
            State state;
            synchronized (STATES) {
                state = STATES.get(original);
                if (state == null) {
                    state = new State();
                    STATES.put(original, state);
                }
            }
            boolean checked = original.isChecked();
            boolean enabled = original.isEnabled();
            boolean dark = (original.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int w = original.getWidth();
            int h = original.getHeight();

            // Existing MIUIX onDraw must remain the fallback if the custom
            // renderer fails. Native view state/gestures are never overwritten.
            if (canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= 33
                    && !state.shaderUnsupported) {
                try {
                    if (state.shader == null) {
                        state.shader = new RuntimeShader(CHROME_AGSL);
                        state.shaderPaint.setShader(state.shader);
                    }
                    state.shader.setFloatUniform("uSize", (float) w, (float) h);
                    state.shader.setFloatUniform("uChecked", checked ? 1f : 0f);
                    state.shader.setFloatUniform("uDark", dark ? 1f : 0f);
                    state.shader.setFloatUniform("uEnabled", enabled ? 1f : 0f);
                    canvas.drawRect(0f, 0f, w, h, state.shaderPaint);
                    if (nativePaints++ < 4) {
                        Api101Bridge.log(TAG + " native AGSL canvas rendered class="
                                + original.getClass().getName() + " size=" + w + "x" + h);
                    }
                    return true;
                } catch (Throwable shaderError) {
                    state.shaderUnsupported = true;
                    state.shaderPaint.setShader(null);
                    if (!agslUnavailableLogged) {
                        agslUnavailableLogged = true;
                        Api101Bridge.log(TAG + " AGSL unavailable, using Canvas gradient: "
                                + shaderError.getClass().getName() + " "
                                + shaderError.getMessage());
                    }
                }
            }

            drawCanvasFallback(canvas, state, original, w, h, checked, dark, enabled);
            if (nativePaints++ < 4) {
                Api101Bridge.log(TAG + " native gradient canvas rendered class="
                        + original.getClass().getName() + " size=" + w + "x" + h);
            }
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native painter failed; keeping MIUIX original: "
                    + error.getClass().getName() + " " + error.getMessage());
            return false;
        }
    }

    private static void drawCanvasFallback(
            Canvas canvas, State state, View view, int width, int height,
            boolean checked, boolean dark, boolean enabled) {
        float h = height;
        float w = width;
        float radius = h * 0.35f;
        state.track.set(w * 0.045f, h * 0.15f, w * 0.955f, h * 0.85f);
        int base = checked ? (dark ? 0xff2b999e : 0xff30baa0)
                : (dark ? 0xff343a44 : 0xffd4dce4);
        int high = checked ? (dark ? 0xff69ccd0 : 0xff8aefd3)
                : (dark ? 0xff69737f : 0xfff9fbff);
        float opacity = enabled ? 1f : 0.44f;
        state.fill.setAlpha(Math.round(opacity * 255));
        state.fill.setShader(new LinearGradient(0f, h * 0.16f, 0f, h * 0.84f,
                high, base, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(state.track, radius, radius, state.fill);
        state.fill.setShader(null);
        state.outline.setStyle(Paint.Style.STROKE);
        state.outline.setStrokeWidth(Math.max(1f, h * 0.033f));
        state.outline.setColor(dark ? 0x77ffffff : 0xa8ffffff);
        state.outline.setAlpha(Math.round(opacity * 180));
        canvas.drawRoundRect(state.track, radius, radius, state.outline);
        state.outline.setStyle(Paint.Style.FILL);
        float thumbRadius = h * 0.292f;
        float travel = Math.max(0, w * 0.455f - h * 0.35f - h * 0.03f);
        float cx = w * 0.5f + (checked ? travel : -travel);
        float cy = h * 0.5f;
        state.knob.set(cx - thumbRadius, cy - thumbRadius,
                cx + thumbRadius, cy + thumbRadius);
        state.fill.setShader(new LinearGradient(0, cy - thumbRadius, 0, cy + thumbRadius,
                0xffffffff, 0xffd0dbe5, Shader.TileMode.CLAMP));
        state.fill.setAlpha(Math.round(opacity * 255f));
        canvas.drawOval(state.knob, state.fill);
        state.fill.setShader(null);
        state.outline.setColor(Color.WHITE);
        state.outline.setAlpha(Math.round(opacity * 115f));
        state.outline.setStyle(Paint.Style.STROKE);
        state.outline.setStrokeWidth(Math.max(0.8f, height * 0.025f));
        canvas.drawOval(state.knob, state.outline);
        state.outline.setStyle(Paint.Style.FILL);
        state.fill.setAlpha(255);
    }
}
