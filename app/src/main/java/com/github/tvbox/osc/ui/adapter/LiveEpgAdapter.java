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

    // ---- 绑定路径缓存：以下值都是「列表级常量」，原实现在每个 item 每次 bind 都重新取一遍 ----
    // Resources.getColor() 已废弃且内部走 AssetManager 加锁，getString() 走 AssetManager，
    // 主题色是 Activity 级常量；EPG 列表上百条时焦点移动会反复触发重绑，缓存后全部降为字段读。
    private boolean resourcesResolved = false;
    private int colorWhite;
    private int color32364E;
    private int color32364E_40;
    private int color3D3D3D;
    private int themeColor;
    private String strLive;
    private String strPlayback;
    private String strBooking;
    private String strLiveNow;
    private String strPlaybackNow;

    // "yyyy-MM-dd" 粒度到天，30s 刷新一次足够；避免每个 item 每次 bind 都做一次
    // SimpleDateFormat.format（内部要初始化 Calendar、查时区表）
    private String todayStr;
    private long todayStrUpdatedAt = 0L;

    public LiveEpgAdapter() {
        super(R.layout.item_epglist, new ArrayList<>());
    }

    private void ensureResources() {
        if (resourcesResolved) return;
        resourcesResolved = true;
        colorWhite = mContext.getResources().getColor(R.color.color_FFFFFF);
        color32364E = mContext.getResources().getColor(R.color.color_32364E);
        color32364E_40 = mContext.getResources().getColor(R.color.color_32364E_40);
        color3D3D3D = mContext.getResources().getColor(R.color.color_3D3D3D);
        themeColor = (mContext instanceof BaseActivity ? ((BaseActivity) mContext).getThemeColor() : 0);
        strLive = mContext.getString(R.string.epg_live);
        strPlayback = mContext.getString(R.string.epg_playback);
        strBooking = mContext.getString(R.string.epg_booking);
        strLiveNow = mContext.getString(R.string.epg_live_now);
        strPlaybackNow = mContext.getString(R.string.epg_playback_now);
    }

    private String getTodayStr(long nowMs) {
        if (todayStr == null || nowMs - todayStrUpdatedAt > 30_000L) {
            todayStr = timeFormat.format(new Date(nowMs));
            todayStrUpdatedAt = nowMs;
        }
        return todayStr;
    }

    public void CanBack(Boolean source_include_back) {
        this.source_include_back = source_include_back;
    }

    @Override
    protected void convert(BaseViewHolder holder, Epginfo value) {
        ensureResources();
        long nowMs = System.currentTimeMillis();
        // 用 long 比较代替新建 Date 再做 compareTo：省掉每个 item 一次对象分配
        long startMs = value.startdateTime != null ? value.startdateTime.getTime() : 0L;
        long endMs = value.enddateTime != null ? value.enddateTime.getTime() : 0L;
        boolean isLive = nowMs >= startMs && nowMs <= endMs;
        boolean isPast = nowMs > endMs;
        boolean isFuture = nowMs < startMs;

        String today = getTodayStr(nowMs);
        TextView textview = holder.getView(R.id.tv_epg_name);
        TextView timeview = holder.getView(R.id.tv_epg_time);
        TextView shiyi = holder.getView(R.id.shiyi);
        AudioWaveView wqddg_AudioWaveView = holder.getView(R.id.wqddg_AudioWaveView);
        wqddg_AudioWaveView.setVisibility(View.GONE);

        if (value.index == selectedEpgIndex && value.index != focusedEpgIndex && (value.currentEpgDate.equals(shiyiDate) || value.currentEpgDate.equals(today))) {
            // takagen99: Added Theme Color
            textview.setTextColor(themeColor);
            timeview.setTextColor(themeColor);
        } else {
            textview.setTextColor(colorWhite);
            timeview.setTextColor(colorWhite);
        }

        if (isLive) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(color32364E);
            shiyi.setTextColor(colorWhite);
            shiyi.setText(strLive);
        } else if (isPast && source_include_back) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(color32364E_40);
            shiyi.setTextColor(colorWhite);
            shiyi.setText(strPlayback);
        } else if (isFuture && source_include_back) {
            shiyi.setVisibility(View.VISIBLE);
            shiyi.setBackgroundColor(color3D3D3D);
            shiyi.setTextColor(colorWhite);
            shiyi.setText(strBooking);
        } else {
            shiyi.setVisibility(View.GONE);
        }

        textview.setText(value.title);
        timeview.setText(value.start + " - " + value.end);
        textview.setTextColor(colorWhite);
        timeview.setTextColor(colorWhite);
        if (ShiyiSelection == false) {
            if (isLive) {
                wqddg_AudioWaveView.setVisibility(View.VISIBLE);
                // takagen99: Added Theme Color
                textview.setTextColor(themeColor);
                timeview.setTextColor(themeColor);
                textview.setFreezesText(true);
                timeview.setFreezesText(true);
                shiyi.setText(strLiveNow);
            } else {
                wqddg_AudioWaveView.setVisibility(View.GONE);
            }
        } else {
            if (value.index == this.selectedEpgIndex && value.currentEpgDate.equals(shiyiDate)) {
                wqddg_AudioWaveView.setVisibility(View.VISIBLE);
                // takagen99: Added Theme Color
                textview.setTextColor(themeColor);
                timeview.setTextColor(themeColor);
                textview.setFreezesText(true);
                timeview.setFreezesText(true);
                shiyi.setText(strPlaybackNow);
                if (isLive) {
                    shiyi.setText(strLiveNow);
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
