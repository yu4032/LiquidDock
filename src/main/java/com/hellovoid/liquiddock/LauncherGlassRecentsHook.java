package com.hellovoid.liquiddock;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.WeakHashMap;

/** Uses HyperOS semantic Recents and wallpaper-animation boundaries instead of wall-clock delays. */
final class LauncherGlassRecentsHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String TRACE_TAG = "[DC][WallpaperReturnTrace]";
    private static final String RECENTS_DISPATCHER =
            "com.miui.home.recents.RecentsServiceDispatcher";
    private static final String LOCAL_WALLPAPER =
            "com.miui.home.recents.anim.LocalWallpaperElement";
    private static final String SYSTEM_WALLPAPER =
            "com.miui.home.recents.anim.SystemWallpaperElement";
    private static final String WALLPAPER_PARAM =
            "com.miui.home.recents.anim.WallpaperParam";
    private static final String HYPER_SPRING =
            "com.miui.home.recents.anim.HyperSpringAnimation";
    private static final String MULTI_SPRING =
            "com.miui.home.recents.anim.MultiSpringDynamicAnimation";

    private static final RecentsWallpaperSettleState WALLPAPER_SETTLE =
            new RecentsWallpaperSettleState();
    private static final ThreadLocal<Long> LOCAL_WALLPAPER_SERIAL = new ThreadLocal<>();
    private static final WeakHashMap<Object, Long> LOCAL_SPRING_SERIALS = new WeakHashMap<>();
    private static final ArrayDeque<Long> SYSTEM_DRAW_END_SERIALS = new ArrayDeque<>();
    // Debug-only tracking: never grants wallpaper settle authority.
    private static final WeakHashMap<Object, String> DIAGNOSTIC_ZOOM_TARGETS =
            new WeakHashMap<>();
    private static volatile long diagnosticReturnSerial = -1L;

    private static boolean installed;

    private LauncherGlassRecentsHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed || config == null || !config.enabled || !config.glass.enabled) return;
        LauncherRecentsCapsuleGlassHook.install(classLoader);
        installWallpaperSettleAuthority(classLoader);
        installStateManagerDiagnostic(classLoader);
        try {
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewShow", chain -> {
                long staleReturn = WALLPAPER_SETTLE.pendingSerial();
                trace("recents-show previousReturn=" + diagnosticReturnSerial
                        + " pendingSerial=" + staleReturn);
                diagnosticReturnSerial = -1L;
                WALLPAPER_SETTLE.onRecentsShown();
                if (staleReturn > 0L) discardWallpaperAuthorities(staleReturn);
                LauncherGlassSceneController.setRecentsCoveredForAll(true);
                LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                // Workspace coverage intentionally suspends its producer. The Recents capsules are
                // a separate live owner, so reclaim the root update flag only after vendor show
                // handling has completed and any competing pause has already happened.
                LauncherRecentsCapsuleGlassHook.onRecentsShown();
                return result;
            });
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewHide", chain -> {
                boolean workstationMode = MainHook.isWorkstationMode();
                boolean recentsCovered = LauncherGlassSceneController.isRecentsCoveredByVendor();
                if (!recentsCovered) {
                    MainHook.log(TAG + " ignoring non-covered Recents hide");
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                // Arm before vendor hide handling. Launcher can start the wallpaper HOME spring
                // from inside onRecentViewHide(), so arming afterwards can miss the real start.
                final long supersededSerial = WALLPAPER_SETTLE.pendingSerial();
                final long serial = WALLPAPER_SETTLE.onReturnStarted();
                diagnosticReturnSerial = serial;
                trace("recents-hide-start serial=" + serial
                        + " covered=" + recentsCovered
                        + " workstation=" + workstationMode);
                if (supersededSerial > 0L) {
                    discardWallpaperAuthorities(supersededSerial);
                    MainHook.log(TAG + " Recents wallpaper return superseded oldSerial="
                            + supersededSerial + " newSerial=" + serial);
                }
                LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(true);

                Object result;
                try {
                    result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                } catch (Throwable error) {
                    cancelWallpaperSettle(serial, "vendor-hide-exception");
                    throw error;
                }

                // If vendor hide handling did not bind this return serial to either the Local
                // spring-end path or the System wallpaper draw-end path, no future callback can
                // legally release this barrier. Fail closed only for the duration of vendor hide
                // handling, then drop the impossible fence instead of wedging future HOME/wallpaper
                // freshness indefinitely.
                boolean wallpaperAuthorityArmed =
                        WALLPAPER_SETTLE.hasCompletionAuthority(serial);
                trace("recents-hide-vendor-return serial=" + serial
                        + " authorityArmed=" + wallpaperAuthorityArmed);
                if (!wallpaperAuthorityArmed) {
                    cancelWallpaperSettle(serial, "no-vendor-wallpaper-authority");
                }

                boolean rolloverAccepted = true;
                if (WorkstationRecentsRecoveryPolicy.shouldRequestRollover(
                        workstationMode, recentsCovered)) {
                    // Workstation endpoint rollover is a separate authority. Acceptance only says
                    // the replacement producer lifecycle may proceed; it never means wallpaper
                    // animation settled or a fresh frame exists.
                    rolloverAccepted = LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn();
                }
                WorkstationRecentsRecoveryPolicy.Decision recovery =
                        WorkstationRecentsRecoveryPolicy.onRecentsReturn(
                                workstationMode, rolloverAccepted);
                if (!recovery.allowUncover) {
                    cancelWallpaperSettle(serial, "workstation-rollover-rejected");
                    MainHook.log(TAG
                            + " Workstation Recents producer rollover rejected; HOME remains covered"
                            + " serial=" + serial);
                    return result;
                }

                LauncherGlassSceneController.setRecentsCoveredForAll(false);
                MainHook.log(TAG + " Recents HOME return "
                        + (wallpaperAuthorityArmed
                        ? "armed wallpaper authority"
                        : "without wallpaper authority")
                        + " serial=" + serial);
                return result;
            });
            installed = true;
            MainHook.log(TAG + " semantic Recents dispatcher hooks installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " semantic Recents dispatcher unavailable: " + error);
        }
    }

    /**
     * HyperOS 4.50 has two wallpaper implementations:
     * LocalWallpaperElement owns a HyperSpringAnimation, while SystemWallpaperElement delegates the
     * spring to miui.wallpaper.animation. Each path therefore uses its own vendor completion event.
     */

    /**
     * Observe the two known semantic paths from the frozen Launcher 4.50 decompilation.
     * These hooks report event ordering only. They never change the vendor dispatch result.
     */
    private static void installStateManagerDiagnostic(ClassLoader loader) {
        try {
            Class<?> eventClass = Class.forName(
                    "com.miui.home.recents.event.Event", false, loader);
            Method eventType = eventClass.getMethod("getType");
            for (String owner : new String[]{
                    "com.miui.home.recents.anim.StateManager$RecentState",
                    "com.miui.home.recents.anim.StateManager$IdleState"}) {
                try {
                    HookUtil.hookMethod(loader, owner, "handleEvent", chain -> {
                        if (MainHook.debugLogging) {
                            try {
                                Object event = chain.getArg(0);
                                int type = ((Number) eventType.invoke(event)).intValue();
                                if (type == 6203 || type == 7013) {
                                    trace("state-event type=" + type + " owner=" + owner
                                            + " detail=" + diagnosticEventDetail(event, type)
                                            + " event=" + describeParam(event));
                                }
                            } catch (Throwable error) {
                                trace("state-event-read-error owner=" + owner
                                        + " error=" + error.getClass().getSimpleName());
                            }
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    }, "com.miui.home.recents.event.Event");
                    trace("state-observer-installed owner=" + owner);
                } catch (Throwable error) {
                    trace("state-observer-unavailable owner=" + owner
                            + " error=" + error.getClass().getSimpleName());
                }
            }
        } catch (Throwable error) {
            trace("state-observer-unavailable error=" + error.getClass().getSimpleName());
        }
    }

    static long diagnosticReturnSerial() {
        return diagnosticReturnSerial;
    }

    private static String diagnosticEventDetail(Object event, int type) {
        if (event == null) return "null";
        try {
            if (type == 6203) {
                return "toHome=" + event.getClass().getMethod("getToHome").invoke(event);
            }
            if (type == 7013) {
                Object info = event.getClass().getMethod("getInfo").invoke(event);
                if (info != null) {
                    return "fromRecentLaunchAnimEnd="
                            + info.getClass().getMethod("isFromRecentLaunchAnimEnd").invoke(info);
                }
            }
        } catch (Throwable ignored) {
            return "unavailable";
        }
        return "none";
    }

    private static String diagnosticCallsite() {
        StringBuilder caller = new StringBuilder();
        try {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                String owner = frame.getClassName();
                if (!owner.startsWith("com.miui.home.")
                        || owner.startsWith("com.hellovoid.liquiddock.")) continue;
                if (caller.length() > 0) caller.append(" <- ");
                caller.append(owner.substring("com.miui.home.".length()))
                        .append('.').append(frame.getMethodName())
                        .append(':').append(frame.getLineNumber());
                if (caller.length() > 250) break;
            }
        } catch (Throwable ignored) {
            return "unavailable";
        }
        return caller.length() > 0 ? caller.toString() : "unknown";
    }

    private static String identity(Object object) {
        return object == null ? "null" : Integer.toHexString(System.identityHashCode(object));
    }

    private static String describeParam(Object object) {
        if (object == null) return "null";
        try {
            return String.valueOf(object).replace((char) 10, ' ');
        } catch (Throwable ignored) {
            return object.getClass().getSimpleName();
        }
    }

    private static String queryRunning(Object animation) {
        if (animation == null) return "unknown";
        try {
            Method method = animation.getClass().getMethod("isRunning");
            return String.valueOf(method.invoke(animation));
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static void trace(String message) {
        if (MainHook.debugLogging) {
            MainHook.log(TRACE_TAG + " returnSerial=" + diagnosticReturnSerial + " " + message);
        }
    }

    private static void installWallpaperSettleAuthority(ClassLoader classLoader) {
        try {
            Class<?> wallpaperParam = Class.forName(WALLPAPER_PARAM, false, classLoader);
            Class<?> localWallpaper = Class.forName(LOCAL_WALLPAPER, false, classLoader);
            Class<?> hyperSpring = Class.forName(HYPER_SPRING, false, classLoader);
            Class<?> multiSpring = Class.forName(MULTI_SPRING, false, classLoader);

            // Vendor LocalWallpaperElement.setTo() cancels a running spring first, then writes the
            // snapped zoom value synchronously. That snap supersedes any return animation which had
            // armed our settle fence, so release only after vendor setTo() has applied the final
            // wallpaper target.
            HookUtil.hookMethod(localWallpaper, "setTo", new Class<?>[]{wallpaperParam}, chain -> {
                long serial = WALLPAPER_SETTLE.pendingSerial();
                if (MainHook.debugLogging) {
                    trace("local-setTo target=" + describeParam(chain.getArg(0))
                            + " pendingSerial=" + serial);
                }
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                trace("local-setTo-applied pendingSerial=" + serial);
                if (serial > 0L) {
                    cancelWallpaperSettle(serial, "local-wallpaper-setTo");
                }
                return result;
            });

            HookUtil.hookMethod(localWallpaper, "animTo", new Class<?>[]{wallpaperParam}, chain -> {
                long serial = WALLPAPER_SETTLE.pendingSerial();
                if (MainHook.debugLogging) {
                    trace("local-animTo target=" + describeParam(chain.getArg(0))
                            + " pendingSerial=" + serial
                            + " owner=" + identity(chain.getThisObject()));
                }
                Long previous = LOCAL_WALLPAPER_SERIAL.get();
                if (serial > 0L) LOCAL_WALLPAPER_SERIAL.set(serial);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    if (previous == null) LOCAL_WALLPAPER_SERIAL.remove();
                    else LOCAL_WALLPAPER_SERIAL.set(previous);
                }
            });

            // LocalWallpaperElement always expresses its target through the public semantic
            // HyperSpringAnimation.setFinalPosition("zoom", ...), even when the spring is already
            // running. Associate that exact animation instance with the current return serial.
            HookUtil.hookMethod(hyperSpring, "setFinalPosition",
                    new Class<?>[]{String.class, float.class}, chain -> {
                        Long serial = LOCAL_WALLPAPER_SERIAL.get();
                        Object type = chain.getArg(0);
                        if (MainHook.debugLogging && "zoom".equals(String.valueOf(type))) {
                            String target = String.valueOf(chain.getArg(1));
                            synchronized (DIAGNOSTIC_ZOOM_TARGETS) {
                                DIAGNOSTIC_ZOOM_TARGETS.put(chain.getThisObject(), target);
                            }
                            trace("local-zoom-retarget spring=" + identity(chain.getThisObject())
                                    + " target=" + target + " scopedSerial=" + serial
                                    + " running=" + queryRunning(chain.getThisObject()));
                        }
                        if (serial != null && serial > 0L && "zoom".equals(String.valueOf(type))
                                && WALLPAPER_SETTLE.armCompletionAuthority(serial)) {
                            synchronized (LOCAL_SPRING_SERIALS) {
                                LOCAL_SPRING_SERIALS.put(chain.getThisObject(), serial);
                            }
                            MainHook.log(TAG + " Recents wallpaper authority=local-spring serial="
                                    + serial);
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });

            // MultiSpringDynamicAnimation.doAnimationFrame() returns true only when every bundle
            // reached its final spring state and endAnimationInternal(false) ran. The distinct
            // LocalWallpaperElement.setTo() hook above owns the vendor cancellation/snap path.
            HookUtil.hookMethod(multiSpring, "doAnimationFrame",
                    new Class<?>[]{long.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!Boolean.TRUE.equals(result)) return result;
                        Long serial;
                        synchronized (LOCAL_SPRING_SERIALS) {
                            serial = LOCAL_SPRING_SERIALS.remove(chain.getThisObject());
                        }
                        String target;
                        synchronized (DIAGNOSTIC_ZOOM_TARGETS) {
                            target = DIAGNOSTIC_ZOOM_TARGETS.remove(chain.getThisObject());
                        }
                        if (target != null) {
                            trace("local-spring-frame-terminal spring="
                                    + identity(chain.getThisObject())
                                    + " target=" + target + " scopedSerial=" + serial);
                        }
                        if (serial != null) {
                            releaseWallpaperSettle(serial, "local-wallpaper-spring-end");
                        }
                        return result;
                    });
            MainHook.log(TAG + " LocalWallpaperElement spring-end authority installed");
            try {
                HookUtil.hookMethod(multiSpring, "cancel", new Class<?>[0], chain -> {
                    Object animation = chain.getThisObject();
                    String target;
                    synchronized (DIAGNOSTIC_ZOOM_TARGETS) {
                        target = DIAGNOSTIC_ZOOM_TARGETS.remove(animation);
                    }
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (target != null) {
                        trace("local-spring-cancel spring=" + identity(animation)
                                + " priorTarget=" + target);
                    }
                    return result;
                });
            } catch (Throwable error) {
                trace("local-cancel-observer-unavailable error="
                        + error.getClass().getSimpleName());
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " Local wallpaper settle authority unavailable: " + error);
        }

        try {
            Class<?> wallpaperParam = Class.forName(WALLPAPER_PARAM, false, classLoader);
            Class<?> systemWallpaper = Class.forName(SYSTEM_WALLPAPER, false, classLoader);

            // SystemWallpaperElement.setTo() sends the explicit "setTo" command to
            // miui.wallpaper.animation. It semantically replaces a prior startAnim command and is
            // the only Launcher-side terminal signal when the wallpaper service emits no draw-end
            // for a superseded/no-op spring.
            HookUtil.hookMethod(systemWallpaper, "setTo",
                    new Class<?>[]{wallpaperParam}, chain -> {
                        long serial = WALLPAPER_SETTLE.pendingSerial();
                        if (MainHook.debugLogging) {
                            trace("system-setTo target=" + describeParam(chain.getArg(0))
                                    + " pendingSerial=" + serial
                                    + " callsite=" + diagnosticCallsite());
                        }
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        trace("system-setTo-applied pendingSerial=" + serial);
                        if (serial > 0L) {
                            cancelWallpaperSettle(serial, "system-wallpaper-setTo");
                        }
                        return result;
                    });

            HookUtil.hookMethod(systemWallpaper, "animTo",
                    new Class<?>[]{wallpaperParam}, chain -> {
                        long serial = WALLPAPER_SETTLE.pendingSerial();
                        if (MainHook.debugLogging) {
                            trace("system-animTo target=" + describeParam(chain.getArg(0))
                                    + " pendingSerial=" + serial
                                    + " owner=" + identity(chain.getThisObject())
                                    + " callsite=" + diagnosticCallsite());
                        }
                        boolean armed = serial > 0L && armSystemDrawEnd(serial);
                        if (armed && !WALLPAPER_SETTLE.armCompletionAuthority(serial)) {
                            rollbackSystemDrawEnd(serial);
                            armed = false;
                        }
                        if (armed) {
                            MainHook.log(TAG + " Recents wallpaper authority=system-draw-end serial="
                                    + serial);
                        }
                        try {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        } catch (Throwable error) {
                            if (armed) {
                                rollbackSystemDrawEnd(serial);
                                WALLPAPER_SETTLE.revokeCompletionAuthority(serial);
                            }
                            throw error;
                        }
                    });
            MainHook.log(TAG + " SystemWallpaperElement draw-end authority armed");
        } catch (Throwable error) {
            MainHook.log(TAG + " System wallpaper settle authority unavailable: " + error);
        }
    }

    /**
     * A new wallpaper content generation supersedes any Recents settle authority that belongs to
     * the previous wallpaper. Keeping that old serial pending would make the new cache-ready
     * generation wait for an animation completion which can no longer validate its pixels.
     */
    static void onWallpaperContentChanged() {
        long serial = WALLPAPER_SETTLE.pendingSerial();
        if (serial <= 0L || !WALLPAPER_SETTLE.cancelReturn(serial)) return;
        discardWallpaperAuthorities(serial);
        // SceneController clears its capture barrier as part of onWallpaperChangedForAll(), without
        // requesting a frame. The matching WallpaperInfoUpdateTask completion remains the sole
        // cache-ready authority allowed to start the new wallpaper pulse.
        MainHook.log(TAG + " Recents wallpaper authority superseded by content generation serial="
                + serial);
    }

    /** Called by the existing typed MIUI wallpaper callback bridge on vendor onDrawFrameEnd(). */
    static void onSystemWallpaperDrawFrameEnd() {
        long serial;
        synchronized (SYSTEM_DRAW_END_SERIALS) {
            Long queued = SYSTEM_DRAW_END_SERIALS.pollFirst();
            serial = queued == null ? -1L : queued;
        }
        trace("system-draw-frame-end queuedSerial=" + serial
                + " pendingSerial=" + WALLPAPER_SETTLE.pendingSerial());
        if (serial > 0L) {
            releaseWallpaperSettle(serial, "system-wallpaper-draw-end");
        }
    }

    private static boolean armSystemDrawEnd(long serial) {
        synchronized (SYSTEM_DRAW_END_SERIALS) {
            Long tail = SYSTEM_DRAW_END_SERIALS.peekLast();
            if (tail != null && tail == serial) return false;
            SYSTEM_DRAW_END_SERIALS.addLast(serial);
            return true;
        }
    }

    private static void rollbackSystemDrawEnd(long serial) {
        synchronized (SYSTEM_DRAW_END_SERIALS) {
            Long tail = SYSTEM_DRAW_END_SERIALS.peekLast();
            if (tail != null && tail == serial) SYSTEM_DRAW_END_SERIALS.removeLast();
        }
    }

    private static void discardSystemDrawEnd(long serial) {
        if (serial <= 0L) return;
        synchronized (SYSTEM_DRAW_END_SERIALS) {
            SYSTEM_DRAW_END_SERIALS.removeIf(
                    queued -> queued != null && queued.longValue() == serial);
        }
    }

    private static void discardLocalSpring(long serial) {
        if (serial <= 0L) return;
        synchronized (LOCAL_SPRING_SERIALS) {
            LOCAL_SPRING_SERIALS.entrySet().removeIf(
                    entry -> entry.getValue() != null && entry.getValue().longValue() == serial);
        }
    }

    private static void discardWallpaperAuthorities(long serial) {
        discardLocalSpring(serial);
        discardSystemDrawEnd(serial);
    }

    private static boolean cancelWallpaperSettle(long serial, String reason) {
        if (!WALLPAPER_SETTLE.cancelReturn(serial)) return false;
        discardWallpaperAuthorities(serial);
        LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
        MainHook.log(TAG + " Recents wallpaper settle cancelled reason=" + reason
                + " serial=" + serial);
        trace("settle-cancelled serial=" + serial + " reason=" + reason);
        return true;
    }

    private static void releaseWallpaperSettle(long serial, String authority) {
        if (!WALLPAPER_SETTLE.onWallpaperSettled(serial)) {
            MainHook.log(TAG + " ignoring stale Recents wallpaper settle authority=" + authority
                    + " serial=" + serial + " active=" + WALLPAPER_SETTLE.activeSerial());
            return;
        }
        discardWallpaperAuthorities(serial);
        LauncherGlassSceneController.setRecentsWallpaperSettlePendingForAll(false);
        MainHook.log(TAG + " Recents wallpaper settled authority=" + authority
                + " serial=" + serial);
        trace("settle-confirmed serial=" + serial + " authority=" + authority);
    }
}
