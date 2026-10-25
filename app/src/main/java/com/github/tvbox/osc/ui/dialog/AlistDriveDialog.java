package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.StorageDrive;
import com.github.tvbox.osc.event.InputMsgEvent;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.StorageDriveType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

public class AlistDriveDialog extends BaseDialog {

    private StorageDrive drive = null;
    private EditText etName;
    private EditText etUrl;
    private EditText etInitPath;
    private EditText etPassword;

    public AlistDriveDialog(@NonNull @NotNull Context context, StorageDrive drive) {
        super(context);
        setContentView(R.layout.dialog_alistdrive);
        if(drive != null)
            this.drive = drive;
        // 启用 EventBus 以接收 IME 软键盘的 InputMsgEvent 事件
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        etName = findViewById(R.id.etName);
        etUrl = findViewById(R.id.etUrl);
        etInitPath = findViewById(R.id.etInitPath);
        etPassword = findViewById(R.id.etPassword);
        etName.setFocusableInTouchMode(true);
        etName.requestFocus();
        if(drive != null) {
            etName.setText(drive.name);
            try {
                JsonObject config = JsonParser.parseString(drive.configJson).getAsJsonObject();
                initSavedData(etUrl, config, "url");
                initSavedData(etInitPath, config, "initPath");
                initSavedData(etPassword, config, "password");
            }catch (Exception ex) { }
        }
        findViewById(R.id.btnConfirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = etName.getText().toString();
                String url = etUrl.getText().toString();
                String initPath = etInitPath.getText().toString();
                String password = etPassword.getText().toString();
                if(name == null || name.length() == 0)
                {
                    Toast.makeText(AlistDriveDialog.this.getContext(), AlistDriveDialog.this.getContext().getString(R.string.input_space_name), Toast.LENGTH_SHORT).show();
                    return;
                }
                if(url == null || url.length() == 0)
                {
                    Toast.makeText(AlistDriveDialog.this.getContext(), AlistDriveDialog.this.getContext().getString(R.string.input_alist_url), Toast.LENGTH_SHORT).show();
                    return;
                }
                if(!url.endsWith("/"))
                    url += "/";
                JsonObject config = new JsonObject();
                config.addProperty("url", url);
                if(initPath.length() > 0 && initPath.startsWith("/"))
                    initPath = initPath.substring(1);
                if(initPath.length() > 0 && initPath.endsWith("/"))
                    initPath = initPath.substring(0, initPath.length() - 1);
                config.addProperty("initPath", initPath);
                config.addProperty("password", password);
                if(drive != null) {
                    drive.name = name;
                    drive.configJson = config.toString();
                }
                // DB 写移后台，完成后回主线程发刷新事件并关窗
                final StorageDrive finalDrive = drive;
                AppTaskExecutor.deskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (finalDrive != null) {
                            RoomDataManger.updateDriveRecord(finalDrive);
                        } else {
                            RoomDataManger.insertDriveRecord(name, StorageDriveType.TYPE.ALISTWEB, config);
                        }
                        AppTaskExecutor.mainThread().execute(new Runnable() {
                            @Override
                            public void run() {
                                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
                                AlistDriveDialog.this.dismiss();
                            }
                        });
                    }
                });
            }
        });
        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlistDriveDialog.this.dismiss();
            }
        });
    }

    private void initSavedData(EditText etField, JsonObject config, String fieldName) {
        if(config.has(fieldName))
            etField.setText(config.get(fieldName).getAsString());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onInputMsgEvent(InputMsgEvent inputMsgEvent) {
        View vFocus = this.getWindow().getDecorView().findFocus();
        if(vFocus instanceof EditText)
        {
            ((EditText) vFocus).setText(inputMsgEvent.getText());
        }
    }
}