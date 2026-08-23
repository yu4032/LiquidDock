from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(name, old, new, label):
    path = ROOT / name
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1))


patch_once(
    "LauncherGlassSession.java",
    '''    void requestFreshBackdrop(long generation) {
        if (shuttingDown || generation < sceneGeneration) return;
        invalidateGeneration(generation);
        requestFrame(true);
    }

''',
    '''    void requestFreshBackdrop(long generation) {
        if (shuttingDown || generation < sceneGeneration) return;
        invalidateGeneration(generation);
        // A stable Launcher DecorView can survive while its ViewRoot/SurfaceControl is replaced
        // during App -> HOME. Revalidate on the UI thread before pulsing PassBlur so a fresh
        // request can never target a dead producer binding.
        mainHandler.post(() -> recoverFreshBackdropOnUi(generation, 0));
    }

    private void recoverFreshBackdropOnUi(long generation, int attempt) {
        if (shuttingDown || generation != sceneGeneration) return;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) {
            retryFreshBackdropRecovery(generation, attempt);
            return;
        }

        // ViewRootImpl replacement does not necessarily detach the stable DecorView, so the old
        // ViewTreeObserver can die without the root attach listener firing. Reinstall it here.
        installRootObserver();
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            retryFreshBackdropRecovery(generation, attempt);
            return;
        }

        Miuix307PassBlurBridge.Binding current = binding;
        if (current != null && (!current.rootSurface.isValid()
                || !isSameSurface(current.rootSurface, geometry.rootSurface))) {
            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
            if (nextGeneration > 0L) sceneGeneration = nextGeneration;
            MainHook.log(TAG + " producer Surface generation changed old=" + current.rootName
                    + " new=" + geometry.rootSurface);
            rebindProducer();
            return;
        }

        boolean producerChanged = refreshProducerGeometryOnUi(root);
        if (producerChanged || generation != sceneGeneration) return;

        current = binding;
        if (current == null) {
            // Startup may reach the fresh boundary before EGL/input creation; a Surface rebind can
            // also already be pending. Start exactly one existing bootstrap/rebind path.
            if (inputSurfaceTexture == null || inputProducerSurface == null) requestFrame(true);
            else bindProducerWhenReady(0);
            return;
        }
        if (!current.bound || !current.rootSurface.isValid()
                || !isSameSurface(current.rootSurface, geometry.rootSurface)) {
            rebindProducer();
            return;
        }
        requestFrame(true);
    }

    private void retryFreshBackdropRecovery(long generation, int attempt) {
        if (shuttingDown || generation != sceneGeneration
                || attempt >= MAX_BIND_RETRY_FRAMES) return;
        View root = rootRef.get();
        if (root == null) return;
        root.postOnAnimation(() -> recoverFreshBackdropOnUi(generation, attempt + 1));
    }

''',
    "fresh backdrop Surface generation recovery",
)

