package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.dialog.ConfirmClearDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * History/Collect 两个列表页的公共骨架：删除模式、网格布局、清空确认、焦点与缩放动画。
 * 子类只需提供 Adapter、数据加载与条目点击行为。
 */
public abstract class BaseVodListActivity<T> extends BaseActivity {
    protected TextView tvDelTip;
    protected ImageView tvDelete;
    protected ImageView tvClear;
    protected TvRecyclerView mGridView;
    protected BaseQuickAdapter<T, BaseViewHolder> listAdapter;
    protected boolean delMode = false;

    /** 子类返回自己的 Adapter 实例（数据模型不同） */
    protected abstract BaseQuickAdapter<T, BaseViewHolder> createAdapter();

    /** 子类加载数据并 setNewData */
    protected abstract void loadData();

    /** 子类处理条目点击（delMode 已在基类消费） */
    protected abstract void onItemClicked(T item, int position);

    /** ConfirmClearDialog 的页签（"History" / "Collect"） */
    protected abstract String clearTag();

    @Override
    protected void init() {
        initView();
        loadData();
    }

    protected void toggleDelMode() {
        // takagen99: Toggle Delete Mode
        HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
        listAdapter.notifyDataSetChanged();

        delMode = !delMode;
        tvDelTip.setVisibility(delMode ? View.VISIBLE : View.GONE);
    }

    private void initView() {
        EventBus.getDefault().register(this);
        tvDelTip = findViewById(R.id.tvDelTip);
        tvDelete = findViewById(R.id.tvDelete);
        tvClear = findViewById(R.id.tvClear);
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, isBaseOnWidth() ? 5 : 6));
        listAdapter = createAdapter();
        mGridView.setAdapter(listAdapter);
        tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDelMode();
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfirmClearDialog dialog = new ConfirmClearDialog(mContext, clearTag());
                dialog.show();
            }
        });
        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    tvDelete.setFocusable(true);
                    tvClear.setFocusable(true);
                    tvDelete.requestFocus();
                }
                return false;
            }
        });
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        listAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                T item = listAdapter.getData().get(position);
                if (item != null) {
                    if (delMode) {
                        listAdapter.remove(position);
                        onItemDeleted(item);
                    } else {
                        onItemClicked(item, position);
                    }
                }
            }
        });
        listAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                tvDelete.setFocusable(true);
                toggleDelMode();
                return true;
            }
        });
    }

    /** delMode 下点击条目删除一条记录（VodRecord / VodCollect 各自实现） */
    protected abstract void onItemDeleted(T item);

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            loadData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }
}
