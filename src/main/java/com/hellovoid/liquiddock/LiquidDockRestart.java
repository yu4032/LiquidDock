package com.hellovoid.liquiddock;

import android.content.Context;
import android.widget.Toast;

import java.io.File;

/**
 * 通过 launcher 进程内的原生看门狗重启桌面，不依赖 root。
 *
 * 看门狗（os4/native/safe_mode.h）每秒轮询 restart.request，读到后清标记并
 * SIGKILL 自身，系统随后重新拉起 home，hook 随之重新安装。
 */
public final class LiquidDockRestart {
    public static final String DIR = "/sdcard/Android/media/com.hellovoid.liquiddock";

    private LiquidDockRestart() {}

    public static boolean request() {
        try {
            File dir = new File(DIR);
            if (!dir.exists() && !dir.mkdirs()) return false;
            File f = new File(dir, "restart.request");
            return f.createNewFile() || f.setLastModified(System.currentTimeMillis());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 看门狗自杀后把桌面拉回前台：home 不是 top 时被 kill，系统不会主动重建。 */
    public static void pullHomeForward(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.content.Intent home = new android.content.Intent(
                        android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_HOME)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(home);
            } catch (Throwable ignored) {
            }
        }, 1600);
    }

    public static void requestLauncher(Context context) {
        if (request()) {
            Toast.makeText(context, "桌面即将重启", Toast.LENGTH_SHORT).show();
            pullHomeForward(context);
        } else {
            Toast.makeText(context, "无法写入重启请求", Toast.LENGTH_SHORT).show();
        }
    }
}
