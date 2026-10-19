package com.hellovoid.prismal;

import org.junit.Test;

import static org.junit.Assert.*;

/** Contracts for the optional macOS 27 reconstructed material stages. */
public class PrismalReconstructed27ShaderTest {
    private static String decorated() {
        return PrismalEdgeDecorationShader.apply(
                PrismalOs4InvariantShader.apply(
                        PrismalComponentGateShader.apply(
                                PrismalOpticalEdgeShader.apply(
                                        PrismalSingleEdgeShader.apply(
                                                PrismalShaderSources.FRAGMENT)))));
    }

    private static String full() {
        return PrismalReconstructed27Shader.apply(decorated());
    }

    private static int count(String text, String needle) {
        int n = 0, from = 0, pos;
        while ((pos = text.indexOf(needle, from)) >= 0) {
            n++;
            from = pos + needle.length();
        }
        return n;
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t*t*(3f-2f*t);
    }

    private static float blurRamp(float depth, float reach) {
        float r = Math.max(.01f, reach);
        float a = smooth(depth/(r*.30f));
        float b = smooth((depth-r*.30f)/(r*.35f));
        float c = smooth((depth-r*.65f)/(r*.35f));
        return Math.max(0f, Math.min(1f,
                1f-.43f*a-.33f*b-.24f*c));
    }

    private static float blurFill(float face, float fill, float l, float d, float normal) {
        float base = 1f-l-d;
        float mixed = l*Math.max(face, fill)+d*Math.min(face, fill)+base*face;
        return mixed*(1f-normal)+fill*normal;
    }

    @Test public void composesAfterNativePrismalWithoutMutatingOriginalShader() {
        String prior = decorated(), full = full();
        assertTrue(full.length() > prior.length());
        assertTrue(prior.contains("float innerHighlightAlpha = 0.0;"));
        assertTrue(prior.contains("float outlineAlpha = 0.0;"));
        assertTrue(full.contains("float innerHighlightAlpha = 0.0;"));
        assertTrue(full.contains("float outlineAlpha = 0.0;"));
        assertTrue(full.contains("gl_FragColor = vec4(finalRgb, finalAlpha);"));
        assertFalse(prior.contains("optical27SevenTap"));
        assertFalse(prior.contains("optical27BlurFill"));
    }

    @Test public void nativeEs2SevenTapReplacesThreeTapRatherThanStackingSamples() {
        String shader = full();
        assertTrue(shader.contains("vec3 optical27SevenTap(vec2 offset, vec2 push, float pinch)"));
        assertTrue(shader.contains("for (int i = 0; i < 3; ++i)"));
        assertTrue(shader.contains("for (int j = 0; j < 4; ++j)"));
        assertTrue(shader.contains("accum.r += c.r * w;"));
        assertTrue(shader.contains("accum.g += c.g * (1.0 - w);"));
        assertTrue(shader.contains("accum.b += c.b * w;"));
        assertTrue(shader.contains("return accum * vec3(0.5, 1.0 / 3.0, 0.5);"));
        assertTrue(shader.contains("color = optical27SevenTap(baseOffset,"));
        assertEquals("one refraction chroma path, no duplicate 3tap",0,
                count(shader, "float r = texture2D(u_blurredTexture, uvR).r;"));
        assertFalse(shader.contains("textureLod("));
        assertFalse(shader.contains("textureSize("));
        assertFalse(shader.contains("sampler2DArray"));
        // Reconstructed 3 forward / 4 backward weights normalize RGB equally.
        float r=0f, g=0f, b=0f;
        for(int i=0;i<3;i++){
            float w=1f-i/3f;
            r+=w; g+=1f-w;
        }
        for(int i=0;i<4;i++){
            float w=i/3f;
            g+=1f-w; b+=w;
        }
        assertEquals(1f,r*.5f,.00001f);
        assertEquals(1f,g/3f,.00001f);
        assertEquals(1f,b*.5f,.00001f);
    }

