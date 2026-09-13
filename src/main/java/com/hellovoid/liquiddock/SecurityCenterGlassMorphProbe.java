package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diagnostic-only observer for Security Center glass geometry and presentation flow. */
final class SecurityCenterGlassMorphProbe {
    private static final String TAG = "[DC][GlassMorphProbe]";
    private static final State STATE = new State();
    private static final Object FRAME_LOCK = new Object();
    private static final int MAX_BOUND_FRAMES = 64;

    private static long nextFrameId;
    private static FrameSnapshot pendingFrame;
    private static final LinkedHashMap<Long, BoundFrame> BOUND_FRAMES =
            new LinkedHashMap<Long, BoundFrame>() {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, BoundFrame> eldest) {
                    return size() > MAX_BOUND_FRAMES;
                }
            };
    private static final IdentityHashMap<Object, OutputBinding> OUTPUT_BINDINGS =
            new IdentityHashMap<>();

    static final class State {
        private final Map<String, GeometrySample> lastGeometry = new LinkedHashMap<>();

        synchronized boolean shouldLogGeometry(
                String stage,
                String role,
                long generation,
                float left,
                float top,
                float right,
                float bottom,
                float radius,
                int sinkWidth,
                int sinkHeight,
                boolean ready,
                boolean authorized) {
            String key = safe(stage) + '|' + safe(role);
            GeometrySample next = new GeometrySample(
                    generation, left, top, right, bottom, radius,
                    sinkWidth, sinkHeight, ready, authorized);
            GeometrySample previous = lastGeometry.get(key);
            if (next.sameAs(previous)) return false;
            lastGeometry.put(key, next);
            return true;
        }

        synchronized void reset() {
            lastGeometry.clear();
        }
    }

    private static final class GeometrySample {
        final long generation;
        final float left;
        final float top;
        final float right;
        final float bottom;
        final float radius;
        final int sinkWidth;
        final int sinkHeight;
        final boolean ready;
        final boolean authorized;

        GeometrySample(
                long generation,
                float left,
                float top,
                float right,
                float bottom,
                float radius,
                int sinkWidth,
                int sinkHeight,
                boolean ready,
                boolean authorized) {
            this.generation = generation;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radius = radius;
            this.sinkWidth = sinkWidth;
            this.sinkHeight = sinkHeight;
            this.ready = ready;
            this.authorized = authorized;
        }

        boolean sameAs(GeometrySample other) {
            return other != null
                    && generation == other.generation
                    && close(left, other.left)
                    && close(top, other.top)
                    && close(right, other.right)
                    && close(bottom, other.bottom)
                    && close(radius, other.radius)
                    && sinkWidth == other.sinkWidth
                    && sinkHeight == other.sinkHeight
                    && ready == other.ready
                    && authorized == other.authorized;
        }
    }

    private static final class NodeSnapshot {
        final String role;
        final float left;
        final float top;
        final float right;
        final float bottom;
        final float radius;

        NodeSnapshot(String role, SecurityCenterGlassGeometry geometry) {
            this.role = role;
            left = geometry.left;
            top = geometry.top;
            right = geometry.left + geometry.width;
            bottom = geometry.top + geometry.height;
            radius = geometry.cornerRadius;
        }
    }

    private static final class FrameSnapshot {
        final long frameId;
        final NodeSnapshot[] nodes;

        FrameSnapshot(long frameId, NodeSnapshot[] nodes) {
            this.frameId = frameId;
            this.nodes = nodes;
        }
    }

    private static final class BoundFrame {
        final FrameSnapshot frame;
        final long generation;
        final long serial;

        BoundFrame(FrameSnapshot frame, long generation, long serial) {
            this.frame = frame;
            this.generation = generation;
            this.serial = serial;
        }
    }

    private static final class OutputBinding {
        final long serial;
        final long generation;
        final String role;

        OutputBinding(long serial, long generation, String role) {
            this.serial = serial;
            this.generation = generation;
            this.role = role;
        }
    }

    private SecurityCenterGlassMorphProbe() {}

    static void stageFrame(
            SecurityCenterGlassGeometry dock,
            SecurityCenterGlassGeometry box,
            SecurityCenterGlassGeometry apps) {
        if (dock == null) return;
        List<NodeSnapshot> nodes = new ArrayList<>(3);
        nodes.add(new NodeSnapshot("DOCK", dock));
        if (box != null) nodes.add(new NodeSnapshot("TOOLBOX", box));
        if (apps != null) nodes.add(new NodeSnapshot("ALL_APPS", apps));
        synchronized (FRAME_LOCK) {
            pendingFrame = new FrameSnapshot(++nextFrameId, nodes.toArray(new NodeSnapshot[0]));
        }
    }

    static void bindLatestFrame(long generation, long serial) {
        if (generation < 0L || serial < 0L) return;
        FrameSnapshot frame;
        synchronized (FRAME_LOCK) {
            frame = pendingFrame;
            if (frame == null) return;
            BOUND_FRAMES.put(serial, new BoundFrame(frame, generation, serial));
        }
        boolean changed = false;
        for (NodeSnapshot node : frame.nodes) {
            if (STATE.shouldLogGeometry(
                    "RESOLVED", node.role, generation,
                    node.left, node.top, node.right, node.bottom, node.radius,
                    0, 0, true, false)) {
                changed = true;
                log(formatGeometry(
                        "RESOLVED", node.role, generation, serial,
                        node.left, node.top, node.right, node.bottom, node.radius,
                        0, 0, true, false)
                        + " frame=" + frame.frameId);
            }
        }
        if (changed) {
            event("FRAME_BIND", "FRAME", generation, serial,
                    "frame=" + frame.frameId + " nodes=" + frame.nodes.length);
        }
    }

    static void sourceAccepted(long generation, long serial) {
        event("SOURCE_ACCEPT", "FRAME", generation, serial, "");
    }

    static void submitted(long serial, Object[] outputs) {
        BoundFrame bound;
        synchronized (FRAME_LOCK) {
            bound = BOUND_FRAMES.get(serial);
        }
        if (bound == null) {
            event("SUBMIT", "FRAME", -1L, serial, "frame=missing");
            return;
        }
        for (int i = 0; i < bound.frame.nodes.length; i++) {
            NodeSnapshot node = bound.frame.nodes[i];
            Object output = outputs != null && i < outputs.length ? outputs[i] : null;
            int width = invokeInt(output, "getWidth", 0);
            int height = invokeInt(output, "getHeight", 0);
            boolean ready = invokeBoolean(output, "isPresentationReady", false);
            boolean authorized = readBooleanField(output, "authorizedVisible", false);
            float contentAlpha = readFloatField(output, "contentAlpha", Float.NaN);
            int visibility = invokeInt(output, "getVisibility", -1);
            synchronized (FRAME_LOCK) {
                if (output != null) {
                    OUTPUT_BINDINGS.put(output,
                            new OutputBinding(serial, bound.generation, node.role));
                }
            }
            geometryEvent(
                    "SUBMIT", node.role, bound.generation, serial,
                    node.left, node.top, node.right, node.bottom, node.radius,
                    width, height, ready, authorized);
            event("SUBMIT_STATE", node.role, bound.generation, serial,
                    "frame=" + bound.frame.frameId
                            + " contentAlpha=" + contentAlpha
                            + " viewVisibility=" + visibility);
        }
    }

    static void surfaceAck(long serial, Object output, int acknowledged, int required, boolean complete) {
        OutputBinding binding;
        synchronized (FRAME_LOCK) {
            binding = OUTPUT_BINDINGS.get(output);
        }
        long generation = binding != null ? binding.generation : generationForSerial(serial);
        String role = binding != null ? binding.role : "UNKNOWN";
        event("SURFACE_ACK", role, generation, serial,
                "acknowledged=" + acknowledged + "/" + required
                        + " complete=" + complete
                        + " sink=" + invokeInt(output, "getWidth", 0)
                        + "x" + invokeInt(output, "getHeight", 0)
                        + " ready=" + invokeBoolean(output, "isPresentationReady", false)
                        + " authorized=" + readBooleanField(output, "authorizedVisible", false)
                        + " contentAlpha=" + readFloatField(output, "contentAlpha", Float.NaN)
                        + " viewVisibility=" + invokeInt(output, "getVisibility", -1));
    }

    static void frameAck(
            long serial,
            long generation,
            boolean acceptedCurrent,
            boolean requestSource,
            long nextGeneration) {
        event("ACK", "FRAME", generation, serial,
                "acceptedCurrent=" + acceptedCurrent
                        + " requestSource=" + requestSource
                        + " nextGeneration=" + nextGeneration);
    }

    static void presentationCancelled(long serial, String reason) {
        event("CANCEL", "FRAME", generationForSerial(serial), serial, "reason=" + safe(reason));
    }

    static void ownership(String transition, String owner, boolean vendorSuppressed) {
        event("OWNERSHIP", "MATERIAL", -1L, -1L,
                "transition=" + safe(transition)
                        + " owner=" + safe(owner)
                        + " vendorSuppressed=" + vendorSuppressed);
    }

    static void reset() {
        STATE.reset();
        synchronized (FRAME_LOCK) {
            pendingFrame = null;
            BOUND_FRAMES.clear();
            OUTPUT_BINDINGS.clear();
        }
        event("RESET", "FRAME", -1L, -1L, "");
    }

    static void geometryEvent(
            String stage,
            String role,
            long generation,
            long serial,
            float left,
            float top,
            float right,
            float bottom,
            float radius,
            int sinkWidth,
            int sinkHeight,
            boolean ready,
            boolean authorized) {
        log(formatGeometry(
                stage, role, generation, serial,
                left, top, right, bottom, radius,
                sinkWidth, sinkHeight, ready, authorized));
    }

    static String formatGeometry(
            String stage,
            String role,
            long generation,
            long serial,
            float left,
            float top,
            float right,
            float bottom,
            float radius,
            int sinkWidth,
            int sinkHeight,
            boolean ready,
            boolean authorized) {
        return "stage=" + safe(stage)
                + " role=" + safe(role)
                + " generation=" + generation
                + " serial=" + serial
                + " rect=[" + Float.toString(left)
                + "," + Float.toString(top)
                + "," + Float.toString(right)
                + "," + Float.toString(bottom) + "]"
                + " radius=" + Float.toString(radius)
                + " sink=" + sinkWidth + "x" + sinkHeight
                + " ready=" + ready
                + " authorized=" + authorized;
    }

    static void event(String event, String role, long generation, long serial, String details) {
        StringBuilder line = new StringBuilder()
                .append("event=").append(safe(event))
                .append(" role=").append(safe(role))
                .append(" generation=").append(generation)
                .append(" serial=").append(serial);
        if (details != null && !details.isEmpty()) line.append(' ').append(details);
        log(line.toString());
    }

    private static long generationForSerial(long serial) {
        synchronized (FRAME_LOCK) {
            BoundFrame bound = BOUND_FRAMES.get(serial);
            return bound != null ? bound.generation : -1L;
        }
    }

    private static int invokeInt(Object target, String name, int fallback) {
        Object value = invoke(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean invokeBoolean(Object target, String name, boolean fallback) {
        Object value = invoke(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static Object invoke(Object target, String name) {
        if (target == null) return null;
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean readBooleanField(Object target, String name, boolean fallback) {
        Object value = readField(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static float readFloatField(Object target, String name, float fallback) {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }

    private static boolean close(float first, float second) {
        if (Float.isNaN(first) || Float.isNaN(second)) {
            return Float.isNaN(first) && Float.isNaN(second);
        }
        if (Float.isInfinite(first) || Float.isInfinite(second)) return first == second;
        return Math.abs(first - second) < 0.01f;
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }
}
