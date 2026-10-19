# QuartzCore reconstruction → Prismal stage mapping (experimental)

Source: [medfa12/liquid-glass-react-native](https://github.com/medfa12/liquid-glass-react-native),
macOS 27 research shader `src/liquid_glass_27.glsl` (MIT, license copied in
`docs/licenses/LiquidGlassReactNative-MIT.txt`).

**Purpose:** reproduce the order and *classes* of optical operations while
retaining HyperOS Launcher geometry, native PassBlur zero-copy texture ownership,
original Prismal body shading, full-resolution output, and Dock OEM MiShadow.
Not an Apple source-code port and **not** a pixel-fit validation of iOS 27.

| Reconstructed stage | LiquidDock implementation | Constraint / difference |
| --- | --- | --- |
| supercircle SDF + normalized derivative | Existing native rounded-box SDF, derivative-corrected normal and `fwidth` | **Do not replace** native Dock geometry with macOS supercircle |
| shadow then ring | Existing external cast shadow for non-Dock scopes; inner analytic `erfc` ring | Dock continues to use OEM MiShadow; no duplicate external shadow |
| inner refraction + blur ramp | Original Prismal lens + two-stage lens arc; optional `optical27BlurRamp` | Four-knot, sharp/Gaussian **two-level** approximation; GLES2 has no `textureLod` |
| seven-sample chromatic aberration | `optical27SevenTap` replaces three-tap branch in optional program | **7 samples only when chromatic aberration is enabled**; otherwise original single tap |
| blur fill: lighten/darken/normal | `optical27BlurFill` with partition-of-unity color mixing | Near the inside edge only; reuses existing Gaussian texture |
| outer refraction crossfade | `innerBendPx`, `outerBendPx`, `refractionHandoff` | One prepared-source sample for the optical contour |
| opposing key/fill highlight | `opticalOpposingFill`, signed normal and masked soft knee | Uses OS4 light uniforms; separate original inner highlight |
| edge bleed | SDF-distance `bleedBand` shading and `optical27Grade` | Reuses optical edge sample, not an additional offscreen blur |
| face, bleed, shadow matrices | `optical27Grade` with individual dynamic gain/bias recipes | Mapped to existing material/tint inputs; not 63 private macOS uniforms |
| cover/AA + holding tone | Existing SDF alpha and independent inner/outer layers; `opticalErfcHalf` hold | Preserve output alpha compositing and Dock-only viewport alignment |
| post-coverage glint | Bounded exponential inner reflection | Does not overwrite external refracted outline |

The full experimental GLES2 shader is attempted **only** for nodes with optical
decoration or optional cast shadow. A compile/link failure falls back to the
smaller, previously tested two-band optical shader; that shader can itself fall
back to the baseline Prismal program. The main compiled baseline is unchanged.

No new FBO, TextureView, Surface, screenshot, or capture path is introduced.
Seven chromatic taps and near-edge blur-fill incur extra fragment work when
their conditions are enabled. Verify device shader logs:
`LiquidDock-Prismal`: "Full reconstructed-27 optical stages ready" or
"Experimental reconstructed-27 shader unavailable".

**QA:** build/unit tests verify transformation anchors and math, not Mali
runtime GLSL acceptance. Profile frame time on high refresh-rate devices;
compare to the solid-lavender iOS reference for a continuous neutral outer
contour enclosing an inner specular highlight around every corner. The actual
shader parameters and sRGB gamma response remain platform-specific.
