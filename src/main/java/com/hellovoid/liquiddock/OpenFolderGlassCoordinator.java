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

/** Pre-open desktop snapshot for native folder lens, with matched open/close geometry. */
final class OpenFolderGlassCoordinator {
    private static final String TAG = "[DC][OpenFolderGlass]";
    private static final OpenFolderGlassState OWNER = new OpenFolderGlassState();
    private static State current;

    private OpenFolderGlassCoordinator() {}

    static boolean hasActiveSession() { return current != null; }

    /** OWNERSHIP TOKEN for the prepared output, distinct from vendor
     * FolderIcon.openedFolderSerial (touch preparations may be cancelled). */
    static long activeSessionSerial() {
        State state = current;
        return state != null ? state.serial : 0L;
    }

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
        if (state != null && state.icon.get() == icon) {
            state.expectedFolderInfo = folderInfo;
            state.nativeOpenRequested = true;
        }
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
        // Fail closed when the native Launcher surface identity is unavailable.
        if (launcherSurfaceName.isBlank()) return;
        // Prepare identity/geometry only. Starting an actual producer on
        // ACTION_DOWN captured an intermittently visible Dock *before* the OEM
        // FolderState alpha=0 transaction reached SurfaceFlinger.
        State state = new State(root, sourceIcon, OWNER.prepare(), glass,
                launcherSurfaceName, Miuix307ZeroCopyRenderer.folderCaptureDockSurfaceNames());
        current = state;
        root.addOnAttachStateChangeListener(state.attachListener);
    }

    /** The OEM FolderState has hidden Dock before we bind the independent
     * wallpaper PassBlur source. Its root names are still excluded as a
     * second line of defense against a window reparenting/buffer race.
     */
    private static void startWallpaperCapture(State state) {
        if (!live(state) || state.source != null || state.session != null
                || state.motion.isClosing()) return;
        View root = state.root.get();
        if (root == null || !root.isAttachedToWindow()) { failOpen(state); return; }
        java.util.LinkedHashSet<String> dockRoots = new java.util.LinkedHashSet<>();
        for (String name : state.dockRootsAtPrepare) {
            if (name != null && !name.isBlank()) dockRoots.add(name);
        }
        for (String name : Miuix307ZeroCopyRenderer.folderCaptureDockSurfaceNames()) {
            if (name != null && !name.isBlank()) dockRoots.add(name);
        }
        String[] excludedDockRoots = dockRoots.toArray(new String[0]);
        MainHook.diag("[FolderTrace] wallpaper capture AFTER native folder enter"
                + " launcherRoot=" + state.launcherSurfaceName
                + " dockRoots=" + dockRoots);
        state.source = ShortcutPopupSourceOverlay.attachToLauncherBounds(root, new ShortcutPopupSourceOverlay.Listener() {
            @Override public void onAttached(ShortcutPopupSourceOverlay source) {
                if (!live(state)) return;
                state.session = new ShortcutPopupGlassSession(source, state.glass, new ShortcutPopupGlassSession.Listener() {
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
                }, PassBlurBindRequest.openFolder(
                        source, state.launcherSurfaceName, excludedDockRoots));
                attachOutputLayer(state);
                state.session.requestInitialCapture();
            }
            @Override public void onAttachFailed(Throwable error) { if (live(state)) failOpen(state); }
        }, "LiquidDockFolderBackdropSource");
        if (state.source == null && live(state)) releaseAll();
    }

    private static void scheduleWallpaperCaptureAfterNativeHide(State state) {
        View root = state.root.get();
        if (root == null) { failOpen(state); return; }
        // The WindowManager Dock FolderState alpha transaction is asynchronous
        // to native View callbacks. Give SurfaceFlinger two display frames to
        // apply it before we even create the one-shot capture producer.
        root.postOnAnimation(() -> root.postOnAnimation(() -> {
            if (live(state) && !state.motion.isClosing()
                    && state.cling.get() != null) startWallpaperCapture(state);
        }));
    }

    private static void failOpen(State state) {
        if (live(state)) releaseAll();
    }

    /** Attach only the refractive lens below the OEM FolderCling.
     * Native Launcher remains the sole owner of the surrounding background blur.
     */
    private static void attachOutputLayer(State state) {
        if (!live(state) || state.layer != null || state.session == null) return;
        View cling = state.cling.get();
        if (cling == null) return;
        ViewParent parent = cling.getParent();
        if (!(parent instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) parent;
        int folderIndex = host.indexOfChild(cling);
        if (folderIndex < 0) return;
        View workspace = LauncherGlassHierarchy.findWorkspaceRoot(state.icon.get());
        // TextureView must remain in the SAME parent / stable EGL Surface.
        // Put the transparent refractive overlay behind the native Workspace
        // itself, not merely below FolderCling. With the old z-order, the
        // terminal lens was drawn OVER the reappearing source-folder mini
        // icons during the 80ms takeover fade, washing them out for one frame.
        // Workspace icons must always paint on top of the retained glass,
        // while the OEM modal FolderCling continues painting above both.
        View workspaceSibling = workspace;
        while (workspaceSibling != null && workspaceSibling.getParent() != host
                && workspaceSibling.getParent() instanceof View) {
            workspaceSibling = (View) workspaceSibling.getParent();
        }
        int workspaceIndex = workspaceSibling != null && workspaceSibling.getParent() == host
                ? host.indexOfChild(workspaceSibling) : -1;
        int position = workspaceIndex >= 0 && workspaceIndex < folderIndex
                ? workspaceIndex : folderIndex;
        ShortcutPopupGlassLayer layer = new ShortcutPopupGlassLayer(cling.getContext(), state.session);
        state.layerHost = new WeakReference<>(host);
        state.layer = layer;
        host.addView(layer, position,
                new ViewGroup.LayoutParams(state.width, state.height));
        MainHook.diag("[FolderTrace] lens z-order=" +
                (position == workspaceIndex && workspaceIndex >= 0 ? "behind-Workspace" : "fallback-below-Cling")
                + " lensIndex=" + host.indexOfChild(layer)
                + " workspaceIndex=" + (workspaceSibling != null ? host.indexOfChild(workspaceSibling) : -1)
                + " folderIndex=" + host.indexOfChild(cling));
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
        // PassBlur excludes the whole Launcher root and Dock; the first
        // wallpaper-only frame remains safe even after FolderCling opens.
        state.preDraw = () -> { sync(state); return true; };
        state.observer.addOnPreDrawListener(state.preDraw);
        scheduleWallpaperCaptureAfterNativeHide(state);
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
        // Closing may pass reversed source/destination arrays. The accepted
        // OPEN spec already defines the expanded direction of native Folme;
        // overwriting it during CLOSE makes a valid translation look negative.
        if (!opening && state.animationSpecReady) return;
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
        MainHook.diag("[FolderTrace] native animation spec accepted glassSerial="
                + state.serial + " opening=" + opening + " source="
                + state.nativeSourceWidth + "x" + state.nativeSourceHeight
                + " target=" + state.nativeTargetWidth + "x" + state.nativeTargetHeight);
    }

    static void onClosing(View folderCling, boolean animated) {
        State state = current;
        if (!matches(state, folderCling)) return;
        state.close.begin(state.serial, animated);
        // The expanded endpoint is sampled only while native Folder is open.
        // Closing geometry itself must follow the animation plate's *current*
        // transformed bounds, not a rescaled stale endpoint image.
        if (state.expandedFrame == null) {
            View folder = state.folder.get();
            View panel = view(field(folder, "mBackgroundView"));
            if (panel == null) panel = view(invoke(folder, "getContent"));
            state.expandedFrame = panel != null ? bounds(panel, state) : null;
        }
        OWNER.beginClose(state.serial);
        state.motion.beginClose();
        // Reuse the same prepared wallpaper and optical material for the
        // entire reverse spring. Changing the blur kernel here stalls frames
        // and causes a final-frame discontinuity at the native handoff.
        if (state.session != null) state.session.beginFolderClose();
        state.nativeCloseProgress.reset();
        onClosingAnimationMaterialUpdated();
    }

    /** Read the actual Folme channels initialized by setIconBgAnim(false).
     * Native animation progress is useful; the ImageView's root-space bounds
     * are NOT the same geometry as Folder.mBackgroundView. Never translate
     * or scale the lens using a child image's unrelated layout origin.
     */
    static void onClosingNativePlatePrepared() {
        State state = current;
        if (state == null || !state.motion.isClosing()) return;
        View anim = animationImage(state);
        if (anim == null) return;
        state.nativeCloseProgress.capture(anim.getTranslationX(), anim.getTranslationY(),
                anim.getScaleX(), state.nativeTargetX, state.nativeTargetY,
                state.nativeExpandedScaleX);
        MainHook.diag("[FolderTrace] native close progress channels="
                + state.nativeCloseProgress.channelDescription()
                + " serial=" + state.serial);
    }

    /** The OEM close spring may replace its temporary anim-image drawable. */
    static void onClosingAnimationMaterialUpdated() {
        State state = current;
        if (state == null || !state.motion.isClosing() || !OWNER.maySuppressNative()) return;
        View anim = animationImage(state);
        if (anim instanceof ImageView) track(state, ((ImageView) anim).getDrawable());
        if (anim != null) track(state, anim.getBackground());
        View plate = nativeAnimationPlate(state);
        if (plate != null) track(state, plate.getBackground());
        claimNativeBlur(state);
    }

    static long closeSerial(View folder) {
        State state = current;
        return state != null && state.folder.get() == folder ? state.serial : 0L;
    }

    static void onClosed(long serial) {
        State state = current;
        if (state != null && serial == state.serial && OWNER.owns(serial)
                && state.close.contentFinished(serial)) finishNativeClose(state);
    }

    static long activeClosingSerial() {
        State state = current;
        return state != null && state.motion.isClosing() ? state.serial : 0L;
    }

    static void onNativeBackgroundCloseFinished(long serial) {
        State state = current;
        if (state != null && serial != 0L && state.serial == serial && OWNER.owns(serial)
                && state.close.backdropFinished(serial)) finishNativeClose(state);
    }

    /** Native content and background completion are separate. Once both have
     * actually ended, commit the exact icon endpoint and Workspace blur onto
     * the EGL queue BEFORE removing the independent TextureView. A stale
     * intermediate GPU buffer must never be our final closing image.
     */
    private static void finishNativeClose(State state) {
        if (!live(state) || state.finalCloseQueued) return;
        state.finalCloseQueued = true;
        OpenFolderGlassGeometry.Frame icon = state.geometry.acceptAnimation(
                state.closedFrame, state.width, state.height);
        if (icon == null || state.layer == null || state.session == null
                || !state.session.hasFrozenBackdrop()) {
            releaseAll();
            return;
        }
        LauncherGlassGeometry.Snapshot endpoint = LauncherGlassGeometry.resolveStatic(
                state.width, state.height, icon.left, icon.top,
                icon.right, icon.bottom, icon.radius);
        if (endpoint == null) { releaseAll(); return; }
        MainHook.diag("[FolderTrace] final close EGL endpoint glassSerial=" + state.serial);
        state.session.finishFolderClose(endpoint, () -> {
            if (!live(state)) return;
            ShortcutPopupGlassLayer output = state.layer;
            View root = state.root.get();
            if (root == null || output == null || !output.isAttachedToWindow()
                    || !LauncherGlassSceneController.awaitFolderHandoff(root, () -> {
                        if (live(state) && state.layer != null) {
                            MainHook.diag("[FolderTrace] workspace frame ready, crossfading icon glass"
                                    + " serial=" + state.serial);
                            state.layer.fadeIntoWorkspace(() -> {
                                if (live(state)) releaseAll();
                            });
                        }
                    })) {
                releaseAll();
                return;
            }
            // Opposite of the old sequence: start Workspace capture while the
            // final OPEN_FOLDER icon glass is still presented. Its own one-shot
            // wallpaper source never samples Launcher/Dock.
            if (!MiuixFolderGlassHook.beginExpandedFolderHandoff(state.serial)) {
                LauncherGlassSceneController.cancelFolderHandoff(root);
                releaseAll();
                return;
            }
            state.inputsResumed = true;
            MainHook.diag("[FolderTrace] holding final glass until Workspace visible"
                    + " serial=" + state.serial);
            // Failure-only lifecycle fence: don't strand a TextureView if
            // Launcher is paused or the resident output cannot become ready.
            root.postDelayed(() -> {
                if (live(state)) {
                    MainHook.diag("[FolderTrace] handoff timeout fallback serial=" + state.serial);
                    releaseAll();
                }
            }, 1500L);
        });
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

    /** Synchronous native setter fence for a just-rebound close-animation
     * image, which can arrive after pre-draw but before Surface composition.
     * Do not affect widget hosts, folder content or unrelated View blurs.
     */
    static boolean ownsNativeBlurMode(View material) {
        State state = current;
        return state != null && material != null && OWNER.maySuppressNative()
                && isOwnedNativeBackground(state, material);
    }

    static void rememberNativeBlur(View material, Object[] args) {
        State state = current;
        if (state == null || args == null || !isOwnedNativeBackground(state, material)) return;
        state.blurRequests.put(material, args.clone());
        if (OWNER.maySuppressNative()) claimNativeBlur(state);
    }

    static void releaseAll() {
        State state = current;
        current = null;
        OWNER.release();
        if (state == null) return;
        MainHook.diag("[FolderTrace] GlassCoordinator.releaseAll glassSerial="
                + state.serial + " nativeFolderSerial="
                + MiuixFolderGlassHook.diagnosticNativeSerial());
        View root = state.root.get();
        if (root != null) LauncherGlassSceneController.cancelFolderHandoff(root);
        state.materials.invalidateAll();
        if (state.observer != null && state.preDraw != null && state.observer.isAlive()) {
            state.observer.removeOnPreDrawListener(state.preDraw);
        }
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
        // The healthy path already thawed Workspace before crossfading the
        // resident buffer. Retain the old direct-release path for fallback.
        if (!state.inputsResumed) MiuixFolderGlassHook.onExpandedFolderOutputReleased(state.serial);
    }

    private static void sync(State state) {
        if (!live(state) || state.finalCloseQueued) return;
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
            // The OEM may animate translation and scale on independent Folme
            // curves. Use whichever actually moved, but NEVER use the native
            // ImageView's own root-space rectangle as the glass silhouette:
            // its bounds/pivot can differ from both material endpoints.
            if (state.motion.isClosing()) observed = Float.NaN;
            if (state.motion.isClosing() && animImage != null) {
                if (!state.nativeCloseProgress.hasChannels()) {
                    state.nativeCloseProgress.capture(animImage.getTranslationX(),
                            animImage.getTranslationY(), animImage.getScaleX(),
                            state.nativeTargetX, state.nativeTargetY,
                            state.nativeExpandedScaleX);
                }
                float reverse = state.nativeCloseProgress.sample(
                        animImage.getTranslationX(), animImage.getTranslationY(),
                        animImage.getScaleX(), state.nativeTargetX,
                        state.nativeTargetY, state.nativeExpandedScaleX);
                if (Float.isFinite(reverse)) observed = reverse;
            }
            float amount = state.motion.sample(observed);
            // One root-coordinate path for OPEN and CLOSE: no child-View
            // layout-origin handoff, one-time offset or second animation.
            OpenFolderGlassGeometry.Frame frame = state.geometry.acceptProgress(
                    state.closedFrame, target, amount, state.width, state.height);
            if (frame == null) return;
            state.materials.beginFrame(state.serial);
            track(state, folder.getBackground());
            track(state, panel != null ? panel.getBackground() : null);
            if (animImage instanceof ImageView) track(state, ((ImageView) animImage).getDrawable());
            if (animImage != null) track(state, animImage.getBackground());
            // The OEM close plate is a FolderIcon container. Its background
            // (and native RenderNode blur) may be distinct from the child
            // icon image's drawable. Cover both actual identities rather than
            // globally muting BlurUtilities or all FolderIcon instances.
            View plate = nativeAnimationPlate(state);
            if (plate != null) track(state, plate.getBackground());
            if (OWNER.maySuppressNative()) claimNativeBlur(state);
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
            if (!isOwnedNativeBackground(state, material)) continue;
            claimNativeBackground(state, material);
            if (state.claimedBlur.containsKey(material)) {
                LauncherWidgetNativeViewBlurGate.rememberFolderInitializer(material, entry.getValue());
            }
        }
        // The temporary plate and its ImageView have independent native
        // advanced-material RenderNodes on some Launcher 4.50 paths.
        claimNativeBackground(state, nativeAnimationPlate(state));
        claimNativeBackground(state, animationImage(state));
    }

    private static void claimNativeBackground(State state, View material) {
        if (material == null || state.claimedBlur.containsKey(material)) return;
        if (LauncherWidgetNativeViewBlurGate.claimFolder(material)) {
            state.claimedBlur.put(material, Boolean.TRUE);
        }
    }

    private static boolean isOwnedNativeBackground(State state, View material) {
        if (material == null) return false;
        View nativePanel = view(field(state.folder.get(), "mBackgroundView"));
        return material == animationImage(state)
                || material == nativeAnimationPlate(state)
                || (material == nativePanel && state.blurRequests.containsKey(material));
    }

    private static View nativeAnimationPlate(State state) {
        View cling = state.cling.get();
        if (cling == null) cling = state.animationCling.get();
        return view(invoke(cling, "getFolderAnimIcon"));
    }

    private static void cancelUnused(View icon) {
        State state = current;
        // ACTION_UP/CANCEL can precede the OEM's delayed FolderCling.open.
        // Once Launcher.openFolder has accepted this owner, it is no longer
        // an unused touch session and must survive that native handoff.
        if (state != null && state.icon.get() == icon
                && !state.nativeOpenRequested && state.cling.get() == null) releaseAll();
    }

    private static boolean live(State state) { return current == state && OWNER.owns(state.serial); }
    private static boolean matches(State state, View cling) { return state != null && state.cling.get() == cling; }
    private static boolean enabled(View icon) {
        if (icon == null || !GlassRuntimeState.isEnabled()) return false;
        return isSmall(icon) ? GlassRuntimeState.isSmallFolderEnabled() : GlassRuntimeState.isLargeFolderEnabled();
    }
    private static boolean isSmall(View icon) { return icon != null && icon.getClass().getName().contains("FolderIcon1x1"); }
    private static View animationImage(State state) {
        return image(nativeAnimationPlate(state));
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
        final String launcherSurfaceName;
        final String[] dockRootsAtPrepare;
        Object expectedFolderInfo;
        boolean nativeOpenRequested;
        final int width, height, rotation;
        LiquidDockConfig.Glass glass;
        final Matrix toGlobal = new Matrix(), rootToGlobal = new Matrix(), toRoot = new Matrix();
        final float[] points = new float[8], basis = new float[6];
        OpenFolderGlassGeometry.Frame closedFrame, expandedFrame;
        final OpenFolderGlassGeometry geometry = new OpenFolderGlassGeometry();
        final OpenFolderNativeCloseProgress nativeCloseProgress = new OpenFolderNativeCloseProgress();
        boolean finalCloseQueued;
        boolean inputsResumed;
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
        State(View root, ViewGroup icon, long serial, LiquidDockConfig.Glass glass,
              String launcherSurfaceName, String[] dockRootsAtPrepare) {
            this.root = new WeakReference<>(root);
            this.icon = new WeakReference<>(icon);
            this.serial = serial;
            this.launcherSurfaceName = launcherSurfaceName;
            this.dockRootsAtPrepare = dockRootsAtPrepare != null
                    ? dockRootsAtPrepare.clone() : new String[0];
            this.glass = glass;
            width = root.getWidth(); height = root.getHeight();
            rotation = root.getDisplay() != null ? root.getDisplay().getRotation() : -1;
        }
    }
}
