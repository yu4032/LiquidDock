# Launcher Icon/Widget Glass + Surface Alignment Design

## Problem

The single-root static compositor substantially reduced Launcher glass power use, but device validation still shows a small uniform leftward wallpaper sampling bias. The same compositor should now support widgets and app icons without returning to per-object TextureView/Surface ownership.

## Alignment diagnosis

Static node geometry is already expressed in stable-root coordinates by mapping the complete local View corners through `transformMatrixToGlobal()` and the inverse stable-root matrix. It no longer uses clipped visible rectangles. A uniform residual offset across all static nodes therefore points upstream, at the wallpaper producer normalization.

`LauncherGlassSession.renderNormalizationRoot()` currently maps the entire OES texture (`uBackdropRect = 0,0,1,1`) onto the Decor/root content dimensions. The OES producer, however, is bound to the ViewRoot surface buffer. Android ViewRoot may allocate surface insets around the actual window content. Ignoring those surface insets shifts the content-to-texture mapping by a small constant amount.

The fix is to read `ViewRootImpl.mWindowAttributes.surfaceInsets`, convert the content rectangle to normalized buffer UVs, and use that rectangle for root normalization. The correction is data-derived; no empirical pixel offset is allowed. If reflection is unavailable or the insets are invalid, fall back to the current full-buffer rect and log the fallback.

The OES texture matrix / crop compensation shader is deliberately left unchanged in this change. If hardware validation later reports residual bias while measured surface insets are zero, that becomes a separate crop-matrix investigation rather than combining two coordinate corrections.

## Surface-content mapping

Add a pure `LauncherGlassSurfaceContentRect` helper. For a surface buffer `W x H` and insets `(L,T,R,B)`, the content rectangle in GL/root UV convention is:

- left = `L / W`
- bottom = `B / H`
- width = `(W - L - R) / W`
- height = `(H - T - B) / H`

Insets are clamped so malformed values cannot create a negative or empty content area. Zero insets produce exactly `(0,0,1,1)`.

`ProducerGeometry` carries this content UV rect and treats changes in it as producer geometry changes. `renderNormalizationRoot()` uploads that rect to `uBackdropRect` while retaining `uValidDockRect = 0,0,1,1` and the existing `uTexMatrix`/rotation path.

## Static material architecture

Keep exactly one static root `LauncherGlassStaticLayer` and one shared `LauncherGlassSession` producer/backdrop. Folder, widget and icon objects are represented only by lightweight `LauncherGlassStaticNode` instances.

Generalize the node with `LauncherGlassDragState.Kind`:

- `FOLDER`: current full material-host bounds and current folder corner policy.
- `WIDGET`: full widget-host bounds, using native corner radius when discoverable and a conservative proportional fallback otherwise.
- `ICON`: a local visual sub-rectangle representing the actual icon graphic, not the full ShortcutIcon label View.

No new static TextureView, Surface, EGLSurface, producer, FBO or wallpaper source is created per widget/icon.

## Icon geometry

Device logs show a `ShortcutIcon` View includes its label (for example 203x233 or 196x230), while the rendered icon graphic is materially smaller (about 173x173 or 163x163). Therefore the full View bounds are invalid for icon glass.

Prefer Android/MIUI drawable geometry over a hard-coded icon size:

1. If the host is a `TextView`, inspect its compound drawables and use the top drawable bounds/intrinsic size.
2. Resolve the drawable's local placement from the TextView's compound/padding layout, centered horizontally and within the drawable content area.
3. If the drawable cannot provide a valid square/rect, use a conservative centered square derived from the host width and the non-label portion rather than extending through the label.

Encapsulate this in `LauncherGlassIconGeometry` so the fallback is pure/testable and static-node code remains generic.

The root static layer is inserted below Launcher content. The icon drawable and label are not made transparent or replaced. Consequently opaque icon pixels naturally cover the glass, while transparent icon pixels reveal the glass underneath—exactly the requested "transparent icon over glass bottom" behavior.

## Widget discovery

Install a dedicated `MiuixLauncherStaticGlassHook` for concrete Launcher classes, avoiding global View hooks:

- `com.miui.home.launcher.LauncherAppWidgetHostView`
- `com.miui.home.launcher.maml.MaMlHostView`
- `com.miui.home.launcher.ShortcutIcon`

Hook the concrete classes' declared constructors. After the original constructor returns, install a per-instance attach listener and schedule binding after layout when dimensions are valid. Repeated constructor/attach callbacks must resolve to the same weak static-node binding.

Widgets keep all original child drawing and interaction. The shared static glass is only behind the widget host, so transparent widget regions expose the glass and opaque widget content remains unchanged.

## Drag lifecycle

The existing `LauncherGlassDragOverlay` remains the only moving glass Surface. Extend source resolution so static nodes for widgets and icons are also suppressed while their corresponding DragView is active, then restored at drag end. Do not create a second moving material.

The drag hook installation gate becomes `folderEnabled || widgetEnabled || iconEnabled` under master Liquid Glass, instead of being folder-only.

## Configuration and UI

Add exported booleans, default `true` under the master Liquid Glass switch:

- `liquid_widget_glass`
- `liquid_icon_glass`

Expose both in Compose and legacy settings. Folder radius remains folder-specific. The new toggles do not alter Dock behavior.

## Constraints

- GPU-only wallpaper source.
- No PixelCopy, ImageReader, Bitmap capture/readback, `glReadPixels`, or screen recording.
- No per-object static Surface/TextureView.
- No empirical alignment offset constant.
- Do not modify Dock PassBlur ownership.
- Do not change Prismal optics merely to compensate geometry.
- Preserve existing Folder press/open/drag behavior.

## Verification

Automated gates must cover:

- surface-inset UV conversion, including zero and asymmetric insets;
- Session reads `surfaceInsets` and no longer hardcodes full-buffer root mapping;
- generic static node kinds remain lightweight;
- concrete ShortcutIcon/widget hooks exist without a global View attach hook;
- icon glass uses a visual sub-rect rather than the label View bounds;
- widget/icon static nodes share the existing root static layer;
- drag suppression covers folder/widget/icon;
- config, export/import and both settings UIs;
- complete unit suite and `assembleDebug`.

Hardware validation after compilation should confirm exact wallpaper alignment, visual icon-glass placement, widget transparency behavior, page-scroll smoothness, and power characteristics.