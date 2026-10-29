package com.github.tvbox.osc.ui.adapter;

import android.annotation.SuppressLint;
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

public class ButtonAdapter<T> extends ListAdapter<T, ButtonAdapter.SelectViewHolder> {

    static class SelectViewHolder extends RecyclerView.ViewHolder {
        // 修复：原 ViewHolder 不缓存子 view，每次 bind 都要 findViewById 遍历整棵 item 子树
        final TextView tvName;

        SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
        }
    }

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);

        String getDisplay(T val);
    }


    public static DiffUtil.ItemCallback<String> stringDiff = new DiffUtil.ItemCallback<String>() {

        @Override
        public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }
    };


    private ArrayList<T> data = new ArrayList<>();

    private int select = 0;

    private boolean firstFlag = true;

    private SelectDialogInterface dialogInterface = null;

    public ButtonAdapter(SelectDialogInterface dialogInterface, DiffUtil.ItemCallback diffCallback) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        if (newData != null) data.addAll(newData);
        select = defaultSelect;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }


    @Override
    public SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_button, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectViewHolder holder, int position) {
        T value = data.get(position);
        String name = dialogInterface.getDisplay(value);
        TextView item = holder.tvName;
        if (position == select){
            name = "√ " + name;
            if(firstFlag){
                firstFlag = false;
                item.requestFocus();
            }
        }
        item.setText(name);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 修复：原实现用 @SuppressLint("RecyclerView") 压制警告、直接捕获绑定时的
                // position 与 value。数据变化后该下标失效，会把选中态刷到错误的行并回传错误的 value。
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos < 0 || pos >= data.size()) return;
                T cur = data.get(pos);
                if (pos == select)
                    return;
                if (select >= 0 && select < data.size()) notifyItemChanged(select);
                select = pos;
                notifyItemChanged(select);
                dialogInterface.click(cur, pos);
            }
        });
    }
}