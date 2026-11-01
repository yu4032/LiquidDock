# Workspace glass resource lifetime

## References inspected

- [BlurView RenderNodeBlurController](https://github.com/Dimezis/BlurView/blob/master/library/src/main/java/eightbitlab/com/blurview/RenderNodeBlurController.java): the controller owns a persistent RenderNode; transforms and captured content update separately.
- [Liquid Glass Easy shader resources](https://github.com/AhmeedGamil/liquid_glass_easy/blob/main/lib/src/widgets/lens/liquid_glass_shaders.dart): compiled programs are shared; uniform-bearing shader instances belong to individual lenses.
- [Liquid Glass Easy shape cache](https://github.com/AhmeedGamil/liquid_glass_easy/blob/main/lib/src/widgets/lens/liquid_glass_shape_cache.dart): caches local shape data independently of screen position, with reference counting and bounded texture size.
- [Liquid Glass Easy batch](https://github.com/AhmeedGamil/liquid_glass_easy/blob/main/lib/src/widgets/lens/liquid_glass_batch.dart): shares backdrop acquisition while preserving independent lens styles.

These projects use different rendering engines. Their performance claims are not LiquidDock measurements.

## Applied resource ownership

| Resource | Owner / update boundary |
| --- | --- |
| Workspace RuntimeShader programs | Workspace HWUI View; create on first binding, retire in clearBackdrops |
| BitmapShader inputs | Immutable published background image pair; rebind on image replacement |
| Wallpaper scale and logical dimensions | Background publication metadata; update on every replacement |
| Glass geometry and opacity | Current Launcher pre-draw snapshot |
| Background sampling coordinates | Launcher root space, including the current wallpaper transform |

Changing a background must not compile the same three programs again. The shortcut popup View already follows this ownership pattern; Workspace now follows it too.

Device regression check:

```
adb shell am instrument -w -e case workspaceShaderReuse com.hellovoid.liquiddock.test/com.hellovoid.liquiddock.SettingsSearchInstrumentation
```

The check uses real Android RuntimeShader objects. It verifies program identity across image replacement, replacement image/dimension/rotation publication, and program retirement/recreation after clear. It leaves Launcher preferences unchanged.

One device run measured replacement binding at about 26.7 ms before reuse and 0.03 ms after reuse. This measures binding CPU time, not complete animation frame time. Initial compilation remains separate.

## Further candidate: local geometry cache

Separate shape evaluation from background sampling. Cache only local geometric data, keyed by shape and material inputs; position and opacity should not invalidate it. Every displayed frame must still sample the background at the current root-space location.

Before adopting this structure:

- Verify scaling and radius changes. Rebuilding the cache every spring frame may cost more than direct evaluation.
- Verify data precision, filtering, premultiplication and color conversion. A cached SDF or normal map must not reintroduce edge bands or motion jitter.
- Bound GPU memory and retire resources with their consumers.
- Keep drag background sampling live. Caching shape data does not permit freezing the refracted background.
- Compare pixels and FrameTimeline on the target device before replacing the analytic path.
