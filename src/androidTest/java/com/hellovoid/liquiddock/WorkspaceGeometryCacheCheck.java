package com.hellovoid.liquiddock;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

/** Exercise real Android matrices and per-frame ancestry revisions without changing Launcher. */
final class WorkspaceGeometryCacheCheck {
    static void run(Instrumentation owner) {
        Bundle result = new Bundle();
        Throwable[] failure = new Throwable[1];
        owner.runOnMainSync(
                () -> {
                    try {
                        Context context = owner.getTargetContext();
                        FrameLayout root = new FrameLayout(context);
                        View shared = new View(context);
                        root.addView(shared);
                        root.layout(0, 0, 1000, 600);
                        shared.layout(10, 20, 300, 200);
                        LauncherGlassStaticGeometryCache.Frame frame =
                                new LauncherGlassStaticGeometryCache.Frame();
                        frame.reset();
                        long first = frame.sample(shared).revision;
                        require(
                                frame.sample(shared).revision == first,
                                "shared ancestor changed within one traversal");
                        frame.reset();
                        require(
                                frame.sample(shared).revision == first,
                                "stable ancestor changed revision");
                        shared.setScaleX(.8f);
                        frame.reset();
                        long scaled = frame.sample(shared).revision;
                        require(scaled != first, "scale was missed");
                        shared.setAlpha(.4f);
                        frame.reset();
                        long faded = frame.sample(shared).revision;
                        require(faded != scaled, "alpha was missed");
                        shared.setTranslationX(25f);
                        frame.reset();
                        long moved = frame.sample(shared).revision;
                        require(moved != faded, "translation was missed");
                        shared.setVisibility(View.INVISIBLE);
                        frame.reset();
                        require(frame.sample(shared).revision != moved, "visibility was missed");
                        android.graphics.Matrix transform = new android.graphics.Matrix();
                        transform.setScale(.9f, .85f);
                        long beforeAnimation = frame.sample(shared).revision;
                        shared.setAnimationMatrix(transform);
                        frame.reset();
                        long animated = frame.sample(shared).revision;
                        require(animated != beforeAnimation, "native animation matrix was missed");
                        shared.setAnimationMatrix(null);
                        frame.reset();
                        require(
                                frame.sample(shared).revision != animated,
                                "animation matrix removal was missed");
                        // Recycle the frame slots in a different order. A previous sample cannot
                        // authorize reuse for another View even when its values happen to match.
                        frame.reset();
                        long rootRevision = frame.sample(root).revision;
                        require(
                                rootRevision != moved,
                                "recycled slot retained another View's revision");
                        frame.sample(shared);
                        checkSharedInvalidation(context);
                        result.putString(
                                "result",
                                "PASS: shared ancestor revisions preserve scale, alpha, translation"
                                    + " and visibility");
                    } catch (Throwable error) {
                        failure[0] = error;
                    }
                });
        if (failure[0] != null)
            result.putString("failure", android.util.Log.getStackTraceString(failure[0]));
        owner.finish(failure[0] == null ? -1 : 1, result);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void checkSharedInvalidation(Context context) {
        AttachedLayout root = new AttachedLayout(context), parent = new AttachedLayout(context);
        AttachedLayout first = new AttachedLayout(context), second = new AttachedLayout(context);
        root.addView(parent);
        parent.addView(first);
        parent.addView(second);
        root.layout(0, 0, 1000, 600);
        parent.layout(0, 0, 1000, 600);
        first.layout(0, 0, 166, 166);
        second.layout(200, 0, 366, 166);
        LauncherGlassStaticGeometryCache a = new LauncherGlassStaticGeometryCache();
        LauncherGlassStaticGeometryCache b = new LauncherGlassStaticGeometryCache();
        LauncherGlassStaticGeometryCache.Frame frame = new LauncherGlassStaticGeometryCache.Frame();
        frame.reset();
        require(
                !reuse(a, first, root, frame) && !reuse(b, second, root, frame),
                "new nodes reused unknown geometry");
        frame.reset();
        require(
                reuse(a, first, root, frame) && reuse(b, second, root, frame),
                "stable shared ancestry failed reuse");
        parent.setScaleX(.8f);
        frame.reset();
        require(
                !reuse(a, first, root, frame) && !reuse(b, second, root, frame),
                "shared scale did not invalidate both descendants");
        frame.reset();
        require(
                reuse(a, first, root, frame) && reuse(b, second, root, frame),
                "settled scale did not resume reuse");
        parent.setHasTransientState(true);
        for (int i = 0; i < 2; i++) {
            frame.reset();
            require(
                    !reuse(a, first, root, frame) && !reuse(b, second, root, frame),
                    "transient native motion reused geometry");
        }
        parent.setHasTransientState(false);
        frame.reset();
        require(!reuse(a, first, root, frame), "first settled frame skipped authoritative capture");
        frame.reset();
        require(reuse(a, first, root, frame), "settled frame never resumed reuse");
    }

    private static boolean reuse(
            LauncherGlassStaticGeometryCache cache,
            View material,
            View root,
            LauncherGlassStaticGeometryCache.Frame frame) {
        return cache.unchanged(
                material,
                root,
                LauncherGlassDragState.Kind.FOLDER,
                new GlassComponentStyle(true, 0f, 0f),
                0f,
                1f,
                null,
                0,
                0,
                frame);
    }

    private static final class AttachedLayout extends FrameLayout {
        AttachedLayout(Context context) {
            super(context);
        }

        @Override
        public boolean isAttachedToWindow() {
            return true;
        }
    }
}
