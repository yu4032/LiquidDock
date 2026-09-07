# LiquidDock Third-Party Notices

LiquidDock uses or compiles against the following open-source projects:

- **Compose Miuix 0.9.3** (`miuix-ui-android`, `miuix-preference-android`) — Apache License 2.0 — <https://github.com/compose-miuix-ui/miuix>
- **AndroidX Activity Compose 1.13.0 / Preference 1.2.1 / AppCompat 1.7.0** — Apache License 2.0 — <https://source.android.com/docs/setup/about/licenses>
- **LSPosed / libxposed API 101.0.1 + service 101.0.0** — GPL-3.0 — <https://github.com/LSPosed/LSPosed>

Implementation and interaction references:

- **HyperCeiler** — HyperOS module engineering and settings-organization reference — GPL-3.0 — <https://github.com/ReChronoRain/HyperCeiler>
- **Prismal** — bundled/adapted standalone optical-renderer module plus shader/parameter-design reference — MIT License — <https://github.com/styropyr0/Prismal>

Historical reference:

- **HyperLight** informed experiments in the retired 1.x screenshot/capture implementation. The current `main` does **not** use that ScreenCapture/bitmap pipeline; v2.x uses HyperOS MiuiX PassBlur + OES/GLES zero-copy rendering.

LiquidDock also reflects and calls private HyperOS/MIUI framework APIs at runtime for compatibility with the target ROM. Those platform APIs are not bundled third-party source code.

The current Workspace quality controls do not bundle a replacement PassBlur implementation: LiquidDock owns the bridge, OES consumer and local Prismal/FBO pipeline while HyperOS/MIUI remains the native PassBlur producer.

The names and trademarks of referenced projects belong to their respective owners.