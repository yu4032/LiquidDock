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

    public static void requestLauncher(Context context) {
        if (request()) {
            Toast.makeText(context, "桌面即将重启", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "无法写入重启请求", Toast.LENGTH_SHORT).show();
        }
    }
}
