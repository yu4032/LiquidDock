package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;

/** Manual libxposed API 102 hot-reload request. Only the exact SystemUI process is eligible. */
public final class SystemUiHotReloadRequest {
    private static final String TARGET = "com.android.systemui";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SystemUiHotReloadRequest() {}

    public static void request(Context context) {
        final Context app = context.getApplicationContext();
        final XposedService service = LiquidDockApp.service();
        if (service == null) {
            show(app, "Xposed 服务未连接，无法热重载");
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                if (service.getApiVersion() < XposedService.API_102) {
                    show(app, "框架不支持 libxposed API 102");
                    return;
                }
                HookedTarget target = null;
                for (HookedTarget candidate : service.getRunningTargets()) {
                    if (TARGET.equals(candidate.getProcessName())) {
                        target = candidate;
                        break;
                    }
                }
                if (target == null) {
                    show(app, "SystemUI 未出现在模块已注入的运行进程中");
                    return;
                }
                if (target.getState() == HookedTarget.State.UP_TO_DATE) {
                    show(app, "SystemUI 已加载当前模块版本，无需热重载");
                    return;
                }
                if (target.getState() == HookedTarget.State.RELOADING) {
                    show(app, "SystemUI 正在热重载");
                    return;
                }
                // Service validates the snapshot handle and reports the actual result through
                // its callback. Never report success merely because this call returned.
                service.hotReloadModule(target, null, (completed, result) -> {
                    Log.i("LiquidDock", "SystemUI hot reload result: " + result);
                    show(app, "SystemUI 热重载结果：" + result);
                });
                show(app, "已请求 SystemUI 热重载，等待框架反馈");
            } catch (Throwable error) {
                Log.w("LiquidDock", "SystemUI hot reload request unavailable", error);
                show(app, "SystemUI 热重载请求失败，请使用作用域重启");
            }
        }, "LD-SystemUI-HotReload");
        worker.start();
    }

    private static void show(Context context, String message) {
        MAIN.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }
}
