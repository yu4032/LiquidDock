# Open-folder glass: frozen desktop snapshot (October 15, 2026)

## Source and visibility

`OPEN_FOLDER` captures the **composited desktop** at the beginning of the open gesture, before the first native `FolderCling` drawing pass. Neither the Launcher root surface nor the Dock is excluded from the PassBlur source. System chrome exclusions are retained. LiquidDock does not suppress the desktop or Dock on folder open merely for capture. This is a one-shot GPU texture; do not use `ScreenCapture`, `PixelCopy` or a software `Bitmap` capture.

A compositor frame is accepted only before the first `FolderCling` pre-draw. The first valid input is normalized/prepared then frozen and is reused by both opening and closing animations. If no safe frame arrives in this time window, keep native folder material rather than capture the already-open folder into itself. The source does not track live wallpapers, icon movement, or desktop changes after opening; that is intentional.

## Geometry and optics

The native Folme geometry supplies both directions; closing reverses the same frame coordinates and uses the frozen texture. Four **folder-only** optics controls (blur 0–60 px, IOR 100–200, thickness 1–60 dp, tint alpha 0–160) live under **Appearance → Folders**, registered with `ConfigSchema`/`PresetManager` and `LiveGlassConfigState`. Their defaults are 6 px, 1.55, 18 dp, and 35/255. These copy only the corresponding fields of a fresh immutable `PrismalParams`, leaving global glass/Dock optics unchanged. Live changes adjust the existing prepared texture without new capture.

## Regression boundaries

The existing shortcut popup and all other PassBlur domains must continue to exclude their own root. The native OS folder fade/interaction/close completion, invalidation and disposal remain intact. The Launcher workspace and Dock continue to render through their own pipelines. Acceptable fallback: no glass for an ultra-fast open when GPU capture cannot meet the first-draw deadline; never display a late self-sampling frame.

JVM focused tests: `OpenFolderGlassOpticsTest`, `PassBlurBindPolicyTest`, `OpenFolderBackdropPolicyTest`, `OpenFolderGlassStateTest`, `OpenFolderMotionStateTest` and `OpenFolderGlassGeometryTest`. Android CI and device visual validation required before merge.
