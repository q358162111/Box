package com.github.tvbox.osc.ui.tv.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Created by acer on 2018/7/13.
 */

@SuppressLint("AppCompatCustomView")
public class MarqueeTextView extends TextView{
    public MarqueeTextView(Context context) {
        this(context, null);
    }

    public MarqueeTextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MarqueeTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSelected(true);
        setSingleLine(true);
        setMarqueeRepeatLimit(-1);
        setEllipsize(TextUtils.TruncateAt.MARQUEE);
        // 跑马灯生效条件：必须 focusable + focused，配合 setSelected 才能滚动
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public boolean isFocused() {
        // 注意：永远返回 true 会导致 tv 屏幕连续跑马灯、CPU/GPU 高占用。
        // 原作者意图是让 TextView 在未聚焦时也能滚动，但正确做法是设置 focusable + focused 默认状态。
        return super.isFocused();
    }
}
