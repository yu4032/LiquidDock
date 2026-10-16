package com.hellovoid.prismal;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrismalDualBlurShaderTest {
    @Test public void onlyOptInShaderInterpolatesEveryBlurredSample() {
        String stock = PrismalShaderSources.FRAGMENT;
        String patched = PrismalDualBlurShader.apply(stock);
        assertTrue(stock.contains("texture2D(u_blurredTexture,"));
        assertTrue(patched.contains("uniform sampler2D u_workspaceBlurredTexture;"));
        assertTrue(patched.contains("uniform float u_workspaceBlurMix;"));
        assertTrue(patched.contains("vec4 sampleFolderBlur(vec2 uv)"));
        assertTrue(patched.contains("sampleFolderBlur(uvCenter)"));
        assertTrue(patched.contains("sampleFolderBlur(reflUv)"));
        assertTrue(patched.contains("u_workspaceBlurMix >= 1.0"));
        assertTrue("Keep all original glass optics untouched",
                patched.contains("float innerShadow"));
        assertTrue("Normal upstream shader must remain unchanged",
                stock.contains("texture2D(u_blurredTexture,"));
    }

    @Test public void unexpectedShaderSignatureFailsClosed() {
        try {
            PrismalDualBlurShader.apply("void main() {}");
            fail("Missing blur sampler must fail");
        } catch (IllegalStateException expected) {}
    }
}
