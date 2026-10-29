package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class FastListAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public FastListAdapter() {
        super(R.layout.item_fast_search_site, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvSearchSite, item);
    }


    // 记录失去焦点的控件
    public void onLostFocus(View child) {
        if(lostFocusTimestamp==0) {
            lostFocusTimestamp = System.currentTimeMillis();
        }
    }

    // 记录获得焦点的控件
    public int onSetFocus(View child){
        // 可以调整间隔还判断到底是不是整个view失去了焦点
        if(System.currentTimeMillis() - lostFocusTimestamp > 200) {
            setp = 0;
        }
        int index=((ViewGroup)child.getParent()).indexOfChild(child);
        View focusView = getFocusView();
        if(focusView != null && focusView.getParent() != null){
            int index2=((ViewGroup)focusView.getParent()).indexOfChild(focusView);
            if(Math.abs(index-index2) > setp) { // 跳了控件，将焦点恢复到之前的控件上去
                setp = 0;
                int offset = (index > index2? -1 :1);
                ViewGroup parent =  ((ViewGroup)focusView.getParent());
                View target = parent.getChildAt(index+offset);
                // 防御：越界或已移除时 getChildAt 返回 null，原实现会 NPE
                if (target != null) target.requestFocus();
                return  -1;
            }
        }
        lostFocusTimestamp =0;
        focusViewRef = new WeakReference<>(child);
        setp =1;
        return 1;
    }

    public void  reset(){
        lostFocusTimestamp =0;
        setp =0;
        focusViewRef = null;
    }

    public long lostFocusTimestamp=0;   // 控件失去焦点的时间
    public int setp =0; //步长
    // 修复：原为强引用 View。View 持有 Activity Context，adapter 一旦被异步回调或静态缓存
    // 持有，就会连带泄漏整个 Activity（本类 reset() 全仓库只在 FastSearchActivity 调一次，
    // 回收完全靠运气）。改用 WeakReference，不影响取不到时跳过该分支的原有逻辑。
    private WeakReference<View> focusViewRef;   // 当前获得焦点的控件

    private View getFocusView() {
        return focusViewRef == null ? null : focusViewRef.get();
    }
}