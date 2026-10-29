package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class CollectAdapter extends BaseQuickAdapter<VodCollect, BaseViewHolder> {
    public CollectAdapter() {
        super(R.layout.item_grid, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodCollect item) {
        // takagen99: Add Delete Mode
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        if (HawkConfig.hotVodDelete) {
            tvDel.setVisibility(View.VISIBLE);
        } else {
            tvDel.setVisibility(View.GONE);
        }

//        helper.setVisible(R.id.tvYear, false);
        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
        helper.setVisible(R.id.tvNote, false);
        helper.setText(R.id.tvName, item.name);
        TextView tvYear = helper.getView(R.id.tvYear);
        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        tvYear.setText(source!=null?source.getName():"");
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        //由于部分电视机使用glide报错
        if (!TextUtils.isEmpty(item.pic)) {
            // takagen99 : Use Glide instead
            // 修复：原调用未传目标尺寸，Glide 不做下采样，海报原图（常 1000x1500 以上）
            // 会按原始分辨率解码进内存。补上 override，与其它列表 adapter 保持一致。
            ImgUtil.load(item.pic, ivThumb, 14,
                    AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth),
                    AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight));
        } else {
            ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }
    }
}