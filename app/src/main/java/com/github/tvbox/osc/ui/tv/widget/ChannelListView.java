package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ListView;

public class ChannelListView extends ListView {
    DataChangedListener dataChangedListener;
    // 防御：原代码直接引用 LivePlayActivity.currentChannelGroupIndex 静态字段，
    // 1. 与 LivePlayActivity 形成循环依赖（widget -> Activity）；
    // 2. 默认值在 static 初始化期就固化为 LivePlayActivity 的当时值，无法动态更新；
    // 3. 静态字段引用 Activity 上下文时内存泄漏风险。
    // 改为 0 默认值，使用方应在 setSelect 时显式传入。
    public int pos = 0;
    private int y;

    public ChannelListView(Context context) {
        super(context);
    }

    public ChannelListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ChannelListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSelect(int position, int y) {
        super.setSelection(position);
        pos = position;
        this.y = y;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction,
                                  Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) {
            setSelectionFromTop(pos, y);
        }
    }

    @Override
    protected void handleDataChanged() {
        super.handleDataChanged();
        if (dataChangedListener != null) dataChangedListener.onSuccess();
    }

    public void setDataChangedListener(DataChangedListener dataChangedListener) {
        this.dataChangedListener = dataChangedListener;
    }

    public interface DataChangedListener {
        public void onSuccess();
    }

}
