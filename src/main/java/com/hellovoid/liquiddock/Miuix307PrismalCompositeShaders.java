package com.hellovoid.liquiddock;

/** LiquidDock-only final crop from the portable Prismal framebuffer into the visible Dock view. */
final class Miuix307PrismalCompositeShaders {
    static final String FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec4 uCropRect;
            varying vec2 vUv;
            void main() {
                vec2 uv = uCropRect.xy + vUv * uCropRect.zw;
                gl_FragColor = texture2D(uTexture, uv);
            }
            """;

    /** File-scoped dual blur for the full-screen wallpaper layer; the main
     * glass shader uses the same native Folme progress, with no added pass.
     */
    static final String FOLDER_DUAL_BLUR_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform sampler2D uWorkspaceTexture;
            uniform float uBlurMix;
            uniform vec4 uCropRect;
            varying vec2 vUv;
            void main() {
                vec2 uv = uCropRect.xy + vUv * uCropRect.zw;
                vec4 folder = texture2D(uTexture, uv);
                if (uBlurMix <= 0.0) {
                    gl_FragColor = folder;
                } else if (uBlurMix >= 1.0) {
                    gl_FragColor = texture2D(uWorkspaceTexture, uv);
                } else {
                    gl_FragColor = mix(folder, texture2D(uWorkspaceTexture, uv), uBlurMix);
                }
            }
            """;

    private Miuix307PrismalCompositeShaders() {}
}
