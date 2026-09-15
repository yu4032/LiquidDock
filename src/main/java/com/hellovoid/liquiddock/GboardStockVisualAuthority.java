package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps Gboard's own floating-keyboard fills and shadow suppressed while LiquidDock glass is
 * presented. Runtime visual-tree evidence for the audited Gboard build proves two additional
 * opaque qzw layers: the 0x7f0b064f top-edge frame and the active main KeyboardViewHolder content.
 * Both are therefore owned explicitly and restored on release; key drawables remain untouched.
 */
final class GboardStockVisualAuthority {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static final Object LOCK = new Object();

    private static final int KEYBOARD_HEADER_VIEW_HOLDER_ID = 0x7f0b0643;
    private static final int MAIN_KEYBOARD_VIEW_HOLDER_ID = 0x7f0b061a;
    private static final int AUX_KEYBOARD_VIEW_HOLDER_ID = 0x7f0b02f6;
    private static final int KEYBOARD_HOLDER_ID = 0x7f0b0644;
    private static final int KEYBOARD_TOP_EDGE_FRAME_ID = 0x7f0b064f;
    private static final int[] CONTAINER_IDS = {
            KEYBOARD_HEADER_VIEW_HOLDER_ID,
            MAIN_KEYBOARD_VIEW_HOLDER_ID,
            AUX_KEYBOARD_VIEW_HOLDER_ID,
            KEYBOARD_HOLDER_ID
    };

    private static final class Claim {
        final View baseArea;
        final View bottomFrame;
        final Drawable baseBackground;
        final Drawable bottomBackground;
        final float baseElevation;
        final View[] containers;
        final Drawable[] containerBackgrounds;
        final View headerHolder;
        final View mainHolder;
        final View topEdge;
        final Drawable topEdgeBackground;
        final Map<View, Drawable> boundContentBackgrounds = new WeakHashMap<>();
        // Alias retained for the original header-content regression contract.
        final Map<View, Drawable> headerContentBackgrounds = boundContentBackgrounds;

        Claim(View baseArea, View bottomFrame) {
            this.baseArea = baseArea;
            this.bottomFrame = bottomFrame;
            this.baseBackground = baseArea.getBackground();
            this.bottomBackground = bottomFrame.getBackground();
            this.baseElevation = baseArea.getElevation();
            this.containers = new View[CONTAINER_IDS.length];
            this.containerBackgrounds = new Drawable[CONTAINER_IDS.length];
            View resolvedHeaderHolder = null;
            View resolvedMainHolder = null;
            for (int i = 0; i < CONTAINER_IDS.length; i++) {
                View view = baseArea.findViewById(CONTAINER_IDS[i]);
                containers[i] = view;
                containerBackgrounds[i] = view != null ? view.getBackground() : null;
                if (CONTAINER_IDS[i] == KEYBOARD_HEADER_VIEW_HOLDER_ID) {
                    resolvedHeaderHolder = view;
                } else if (CONTAINER_IDS[i] == MAIN_KEYBOARD_VIEW_HOLDER_ID) {
                    resolvedMainHolder = view;
                }
            }
            this.headerHolder = resolvedHeaderHolder;
            this.mainHolder = resolvedMainHolder;
            this.topEdge = baseArea.findViewById(KEYBOARD_TOP_EDGE_FRAME_ID);
            this.topEdgeBackground = topEdge != null ? topEdge.getBackground() : null;
        }
    }

    private static final Map<View, Claim> BY_BASE = new WeakHashMap<>();
    private static final Map<View, Claim> BY_BOTTOM = new WeakHashMap<>();
    private static final Map<View, Claim> BY_DYNAMIC_HOLDER = new WeakHashMap<>();
    private static boolean installed;

    private GboardStockVisualAuthority() {}

    static boolean install(ClassLoader classLoader) {
        synchronized (LOCK) {
            if (installed) return true;
            if (classLoader == null) return false;
            try {
                Class<?> manager = classLoader.loadClass("pef");
                Method setBaseColor = manager.getDeclaredMethod("j", Integer.TYPE);
                Method setBottomColor = manager.getDeclaredMethod("e", Integer.TYPE);
                Class<?> keyboardViewHolder = classLoader.loadClass(
                        "com.google.android.libraries.inputmethod.keyboard.impl.KeyboardViewHolder");
                Method bindKeyboardView = findKeyboardViewBindMethod(keyboardViewHolder);

                HookUtil.hook(setBaseColor, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    View baseArea = reflectedView(owner, "e");
                    Claim claim = claimForBase(baseArea);
                    if (claim != null) {
                        applyClaim(claim);
                        log("preserved transparent floating stock visuals against pef.j(int)");
                    }
                    return result;
                });

                HookUtil.hook(setBottomColor, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    View bottomFrame = reflectedView(owner, "o");
                    Claim claim = claimForBottom(bottomFrame);
                    if (claim != null) {
                        applyClaim(claim);
                        log("preserved transparent floating stock visuals against pef.e(int)");
                    }
                    return result;
                });

                HookUtil.hook(bindKeyboardView, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    View holder = owner instanceof View ? (View) owner : null;
                    Claim claim = claimForDynamicHolder(holder);
                    if (claim != null) {
                        suppressBoundContentBackground(claim, holder);
                        log("preserved transparent bound keyboard content against "
                                + "KeyboardViewHolder.j(...)");
                    }
                    return result;
                });

