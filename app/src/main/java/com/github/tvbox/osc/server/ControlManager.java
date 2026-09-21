package com.github.tvbox.osc.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.receiver.DetailReceiver;
import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2021/1/4
 * @description:
 */
public class ControlManager {
    private static ControlManager instance;
    private RemoteServer mServer = null;
    public static Context mContext;

    private ControlManager() {

    }

    public static ControlManager get() {
        if (instance == null) {
            synchronized (ControlManager.class) {
                if (instance == null) {
                    instance = new ControlManager();
                }
            }
        }
        return instance;
    }

    public static void init(Context context) {
        // 强制使用 Application Context，防止误传 Activity Context 造成泄漏
        mContext = context != null ? context.getApplicationContext() : null;
    }

    public String getAddress(boolean local) {
        RemoteServer server = mServer;
        if (server == null) {
            // 服务器尚未启动或全部端口被占用时返回本机兜底地址，避免 NPE
            return "http://127.0.0.1:" + RemoteServer.serverPort + "/";
        }
        return local ? server.getLoadAddress() : server.getServerAddress();
    }

    public synchronized void startServer() {
        if (mServer != null) {
            return;
        }
        do {
            mServer = new RemoteServer(RemoteServer.serverPort, mContext);
            mServer.setDataReceiver(new DataReceiver() {
                @Override
                public void onTextReceived(String text) {
                    if (!TextUtils.isEmpty(text)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("title", text);
                        intent.setAction(SearchReceiver.action);
                        intent.setPackage(mContext.getPackageName());
                        intent.setComponent(new ComponentName(mContext, SearchReceiver.class));
                        intent.putExtras(bundle);
                        mContext.sendBroadcast(intent);
                    }
                }

                @Override
                public void onApiReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_API_URL_CHANGE, url));
                }

                @Override
                public void onLiveReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_LIVE_URL_CHANGE, url));
                }

                @Override
                public void onEpgReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_EPG_URL_CHANGE, url));
                }

                @Override
                public void onProxysReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PROXYS_CHANGE, url));
                }

                @Override
                public void onPushReceived(String url) {
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, url));
                }

                @Override
                public void onMirrorReceived(String id, String sourceKey) {
                    if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(sourceKey)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("id", id);
                        bundle.putString("sourceKey", sourceKey);
                        intent.setAction(DetailReceiver.action);
                        intent.setPackage(mContext.getPackageName());
                        intent.setComponent(new ComponentName(mContext, DetailReceiver.class));
                        intent.putExtras(bundle);
                        mContext.sendBroadcast(intent);
                    }
                }
            });
            try {
                mServer.start();
                IjkMediaPlayer.setDotPort(Hawk.get(HawkConfig.DOH_URL, 0) > 0, RemoteServer.serverPort);
                break;
            } catch (IOException ex) {
                RemoteServer.serverPort++;
                mServer.stop();
            }
        } while (RemoteServer.serverPort < 9999);
        // 全部端口占用时重置引用，避免后续 startServer 空转（直接 return）
        if (mServer != null && !mServer.isStarting()) {
            mServer = null;
        }
    }

    public void stopServer() {
        if (mServer != null && mServer.isStarting()) {
            mServer.stop();
        }
        // 置空以允许后续重新启动
        mServer = null;
    }
}