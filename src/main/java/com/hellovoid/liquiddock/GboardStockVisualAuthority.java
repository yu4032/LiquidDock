package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Owns only the structurally resolved floating-keyboard stock visuals while glass is presented.
 *
 * <p>Vendor writes are intercepted at stable View APIs instead of being corrected from pre-draw.
 * While a target is claimed, Gboard's latest background/alpha/elevation intent is remembered but
 * not shown. Release replays that latest intent. Dynamic KeyboardViewHolder content is claimed from
 * the stable ViewGroup add/layout boundaries, so stable frames do no visual-authority work.</p>
 */
final class GboardStockVisualAuthority {
    private static final Object LOCK = new Object();
    private static final String SOFT_KEYBOARD_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyboardView";

    private static final ThreadLocal<Integer> MODULE_MUTATION_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private static final Map<View, Claim> BY_BASE = new WeakHashMap<>();
    private static final Map<View, Claim> OWNER_BY_VIEW = new WeakHashMap<>();
    private static final Map<ViewGroup, Claim> CLAIM_BY_HOLDER = new WeakHashMap<>();

    private static Method setBackground;
    private static Method setAlpha;
    private static Method setElevation;
    private static Method addView;
    private static boolean installAttempted;
    private static boolean installed;

    private GboardStockVisualAuthority() {}

    static boolean claim(GboardFloatingStructureResolver.Structure structure) {
        if (structure == null || structure.keyboardArea == null
                || structure.stockBackground == null || structure.bottomFrame == null) {
            return false;
        }
        if (!install()) return false;

        Claim claim;
        synchronized (LOCK) {
            Claim existing = BY_BASE.get(structure.keyboardArea);
            if (existing != null) return true;
            claim = new Claim(structure);
            if (!reserveStructureLocked(claim)) return false;
            BY_BASE.put(structure.keyboardArea, claim);
        }

        try {
            claimInitialStructure(claim);
            return true;
        } catch (Throwable error) {
            releaseClaim(claim, true);
            log("initial stock visual claim failed", error);
            return false;
        }
    }

    static void release(GboardFloatingStructureResolver.Structure structure) {
        if (structure == null || structure.keyboardArea == null) return;
        Claim claim;
        synchronized (LOCK) {
            claim = BY_BASE.remove(structure.keyboardArea);
        }
        if (claim != null) releaseClaim(claim, true);
    }

