package com.github.tvbox.osc.ui.adapter;

import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.github.tvbox.osc.base.BaseLazyFragment;

import java.util.List;

/**
 * @user acer
 * @date 2018/12/4
 */

public class SettingPageAdapter extends FragmentPagerAdapter {
    private final FragmentManager fragmentManager;
    private List<BaseLazyFragment> list;

    public SettingPageAdapter(FragmentManager fm) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.fragmentManager = fm;
        this.list = null;
    }

    public SettingPageAdapter(FragmentManager fm, List<BaseLazyFragment> list) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.fragmentManager = fm;
        this.list = list;
    }

    @Override
    public Fragment getItem(int position) {
        if (list == null) return null;
        return list.get(position);
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    // 不再重写 instantiateItem/destroyItem，与 HomePageAdapter 保持一致。
    // 此前重写 hide/show 却注释掉 super.destroyItem(container, position, object)，
    // 导致 Fragment 永不销毁、只被 hide，切换设置页时内存只增不减；
    // 且 commitAllowingStateLoss() 会丢弃 Fragment 事务状态。
}