                installed = true;
                log("stock visual authority installed");
                return true;
            } catch (Throwable error) {
                log("stock visual authority unavailable cause=" + failureSummary(error));
                return false;
            }
        }
    }

    static boolean claim(View baseArea, View bottomFrame) {
        if (baseArea == null || bottomFrame == null) return false;
        Claim claim;
        synchronized (LOCK) {
            Claim existing = BY_BASE.get(baseArea);
            if (existing != null) {
                applyClaim(existing);
                return existing.bottomFrame == bottomFrame;
            }
            claim = new Claim(baseArea, bottomFrame);
            BY_BASE.put(baseArea, claim);
            BY_BOTTOM.put(bottomFrame, claim);
            if (claim.headerHolder != null) BY_DYNAMIC_HOLDER.put(claim.headerHolder, claim);
            if (claim.mainHolder != null) BY_DYNAMIC_HOLDER.put(claim.mainHolder, claim);
        }
        applyClaim(claim);
        GboardVisualTreeDiagnostics.dump(baseArea);
        log("claimed stock visuals baseElevation=" + claim.baseElevation
                + " containerCount=" + presentContainerCount(claim)
                + " boundContentCount=" + claim.boundContentBackgrounds.size()
                + " topEdge=" + (claim.topEdge != null));
        return true;
    }

    static void release(View baseArea, View bottomFrame) {
        Claim claim = null;
        synchronized (LOCK) {
            if (baseArea != null) claim = BY_BASE.get(baseArea);
            if (claim == null && bottomFrame != null) claim = BY_BOTTOM.get(bottomFrame);
            if (claim == null) return;
            BY_BASE.remove(claim.baseArea);
            BY_BOTTOM.remove(claim.bottomFrame);
            if (claim.headerHolder != null) BY_DYNAMIC_HOLDER.remove(claim.headerHolder);
            if (claim.mainHolder != null) BY_DYNAMIC_HOLDER.remove(claim.mainHolder);
        }
        try { claim.baseArea.setBackground(claim.baseBackground); }
        catch (Throwable ignored) {}
        try { claim.baseArea.setElevation(claim.baseElevation); }
        catch (Throwable ignored) {}
        try { claim.bottomFrame.setBackground(claim.bottomBackground); }
        catch (Throwable ignored) {}
        if (claim.topEdge != null) {
            try { claim.topEdge.setBackground(claim.topEdgeBackground); }
            catch (Throwable ignored) {}
        }
        for (int i = 0; i < claim.containers.length; i++) {
            View view = claim.containers[i];
            Drawable saved = claim.containerBackgrounds[i];
            if (view == null) continue;
            try { view.setBackground(saved); } catch (Throwable ignored) {}
        }
        for (Map.Entry<View, Drawable> entry : claim.boundContentBackgrounds.entrySet()) {
            View content = entry.getKey();
            Drawable saved = entry.getValue();
            if (content == null) continue;
            try { content.setBackground(saved); } catch (Throwable ignored) {}
        }
        claim.boundContentBackgrounds.clear();
        log("released stock visuals");
    }

    private static void applyClaim(Claim claim) {
        if (claim == null) return;
        try { claim.baseArea.setBackground(null); } catch (Throwable ignored) {}
        try { claim.baseArea.setElevation(0f); } catch (Throwable ignored) {}
        try { claim.bottomFrame.setBackground(null); } catch (Throwable ignored) {}
        View topEdge = claim.topEdge;
        if (topEdge != null) {
            try { topEdge.setBackground(null); } catch (Throwable ignored) {}
        }
        for (View view : claim.containers) {
            if (view == null) continue;
            try { view.setBackground(null); } catch (Throwable ignored) {}
        }
        suppressHeaderContentBackground(claim);
        suppressBoundContentBackground(claim, claim.mainHolder);
    }

    private static void suppressHeaderContentBackground(Claim claim) {
        if (claim == null) return;
        suppressBoundContentBackground(claim, claim.headerHolder);
    }

    private static void suppressBoundContentBackground(Claim claim, View holder) {
        if (claim == null || holder == null) return;
        View content = reflectedView(holder, "b");
        if (content == null) return;
        synchronized (LOCK) {
            if (!claim.boundContentBackgrounds.containsKey(content)) {
                claim.boundContentBackgrounds.put(content, content.getBackground());
            }
        }
        try { content.setBackground(null); } catch (Throwable ignored) {}
    }

    private static Method findKeyboardViewBindMethod(Class<?> keyboardViewHolder) throws Exception {
        Method match = null;
        for (Method method : keyboardViewHolder.getDeclaredMethods()) {
            if (!"j".equals(method.getName())) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 4) continue;
            if (!View.class.isAssignableFrom(params[2]) || params[3] != String.class) continue;
            if (match != null) {
                throw new NoSuchMethodException("ambiguous KeyboardViewHolder.j bind method");
            }
            match = method;
        }
        if (match == null) throw new NoSuchMethodException("KeyboardViewHolder.j bind method missing");
        return match;
    }

    private static int presentContainerCount(Claim claim) {
        int count = 0;
        if (claim != null) {
            for (View view : claim.containers) if (view != null) count++;
        }
        return count;
    }

    private static Claim claimForBase(View baseArea) {
        if (baseArea == null) return null;
        synchronized (LOCK) { return BY_BASE.get(baseArea); }
    }

    private static Claim claimForBottom(View bottomFrame) {
        if (bottomFrame == null) return null;
        synchronized (LOCK) { return BY_BOTTOM.get(bottomFrame); }
    }

    private static Claim claimForDynamicHolder(View holder) {
        if (holder == null) return null;
        synchronized (LOCK) { return BY_DYNAMIC_HOLDER.get(holder); }
    }

    private static Claim claimForHeaderHolder(View headerHolder) {
        return claimForDynamicHolder(headerHolder);
    }

    private static View reflectedView(Object owner, String fieldName) {
        if (owner == null) return null;
        try {
            Object value = HookUtil.getField(owner, fieldName);
            return value instanceof View ? (View) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String failureSummary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
