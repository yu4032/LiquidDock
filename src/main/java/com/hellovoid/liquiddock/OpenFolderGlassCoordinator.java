package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;

/** Wallpaper-only native folder glass with matched open/close geometry. */
final class OpenFolderGlassCoordinator {
    private static final String TAG = "[DC][OpenFolderGlass]";
    private static final OpenFolderGlassState OWNER = new OpenFolderGlassState();
    private static State current;

    private OpenFolderGlassCoordinator() {}

    static boolean followsNativeWorkspaceFade() {
        State state = current;
        // Launcher dispatches FolderIcon.onOpen / FolderStatus before the
        // FolderCling.open callback installs cling. Prepared source ownership
        // is already sufficient to follow native Workspace alpha.
        return state != null && live(state);
    }

    static void onTouch(ViewGroup icon, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) prepare(icon);
        else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelUnused(icon);
        else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            State state = current;
            // Touch may have been a press/drag rather than a folder open.
            // Cancel unused capture without delaying Launcher.openFolder.
            icon.postDelayed(() -> { if (current == state) cancelUnused(icon); }, 300L);
        }
    }

    /** Accessibility and programmatic launches may bypass icon touch events. */
    static void ensurePrepared(ViewGroup icon, Object folderInfo) {
        State state = current;
        if (state == null || state.icon.get() != icon) prepare(icon);
        state = current;
        if (state != null && state.icon.get() == icon) state.expectedFolderInfo = folderInfo;
    }

    static void prepare(ViewGroup sourceIcon) {
        if (!OpenFolderGlassHook.isInstalled() || !enabled(sourceIcon)
                || !LauncherGlassHierarchy.isWorkspace(sourceIcon)) return;
        releaseAll();
        View root = sourceIcon.getRootView();
        LiquidDockConfig.Glass glass = LiveGlassConfigState.currentGlass();
        if (glass == null || !root.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        RootPassBlurEndpointBridge.Endpoint endpoint = RootPassBlurEndpointBridge.inspect(root);
        if (endpoint == null || !endpoint.isValid()) return;
        String launcherSurfaceName = Miuix307PassBlurBridge.surfaceName(endpoint.rootSurface);
        // Fail closed if the exact Launcher layer cannot be excluded.
        if (launcherSurfaceName.isBlank()) return;
        State state = new State(root, sourceIcon, OWNER.prepare(), glass);
        current = state;
        root.addOnAttachStateChangeListener(state.attachListener);
        state.source = ShortcutPopupSourceOverlay.attachToLauncherBounds(root, new ShortcutPopupSourceOverlay.Listener() {
            @Override public void onAttached(ShortcutPopupSourceOverlay source) {
                if (!live(state)) return;
                state.session = new ShortcutPopupGlassSession(source, glass, new ShortcutPopupGlassSession.Listener() {
                    @Override public void onBackdropReady() {
                        if (live(state)) OWNER.onBackdropReady(state.serial);
                    }
                    @Override public void onPresented() {
                        if (!live(state) || !OWNER.onPresented(state.serial)) return;
                        if (state.layer != null) state.layer.reveal();
                        state.materials.invalidateAll();
                        claimNativeBlur(state);
                        View root = state.root.get();
                        if (root != null) root.postInvalidateOnAnimation();
                    }
                    @Override public void onFailure(Throwable error) {
                        if (live(state)) {
                            MainHook.log(TAG + " fallback: " + error);
                            failOpen(state);
                        }
                    }
                }, PassBlurBindRequest.openFolder(source, launcherSurfaceName));
                attachOutputLayer(state);
                state.session.requestInitialCapture();
            }
            @Override public void onAttachFailed(Throwable error) { if (live(state)) failOpen(state); }
        }, "LiquidDockFolderBackdropSource");
        if (state.source == null && live(state)) releaseAll();
    }

    private static void failOpen(State state) {
        if (live(state)) releaseAll();
    }

    /** Keep glass below FolderCling content, but outside its visibility lifecycle.
     * On native close the OEM hides FolderCling before its background spring ends.
     * A TextureView child of FolderCling would vanish during that spring.
     */
    private static void attachOutputLayer(State state) {
        if (!live(state) || state.layer != null || state.session == null) return;
        View cling = state.cling.get();
        if (cling == null) return;
        ViewParent parent = cling.getParent();
        if (!(parent instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) parent;
        int position = host.indexOfChild(cling);
        if (position < 0) return;
        ShortcutPopupGlassLayer layer = new ShortcutPopupGlassLayer(cling.getContext(), state.session);
        state.layerHost = new WeakReference<>(host);
        state.layer = layer;
        host.addView(layer, position, new ViewGroup.LayoutParams(state.width, state.height));
        host.postInvalidateOnAnimation();
    }

    static void onOpening(View folderCling) {
        State state = current;
        if (state == null || state.root.get() != folderCling.getRootView()) return;
        View sourceIcon = state.icon.get();
        View folder = view(invoke(folderCling, "getFolder"));
        Object info = folder != null ? invoke(folder, "getInfo") : null;
        if (info == null) info = field(folder, "mInfo");
        Object sourceFolder = invoke(sourceIcon, "getFolder");
        // Launcher.openFolder supplies the authoritative source/info pair. FolderInfo.icon
        // is not a stable OEM field and must not invalidate a valid opened-folder owner.
        if (folder == null || !(folderCling instanceof ViewGroup) || !enabled(sourceIcon)
                || (sourceFolder != null && sourceFolder != folder)
                || (state.expectedFolderInfo != null && info != null
                        && state.expectedFolderInfo != info)) {
            MainHook.log(TAG + " native folder/source mismatch; retaining OEM material");
            releaseAll();
            return;
        }
        if (!OWNER.beginOpen(state.serial)) { releaseAll(); return; }
        if (state.session != null && state.session.hasFrozenBackdrop()) {
            OWNER.onBackdropReady(state.serial);
        }
        state.cling = new WeakReference<>(folderCling);
        state.folder = new WeakReference<>(folder);
        state.closedFrame = bounds(image(sourceIcon), state);
        state.geometry.acceptAnimation(state.closedFrame, state.width, state.height);
        attachOutputLayer(state);
        state.observer = state.root.get().getViewTreeObserver();
        // Wallpaper-only source stays safe throughout native opening.
        // Do not reject its first GPU frame just because FolderCling drew first.
        state.preDraw = () -> { sync(state); return true; };
        state.observer.addOnPreDrawListener(state.preDraw);
        state.root.get().postInvalidateOnAnimation();
    }

    static void onAnimationHost(View folderCling) {
        State state = current;
        if (state != null && state.root.get() == folderCling.getRootView()) {
            state.animationCling = new WeakReference<>(folderCling);
        }
    }

    static void onOpenFinished(View folderCling) {
        State state = current;
        if (!matches(state, folderCling)) return;
        state.motion.onContentFinished();
        sync(state);
    }

    /** Native endpoint transforms shared by open and close. */
    static void onAnimationSpec(boolean opening, View icon, View grid, int[] source, int[] destination, int inset) {
        State state = current;
        if (state == null || opening == state.motion.isClosing()
                || image(icon) != animationImage(state) || source == null || destination == null) return;
        View image = image(icon);
        Object size = invoke(image, "getOriginalSize");
        int[] original = size instanceof int[] ? (int[]) size : null;
        Drawable drawable = image instanceof ImageView ? ((ImageView) image).getDrawable() : null;
        boolean rounded = drawable != null && drawable.getClass().getName().contains("RoundedBitmapDrawable");
        state.nativeSourceWidth = original != null ? original[0] : image.getWidth();
        state.nativeSourceHeight = original != null ? original[1] : image.getHeight();
        state.nativeTargetWidth = grid.getWidth();
        state.nativeTargetHeight = Math.max(grid.getHeight(), grid.getWidth() / 2);
        // The OEM non-rounded animation interpolates scale, not layout width.
        state.nativeExpandedScaleX = !rounded && image.getWidth() > 0
                ? grid.getWidth() / (float) image.getWidth() : Float.NaN;
        state.nativeTargetX = destination[0] - source[0];
        state.nativeTargetY = destination[1] - source[1];
        if (rounded && original != null) {
            state.nativeTargetX += grid.getWidth() / 2f - original[0] / 2f - inset;
            state.nativeTargetY += state.nativeTargetHeight / 2f - original[1] / 2f;
        }
        state.animationSpecReady = true;
    }

    static void onClosing(View folderCling, boolean animated) {
        State state = current;
        if (!matches(state, folderCling)) return;
        state.close.begin(state.serial, animated);
        // Freeze the expanded destination: native Folder.onClose hides the
        // content background before its reverse Folme transition. Reading its
        // bounds afterward would collapse the final panel to the icon size.
        if (state.expandedFrame == null) {
            View folder = state.folder.get();
            View panel = view(field(folder, "mBackgroundView"));
            if (panel == null) panel = view(invoke(folder, "getContent"));
            state.expandedFrame = panel != null ? bounds(panel, state) : null;
        }
        OWNER.beginClose(state.serial);
        state.motion.beginClose();
        // Native reverse spring supplies geometry, but from its first frame
        // the prepared wallpaper must use Workspace rather than folder blur.
        // Re-blur the frozen input once; never recapture a desktop-in-folder frame.
        if (state.session != null) state.session.beginFolderClose();
        onClosingAnimationMaterialUpdated();
    }

    /** The OEM close spring may replace its temporary anim-image drawable. */
    static void onClosingAnimationMaterialUpdated() {
        State state = current;
        if (state == null || !state.motion.isClosing() || !OWNER.maySuppressNative()) return;
        View anim = animationImage(state);
        if (anim instanceof ImageView) {
            track(state, ((ImageView) anim).getDrawable());
        }
        if (anim != null) track(state, anim.getBackground());
        claimNativeBlur(state);
    }

    static long closeSerial(View folder) {
        State state = current;
        return state != null && state.folder.get() == folder ? state.serial : 0L;
    }

    static void onClosed(long serial) {
        State state = current;
        if (state != null && serial == state.serial && OWNER.owns(serial)
                && state.close.contentFinished(serial)) releaseAll();
    }

    static long activeClosingSerial() {
        State state = current;
        return state != null && state.motion.isClosing() ? state.serial : 0L;
    }

    static void onNativeBackgroundCloseFinished(long serial) {
        State state = current;
        if (state != null && serial != 0L && state.serial == serial && OWNER.owns(serial)
                && state.close.backdropFinished(serial)) releaseAll();
    }

    static void releaseIfStyle(boolean small) {
        State state = current;
        if (state != null && isSmall(state.icon.get()) == small) releaseAll();
    }

    static Runnable closeCompletion(long serial) {
        return OWNER.guardCompletion(serial, () -> onClosed(serial));
    }

    static boolean shouldSuppress(Drawable drawable) {
        State state = current;
        return state != null && state.materials.shouldSuppress(drawable);
    }

    static void rememberNativeBlur(View material, Object[] args) {
        State state = current;
        if (state == null || material != animationImage(state) || args == null) return;
        state.blurRequests.put(material, args.clone());
        if (OWNER.maySuppressNative()) claimNativeBlur(state);
    }

    static void releaseAll() {
        State state = current;
        current = null;
        OWNER.release();
        if (state == null) return;
        state.materials.invalidateAll();
        if (state.observer != null && state.preDraw != null && state.observer.isAlive()) {
            state.observer.removeOnPreDrawListener(state.preDraw);
        }
        View root = state.root.get();
        if (root != null) root.removeOnAttachStateChangeListener(state.attachListener);
        if (state.layer != null) state.layer.dispose();
        if (state.session != null) state.session.shutdown();
        if (state.source != null) state.source.dispose();
        for (View material : state.claimedBlur.keySet()) LauncherWidgetNativeViewBlurGate.releaseFolder(material);
        state.claimedBlur.clear();
        state.blurRequests.clear();
        state.geometry.reset();
        View cling = state.cling.get();
        if (cling != null) cling.postInvalidateOnAnimation();
    }

    private static void sync(State state) {
        if (!live(state)) return;
        try {
            View root = state.root.get(), cling = state.cling.get(), folder = state.folder.get();
            ViewGroup host = state.layerHost.get();
            if (root == null || cling == null || folder == null || !root.isAttachedToWindow()
                    || root.getWidth() != state.width || root.getHeight() != state.height
                    || (!state.motion.isClosing() && !cling.isAttachedToWindow())
                    || (!state.motion.isClosing() && cling.getVisibility() != View.VISIBLE)
                    || (root.getDisplay() != null && root.getDisplay().getRotation() != state.rotation)
                    || !enabled(state.icon.get())) { releaseAll(); return; }
            // Native close can hide or detach FolderCling while its separate
            // background proxy keeps animating. The sibling output stays live.
            if (state.session == null) return;
            attachOutputLayer(state);
            host = state.layerHost.get();
            if (state.layer == null || host == null || !host.isAttachedToWindow()) return;
            // Align the stable sibling output to the Launcher root, not to
            // FolderCling (whose visibility and transform change during close).
            float[] basis = state.basis;
            basis[0] = basis[1] = basis[3] = basis[4] = 0f;
            basis[2] = basis[5] = 1f;
            Matrix matrix = state.toGlobal;
            matrix.reset();
            host.transformMatrixToGlobal(matrix);
            matrix.mapPoints(basis);
            state.rootToGlobal.reset();
            root.transformMatrixToGlobal(state.rootToGlobal);
            if (!state.rootToGlobal.invert(state.toRoot)) { releaseAll(); return; }
            state.toRoot.mapPoints(basis);
            if (Math.abs(basis[2] - basis[0] - 1f) > 0.001f
                    || Math.abs(basis[3] - basis[1]) > 0.001f
                    || Math.abs(basis[4] - basis[0]) > 0.001f
                    || Math.abs(basis[5] - basis[1] - 1f) > 0.001f) { releaseAll(); return; }
            // The sibling host normally stays fixed. Avoid setting identical
            // RenderNode transforms on every root pre-draw (which can schedule
            // redundant compositions during the closing spring).
            if (Math.abs(state.layer.getTranslationX() + basis[0]) > 0.01f) {
                state.layer.setTranslationX(-basis[0]);
            }
            if (Math.abs(state.layer.getTranslationY() + basis[1]) > 0.01f) {
                state.layer.setTranslationY(-basis[1]);
            }
            LiquidDockConfig.Glass latest = LiveGlassConfigState.currentGlass();
            if (latest != null && latest != state.glass) {
                state.glass = latest;
                state.session.applyLiveGlassConfig(latest);
            }

            View animImage = animationImage(state);
            View panel = view(field(folder, "mBackgroundView"));
            if (panel == null || panel.getWidth() <= 0 || panel.getHeight() <= 0) panel = view(invoke(folder, "getContent"));
            OpenFolderGlassGeometry.Frame target = state.motion.isClosing() ? state.expandedFrame : bounds(panel, state);
            if (target != null && !state.motion.isClosing()) state.expandedFrame = target;
            float observed = Float.NaN;
            if (state.animationSpecReady && animImage != null) {
                observed = OpenFolderBackdropPolicy.nativeVisualProgress(
                        animImage.getTranslationX(), animImage.getTranslationY(),
                        state.nativeTargetX, state.nativeTargetY,
                        animImage.getScaleX(), state.nativeExpandedScaleX);
                if (!Float.isFinite(observed) && (state.nativeTargetWidth != state.nativeSourceWidth
                        || state.nativeTargetHeight != state.nativeSourceHeight)) {
                    observed = OpenFolderBackdropPolicy.openProgress(state.nativeSourceWidth, state.nativeTargetWidth,
                            animImage.getWidth(), state.nativeSourceHeight, state.nativeTargetHeight, animImage.getHeight());
                }
            }
            float amount = state.motion.sample(observed);
            OpenFolderGlassGeometry.Frame frame = state.geometry.acceptProgress(
                    state.closedFrame, target, amount, state.width, state.height);
            if (frame == null) return;
            state.materials.beginFrame(state.serial);
            track(state, folder.getBackground());
            track(state, panel != null ? panel.getBackground() : null);
            if (animImage instanceof ImageView) track(state, ((ImageView) animImage).getDrawable());
            if (animImage != null) {
                track(state, animImage.getBackground());
                // Native close may rebind its animation View independently of
                // setIconBgAnim/setFolderIconBlur. Claim the new RenderNode in
                // this same pre-draw, before its first visible composition.
                if (OWNER.maySuppressNative() && !state.claimedBlur.containsKey(animImage)) {
                    claimNativeBlur(state);
                }
            }
            state.materials.endFrame(state.serial);
            state.session.updateFolderFrame(LauncherGlassGeometry.resolveStatic(
                    state.width, state.height, frame.left, frame.top, frame.right, frame.bottom, frame.radius), amount);
        } catch (Throwable error) {
            MainHook.log(TAG + " geometry fallback: " + error);
            releaseAll();
        }
    }

    private static void track(State state, Drawable drawable) {
        if (drawable == null || !OpenFolderGlassHook.observeDrawable(drawable)) return;
        state.materials.track(state.serial, drawable, drawable::invalidateSelf);
    }

    private static void claimNativeBlur(State state) {
        for (Map.Entry<View, Object[]> entry : state.blurRequests.entrySet()) {
            View material = entry.getKey();
            if (LauncherWidgetNativeViewBlurGate.claimFolder(material)) {
                LauncherWidgetNativeViewBlurGate.rememberFolderInitializer(material, entry.getValue());
                state.claimedBlur.put(material, Boolean.TRUE);
            }
        }
        // FolderAnimController may create or rebind the temporary closing icon
        // without another BlurUtilities.setFolderIconBlur call. Suppress its
        // RenderNode output too, while preserving the native spring geometry.
        View anim = animationImage(state);
        if (anim != null && !state.claimedBlur.containsKey(anim)
                && LauncherWidgetNativeViewBlurGate.claimFolder(anim)) {
            state.claimedBlur.put(anim, Boolean.TRUE);
        }
    }

    private static void cancelUnused(View icon) {
        State state = current;
        if (state != null && state.icon.get() == icon
                && state.cling.get() == null) releaseAll();
    }

    private static boolean live(State state) { return current == state && OWNER.owns(state.serial); }
    private static boolean matches(State state, View cling) { return state != null && state.cling.get() == cling; }
    private static boolean enabled(View icon) {
        if (icon == null || !GlassRuntimeState.isEnabled()) return false;
        return isSmall(icon) ? GlassRuntimeState.isSmallFolderEnabled() : GlassRuntimeState.isLargeFolderEnabled();
    }
    private static boolean isSmall(View icon) { return icon != null && icon.getClass().getName().contains("FolderIcon1x1"); }
    private static View animationImage(State state) {
        View cling = state.cling.get();
        return image(view(invoke(cling != null ? cling : state.animationCling.get(), "getFolderAnimIcon")));
    }
    private static View image(View icon) { return view(invoke(icon, "getIconImageView")); }
    private static View view(Object value) { return value instanceof View ? (View) value : null; }
    private static Object invoke(Object owner, String name) {
        try { return owner == null ? null : HookUtil.requireInvoke(owner, name); } catch (Throwable ignored) { return null; }
    }
    private static Object field(Object owner, String name) {
        try { return owner == null ? null : HookUtil.getField(owner, name); } catch (Throwable ignored) { return null; }
    }

    private static OpenFolderGlassGeometry.Frame bounds(View view, State state) {
        View root = state.root.get();
        if (view == null || root == null) return null;
        int width = view.getWidth(), height = view.getHeight();
        if (width <= 0 || height <= 0) return null;
        float[] points = state.points;
        points[0] = points[1] = points[3] = points[4] = 0f;
        points[2] = points[6] = width;
        points[5] = points[7] = height;
        Matrix toGlobal = state.toGlobal, rootToGlobal = state.rootToGlobal, toRoot = state.toRoot;
        toGlobal.reset(); rootToGlobal.reset(); toRoot.reset();
        view.transformMatrixToGlobal(toGlobal);
        root.transformMatrixToGlobal(rootToGlobal);
        if (!rootToGlobal.invert(toRoot)) return null;
        toGlobal.mapPoints(points);
        toRoot.mapPoints(points);
        float left = Math.min(Math.min(points[0], points[2]), Math.min(points[4], points[6]));
        float top = Math.min(Math.min(points[1], points[3]), Math.min(points[5], points[7]));
        float right = Math.max(Math.max(points[0], points[2]), Math.max(points[4], points[6]));
        float bottom = Math.max(Math.max(points[1], points[3]), Math.max(points[5], points[7]));
        Drawable drawable = view instanceof ImageView ? ((ImageView) view).getDrawable() : view.getBackground();
        Object corner = invoke(drawable, "getCornerRadius");
        float radius = corner instanceof Number ? ((Number) corner).floatValue()
                : state.glass.largeFolderStyle.cornerRadiusDp * view.getResources().getDisplayMetrics().density;
        if (!(radius > 0f)) radius = Math.min(width, height) * 0.16f;
        float scale = Math.min((right - left) / width, (bottom - top) / height);
        return new OpenFolderGlassGeometry.Frame(left, top, right, bottom, radius * scale);
    }

    private static final class State {
        final WeakReference<View> root;
        final WeakReference<ViewGroup> icon;
        WeakReference<View> cling = new WeakReference<>(null), folder = new WeakReference<>(null);
        WeakReference<View> animationCling = new WeakReference<>(null);
        WeakReference<ViewGroup> layerHost = new WeakReference<>(null);
        final long serial;
        Object expectedFolderInfo;
        final int width, height, rotation;
        LiquidDockConfig.Glass glass;
        final Matrix toGlobal = new Matrix(), rootToGlobal = new Matrix(), toRoot = new Matrix();
        final float[] points = new float[8], basis = new float[6];
        OpenFolderGlassGeometry.Frame closedFrame, expandedFrame;
        final OpenFolderGlassGeometry geometry = new OpenFolderGlassGeometry();
        final OpenFolderNativeMaterial materials = new OpenFolderNativeMaterial(OWNER);
        final IdentityHashMap<View, Object[]> blurRequests = new IdentityHashMap<>();
        final IdentityHashMap<View, Boolean> claimedBlur = new IdentityHashMap<>();
        ShortcutPopupSourceOverlay source;
        ShortcutPopupGlassSession session;
        ShortcutPopupGlassLayer layer;
        ViewTreeObserver observer;
        ViewTreeObserver.OnPreDrawListener preDraw;
        final OpenFolderMotionState motion = new OpenFolderMotionState();
        final FolderCloseCompletionGate close = new FolderCloseCompletionGate();
        boolean animationSpecReady;
        float nativeTargetX, nativeTargetY, nativeSourceWidth, nativeSourceHeight;
        float nativeTargetWidth, nativeTargetHeight, nativeExpandedScaleX = Float.NaN;
        final View.OnAttachStateChangeListener attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { if (live(State.this)) releaseAll(); }
        };
        State(View root, ViewGroup icon, long serial, LiquidDockConfig.Glass glass) {
            this.root = new WeakReference<>(root);
            this.icon = new WeakReference<>(icon);
            this.serial = serial;
            this.glass = glass;
            width = root.getWidth(); height = root.getHeight();
            rotation = root.getDisplay() != null ? root.getDisplay().getRotation() : -1;
        }
    }
}
