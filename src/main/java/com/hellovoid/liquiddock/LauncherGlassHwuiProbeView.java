package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Trace;
import android.view.View;

import com.hellovoid.prismal.PrismalRenderer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Debug-only Workspace Prismal material rendered in Launcher root coordinates. */
final class LauncherGlassHwuiProbeView extends View {
    private static final String TAG = "LiquidDock-HWUI";
    private static final Pattern TEXTURE_SAMPLE = Pattern.compile(
            "texture2D\\s*\\(\\s*(u_backgroundTexture|u_blurredTexture)"
                    + "\\s*,\\s*([A-Za-z_]\\w*)\\s*\\)");
    private static final Pattern FRAGMENT_OUTPUT = Pattern.compile(
            "gl_FragColor\\s*=\\s*vec4\\((.*?)\\);", Pattern.DOTALL);
    private static volatile String baselineAgslSource;
    private static volatile String decoratedAgslSource;
    private static volatile String motionDecoratedAgslSource;

    private final LauncherGlassSession session;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap backdrop;
    private Bitmap blurredBackdrop;
    private RuntimeShader baselineShader;
    private RuntimeShader decoratedShader;
    private RuntimeShader motionDecoratedShader;
    private int backdropLogicalWidth;
    private int backdropLogicalHeight;
    private int backdropRotation = -1;
    private boolean drawFailureReported;

