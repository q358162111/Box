package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/27
 */
public class ApiDialog extends BaseDialog {
    private final ImageView ivQRCode;
    private final TextView tvAddress;
    private final EditText inputApi;
    private final EditText inputLive;
    private final EditText inputEPG;
    private final EditText inputProxy;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            inputApi.setText((String) event.obj);
        }
        if (event.type == RefreshEvent.TYPE_LIVE_URL_CHANGE) {
            inputLive.setText((String) event.obj);
        }
        if (event.type == RefreshEvent.TYPE_EPG_URL_CHANGE) {
            inputEPG.setText((String) event.obj);
        }
        if (event.type == RefreshEvent.TYPE_PROXYS_CHANGE) {
            inputProxy.setText((String) event.obj);
        }
    }

    public ApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_api);
        setCanceledOnTouchOutside(true);
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        inputApi = findViewById(R.id.input);
        inputApi.setText(Hawk.get(HawkConfig.API_URL, ""));

        // takagen99: Add Live & EPG Address
        inputLive = findViewById(R.id.input_live);
        inputLive.setText(Hawk.get(HawkConfig.LIVE_URL, ""));
        inputEPG = findViewById(R.id.input_epg);
        inputEPG.setText(Hawk.get(HawkConfig.EPG_URL, ""));
        inputProxy = findViewById(R.id.input_proxy);
        inputProxy.setText(Hawk.get(HawkConfig.PROXY_SERVER, ""));

        findViewById(R.id.inputSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newApi = inputApi.getText().toString().trim();
                String newLive = inputLive.getText().toString().trim();
                String newEPG = inputEPG.getText().toString().trim();
                String newProxyServer = inputProxy.getText().toString().trim();
                // takagen99: Convert all to clan://localhost format
                if (newApi.startsWith("file://")) {
                    newApi = newApi.replace("file://", "clan://localhost/");
                } else if (newApi.startsWith("./")) {
                    newApi = newApi.replace("./", "clan://localhost/");
                }
                if (!newApi.isEmpty()) {
                    ArrayList<String> history = HawkConfig.getHistory(HawkConfig.API_HISTORY);
                    if (!history.contains(newApi))
                        history.add(0, newApi);
                    if (history.size() > 20)
                        history.remove(20);
                    HawkConfig.putHistory(HawkConfig.API_HISTORY, history);
                    listener.onchange(newApi);
                    dismiss();
                }
                // Capture Live input into Settings & Live History (max 20)
                Hawk.put(HawkConfig.LIVE_URL, newLive);
                if (!newLive.isEmpty()) {
                    ArrayList<String> liveHistory = HawkConfig.getHistory(HawkConfig.LIVE_HISTORY);
                    if (!liveHistory.contains(newLive))
                        liveHistory.add(0, newLive);
                    if (liveHistory.size() > 20)
                        liveHistory.remove(20);
                    HawkConfig.putHistory(HawkConfig.LIVE_HISTORY, liveHistory);
                }
                // Capture EPG input into Settings
                Hawk.put(HawkConfig.EPG_URL, newEPG);
                if (!newEPG.isEmpty()) {
                    ArrayList<String> EPGHistory = HawkConfig.getHistory(HawkConfig.EPG_HISTORY);
                    if (!EPGHistory.contains(newEPG))
                        EPGHistory.add(0, newEPG);
                    if (EPGHistory.size() > 20)
                        EPGHistory.remove(20);
                    HawkConfig.putHistory(HawkConfig.EPG_HISTORY, EPGHistory);
                }
                // Capture proxy server input into Settings & Proxy History (max 20)
                Hawk.put(HawkConfig.PROXY_SERVER, newProxyServer);
                if (!newProxyServer.isEmpty()) {
                    ArrayList<String> proxyHistory = HawkConfig.getHistory(HawkConfig.PROXY_HISTORY);
                    if (!proxyHistory.contains(newProxyServer))
                        proxyHistory.add(0, newProxyServer);
                    if (proxyHistory.size() > 20)
                        proxyHistory.remove(20);
                    HawkConfig.putHistory(HawkConfig.PROXY_HISTORY, proxyHistory);
                }
            }
        });
        findViewById(R.id.apiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = HawkConfig.getHistory(HawkConfig.API_HISTORY);
                if (history.isEmpty())
                    return;
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = 0;
                if (history.contains(current))
                    idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip(HomeActivity.getRes().getString(R.string.dia_history_list));
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        inputApi.setText(value);
                        listener.onchange(value);
                        ArrayList<String> history = HawkConfig.getHistory(HawkConfig.API_HISTORY);
                        if (history.contains(value)) {
                            history.remove(value);
                            history.add(0, value);
                        } else {
                            history.add(0, value);
                        }
                        HawkConfig.putHistory(HawkConfig.API_HISTORY, history);
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        HawkConfig.putHistory(HawkConfig.API_HISTORY, data);
                    }
                }, history, idx);
                dialog.show();
            }
        });
        findViewById(R.id.liveHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> liveHistory = HawkConfig.getHistory(HawkConfig.LIVE_HISTORY);
                if (liveHistory.isEmpty())
                    return;
                String current = Hawk.get(HawkConfig.LIVE_URL, "");
                int idx = 0;
                if (liveHistory.contains(current))
                    idx = liveHistory.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip(HomeActivity.getRes().getString(R.string.dia_history_live));
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String liveURL) {
                        inputLive.setText(liveURL);
                        Hawk.put(HawkConfig.LIVE_URL, liveURL);
                        ArrayList<String> liveHistory = HawkConfig.getHistory(HawkConfig.LIVE_HISTORY);
                        if (liveHistory.contains(liveURL)) {
                            liveHistory.remove(liveURL);
                            liveHistory.add(0, liveURL);
                            HawkConfig.putHistory(HawkConfig.LIVE_HISTORY, liveHistory);
                        }
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        HawkConfig.putHistory(HawkConfig.LIVE_HISTORY, data);
                    }
                }, liveHistory, idx);
                dialog.show();
            }
        });
        findViewById(R.id.EPGHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> EPGHistory = HawkConfig.getHistory(HawkConfig.EPG_HISTORY);
                if (EPGHistory.isEmpty())
                    return;
                String current = Hawk.get(HawkConfig.EPG_URL, "");
                int idx = 0;
                if (EPGHistory.contains(current))
                    idx = EPGHistory.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip(HomeActivity.getRes().getString(R.string.dia_history_epg));
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String epgURL) {
                        inputEPG.setText(epgURL);
                        Hawk.put(HawkConfig.EPG_URL, epgURL);
                        ArrayList<String> EPGHistory = HawkConfig.getHistory(HawkConfig.EPG_HISTORY);
                        if (EPGHistory.contains(epgURL)) {
                            EPGHistory.remove(epgURL);
                            EPGHistory.add(0, epgURL);
                            HawkConfig.putHistory(HawkConfig.EPG_HISTORY, EPGHistory);
                        }
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        HawkConfig.putHistory(HawkConfig.EPG_HISTORY, data);
                    }
                }, EPGHistory, idx);
                dialog.show();
            }
        });
        findViewById(R.id.proxyHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> proxyHistory = HawkConfig.getHistory(HawkConfig.PROXY_HISTORY);
                if (proxyHistory.isEmpty())
                    return;
                String current = Hawk.get(HawkConfig.PROXY_SERVER, "");
                int idx = 0;
                if (proxyHistory.contains(current))
                    idx = proxyHistory.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip(HomeActivity.getRes().getString(R.string.dia_history_proxy));
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String proxyURL) {
                        inputProxy.setText(proxyURL);
                        Hawk.put(HawkConfig.PROXY_SERVER, proxyURL);
                        ArrayList<String> proxyHistory = HawkConfig.getHistory(HawkConfig.PROXY_HISTORY);
                        if (proxyHistory.contains(proxyURL)) {
                            proxyHistory.remove(proxyURL);
                            proxyHistory.add(0, proxyURL);
                            HawkConfig.putHistory(HawkConfig.PROXY_HISTORY, proxyHistory);
                        }
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        HawkConfig.putHistory(HawkConfig.PROXY_HISTORY, data);
                    }
                }, proxyHistory, idx);
                dialog.show();
            }
        });
        findViewById(R.id.storagePermission).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (XXPermissions.isGranted(getContext(), DefaultConfig.StoragePermissionGroup())) {
                    Toast.makeText(getContext(), getContext().getString(R.string.set_permission_ok), Toast.LENGTH_SHORT).show();
                } else {
                    XXPermissions.with(getContext())
                            .permission(DefaultConfig.StoragePermissionGroup())
                            .request(new OnPermissionCallback() {
                                @Override
                                public void onGranted(List<String> permissions, boolean all) {
                                    if (all) {
                                        Toast.makeText(getContext(), getContext().getString(R.string.set_permission_ok), Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onDenied(List<String> permissions, boolean never) {
                                    if (never) {
                                        Toast.makeText(getContext(), getContext().getString(R.string.set_permission_fail2), Toast.LENGTH_SHORT).show();
                                        if (getContext() instanceof Activity) XXPermissions.startPermissionActivity((Activity) getContext(), permissions); else { Toast.makeText(getContext(), getContext().getString(R.string.cannot_locate_activity), Toast.LENGTH_SHORT).show(); }
                                    } else {
                                        Toast.makeText(getContext(), getContext().getString(R.string.set_permission_fail1), Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
            }
        });
        refreshQRCode();
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(getContext().getString(R.string.scan_qr_tip, address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, AutoSizeUtils.mm2px(getContext(), 300), AutoSizeUtils.mm2px(getContext(), 300)));
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    OnListener listener = null;

    public interface OnListener {
        void onchange(String api);
    }
}