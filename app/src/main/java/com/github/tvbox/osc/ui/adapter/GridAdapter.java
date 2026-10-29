package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.ImgUtil;
import java.util.ArrayList;
import me.jessyan.autosize.utils.AutoSizeUtils;
/**
 * GridAdapter 支持传入 style 来设置图片的宽高比例，
 * 如果不传 style 则保留旧的默认风格（XML 中 item_grid.xml 定义的尺寸）。
 */
public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private boolean mShowList ;
    private int defaultWidth;
    public ImgUtil.Style style; // 动态风格，传入时调整图片宽高比



    /**
     * 如果 style 传 null，则采用 item_grid.xml 中的默认尺寸
     */
    public GridAdapter(boolean showList, ImgUtil.Style style) {
        super( showList ? R.layout.item_list:R.layout.item_grid, new ArrayList<>());
        this.mShowList = showList;
        if(style!=null ){
            if(style.type.equals("list"))this.mShowList=true;
            this.defaultWidth=ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (this.mShowList) {
            helper.setText(R.id.tvNote, item.note);
            helper.setText(R.id.tvName, item.name);
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            //由于部分电视机使用glide报错
            if (!TextUtils.isEmpty(item.pic)) {
                item.pic=item.pic.trim();
                // 修复：原调用未传目标尺寸，Glide 不做下采样，海报原图按原始分辨率解码进内存。
                // list 模式是小图，浪费尤其明显。补上 override 与其它列表 adapter 保持一致。
                ImgUtil.load(item.pic, ivThumb, (int) App.getInstance().getResources().getDimension(R.dimen.vs_5),
                        AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth),
                        AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight));
            } else {
                ivThumb.setImageResource(R.drawable.img_loading_placeholder);
            }
            return;
        }

        TextView tvYear = helper.getView(R.id.tvYear);
        if (item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        /*if (TextUtils.isEmpty(item.lang)) {
            tvLang.setVisibility(View.GONE);
        } else {
            tvLang.setText(item.lang);
            tvLang.setVisibility(View.VISIBLE);
        }*/
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);
        /*if (TextUtils.isEmpty(item.area)) {
            tvArea.setVisibility(View.GONE);
        } else {
            tvArea.setText(item.area);
            tvArea.setVisibility(View.VISIBLE);
        }*/
        if (TextUtils.isEmpty(item.note)) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if(style!=null){
            newWidth = defaultWidth;
            // 修复：style.ratio=0 时原代码抛 ArithmeticException 除零异常
            newHeight = style.ratio > 0 ? (int)(newWidth / style.ratio) : newWidth;
        }
        //由于部分电视机使用glide报错
        if (!TextUtils.isEmpty(item.pic)) {
            ImgUtil.load(item.pic, ivThumb,  (int) App.getInstance().getResources().getDimension(R.dimen.vs_5), AutoSizeUtils.mm2px(mContext,newWidth), AutoSizeUtils.mm2px(mContext,newHeight));
        } else {
            
            ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }
        applyStyleToImage(ivThumb);//动态设置宽高
    }

    /**
     * 根据传入的 style 动态设置 ImageView 的高度：高度 = 宽度 / ratio
     */
    private void applyStyleToImage(final ImageView ivThumb) {
        if(style!=null && style.ratio > 0){
            ViewGroup container = (ViewGroup) ivThumb.getParent();
            int width = defaultWidth;
            int height = (int) (width / style.ratio);
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            int targetWidth = AutoSizeUtils.mm2px(mContext, width);
            int targetHeight = AutoSizeUtils.mm2px(mContext, height);
            // 尺寸由 style 决定、对所有 item 相同；每次 bind 都 setLayoutParams 会触发 requestLayout，
            // 快速滚动时产生大量无谓的重新测量与布局。仅在尺寸真正变化时才设置。
            if (containerParams.width == targetWidth && containerParams.height == targetHeight) return;
            containerParams.height = targetHeight;
            containerParams.width = targetWidth;
            container.setLayoutParams(containerParams);
        }
    }    
}
