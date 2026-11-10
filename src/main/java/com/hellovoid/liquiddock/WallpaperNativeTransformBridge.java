package com.hellovoid.liquiddock;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Applied-transform callbacks and capture history over the existing Launcher -> WMS command. */
final class WallpaperNativeTransformBridge {
    private static final String KEY = "com.hellovoid.liquiddock.wallpaperTransform";
    private static final String DESCRIPTOR = "com.hellovoid.liquiddock.WallpaperTransform";
    private static final int UPDATE = IBinder.FIRST_CALL_TRANSACTION;
    private static final int CAPTURE = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final ConcurrentHashMap<Integer, IBinder> LISTENERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Integer> LISTENER_UIDS =
            new ConcurrentHashMap<>();
    private static final WallpaperNativeTransformStore APPLIED =
            new WallpaperNativeTransformStore();
    private static volatile IBinder remoteTimeline;
    private static final java.util.concurrent.atomic.AtomicBoolean FAILURE_REPORTED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final IBinder CLIENT =
            new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                        throws RemoteException {
                    if (code != UPDATE) return super.onTransact(code, data, reply, flags);
                    if (Binder.getCallingUid() != 1000) return false;
                    data.enforceInterface(DESCRIPTOR);
                    int display = data.readInt();
                    long timestamp = data.readLong();
                    float scale = data.readFloat(),
                            pivotX = data.readFloat(),
                            pivotY = data.readFloat();
                    remoteTimeline = data.readStrongBinder();
                    data.enforceNoDataAvail();
                    LauncherWallpaperZoomHook.onNativeTransform(
                            display, timestamp, scale, pivotX, pivotY);
                    return true;
                }
            };
    private static final IBinder TIMELINE =
            new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                        throws RemoteException {
                    if (code != CAPTURE) return super.onTransact(code, data, reply, flags);
                    data.enforceInterface(DESCRIPTOR);
                    int display = data.readInt();
                    long timestamp = data.readLong();
                    data.enforceNoDataAvail();
                    Integer uid = LISTENER_UIDS.get(display);
                    if (uid == null || uid != Binder.getCallingUid()) return false;
                    LauncherWallpaperTransformState.Sample sample =
                            APPLIED.capture(display, timestamp, 50);
                    reply.writeNoException();
                    reply.writeInt(sample != null ? 1 : 0);
                    if (sample != null) {
                        reply.writeLong(sample.timestampNs);
                        reply.writeFloat(sample.scale);
                        reply.writeFloat(sample.pivotX);
                        reply.writeFloat(sample.pivotY);
                    }
                    return true;
                }
            };

    static void attach(Bundle command) {
        command.putBinder(KEY, CLIENT);
    }

    static boolean isConnected() {
        return remoteTimeline != null;
    }

    /** Called only when replacing the immutable HWUI backdrop, never per View draw. */
    static LauncherWallpaperTransformState.Sample capture(int display, long timestampNs) {
        IBinder timeline = remoteTimeline;
        if (timeline == null) return null;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(display);
            data.writeLong(timestampNs);
            if (!timeline.transact(CAPTURE, data, reply, 0)) return null;
            reply.readException();
            return reply.readInt() != 0
                    ? new LauncherWallpaperTransformState.Sample(
                            reply.readLong(),
                            reply.readFloat(),
                            reply.readFloat(),
                            reply.readFloat())
                    : null;
        } catch (RemoteException dead) {
            remoteTimeline = null;
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    static void installSystemObserver(ClassLoader loader) {
        try {
            Class<?> manager =
                    Class.forName(
                            "com.android.server.wm.MiuiWallpaperAnimationManager", false, loader);
            Class<?> animation =
                    Class.forName(
                            "com.android.server.wm.MiuiWallpaperSurfaceAnimation", false, loader);
            Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
            Class<?> display = Class.forName("com.android.server.wm.DisplayContent", false, loader);
            Field dc = field(animation, "mDisplayContent");
            Field scale = field(animation, "mHomeWallpaperZoomOut");
            Field pivotX = field(animation, "mPivotX"), pivotY = field(animation, "mPivotY");
            Method displayId = display.getDeclaredMethod("getDisplayId");
            displayId.setAccessible(true);
            Field attrs = field(window, "mAttrs");
            Field ownerUid = field(window, "mOwnerUid");
            Method command =
                    manager.getDeclaredMethod(
                            "startWallpaperAnimation",
                            java.util.ArrayList.class,
                            window,
                            Bundle.class,
                            display);
            HookUtil.hook(
                    command,
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Bundle bundle = (Bundle) args[2];
                        try {
                            if (bundle != null && args[1] != null && args[3] != null) {
                                WindowManager.LayoutParams params =
                                        (WindowManager.LayoutParams) attrs.get(args[1]);
                                IBinder callback = bundle.getBinder(KEY);
                                if (callback != null
                                        && params != null
                                        && "com.miui.home".equals(params.packageName)) {
                                    int id = (Integer) displayId.invoke(args[3]);
                                    LISTENER_UIDS.put(id, ownerUid.getInt(args[1]));
                                    LISTENERS.put(id, callback);
                                    LauncherWallpaperTransformState.Sample applied =
                                            APPLIED.latest(id);
                                    if (applied != null) send(id, callback, applied);
                                }
                                bundle.remove(KEY);
                            }
                        } catch (Throwable error) {
                            reportFailure(error);
                        }
                        return chain.proceed(args);
                    });
            Class<?> update = Class.forName("miuix.animation.listener.UpdateInfo", false, loader);
            HookUtil.hook(
                    animation.getDeclaredMethod("updateAnim", update),
                    chain -> {
                        APPLIED.beginUpdate();
                        try {
                            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            // Read after the complete native update: apply() and updateParams()
                            // have
                            // both finished. Hooking the tiny updateParams() helper separately
                            // misses
                            // compiled callers which inline it, leaving only completion endpoints.
                            observeApplied(
                                    chain.getThisObject(), dc, displayId, scale, pivotX, pivotY);
                            return result;
                        } finally {
                            APPLIED.endUpdate();
                        }
                    });
            // The vendor completion path applies its exact endpoint through WallpaperController
            // before resetting animation fields. Keep that final applied state, not the target
            // originally requested by the Launcher.
            Field surfaceAnimation = field(manager, "mSurfaceAnimation");
            Field homeShouldScale = field(animation, "mHomeShouldScaleWallpaper");
            HookUtil.hook(
                    manager.getDeclaredMethod(
                            "setWallpaperScale", float.class, float.class, float.class),
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        APPLIED.beginUpdate();
                        try {
                            Object result = chain.proceed(args);
                            try {
                                Object instance = surfaceAnimation.get(null);
                                Object content = dc.get(instance);
                                float value = ((Number) args[0]).floatValue();
                                if (content != null && value >= 1f && Float.isFinite(value)) {
                                    if (!homeShouldScale.getBoolean(instance)) value = 1f;
                                    int id = (Integer) displayId.invoke(content);
                                    recordApplied(
                                            id,
                                            new LauncherWallpaperTransformState.Sample(
                                                    System.nanoTime(),
                                                    value,
                                                    ((Number) args[1]).floatValue(),
                                                    ((Number) args[2]).floatValue()));
                                }
                            } catch (Throwable error) {
                                reportFailure(error);
                            }
                            return result;
                        } finally {
                            APPLIED.endUpdate();
                        }
                    });
            Api101Bridge.log(
                    "[DC][WallpaperTransform] native applied-transform observer installed");
            android.util.Log.i("LiquidDock-System", "native wallpaper observer installed");
        } catch (Throwable error) {
            Api101Bridge.log("[DC][WallpaperTransform] native observer unavailable", error);
            android.util.Log.e("LiquidDock-System", "native wallpaper observer unavailable", error);
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static void observeApplied(
            Object animation,
            Field display,
            Method displayId,
            Field scale,
            Field pivotX,
            Field pivotY) {
        if (animation == null) return;
        try {
            Object dc = display.get(animation);
            if (dc == null) return;
            int id = (Integer) displayId.invoke(dc);
            float value = scale.getFloat(animation);
            if (!Float.isFinite(value) || value <= 0f) return;
            LauncherWallpaperTransformState.Sample sample =
                    new LauncherWallpaperTransformState.Sample(
                            System.nanoTime(),
                            value,
                            pivotX.getFloat(animation),
                            pivotY.getFloat(animation));
            recordApplied(id, sample);
        } catch (Throwable error) {
            reportFailure(error);
        }
    }

    private static void recordApplied(int display, LauncherWallpaperTransformState.Sample sample) {
        APPLIED.record(display, sample);
        IBinder callback = LISTENERS.get(display);
        if (callback != null) send(display, callback, sample);
    }

    private static void send(
            int display, IBinder callback, LauncherWallpaperTransformState.Sample sample) {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(display);
            data.writeLong(sample.timestampNs);
            data.writeFloat(sample.scale);
            data.writeFloat(sample.pivotX);
            data.writeFloat(sample.pivotY);
            data.writeStrongBinder(TIMELINE);
            if (!callback.transact(UPDATE, data, null, IBinder.FLAG_ONEWAY))
                LISTENERS.remove(display, callback);
        } catch (RemoteException dead) {
            LISTENERS.remove(display, callback);
        } finally {
            data.recycle();
        }
    }

    private static void reportFailure(Throwable error) {
        if (FAILURE_REPORTED.compareAndSet(false, true)) {
            Api101Bridge.log("[DC][WallpaperTransform] applied transform unavailable", error);
        }
    }
}
