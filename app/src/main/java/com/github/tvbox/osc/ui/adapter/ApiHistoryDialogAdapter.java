package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ApiHistoryDialogAdapter extends ListAdapter<String, ApiHistoryDialogAdapter.SelectViewHolder> {

    static class SelectViewHolder extends RecyclerView.ViewHolder {
        // 修复：ViewHolder 原来完全不缓存子 view，onBind 里对同一个 id findViewById 两次，
        // 每次绑定都要遍历整棵 item 子树。改为构造时缓存一次。
        final TextView tvName;
        final View tvDel;

        SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvDel = itemView.findViewById(R.id.tvDel);
        }
    }

    public interface SelectDialogInterface {
        void click(String value);

        void del(String value, ArrayList<String> data);
    }


    private ArrayList<String> data = new ArrayList<>();

    private String select = "";

    private SelectDialogInterface dialogInterface = null;

    public ApiHistoryDialogAdapter(SelectDialogInterface dialogInterface) {
        super(new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<String> newData, int defaultSelect) {
        data.clear();
        if (newData != null) data.addAll(newData);
        // 防御：defaultSelect 越界或列表为空时原实现直接 IndexOutOfBoundsException 崩溃
        select = (defaultSelect >= 0 && defaultSelect < data.size()) ? data.get(defaultSelect) : "";
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }


    @Override
    public ApiHistoryDialogAdapter.SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new ApiHistoryDialogAdapter.SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_api_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull ApiHistoryDialogAdapter.SelectViewHolder holder, int position) {
        String value = data.get(position);
        String name = select.equals(value) ? "√ " + value : value;
        holder.tvName.setText(name);
        holder.tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (select.equals(value))
                    return;
                // 修复：indexOf 找不到时返回 -1，notifyItemChanged(-1) 是无效刷新。
                // select 初值为 ""，首次点击必然走这条路径。
                int oldPos = data.indexOf(select);
                if (oldPos >= 0) notifyItemChanged(oldPos);
                select = value;
                int newPos = data.indexOf(value);
                if (newPos >= 0) notifyItemChanged(newPos);
                dialogInterface.click(value);
            }
        });
        holder.tvDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (select.equals(value))
                    return;
                int pos = data.indexOf(value);
                if (pos < 0) return;
                // 修复：原实现先 notifyItemRemoved 再 remove，顺序颠倒 ——
                // 通知时数据集尚未变小，RecyclerView 在随后的布局过程中读到不一致的条目数。
                data.remove(pos);
                notifyItemRemoved(pos);
                dialogInterface.del(value, data);
            }
        });
    }
}
