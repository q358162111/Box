package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class QuickSearchAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    public QuickSearchAdapter() {
        super(R.layout.item_quick_search_lite, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        // 修复：原实现直接 getSource(...).getName()，切源/重载配置导致 source 查不到时
        // 返回 null → 每次 bind 都 NPE 崩溃。同 SearchAdapter 一样做判空。
        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        String sourceName = source != null ? source.getName() : "";
        // 用拼接替代 String.format：每次 bind 都要解析格式串并创建 Formatter，纯浪费
        helper.setText(R.id.tvName, sourceName + "  " + item.name
                + (item.type == null ? "" : " " + item.type)
                + (item.note == null ? "" : " " + item.note));
    }
}