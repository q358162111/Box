package com.undcover.freedom.pyramid;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;


/**
 * Created by UndCover on 16/9/7.
 */
public class PyToast {
    private static Toast innerToast;
    private static Context mContext;
    private static PyToast sInstance;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());


    public static void init(Context context) {
        mContext = context;
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
        if (mContext == null) return;
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (innerToast != null) {
                    innerToast.cancel();
                }
                innerToast = Toast.makeText(mContext, msg, duration);
                innerToast.show();
            }
        });
    }

    public static void showMessage(String msg, int duration) {
        if (mContext == null) return;
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, msg, duration).show();
            }
        });
    }
}