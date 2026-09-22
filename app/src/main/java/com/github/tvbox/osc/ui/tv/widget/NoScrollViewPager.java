package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * @author acer
 * @date 2018/7/24
 */

public class NoScrollViewPager extends ViewPager {

    public NoScrollViewPager(@NonNull Context context) {
        this(context, null);
    }

    public NoScrollViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 禁止viewpager里面内容导致页面切换
     * 仅对方向键（D-pad）屏蔽，左右方向键不消费，方便子 View 接收。
     * 否则会丢弃所有按键事件，导致返回键、Home 键等失效。
     *
     * @param event
     * @return
     */
    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        // 修复：原实现 return false 丢弃所有按键，导致 ViewPager 拿到焦点时 DPAD_LEFT/RIGHT/UP/DOWN、
        // Back 等全部被吞。改为：仅屏蔽方向键的页面切换行为，其他按键继续向下派发。
        if (event != null
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return false;
        }
        return super.executeKeyEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // 触屏事件：不消费，但不拦截（onInterceptTouchEvent 已返回 false），系统自然不会切换页面
        return false;
    }

    // Swifly 修正首页触屏左右滑动会移位
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }
}