old_refresh = '''    private boolean refreshProducerGeometryOnUi(View root) {
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null) return false;
        if (!LauncherGlassProducerGeometryGate.matchesRoot(
                rootWidth, rootHeight, geometry.surfaceWidth, geometry.surfaceHeight,
                geometry.insetLeft, geometry.insetTop, geometry.insetRight, geometry.insetBottom)) {
            frameAvailable.set(false);
            hasConsumedFrame = false;
            consumedGeneration = -1L;
            MainHook.log(TAG + " producer geometry not coherent with root root="
                    + rootWidth + "x" + rootHeight + " surface="
                    + geometry.surfaceWidth + "x" + geometry.surfaceHeight);
            return false;
        }
        int nextRotation = geometry.configRotation;
        LauncherGlassSurfaceContentRect nextContentRect = geometry.contentRect;
        boolean changed = nextRotation != configRotation
                || geometry.bufferWidth != boundBufferWidth
                || geometry.bufferHeight != boundBufferHeight
                || !nextContentRect.sameAs(contentRect);
        configRotation = nextRotation;
        if (changed) {
            frameAvailable.set(false);
            hasConsumedFrame = false;
            consumedGeneration = -1L;
            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
            if (nextGeneration > 0L) sceneGeneration = nextGeneration;
            boundBufferWidth = geometry.bufferWidth;
            boundBufferHeight = geometry.bufferHeight;
            contentRect = nextContentRect;
            MainHook.log(TAG + " producer geometry surface="
                    + geometry.surfaceWidth + "x" + geometry.surfaceHeight
                    + " buffer=" + geometry.bufferWidth + "x" + geometry.bufferHeight
                    + " insets=" + geometry.insetLeft + "," + geometry.insetTop
                    + "," + geometry.insetRight + "," + geometry.insetBottom);
            SurfaceTexture input = inputSurfaceTexture;
            if (input != null && geometry.bufferWidth > 0 && geometry.bufferHeight > 0) {
                postRender(() -> {
                    if (!shuttingDown && input == inputSurfaceTexture) {
                        input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
                    }
                }, null);
            }
        }
        Miuix307PassBlurBridge.Binding current = binding;
        if (current != null && (!current.rootSurface.isValid()
                || !isSameSurface(current.rootSurface, geometry.rootSurface))) {
            rebindProducer();
            return true;
        }
        if (changed && current != null && current.bound) {
            Miuix307PassBlurBridge.requestSingleUpdate(current, root);
        }
        return changed;
    }
'''
new_refresh = '''    private boolean refreshProducerGeometryOnUi(View root) {
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            return false;
        }
        if (!LauncherGlassProducerGeometryGate.matchesRoot(
                rootWidth, rootHeight, geometry.surfaceWidth, geometry.surfaceHeight,
                geometry.insetLeft, geometry.insetTop, geometry.insetRight, geometry.insetBottom)) {
            frameAvailable.set(false);
            hasConsumedFrame = false;
            consumedGeneration = -1L;
            MainHook.log(TAG + " producer geometry not coherent with root root="
                    + rootWidth + "x" + rootHeight + " surface="
                    + geometry.surfaceWidth + "x" + geometry.surfaceHeight);
            return false;
        }
        int nextRotation = geometry.configRotation;
        LauncherGlassSurfaceContentRect nextContentRect = geometry.contentRect;
        boolean geometryChanged = nextRotation != configRotation
                || geometry.bufferWidth != boundBufferWidth
                || geometry.bufferHeight != boundBufferHeight
                || !nextContentRect.sameAs(contentRect);
        Miuix307PassBlurBridge.Binding current = binding;
        boolean surfaceChanged = current != null && (!current.rootSurface.isValid()
                || !isSameSurface(current.rootSurface, geometry.rootSurface));
        boolean changed = geometryChanged || surfaceChanged;
        configRotation = nextRotation;
        if (changed) {
            frameAvailable.set(false);
            hasConsumedFrame = false;
            consumedGeneration = -1L;
            backdropPrepared = false;
            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);
            if (nextGeneration > 0L) sceneGeneration = nextGeneration;
            boundBufferWidth = geometry.bufferWidth;
            boundBufferHeight = geometry.bufferHeight;
            contentRect = nextContentRect;
            if (surfaceChanged) {
                MainHook.log(TAG + " producer Surface generation changed old=" + current.rootName
                        + " new=" + geometry.rootSurface);
            } else {
                MainHook.log(TAG + " producer geometry surface="
                        + geometry.surfaceWidth + "x" + geometry.surfaceHeight
                        + " buffer=" + geometry.bufferWidth + "x" + geometry.bufferHeight
                        + " insets=" + geometry.insetLeft + "," + geometry.insetTop
                        + "," + geometry.insetRight + "," + geometry.insetBottom);
            }
            SurfaceTexture input = inputSurfaceTexture;
            if (input != null && geometry.bufferWidth > 0 && geometry.bufferHeight > 0) {
                postRender(() -> {
                    if (!shuttingDown && input == inputSurfaceTexture) {
                        input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
                    }
                }, null);
            }
        }
        if (surfaceChanged) {
            rebindProducer();
            return true;
        }
        if (geometryChanged && current != null && current.bound) {
            Miuix307PassBlurBridge.requestSingleUpdate(current, root);
        }
        return changed;
    }
'''
patch_once(
    "LauncherGlassSession.java",
    old_refresh,
    new_refresh,
    "Surface identity is a producer generation change",
)

print("Workspace Launcher Surface generation recovery patch applied")
