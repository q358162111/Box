package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import me.jessyan.autosize.utils.AutoSizeUtils;
public class SearchAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private int searchWidth;
    private final boolean showLite;

    public SearchAdapter() {
        super(Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0 ? R.layout.item_search_lite : R.layout.item_search, new ArrayList<>());
        showLite = Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0;
        searchWidth = Hawk.get(HawkConfig.SEARCH_RESULT_WIDTH, -1);
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        String sourceName = source != null ? source.getName() : "";
        // lite
        if (showLite) {
            // 用拼接替代 String.format：后者每次 bind 都要解析格式串并创建 Formatter，
            // 在滚动路径上是纯浪费
            helper.setText(R.id.tvName, sourceName + "  " + item.name
                    + (item.type == null ? "" : " " + item.type)
                    + (item.note == null ? "" : " " + item.note));
        } else {// with preview\
            helper.setText(R.id.tvName, item.name);
            helper.setText(R.id.tvSite, sourceName);
            helper.setVisible(R.id.tvNote, item.note != null && !item.note.isEmpty());
            if (item.note != null && !item.note.isEmpty()) {
                helper.setText(R.id.tvNote, item.note);
            }
            FrameLayout flRootView = helper.getView(R.id.fl_root_view);
            // 动态设置 item 宽度。
            // 修复：原实现在 searchWidth==-1（用户从未自定义宽度的默认情况）时，
            // 每个 item 每次 bind 都走一次 Hawk.get —— 而 Hawk.get 是一次磁盘读 + 解密 +
            // 反序列化，列表滚动时等于每帧几十次主线程 IO。宽度已在构造函数中读取，
            // 设置变更会重建 adapter，无需在绑定路径上重复读。
            if (searchWidth != -1) {
                setItemWidth(flRootView);
            }
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            if (!TextUtils.isEmpty(item.pic)) {
                // takagen99 : Use Glide instead
                ImgUtil.load(item.pic, ivThumb, (int) App.getInstance().getResources().getDimension(R.dimen.vs_5),AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth), AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight));
            } else {
                ivThumb.setImageResource(R.drawable.img_loading_placeholder);
            }
        }
    }

    private void setItemWidth(FrameLayout view) {
        GridLayoutManager.LayoutParams layoutParams = (GridLayoutManager.LayoutParams) view.getLayoutParams();
        // 宽度对所有 item 相同；每次 bind 都 setLayoutParams 会触发 requestLayout，
        // 快速滚动时产生大量无谓的重新测量与布局，仅在宽度真正变化时才设置
        if (layoutParams.width == searchWidth) return;
        layoutParams.width = searchWidth;
        view.setLayoutParams(layoutParams);
    }
}