    @Test public void threeModeBlurFillUsesPartitionOfUnity() {
        String shader=full();
        assertTrue(shader.contains("vec3 optical27BlurFill(vec3 face, vec3 fill,"));
        assertTrue(shader.contains("float base = 1.0 - l - d;"));
        assertTrue(shader.contains("l * max(face, fill)"));
        assertTrue(shader.contains("d * min(face, fill)"));
        assertTrue(shader.contains("vec3 mixedFill = optical27BlurFill("));
        assertTrue(shader.contains("if (u_useBlurredTexture == 1) {"));
        assertTrue(shader.contains("vec3 fillSample = texture2D("));
        assertEquals(.4f,blurFill(.4f,.2f,0f,0f,0f),.00001f);
        assertEquals(.34f,blurFill(.4f,.2f,.2f,.3f,0f),.00001f);
        assertEquals(.3f,blurFill(.4f,.2f,0f,0f,.5f),.00001f);
        assertEquals(.2f,blurFill(.4f,.2f,0f,0f,1f),.00001f);
    }

    @Test public void fourKnotBlurRampIsContinuousMonotoneAndBounded() {
        String shader=full();
        assertTrue(shader.contains("float optical27BlurRamp(float depth, float reach)"));
        assertTrue(shader.contains("0.43 * a - 0.33 * b - 0.24 * c"));
        assertEquals(1f,blurRamp(0,12),.00001f);
        assertEquals(0f,blurRamp(12,12),.00001f);
        float previous=1f;
        for(int i=0;i<=120;i++){
            float value=blurRamp(i*.1f,12f);
            assertTrue("blur weight bounded",value>=0f && value<=1f);
            assertTrue("no blur knee jumps",previous-value<.03f);
            assertTrue("radius monotone",value<=previous+.00001f);
            previous=value;
        }
    }

    @Test public void faceBleedAndShadowHaveSeparateAffineGrades() {
        String shader=full();
        assertTrue(shader.contains("vec3 optical27Grade(vec3 rgb, vec3 gain, vec3 bias)"));
        assertTrue(shader.contains("vec3 faceGain = mix(vec3(1.0),"));
        assertTrue(shader.contains("ringShadedGlass = optical27Grade(ringShadedGlass,"));
        assertTrue(shader.contains("optical27Grade(edgeTransmission,"));
        assertTrue(shader.contains("optical27Grade(u_castShadowColor.rgb,"));
        assertTrue(shader.contains("ringShadedGlass * glassAlpha"));
        assertTrue(shader.contains("outlineRgb * outlineAlpha"));
    }

    @Test public void opposingKeyFillIsMaskedToTheInsideAndNotAlphaCoupled() {
        String shader=full();
        assertTrue(shader.contains("float keyFacing = dot("));
        assertTrue(shader.contains("float keyFill = opticalOpposingFill("));
        assertTrue(shader.contains("float softKnee = keyFill /"));
        assertTrue(shader.contains("float keyMask = 1.0 - smoothstep("));
        assertTrue(shader.contains("surfaceDistance < 0.0"));
        assertFalse(shader.contains("outlineAlpha *= softKnee"));
        assertFalse(shader.contains("innerHighlightAlpha *= outlineAlpha"));
    }

    @Test public void keepsLegacyShadowRingEdgeBleedHoldAndTwoStageRefraction() {
        String shader=full();
        assertTrue(shader.contains("float innerBendPx = max(u_lensRefractionPx, 0.0)"));
        assertTrue(shader.contains("float outerBendPx = rimReflectPx * 0.35"));
        assertTrue(shader.contains("float ringInner = opticalErfcHalf("));
        assertTrue(shader.contains("float ringOuter = opticalErfcHalf("));
        assertTrue(shader.contains("float hold = opticalErfcHalf("));
        assertTrue(shader.contains("float bleedBand = 1.0 - smoothstep("));
        assertTrue(shader.contains("float outlineDistance = surfaceDistance"));
        assertTrue(shader.contains("u_backlitViewportInsetPx"));
        assertTrue(shader.contains("float exteriorShadowAlpha = 0.0;"));
        assertEquals(1,count(shader,"texture2D(u_backgroundTexture, rimUv).rgb;"));
    }
}
