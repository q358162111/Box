package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.adapter.HistoryAdapter;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
public class HistoryActivity extends BaseVodListActivity<VodInfo> {

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_history;
    }

    @Override
    protected BaseQuickAdapter<VodInfo, BaseViewHolder> createAdapter() {
        return new HistoryAdapter();
    }

    @Override
    protected String clearTag() {
        return "History";
    }

    @Override
    protected void loadData() {
        // 观看记录全表读（含 Gson 反序列化）移后台，完成后回主线程渲染
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);
                final List<VodInfo> vodInfoList = new ArrayList<>();
                for (VodInfo vodInfo : allVodRecord) {
                    if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty()) {
                        vodInfo.note = "看到" + vodInfo.playNote;
                    }
                    vodInfoList.add(vodInfo);
                }
                AppTaskExecutor.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        listAdapter.setNewData(vodInfoList);
                    }
                });
            }
        });
    }

    @Override
    protected void onItemDeleted(VodInfo vodInfo) {
        // 删除写库移后台
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo);
            }
        });
    }

    @Override
    protected void onItemClicked(VodInfo vodInfo, int position) {
        Bundle bundle = new Bundle();
        bundle.putString("id", vodInfo.id);
        bundle.putString("sourceKey", vodInfo.sourceKey);
        jumpActivity(DetailActivity.class, bundle);
    }
}
