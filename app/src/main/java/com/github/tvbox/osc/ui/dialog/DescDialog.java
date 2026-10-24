package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.github.tvbox.osc.R;
import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

public class DescDialog extends BaseDialog {    

    public DescDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_desc);
        init(context);
    }
    
    public void setDescribe(String describe) {
    	TextView tvDescribe = findViewById(R.id.describe);
        tvDescribe.setText(describe);
        tvDescribe.requestFocus();
        tvDescribe.requestFocusFromTouch();
    }

    private void init(Context context) {
        EventBus.getDefault().register(this);
        setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                // this 是匿名 listener 而非 Dialog,注销错对象导致 Dialog 永不注销而泄漏
                EventBus.getDefault().unregister(DescDialog.this);
            }
        });
    }
}