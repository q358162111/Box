package com.github.tvbox.osc.util;

import static android.content.Context.UI_MODE_SERVICE;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class ScreenUtils {

    public static double getSqrt(Activity activity) {
        if (activity == null) return 0;
        WindowManager wm = activity.getWindowManager();
        if (wm == null) return 0;
        DisplayMetrics dm = new DisplayMetrics();
        // 修复：getDefaultDisplay() 在 API 30+ 标记 deprecated，统一用 WindowMetrics
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                android.view.WindowMetrics metrics = wm.getCurrentWindowMetrics();
                dm.widthPixels = metrics.getBounds().width();
                dm.heightPixels = metrics.getBounds().height();
                // xdpi/ydpi 在 WindowMetrics 中不可用，getMetrics 已从 Display 获取；保留 fallback
                wm.getDefaultDisplay().getMetrics(dm);
            } catch (Throwable ignored) {
            }
        } else {
            wm.getDefaultDisplay().getMetrics(dm);
        }
        // 防御：xdpi/ydpi 可能为 0（部分设备），避免除零返回 NaN
        if (dm.xdpi <= 0 || dm.ydpi <= 0) return 0;
        double x = Math.pow(dm.widthPixels / dm.xdpi, 2);
        double y = Math.pow(dm.heightPixels / dm.ydpi, 2);
        double screenInches = Math.sqrt(x + y);// 屏幕尺寸
        return screenInches;
    }

    private static boolean checkScreenLayoutIsTv(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) > Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    private static boolean checkIsPhone(Context context) {
        // 防御：getSystemService 返回 null 时返回 false（视为非手机）
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) return false;
        try {
            return telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        } catch (Exception e) {
            // 部分设备（平板/AndroidTV 无 telephony 权限）getPhoneType 抛 SecurityException
            return false;
        }
    }

    public static boolean isTv(Context context) {
        // 防御：getSystemService 返回 null 时直接返回 false
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        if (uiModeManager == null) return false;
        try {
            return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION || (checkScreenLayoutIsTv(context) && !checkIsPhone(context));
        } catch (Exception e) {
            return false;
        }
    }

}