# Baidu Input Method Glass Adaptation Design

## Goal

Add a LiquidDock Prismal glass replacement for Baidu Input Method (`com.baidu.input_mi`) with the same safety properties as the current Gboard adaptation: zero-copy PassBlur, stock visuals hidden only after the first presented frame, no fixed-delay lifecycle, and no hooks that depend on R8-obfuscated implementation names or hard-coded resource IDs.

## Reverse-engineering evidence

The analyzed APK manifest registers `com.content.input_mi.ImeService` as the exported `android.view.InputMethod` service. The service overrides stable framework methods including `setInputView(View)` and `onWindowShown()`. The concrete input rendering path contains obfuscated helper classes and a custom self-drawing input view, so those names are evidence only and must not become hook contracts.

The bundle also contains stable keyboard view classes (`com.content.simeji.inputview.KeyboardRegion`, `KeyboardContainer`, `InputView`) whose theme callbacks own stock keyboard backgrounds. These may be used only when discovered structurally at runtime; the adaptation must fail closed when a safe stock-background authority cannot be identified.

## Architecture

1. Scope LiquidDock to `com.baidu.input_mi` and install from `ModuleMain`.
2. Hook only the stable Baidu IME service class and stable lifecycle methods. `setInputView(View)` supplies the authoritative input view; `onWindowShown()` activates runtime resolution.
3. Resolve the rendered keyboard from the runtime view tree. Prefer stable keyboard-region/container classes when present; otherwise refuse to replace the stock visual instead of hooking obfuscated draw methods.
4. Insert a `TextureView` glass sink immediately below keyboard content in the same parent. Track geometry through layout/pre-draw callbacks, never fixed delays.
5. Bind the root to a dedicated `BAIDU_INPUTMETHOD` PassBlur domain and use the existing RootPassBlurBackend -> Prismal -> TextureView zero-copy pipeline.
6. Keep the stock background visible until `TextureView.onSurfaceTextureUpdated()` confirms a presented swap. Only then suppress the structurally claimed stock background. Restore it on detach/failure.

## Constraints

- No direct hook names derived from `xb9`, `e`, `ch9`, or other R8/proguard implementation names.
- No hard-coded `0x7f...` resource IDs.
- No `findViewById` dependency for ownership decisions.
- No `postDelayed` lifecycle workaround.
- No PixelCopy, ScreenCapture, Bitmap capture, or CPU readback.
- Failure must preserve the stock Baidu keyboard.
- Existing Gboard behavior must remain unchanged.

## First implementation boundary

Support only layouts where the stable keyboard-region/container path can be structurally identified and a distinct stock background can be safely suppressed. The direct self-drawing fallback remains unsupported in this change rather than introducing a global Canvas/Drawable interception.