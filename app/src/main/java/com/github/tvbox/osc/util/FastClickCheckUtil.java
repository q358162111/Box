package com.github.tvbox.osc.util;

import android.view.View;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
public class FastClickCheckUtil {
    /**
     * 相同视图点击必须间隔0.5s才能有效
     *
     * @param view 目标视图
     */
    public static void check(View view) {
        check(view, 500);
    }

    /**
     * 设置间隔点击规则，配置间隔点击时长
     *
     * @param view  目标视图
     * @param mills 点击间隔时间（毫秒）
     */
    public static void check(final View view, int mills) {
        if (view == null) return;
        view.setClickable(false);
        // 使用 view.postDelayed：消息队列绑定 View 生命周期，View 被销毁/从 Window 分离后自动不再触发，
        // 避免匿名 Runnable + 静态 Handler 持有外部 View 导致 Activity 泄漏。
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (view.isAttachedToWindow()) {
                    view.setClickable(true);
                }
            }
        }, mills);
    }
}