    LauncherGlassHwuiProbeView(Context context, LauncherGlassSession session) {
        super(context);
        this.session = session;
        setWillNotDraw(false);
        setBackgroundColor(0x00000000);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Cache the static shader result between Workspace geometry/alpha updates. The root
        // Launcher can redraw continuously for native widgets and popup animations; replaying
        // the full-resolution AGSL material on each such frame needlessly heats RenderThread.
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private static String buildAgslSource(boolean decorated, boolean transitionMaterial) {
        String source = PrismalRenderer.workspaceHwuiProbeFragmentSource(
                decorated, transitionMaterial);
        source = source.replace("#extension GL_OES_standard_derivatives : enable", "");
        source = source.replace("precision highp float;", "");
        source = source.replace("uniform sampler2D u_backgroundTexture;",
                "uniform shader u_backgroundTexture;");
        source = source.replace("uniform sampler2D u_blurredTexture;",
                "uniform shader u_blurredTexture;");
        source = source.replace("varying vec2 v_screenTexCoord;", "");
        source = source.replace("varying vec2 v_shapeCoord;", "");

        // AGSL has no fragment derivatives. The source SDF is in logical pixels, so a
        // central one-pixel difference reproduces the original screen-space edge normal.
        source = source.replace("max(length(dFdx(pPx)), 0.5)", "1.0");
        source = source.replace("max(length(dFdy(pPx)), 0.5)", "1.0");
        source = source.replace("float edgeAa = max(fwidth(distMask), 0.75);", "");
        source = replaceExactlyOnce(source,
                "vec2 os4SdfGradient = vec2(\n"
                        + "            dFdx(distMask) / max(edgePixelStep.x, 1e-3),\n"
                        + "            -dFdy(distMask) / max(edgePixelStep.y, 1e-3));",
                "vec2 os4SdfGradient = vec2(\n"
                        + "            (sdRoundBox(pPx + vec2(1.0, 0.0), halfSz, crMask, u_sminSmoothing)\n"
                        + "                    - sdRoundBox(pPx - vec2(1.0, 0.0), halfSz, crMask, u_sminSmoothing)) * 0.5,\n"
                        + "            -(sdRoundBox(pPx + vec2(0.0, 1.0), halfSz, crMask, u_sminSmoothing)\n"
                        + "                    - sdRoundBox(pPx - vec2(0.0, 1.0), halfSz, crMask, u_sminSmoothing)) * 0.5);\n"
                        + "    float edgeAa = max(abs(os4SdfGradient.x) + abs(os4SdfGradient.y), 0.75);");

        Matcher samples = TEXTURE_SAMPLE.matcher(source);
        StringBuffer sampled = new StringBuffer(source.length() + 256);
        int sampleCount = 0;
        while (samples.find()) {
            String function = "u_backgroundTexture".equals(samples.group(1))
                    ? "sampleRaw" : "sampleBlurred";
            samples.appendReplacement(sampled,
                    Matcher.quoteReplacement(function + "(" + samples.group(2) + ")"));
            sampleCount++;
        }
        samples.appendTail(sampled);
        source = sampled.toString();
        if (sampleCount == 0 || source.contains("texture2D(")) {
            throw new IllegalStateException("Prismal texture sampling conversion incomplete; samples="
                    + sampleCount);
        }

        // Reconstructed-27 helpers were originally inside GLSL main and could read the varying
        // directly. AGSL functions receive per-fragment values as parameters.
        String chromaHelperName = source.contains("vec3 optical27SevenTap(")
                ? "optical27SevenTap" : null;
        if (chromaHelperName != null) {
            int helperStart = source.indexOf("vec3 " + chromaHelperName + "(");
            int helperEnd = source.indexOf("\n}", helperStart);
            if (helperEnd < 0) {
                throw new IllegalStateException("Prismal AGSL chromatic helper contract changed");
            }
            String signature = "vec3 " + chromaHelperName
                    + "(vec2 offset, vec2 push, float pinch) {";
            String helper = source.substring(helperStart, helperEnd + 2)
                    .replace(signature, "vec3 " + chromaHelperName
                            + "(vec2 offset, vec2 push, float pinch, vec2 screenUv) {")
                    .replace("backdropUv(v_screenTexCoord,", "backdropUv(screenUv,");
            source = source.substring(0, helperStart) + helper + source.substring(helperEnd + 2);
            source = source.replaceAll(
                    Pattern.quote(chromaHelperName)
                            + "\\(baseOffset,\\s*chromaPush,\\s*pinchMix\\)",
                    chromaHelperName + "(baseOffset, chromaPush, pinchMix, v_screenTexCoord)");
        }

        source = replaceExactlyOnce(source, "uniform vec2  u_resolution;",
                "uniform vec2  u_resolution;\n"
                        + "uniform vec2 uProbeRawScale;\n"
                        + "uniform vec2 uProbeBlurScale;\n"
                        + "uniform vec4 uProbeBounds;\n"
                        + "vec4 sampleRaw(vec2 uv) {\n"
                        + "    vec2 p = clamp(uv, vec2(0.0), vec2(1.0)) * u_resolution * uProbeRawScale;\n"
                        + "    return vec4(u_backgroundTexture.eval(p));\n"
                        + "}\n"
                        + "vec4 sampleBlurred(vec2 uv) {\n"
                        + "    vec2 p = clamp(uv, vec2(0.0), vec2(1.0)) * u_resolution * uProbeBlurScale;\n"
                        + "    return vec4(u_blurredTexture.eval(p));\n"
                        + "}");

        source = replaceExactlyOnce(source, "void main() {", """
                half4 main(float2 fragCoord) {
                    vec2 v_screenTexCoord = vec2(
                            fragCoord.x / u_resolution.x,
                            fragCoord.y / u_resolution.y);
                    vec2 center = (uProbeBounds.xy + uProbeBounds.zw) * 0.5;
                    vec2 v_shapeCoord = vec2(
                            (fragCoord.x - center.x) / u_glassSize.x,
                            -(fragCoord.y - center.y) / u_glassSize.y);
                """);

        source = source.replaceAll("\\bdiscard\\s*;", "return half4(0.0);");
        Matcher outputs = FRAGMENT_OUTPUT.matcher(source);
        StringBuffer returned = new StringBuffer(source.length());
        int outputCount = 0;
        while (outputs.find()) {
            String premultiplied = premultipliedAgslComponents(outputs.group(1));
            outputs.appendReplacement(returned,
                    Matcher.quoteReplacement("return half4(" + premultiplied + ");"));
            outputCount++;
        }
        outputs.appendTail(returned);
        source = returned.toString();
        // GLSL wrote gl_FragColor and then used a bare return; AGSL's equivalent output
        // assignment above already returns the pixel value, so discard the obsolete statement.
        source = source.replaceAll("\\breturn\\s*;", "");
        if (outputCount < 2 || source.contains("gl_FragColor")
                || source.contains("discard;") || source.contains("texture2D(")) {
            throw new IllegalStateException("Prismal GLSL to AGSL conversion incomplete; outputs="
                    + outputCount);
        }
        return source;
    }

    /** GLSL fragment outputs are straight-alpha; Skia RuntimeShader outputs must be premultiplied. */
    private static String premultipliedAgslComponents(String vec4Arguments) {
        int nesting = 0;
        int separator = -1;
        for (int i = 0; i < vec4Arguments.length(); i++) {
            char c = vec4Arguments.charAt(i);
            if (c == '(') nesting++;
            else if (c == ')') nesting--;
            else if (c == ',' && nesting == 0) {
                separator = i;
                break;
            }
        }
        if (separator <= 0 || separator >= vec4Arguments.length() - 1) {
            throw new IllegalStateException("Expected straight-alpha vec4 output: "
                    + vec4Arguments);
        }
        String rgb = vec4Arguments.substring(0, separator).trim();
        String alpha = vec4Arguments.substring(separator + 1).trim();
        return "(" + rgb + ") * (" + alpha + "), " + alpha;
    }

    static String agslSource(boolean decorated) {
        return agslSource(decorated, false);
    }

    static String agslSource(boolean decorated, boolean transitionMaterial) {
        String source = transitionMaterial ? motionDecoratedAgslSource
                : decorated ? decoratedAgslSource : baselineAgslSource;
        if (source != null) return source;
        synchronized (LauncherGlassHwuiProbeView.class) {
            source = transitionMaterial ? motionDecoratedAgslSource
                    : decorated ? decoratedAgslSource : baselineAgslSource;
            if (source == null) {
                source = buildAgslSource(decorated, transitionMaterial);
                if (transitionMaterial) motionDecoratedAgslSource = source;
                else if (decorated) decoratedAgslSource = source;
                else baselineAgslSource = source;
            }
        }
        return source;
    }

    private static String replaceExactlyOnce(String source, String expected, String replacement) {
        int first = source.indexOf(expected);
        if (first < 0 || source.indexOf(expected, first + expected.length()) >= 0) {
            throw new IllegalStateException("Prismal AGSL conversion contract changed: "
                    + expected.substring(0, Math.min(48, expected.length())));
        }
        return source.substring(0, first) + replacement + source.substring(first + expected.length());
    }

    boolean setBackdrops(
            Bitmap rawBitmap, Bitmap blurBitmap,
            int logicalWidth, int logicalHeight, int rotation) {
        if (rawBitmap == null || blurBitmap == null
                || logicalWidth <= 0 || logicalHeight <= 0) return false;
        try {
            RuntimeShader nextBaseline = new RuntimeShader(agslSource(false));
            RuntimeShader nextDecorated = new RuntimeShader(agslSource(true));
            RuntimeShader nextMotionDecorated;
            try {
                nextMotionDecorated = new RuntimeShader(agslSource(true, true));
            } catch (RuntimeException ignored) {
                nextMotionDecorated = null;
            }
            BitmapShader rawInput = new BitmapShader(
                    rawBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            BitmapShader blurInput = new BitmapShader(
                    blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            // Shader inputs preserve HWUI color conversion and alpha premultiplication.
            nextBaseline.setInputShader("u_backgroundTexture", rawInput);
            nextBaseline.setInputShader("u_blurredTexture", blurInput);
            nextDecorated.setInputShader("u_backgroundTexture", rawInput);
            nextDecorated.setInputShader("u_blurredTexture", blurInput);
            if (nextMotionDecorated != null) {
                nextMotionDecorated.setInputShader("u_backgroundTexture", rawInput);
                nextMotionDecorated.setInputShader("u_blurredTexture", blurInput);
            }
            nextBaseline.setFloatUniform("uProbeRawScale",
                    (float) rawBitmap.getWidth() / logicalWidth,
                    (float) rawBitmap.getHeight() / logicalHeight);
            nextBaseline.setFloatUniform("uProbeBlurScale",
                    (float) blurBitmap.getWidth() / logicalWidth,
                    (float) blurBitmap.getHeight() / logicalHeight);
            nextDecorated.setFloatUniform("uProbeRawScale",
                    (float) rawBitmap.getWidth() / logicalWidth,
                    (float) rawBitmap.getHeight() / logicalHeight);
            nextDecorated.setFloatUniform("uProbeBlurScale",
                    (float) blurBitmap.getWidth() / logicalWidth,
                    (float) blurBitmap.getHeight() / logicalHeight);
            if (nextMotionDecorated != null) {
                nextMotionDecorated.setFloatUniform("uProbeRawScale",
                        (float) rawBitmap.getWidth() / logicalWidth,
                        (float) rawBitmap.getHeight() / logicalHeight);
                nextMotionDecorated.setFloatUniform("uProbeBlurScale",
                        (float) blurBitmap.getWidth() / logicalWidth,
                        (float) blurBitmap.getHeight() / logicalHeight);
            }
            backdrop = rawBitmap;
            blurredBackdrop = blurBitmap;
            baselineShader = nextBaseline;
            decoratedShader = nextDecorated;
            motionDecoratedShader = nextMotionDecorated;
            backdropLogicalWidth = logicalWidth;
            backdropLogicalHeight = logicalHeight;
            backdropRotation = rotation;
            drawFailureReported = false;
            postInvalidateOnAnimation();
            return true;
        } catch (Throwable error) {
            logAgslErrorContext(error);
            fail(error);
            return false;
        }
    }

    /** Drop Java/Skia inputs after detach. Let HWUI release in-flight GPU references safely. */
    void clearBackdrops() {
        baselineShader = null;
        decoratedShader = null;
        motionDecoratedShader = null;
        backdrop = null;
        blurredBackdrop = null;
        backdropLogicalWidth = 0;
        backdropLogicalHeight = 0;
        backdropRotation = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RuntimeShader baseline = baselineShader;
        RuntimeShader decorated = decoratedShader;
        RuntimeShader motionDecorated = motionDecoratedShader;
        if (backdrop == null || blurredBackdrop == null
                || baseline == null || decorated == null) return;
        if (getWidth() != backdropLogicalWidth || getHeight() != backdropLogicalHeight) return;
        session.recordHwuiProbeDraw();
        try {
            if (Trace.isEnabled()) Trace.beginSection("LD.Workspace.HWUIProbe.Draw");
            try {
                session.drawHwuiProbe(canvas, paint, baseline, decorated, motionDecorated);
            } finally {
                if (Trace.isEnabled()) Trace.endSection();
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (backdrop != null && (width != backdropLogicalWidth || height != backdropLogicalHeight)) {
            LauncherGlassStaticLayer.markHwuiProbeBackdropPending(getRootView());
        }
        postInvalidateOnAnimation();
    }

    private void fail(Throwable error) {
        if (drawFailureReported) return;
        drawFailureReported = true;
        Throwable rootCause = error;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        android.util.Log.e(TAG, "Workspace HWUI probe failed", error);
        LauncherGlassStaticLayer.failHwuiProbe(getRootView(), this,
                new IllegalStateException("rotation=" + backdropRotation
                        + " shaderError=" + error + " rootCause=" + rootCause, error));
    }

    private static void logAgslErrorContext(Throwable error) {
        String source = decoratedAgslSource != null
                ? decoratedAgslSource : baselineAgslSource;
        if (source == null) return;
        String[] lines = source.split("\\n", -1);
        Matcher errors = Pattern.compile("error:\\s*(\\d+):").matcher(String.valueOf(error));
        while (errors.find()) {
            int line = Integer.parseInt(errors.group(1));
            for (int index = Math.max(0, line - 3); index < Math.min(lines.length, line + 2); index++) {
                MainHook.log("[DC][LauncherGlass] AGSL[" + (index + 1) + "] " + lines[index].trim());
            }
        }
    }
}
