package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.view.animation.Interpolator;
import android.widget.Scroller;

/**
 *
 * @author acer
 * @date 2018/7/24
 */

public class FixedSpeedScroller extends Scroller {
    /** 默认 300ms，避免外部未调用 setmDuration 时滚动为瞬时完成 */
    private int mDuration = 300;

    public void setmDuration(int mDuration) {
        this.mDuration = Math.max(mDuration, 1);
    }

    public FixedSpeedScroller(Context context) {
        super(context);
    }

    public FixedSpeedScroller(Context context, Interpolator interpolator) {
        super(context, interpolator);
    }

    public FixedSpeedScroller(Context context, Interpolator interpolator, boolean flywheel) {
        super(context, interpolator, flywheel);
    }


    @Override
    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        super.startScroll(startX, startY, dx, dy, mDuration);
    }

    @Override
    public void startScroll(int startX, int startY, int dx, int dy) {
        super.startScroll(startX, startY, dx, dy, mDuration);
    }
}