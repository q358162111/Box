package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class ToastHelper {

    /**
     * 复用主线程 Handler，避免每次调用都创建一个永不退出的 Looper 线程导致线程/Activity 泄漏
     */
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void showToast(Context context, String text) {
        if (context == null || text == null) return;
        // 必须使用 ApplicationContext，否则短时 Toast 仍可能持有 Activity 引用
        final Context appCtx = context.getApplicationContext();
        MAIN_HANDLER.post(() -> Toast.makeText(appCtx, text, Toast.LENGTH_SHORT).show());
    }

    public static void debugToast(Context context, String text) {
        if (HawkConfig.isDebug()) {
            showToast(context, text);
        }
    }
}