    private static synchronized boolean install() {
        if (installAttempted) return installed;
        installAttempted = true;
        try {
            setBackground = requireTrackable(
                    View.class, "setBackground", new Class<?>[]{Drawable.class});
            setAlpha = requireTrackable(
                    View.class, "setAlpha", new Class<?>[]{float.class});
            setElevation = requireTrackable(
                    View.class, "setElevation", new Class<?>[]{float.class});
            addView = HookUtil.findMethodExact(
                    ViewGroup.class, "addView",
                    new Class<?>[]{View.class, int.class, ViewGroup.LayoutParams.class});

            hookVisualWrite(setBackground, VisualProperty.BACKGROUND);
            hookVisualWrite(setAlpha, VisualProperty.ALPHA);
            hookVisualWrite(setElevation, VisualProperty.ELEVATION);
            HookUtil.hook(addView, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object receiver = chain.getThisObject();
                View child = args.length > 0 && args[0] instanceof View ? (View) args[0] : null;
                if (receiver instanceof ViewGroup && child != null) {
                    onChildAdded((ViewGroup) receiver, child);
                }
                return result;
            });
            installed = true;
            return true;
        } catch (Throwable error) {
            log("stable Gboard visual interception unavailable", error);
            installed = false;
            return false;
        }
    }

    private static Method requireTrackable(
            Class<?> owner, String name, Class<?>[] parameterTypes) throws Exception {
        Method method = HookUtil.findMethodExact(owner, name, parameterTypes);
        Class<?> result = method.getReturnType();
        if (result != void.class && result != boolean.class && result != Boolean.class) {
            throw new IllegalStateException("unsupported tracked View API return type: " + method);
        }
        return method;
    }

    private static void hookVisualWrite(Method method, VisualProperty property) {
        HookUtil.hook(method, chain -> {
            if (isModuleMutation()) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            Object receiver = chain.getThisObject();
            if (!(receiver instanceof View)) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            View target = (View) receiver;
            Object value = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
            synchronized (LOCK) {
                Claim claim = OWNER_BY_VIEW.get(target);
                if (claim != null && claim.recordVendorWriteLocked(target, property, value)) {
                    return successfulSuppressionResult(method);
                }
            }
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });
    }

    private static Object successfulSuppressionResult(Method method) {
        Class<?> result = method.getReturnType();
        if (result == boolean.class || result == Boolean.class) return Boolean.TRUE;
        return null;
    }

    private static boolean reserveStructureLocked(Claim claim) {
        List<View> staticTargets = claim.staticTargets();
        for (View target : staticTargets) {
            if (target == null) continue;
            Claim existing = OWNER_BY_VIEW.get(target);
            if (existing != null && existing != claim) return false;
        }
        for (ViewGroup holder : claim.structure.keyboardViewHolders) {
            if (holder == null) continue;
            Claim existing = CLAIM_BY_HOLDER.get(holder);
            if (existing != null && existing != claim) return false;
        }
        for (View target : staticTargets) {
            if (target != null) OWNER_BY_VIEW.put(target, claim);
        }
        for (ViewGroup holder : claim.structure.keyboardViewHolders) {
            if (holder != null) CLAIM_BY_HOLDER.put(holder, claim);
        }
        return true;
    }

    private static void claimInitialStructure(Claim claim) {
        claimBackground(claim, claim.structure.keyboardArea);
        claimElevation(claim, claim.structure.keyboardArea);
        claimAlpha(claim, claim.structure.stockBackground);
        claimBackground(claim, claim.structure.contentColumn);
        claimBackground(claim, claim.structure.keyboardHolder);
        claimBackground(claim, claim.structure.bottomFrame);
        claimBackground(claim, claim.structure.topEdge);

        for (ViewGroup holder : claim.structure.keyboardViewHolders) {
            if (holder == null) continue;
            claimBackground(claim, holder);
            View.OnLayoutChangeListener listener =
                    (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                            claimCurrentContent((ViewGroup) view, claim);
            holder.addOnLayoutChangeListener(listener);
            synchronized (LOCK) {
                claim.holderLayoutListeners.put(holder, listener);
            }
            claimCurrentContent(holder, claim);
        }
    }

    private static void onChildAdded(ViewGroup parent, View child) {
        Claim claim;
        synchronized (LOCK) {
            claim = CLAIM_BY_HOLDER.get(parent);
        }
        if (claim == null || !isEligibleContent(parent, child)) return;
        claimBackground(claim, child);
    }

    private static void claimCurrentContent(ViewGroup holder, Claim claim) {
        if (holder == null || claim == null) return;
        int count = holder.getChildCount();
        for (int i = 0; i < count; i++) {
            View content = holder.getChildAt(i);
            if (isEligibleContent(holder, content)) claimBackground(claim, content);
        }
    }

    private static boolean isEligibleContent(ViewGroup holder, View content) {
        if (holder == null || content == null) return false;
        if (SOFT_KEYBOARD_VIEW_CLASS.equals(content.getClass().getName())) return true;
        int holderWidth = holder.getWidth();
        int holderHeight = holder.getHeight();
        return holderWidth > 0 && holderHeight > 0
                && content.getWidth() >= Math.max(1, holderWidth / 2)
                && content.getHeight() >= Math.max(1, holderHeight / 2);
    }

    private static void claimBackground(Claim claim, View target) {
        if (claim == null || target == null) return;
        boolean shouldApply;
        synchronized (LOCK) {
            Snapshot snapshot = claim.snapshotLocked(target);
            OWNER_BY_VIEW.put(target, claim);
            shouldApply = !snapshot.background.isClaimed();
            if (shouldApply) snapshot.background.claim(target.getBackground());
        }
        if (shouldApply) {
            runModuleMutation(() -> {
                if (target.getBackground() != null) target.setBackground(null);
            });
        }
    }

    private static void claimAlpha(Claim claim, View target) {
        if (claim == null || target == null) return;
        boolean shouldApply;
        synchronized (LOCK) {
            Snapshot snapshot = claim.snapshotLocked(target);
            OWNER_BY_VIEW.put(target, claim);
            shouldApply = !snapshot.alpha.isClaimed();
            if (shouldApply) snapshot.alpha.claim(target.getAlpha());
        }
        if (shouldApply) runModuleMutation(() -> target.setAlpha(0f));
    }

    private static void claimElevation(Claim claim, View target) {
        if (claim == null || target == null) return;
        boolean shouldApply;
        synchronized (LOCK) {
            Snapshot snapshot = claim.snapshotLocked(target);
            OWNER_BY_VIEW.put(target, claim);
            shouldApply = !snapshot.elevation.isClaimed();
            if (shouldApply) snapshot.elevation.claim(target.getElevation());
        }
        if (shouldApply) runModuleMutation(() -> target.setElevation(0f));
    }

    private static void releaseClaim(Claim claim, boolean restore) {
        if (claim == null) return;

        List<ListenerEntry> listeners = new ArrayList<>();
        List<RestoreEntry> restores = new ArrayList<>();
        synchronized (LOCK) {
            BY_BASE.remove(claim.structure.keyboardArea);
            for (Map.Entry<ViewGroup, View.OnLayoutChangeListener> entry
                    : claim.holderLayoutListeners.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    listeners.add(new ListenerEntry(entry.getKey(), entry.getValue()));
                }
            }
            claim.holderLayoutListeners.clear();

            for (ViewGroup holder : claim.structure.keyboardViewHolders) {
                if (holder != null && CLAIM_BY_HOLDER.get(holder) == claim) {
                    CLAIM_BY_HOLDER.remove(holder);
                }
            }
            for (Map.Entry<View, Snapshot> entry : claim.snapshots.entrySet()) {
                View target = entry.getKey();
                if (target == null) continue;
                if (OWNER_BY_VIEW.get(target) == claim) OWNER_BY_VIEW.remove(target);
                if (restore) restores.add(new RestoreEntry(target, entry.getValue().releaseLocked()));
            }
            claim.snapshots.clear();
        }

        for (ListenerEntry entry : listeners) {
            try { entry.holder.removeOnLayoutChangeListener(entry.listener); }
            catch (Throwable ignored) {}
        }
        if (!restore) return;

        runModuleMutation(() -> {
            for (RestoreEntry entry : restores) entry.restore();
        });
    }

    private static void runModuleMutation(Runnable action) {
        if (action == null) return;
        int depth = MODULE_MUTATION_DEPTH.get();
        MODULE_MUTATION_DEPTH.set(depth + 1);
        try {
            action.run();
        } finally {
            if (depth == 0) MODULE_MUTATION_DEPTH.remove();
            else MODULE_MUTATION_DEPTH.set(depth);
        }
    }

    private static boolean isModuleMutation() {
        return MODULE_MUTATION_DEPTH.get() > 0;
    }

    private static void log(String message, Throwable error) {
        try { Api101Bridge.log("[DC][GboardFloatingGlass] " + message, error); }
        catch (Throwable ignored) {}
    }

    private enum VisualProperty {
        BACKGROUND,
        ALPHA,
        ELEVATION
    }

    private static final class Claim {
        final GboardFloatingStructureResolver.Structure structure;
        final Map<View, Snapshot> snapshots = new WeakHashMap<>();
        final Map<ViewGroup, View.OnLayoutChangeListener> holderLayoutListeners =
                new WeakHashMap<>();

        Claim(GboardFloatingStructureResolver.Structure structure) {
            this.structure = structure;
        }

        List<View> staticTargets() {
            ArrayList<View> targets = new ArrayList<>();
            targets.add(structure.keyboardArea);
            targets.add(structure.stockBackground);
            targets.add(structure.contentColumn);
            targets.add(structure.keyboardHolder);
            targets.add(structure.bottomFrame);
            targets.add(structure.topEdge);
            targets.addAll(structure.keyboardViewHolders);
            return targets;
        }

        Snapshot snapshotLocked(View target) {
            Snapshot snapshot = snapshots.get(target);
            if (snapshot == null) {
                snapshot = new Snapshot();
                snapshots.put(target, snapshot);
            }
            return snapshot;
        }

        boolean recordVendorWriteLocked(
                View target, VisualProperty property, Object rawValue) {
            Snapshot snapshot = snapshots.get(target);
            if (snapshot == null) return false;
            switch (property) {
                case BACKGROUND:
                    return snapshot.background.recordVendorWrite((Drawable) rawValue);
                case ALPHA:
                    return rawValue instanceof Number
                            && snapshot.alpha.recordVendorWrite(((Number) rawValue).floatValue());
                case ELEVATION:
                    return rawValue instanceof Number
                            && snapshot.elevation.recordVendorWrite(((Number) rawValue).floatValue());
                default:
                    return false;
            }
        }
    }

    private static final class Snapshot {
        final GboardVendorIntentState<Drawable> background = new GboardVendorIntentState<>();
        final GboardVendorIntentState<Float> alpha = new GboardVendorIntentState<>();
        final GboardVendorIntentState<Float> elevation = new GboardVendorIntentState<>();

        RestoreSnapshot releaseLocked() {
            return new RestoreSnapshot(
                    background.release(),
                    alpha.release(),
                    elevation.release());
        }
    }

    private static final class RestoreSnapshot {
        final GboardVendorIntentState.RestoreDecision<Drawable> background;
        final GboardVendorIntentState.RestoreDecision<Float> alpha;
        final GboardVendorIntentState.RestoreDecision<Float> elevation;

        RestoreSnapshot(
                GboardVendorIntentState.RestoreDecision<Drawable> background,
                GboardVendorIntentState.RestoreDecision<Float> alpha,
                GboardVendorIntentState.RestoreDecision<Float> elevation) {
            this.background = background;
            this.alpha = alpha;
            this.elevation = elevation;
        }
    }

    private static final class RestoreEntry {
        final View target;
        final RestoreSnapshot snapshot;

        RestoreEntry(View target, RestoreSnapshot snapshot) {
            this.target = target;
            this.snapshot = snapshot;
        }

        void restore() {
            if (snapshot.background.restore) target.setBackground(snapshot.background.value);
            if (snapshot.elevation.restore && snapshot.elevation.value != null) {
                target.setElevation(snapshot.elevation.value);
            }
            if (snapshot.alpha.restore && snapshot.alpha.value != null) {
                target.setAlpha(snapshot.alpha.value);
            }
        }
    }

    private static final class ListenerEntry {
        final ViewGroup holder;
        final View.OnLayoutChangeListener listener;

        ListenerEntry(ViewGroup holder, View.OnLayoutChangeListener listener) {
            this.holder = holder;
            this.listener = listener;
        }
    }
}
