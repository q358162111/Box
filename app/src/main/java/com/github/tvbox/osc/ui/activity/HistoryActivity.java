package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.adapter.HistoryAdapter;

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
        List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);
        List<VodInfo> vodInfoList = new ArrayList<>();
        for (VodInfo vodInfo : allVodRecord) {
            if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty()) {
                vodInfo.note = "看到" + vodInfo.playNote;
            }
            vodInfoList.add(vodInfo);
        }
        listAdapter.setNewData(vodInfoList);
    }

    @Override
    protected void onItemDeleted(VodInfo vodInfo) {
        RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo);
    }

    @Override
    protected void onItemClicked(VodInfo vodInfo, int position) {
        Bundle bundle = new Bundle();
        bundle.putString("id", vodInfo.id);
        bundle.putString("sourceKey", vodInfo.sourceKey);
        jumpActivity(DetailActivity.class, bundle);
    }
}
