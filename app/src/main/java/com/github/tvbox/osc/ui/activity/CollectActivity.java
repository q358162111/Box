package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;
import com.github.tvbox.osc.ui.adapter.CollectAdapter;

import java.util.List;

public class CollectActivity extends BaseVodListActivity<VodCollect> {

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_collect;
    }

    @Override
    protected BaseQuickAdapter<VodCollect, BaseViewHolder> createAdapter() {
        return new CollectAdapter();
    }

    @Override
    protected String clearTag() {
        return "Collect";
    }

    @Override
    protected void loadData() {
        // 收藏全表读移后台，完成后回主线程渲染
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                final List<VodCollect> data = RoomDataManger.getAllVodCollect();
                AppTaskExecutor.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        listAdapter.setNewData(data);
                    }
                });
            }
        });
    }

    @Override
    protected void onItemDeleted(VodCollect vodInfo) {
        // 删除写库移后台
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                RoomDataManger.deleteVodCollect(vodInfo.getId());
            }
        });
    }

    @Override
    protected void onItemClicked(VodCollect vodInfo, int position) {
        if (ApiConfig.get().getSource(vodInfo.sourceKey) != null) {
            Bundle bundle = new Bundle();
            bundle.putString("id", vodInfo.vodId);
            bundle.putString("sourceKey", vodInfo.sourceKey);
            jumpActivity(DetailActivity.class, bundle);
        } else {
            Intent newIntent = new Intent(mContext, SearchActivity.class);
            newIntent.putExtra("title", vodInfo.name);
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(newIntent);
        }
    }
}
