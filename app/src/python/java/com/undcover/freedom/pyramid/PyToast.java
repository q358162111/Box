package com.undcover.freedom.pyramid;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


/**
 * Created by UndCover on 16/9/7.
 */
public class PyToast {
    private static Toast innerToast;
    private static Context mContext;
    private static volatile PyToast sInstance;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());


    public static void init(Context context) {
        // 强制使用 Application Context，避免 Activity Context 长期持有导致 Activity 泄漏
        mContext = context != null ? context.getApplicationContext() : null;
        if (mContext != null && !(mContext instanceof Application)) {
            Log.w("PyToast", "init() 期望 Application Context，自动转 applicationContext");
        }
    }

    /**
     * 在Application中 用于初始化
     *
     * @return
     */
    public static PyToast getInstance() {
        if (sInstance == null) {
            synchronized (PyToast.class) {
                if (sInstance == null) {
                    sInstance = new PyToast();
                }
            }
        }
        return sInstance;
    }

    public static void showCancelableToast(String msg) {
        showCancelableToast(msg, Toast.LENGTH_SHORT);
    }

    /**
     * 快速显示Toast,无需排队等待；自动切换到主线程，支持在 Python 线程中调用
     *
     * @param msg
     * @param duration
     */
    public static void showCancelableToast(String msg, int duration) {
        final Context ctx = mContext;
        if (ctx == null || msg == null) return;
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (innerToast != null) {
                    innerToast.cancel();
                }
                innerToast = Toast.makeText(ctx, msg, duration);
                innerToast.show();
            }
        });
    }

    public static void showMessage(String msg, int duration) {
        final Context ctx = mContext;
        if (ctx == null || msg == null) return;
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                // 复用同一 Toast 实例，避免连续 showMessage 时被排队延迟
                if (innerToast == null) {
                    innerToast = Toast.makeText(ctx, msg, duration);
                } else {
                    innerToast.setText(msg);
                    innerToast.setDuration(duration);
                }
                innerToast.show();
            }
        });
    }
}