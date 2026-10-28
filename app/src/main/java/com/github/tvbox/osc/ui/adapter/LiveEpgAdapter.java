package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.ui.tv.widget.AudioWaveView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class LiveEpgAdapter extends BaseQuickAdapter<Epginfo, BaseViewHolder> {
    private int selectedEpgIndex = -1;
    private int focusedEpgIndex = -1;
//    public static float fontSize = 20;
//    private final int defaultShiyiSelection = 0;
    private boolean ShiyiSelection = false;
    private boolean source_include_back = false;
    private String shiyiDate = null;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd");

    public LiveEpgAdapter() {
        super(R.layout.item_epglist, new ArrayList<>());
    }

    public void CanBack(Boolean source_include_back) {
        this.source_include_back = source_include_back;
    }

    @Override
    protected void convert(BaseViewHolder holder, Epginfo value) {
        Date now = new Date();
        TextView textview = holder.getView(R.id.tv_epg_name);
        TextView timeview = holder.getView(R.id.tv_epg_time);
        TextView shiyi = holder.getView(R.id.shiyi);
        AudioWaveView wqddg_AudioWaveView = holder.getView(R.id.wqddg_AudioWaveView);
        wqddg_AudioWaveView.setVisibility(View.GONE);

        if (value.index == selectedEpgIndex && value.index != focusedEpgIndex && (value.currentEpgDate.equals(shiyiDate) || value.currentEpgDate.equals(timeFormat.format(now)))) {
            // takagen99: Added Theme Color
//            textview.setTextColor(mContext.getResources().getColor(R.color.color_theme));
//            timeview.setTextColor(mContext.getResources().getColor(R.color.color_theme));
            textview.setTextColor((mContext instanceof BaseActivity ? ((BaseActivity) mContext).getThemeColor() : 0));
            timeview.setTextColor((mContext instanceof BaseActivity ? ((BaseActivity) mContext).getThemeColor() : 0));
        } else {
            textview.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
            timeview.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
        }

        if (now.compareTo(value.startdateTime) >= 0 && now.compareTo(value.enddateTime) <= 0) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(mContext.getResources().getColor(R.color.color_32364E));
            shiyi.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
            shiyi.setText(mContext.getString(R.string.epg_live));
        } else if (now.compareTo(value.enddateTime) > 0 && source_include_back) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(mContext.getResources().getColor(R.color.color_32364E_40));
            shiyi.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
            shiyi.setText(mContext.getString(R.string.epg_playback));
        } else if (now.compareTo(value.startdateTime) < 0 && source_include_back) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(mContext.getResources().getColor(R.color.color_3D3D3D));
            shiyi.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
            shiyi.setText(mContext.getString(R.string.epg_booking));
        } else {
            shiyi.setVisibility(View.GONE);
        }

        textview.setText(value.title);
        timeview.setText(value.start + " - " + value.end);
        textview.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
        timeview.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
        if (ShiyiSelection == false) {
            if (now.compareTo(value.startdateTime) >= 0 && now.compareTo(value.enddateTime) <= 0) {
                wqddg_AudioWaveView.setVisibility(View.VISIBLE);
                // takagen99: Added Theme Color
//                textview.setTextColor(mContext.getResources().getColor(R.color.color_theme));
//                timeview.setTextColor(mContext.getResources().getColor(R.color.color_theme));
                textview.setTextColor((mContext instanceof BaseActivity ? ((BaseActivity) mContext).getThemeColor() : 0));
                timeview.setTextColor((mContext instanceof BaseActivity ? ((BaseActivity) mContext).getThemeColor() : 0));
                textview.setFreezesText(true);
                timeview.setFreezesText(true);
                shiyi.setText(mContext.getString(R.string.epg_live_now));
            } else {
                wqddg_AudioWaveView.setVisibility(View.GONE);
            }
        } else {
            if (value.index == this.selectedEpgIndex && value.currentEpgDate.equals(shiyiDate)) {
                wqddg_AudioWaveView.setVisibility(View.VISIBLE);
                // takagen99: Added Theme Color
//                textview.setTextColor(mContext.getResources().getColor(R.color.color_theme));
//                timeview.setTextColor(mContext.getResources().getColor(R.color.color_theme));
                textview.setTextColor((mContext instanceof BaseActivity ? ((BaseActivity) mContext).getThemeColor() : 0));
                timeview.setTextColor((mContext instanceof BaseActivity ? ((BaseActivity) mContext).getThemeColor() : 0));
                textview.setFreezesText(true);
                timeview.setFreezesText(true);
                shiyi.setText(mContext.getString(R.string.epg_playback_now));
                if (now.compareTo(value.startdateTime) >= 0 && now.compareTo(value.enddateTime) <= 0) {
                    shiyi.setText(mContext.getString(R.string.epg_live_now));
                }
                wqddg_AudioWaveView.setVisibility(View.VISIBLE);
            } else {
                wqddg_AudioWaveView.setVisibility(View.GONE);
            }
        }
    }

    public void setShiyiSelection(int i, boolean t, String currentEpgDate) {
        int previous = this.selectedEpgIndex;
        boolean wasShiyi = ShiyiSelection;
        this.selectedEpgIndex = i;
        this.shiyiDate = t ? currentEpgDate : null;
        ShiyiSelection = t;
        // 原实现无条件 notifyItemChanged(i)：i 为 -1（取消时移）时会用无效下标刷新，属隐患。
        // 时移态之间的切换只影响「旧选中项 + 新选中项」，可以只局部刷新两项；
        // 进出时移态还会改变「正在播」那一项的波形与文案，其位置不固定，只能全量刷新。
        if (wasShiyi && t) {
            if (previous >= 0 && previous != i) notifyItemChanged(previous);
            if (i >= 0) notifyItemChanged(i);
        } else {
            notifyDataSetChanged();
        }
    }

    public int getSelectedIndex() {
        return selectedEpgIndex;
    }

    public void setSelectedEpgIndex(int selectedEpgIndex) {
        if (selectedEpgIndex == this.selectedEpgIndex) return;
        this.selectedEpgIndex = selectedEpgIndex;
        if (this.selectedEpgIndex != -1)
            notifyItemChanged(this.selectedEpgIndex);
    }

    public int getFocusedEpgIndex() {
        return focusedEpgIndex;
    }

    public void setFocusedEpgIndex(int focusedEpgIndex) {
        this.focusedEpgIndex = focusedEpgIndex;
        if (this.focusedEpgIndex != -1)
            notifyItemChanged(this.focusedEpgIndex);
    }
}
