# GUI specular fidelity audit — PrismalAGSL v1.0.4 (2026-10-11)

## Status

**The previous PR #352 directional highlight was conceptually wrong.** It assumed a single upper-left hotspot that decays across the entire rectangle. The actual upstream specular is determined by the **signed-distance gradient of the rounded-rect boundary**, not by the global pixel distance from a light source. PR #352 remains Draft until device testing, and the proven performance improvements from the static Cell conversion must be retained.

Authoritative upstream source pinned to the exact dependency version:
- `Prismal/src/main/assets/agsl/default_specular.agsl`
- `Prismal/src/main/assets/agsl/ambient_specular.agsl`
- `Prismal/src/main/assets/agsl/rounded_rect_sdf.agsl`
- `Prismal/src/main/java/com/styropyr0/prismal/specular/PrismalSpecularStyle.kt`
- `Prismal/src/main/java/com/styropyr0/prismal/specular/PrismalSpecularModifier.kt`

Repository: https://github.com/styropyr0/PrismalAGSL/tree/v1.0.4

## What Prismal actually computes

**Default (Cards / PrismalGlassButton / PrismalGlassStepper)**:
```glsl
float2 grad = prismalGradSdRoundRect(centeredCoord, halfSize, gradRadius);
float2 normal = float2(cos(angle), sin(angle)); // angle = 45 degrees
float d = dot(grad, normal);
float intensity = pow(abs(d), falloff); // falloff = 1
return color * intensity;
```
The `abs` makes the lobes **diagonally symmetric**: the **upper-left and lower-right rounded corners are both bright**, while **upper-right and lower-left are low-intensity**. Straight sides have intermediate intensity. This is a per-boundary-normal illumination field, **not** a white band that fades monotonically from one position.

The default style uses `Color.White.copy(alpha=0.5f)`, `BlendMode.Plus` and a stroke with default width `0.5dp` and blur `width/2`; the native specular node clips to the actual glass outline and internally records a GraphicsLayer. Our baked solution removes that per-Cell layer but approximates its shape-edge width, and does not pretend that a 9-patch exactly reproduces blur and additive blending.

**Ambient (Slider / Toggle thumbs)**:
```glsl
float d = dot(prismalGradSdRoundRect(...), float2(cos(45°), sin(45°)));
float intensity = pow(abs(d), 1.0);
float t = step(0.0, d);
return half4(t, t, t, 1.0) * intensity;
```
This additionally separates the **bright bottom/right-facing normal hemisphere** from the **dark top/left-facing normal hemisphere**. The style defaults to `intensity=0.38` and standard SrcOver blending. Upstream slider and toggle pass `PrismalSpecular.Ambient.copy(width=width/1.5, blurRadius=blurRadius/1.5, alpha=pressProgress)`, so **the true dynamic reflection is press-dependent and approaches zero alpha at rest**. It differs from the static preset idle thumb, which deliberately supplies a low-cost Ambient-like visible contour.

## Fixed palette and new maps

The GUI has fixed theme canvases in `src/main/res/values[-night]/gui_preset_material_colors.xml`. Flat material fills stay consistent with them. Unlike the previous uniformly bright outlines, six transparent 9-patch assets in `drawable[-night]-xxhdpi` encode the normal-based specular:

- `gui_specular_card.9.png` — Default 45-degree `abs(dot)` SDF normal distribution, 24dp reference round-rect corners.
- `gui_specular_pill.9.png` — Default 45-degree `abs(dot)`, rounded capsule with 18dp reference corners.
- `gui_specular_thumb.9.png` — Ambient signed-white/black lobe, 12dp capsule reference corners.

Precomputation evaluates the same `prismalGradSdRoundRect` normal selection and `abs(dot)`, with a small inside-edge falloff approximating a clipped specular stroke. `Default` uses a white alpha palette; `Ambient` uses white/black alpha palettes depending on signed `d`. Stretch bands on the straight sides extend different-length controls while preserving corner lobes.

The six Android resources cover light/dark qualifying directories; the normal-based intensity formula itself is theme-independent, therefore the image byte content is currently identical between day and night. The *underlying pure fill* still differs by theme. This is deliberate, not independently invented lighting.

## Runtime and performance

`GuiPresetChromePalette` still loads one backdrop-free specular drawable per shape at the Scaffold, and Cells use a pure-color `Modifier.background` + a light 9-patch overlay without allocating a separate composable. The 9-patch draw adds a tiny bitmap operation; it does **not** reintroduce per-Cell Prismal AGSL, blur/refraction, dynamic highlight shader, or backdrop capture.

**Active slider/toggle thumbs retain the original Prismal Ambient renderer without an additional baked overlay**, avoiding double specular and preserving its `alpha=pressProgress` behavior. **Idle thumbs** alone use the baked Ambient-inspired style. This is a conscious static-versus-dynamic visual distinction, not pixel-exact parity.

The top header/actions and bottom navigation remain Prismal-driven and are outside this optimization.

## Validation

Static architecture and PNG-byte tests now require:
- Default card/pill maps: both diagonal corners (top-left **and bottom-right**) are bright; other diagonal corners weaker.
- Ambient thumb map: the top-left hemisphere is dark, bottom-right is white.
- No accidental duplicated 9-patch reflection on top of the native touch-time Prismal Ambient.
- No changes to GUI touch-sampling logic or top/bottom navigation source ownership.

These tests verify the geometric lighting convention and build-time resource validity, **not** exact GPU shader equivalence. Verify in-app: natural highlights on long and short Cells, capsule stretch behavior, thumb press/drag/release, light/dark backgrounds, and dense-list GPU load before merging.

Do not merge before user confirmation. PR #340 remains independent.
