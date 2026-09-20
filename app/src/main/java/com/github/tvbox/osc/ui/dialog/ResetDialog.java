package com.github.tvbox.osc.ui.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.DefaultConfig;
import org.jetbrains.annotations.NotNull;

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/27
 */
public class ResetDialog extends BaseDialog {
    private final TextView tvYes;
    private final TextView tvNo;

    @SuppressLint("MissingInflatedId")
    public ResetDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_reset);
        setCanceledOnTouchOutside(true);
        tvYes = findViewById(R.id.btnConfirm);
        tvNo = findViewById(R.id.btnCancel);
        tvYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 修复：原代码点击 Yes 后未 dismiss，对话框仍覆盖在界面上
                DefaultConfig.resetApp(tvYes.getContext());
                ResetDialog.this.dismiss();
            }
        });
        tvNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ResetDialog.this.dismiss();
            }
        });
    }
}
