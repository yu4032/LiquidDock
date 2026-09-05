from pathlib import Path

ROOT = Path('src/main/java/com/hellovoid/liquiddock')


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, got {count}')
    return text.replace(old, new, 1)


def replace_block(text, start, end, new_block, label):
    start_index = text.find(start)
    if start_index < 0 or text.find(start, start_index + 1) >= 0:
        raise SystemExit(f'{label}: start marker not unique')
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise SystemExit(f'{label}: end marker missing')
    return text[:start_index] + new_block + '\n\n' + text[end_index:]


def wire(path, static_node):
    text = path.read_text()
    text = replace_once(
        text,
        '    private volatile boolean suppressedByFolderOpen;\n'
        '    private volatile boolean suppressedByDrag;\n',
        '    private final LauncherGlassSuppressionState suppressionState =\n'
        '            new LauncherGlassSuppressionState();\n',
        f'{path.name} suppression fields')
    text = replace_once(
        text,
        '    private boolean pressTarget;\n'
        '    private float pressProgress;\n'
        '    private float glowCenterX = 0.5f;\n'
        '    private float glowCenterY = 0.5f;\n',
        '    private final LauncherGlassPressState pressState = new LauncherGlassPressState();\n',
        f'{path.name} press fields')

    if static_node:
        folder_block = '''    void setSuppressedByFolderOpen(boolean suppressed) {
        if (disposed || !suppressionState.setFolderOpen(suppressed)) return;
        if (suppressed) resetPressInteraction(false);
        animateVisibilityTo(!suppressionState.isSuppressed());
    }

    void setSuppressedByDrag(boolean suppressed) {
        if (disposed || !suppressionState.setDrag(suppressed)) return;
        if (suppressed) resetPressInteraction(false);
        animateVisibilityTo(!suppressionState.isSuppressed());
    }

    float visibilityAlpha() { return visibilityAlpha; }

    boolean retainLastGeometryDuringFade() {
        return visibilityAlpha > 0.001f && suppressionState.isSuppressed();
    }'''
        text = replace_block(
            text,
            '    void setSuppressedByFolderOpen(boolean suppressed) {',
            '    private void animateVisibilityTo(boolean visible) {',
            folder_block,
            f'{path.name} suppression methods')
    else:
        folder_block = '''    void setSuppressedByFolderOpen(boolean suppressed) {
        if (disposed || !suppressionState.setFolderOpen(suppressed)) return;
        if (suppressed) resetPressInteraction(false);
        syncFromMaterial();
        requestLifecycleRefresh();
    }

    void setSuppressedByDrag(boolean suppressed) {
        if (disposed || !suppressionState.setDrag(suppressed)) return;
        if (suppressed) resetPressInteraction(false);
        syncFromMaterial();
        requestLifecycleRefresh();
    }'''
        text = replace_block(
            text,
            '    void setSuppressedByFolderOpen(boolean suppressed) {',
            '    void setLocalVisualBounds(',
            folder_block,
            f'{path.name} suppression methods')
        text = text.replace('if (suppressedByDrag) {', 'if (suppressionState.isDragSuppressed()) {')
        text = text.replace(
            'int visibility = suppressedByFolderOpen || suppressedByDrag\n'
            '                ? View.GONE : material.getVisibility();',
            'int visibility = suppressionState.isSuppressed()\n'
            '                ? View.GONE : material.getVisibility();')

    press_block = '''    void setPressInteraction(boolean pressed, float normalizedX, float normalizedY) {
        if (disposed) return;
        LauncherGlassPressState.Decision decision =
                pressState.setPressed(pressed, normalizedX, normalizedY);
        if (decision.animate) {
            animatePressTo(decision.targetProgress);
        } else if (decision.publishImmediately) {
            publishInteraction();
        }
    }

    void resetPressInteraction(boolean animated) {
        if (disposed) return;
        LauncherGlassPressState.Decision decision = pressState.reset(animated);
        if (decision.animate) {
            animatePressTo(decision.targetProgress);
            return;
        }
        if (pressAnimator != null) {
            pressAnimator.cancel();
            pressAnimator = null;
        }
        if (decision.publishImmediately) publishInteraction();
    }'''
    text = replace_block(
        text,
        '    void setPressInteraction(boolean pressed, float normalizedX, float normalizedY) {',
        '    private void animatePressTo(float target) {',
        press_block,
        f'{path.name} press input methods')

    animate_block = '''    private void animatePressTo(float target) {
        if (pressAnimator != null) pressAnimator.cancel();
        float start = pressState.progress();
        if (Math.abs(start - target) < 0.001f) {
            pressState.setProgress(target);
            publishInteraction();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, target);
        pressAnimator = animator;
        animator.setDuration(target > start ? AnimationRuntimeState.pressInDurationMs()
                : AnimationRuntimeState.pressOutDurationMs());
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (pressAnimator != valueAnimator || disposed) return;
            pressState.setProgress((Float) valueAnimator.getAnimatedValue());
            publishInteraction();
        });
        animator.start();
    }'''
    text = replace_block(
        text,
        '    private void animatePressTo(float target) {',
        '    private void publishInteraction() {',
        animate_block,
        f'{path.name} press animator')

    if static_node:
        publish = '''    private void publishInteraction() {
        LauncherGlassSession live = ensureLiveSession();
        if (disposed || live == null) return;
        live.updateStaticInteraction(this,
                new PrismalInteractionState(
                        pressState.progress(), pressState.glowCenterX(), pressState.glowCenterY()));
    }'''
        next_marker = '    LauncherGlassGeometry.Snapshot captureGeometry(View root) {'
    else:
        publish = '''    private void publishInteraction() {
        LauncherGlassSession live = ensureLiveSession();
        if (disposed || live == null) return;
        live.updateInteraction(this,
                new PrismalInteractionState(
                        pressState.progress(), pressState.glowCenterX(), pressState.glowCenterY()));
    }'''
        next_marker = '    private static float clamp01(float value) {'
    text = replace_block(
        text,
        '    private void publishInteraction() {',
        next_marker,
        publish,
        f'{path.name} publish interaction')

    forbidden = ('suppressedByFolderOpen', 'suppressedByDrag', 'pressTarget',
                 'pressProgress', 'glowCenterX =', 'glowCenterY =')
    for token in forbidden:
        if token in text:
            raise SystemExit(f'{path.name}: raw runtime field reference remains: {token}')
    path.write_text(text)


wire(ROOT / 'LauncherGlassSinkView.java', False)
wire(ROOT / 'LauncherGlassStaticNode.java', True)
