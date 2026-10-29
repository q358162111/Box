package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.common.text.Cue;
import androidx.recyclerview.widget.DiffUtil;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.SubtitleBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.EXOmPlayer;
import com.github.tvbox.osc.player.IjkmPlayer;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.play.TrackSelector;
import com.github.tvbox.osc.play.DanmuPresenter;
import com.github.tvbox.osc.play.SniffEngine;
import xyz.doikki.videoplayer.player.VideoView;
import com.github.tvbox.osc.player.danmu.Parser;
import com.github.tvbox.osc.player.thirdparty.Kodi;
import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.subtitle.model.Subtitle;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.DanmuSettingDialog;
import com.github.tvbox.osc.ui.dialog.SearchSubtitleDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.SubtitleDialog;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HawkUtils;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.M3U8;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.parser.SuperParse;
import com.github.tvbox.osc.util.StringUtils;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.util.XWalkUtils;
import com.github.tvbox.osc.util.thunder.Jianpian;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.core.XWalkJavascriptResult;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkSettings;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkWebResourceRequest;
import org.xwalk.core.XWalkWebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import master.flame.danmaku.ui.widget.DanmakuView;
import me.jessyan.autosize.AutoSize;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.ProgressManager;

public class PlayActivity extends BaseActivity {
    private MyVideoView mVideoView;
    private TextView mPlayLoadTip;
    private ImageView mPlayLoadErr;
    private ProgressBar mPlayLoading;
    private VodController mController;
    private TrackSelector trackSelector;
    private SourceViewModel sourceViewModel;
    private Handler mHandler;

    private String videoURL;
    private long videoDuration = -1;
    private List<String> videoSegmentationURL = new ArrayList<>();

    private BroadcastReceiver pipActionReceiver;
    public static final String BROADCAST_ACTION = "VOD_CONTROL";
    public static final int BROADCAST_ACTION_PREV = 0;
    public static final int BROADCAST_ACTION_PLAYPAUSE = 1;
    public static final int BROADCAST_ACTION_NEXT = 2;

    ExecutorService executorService;
    private DanmakuView mDanmuView;
    private DanmuPresenter danmuPresenter;
    private String danmuText;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE) {
            mController.mSubtitleView.setTextSize((int) event.obj);
        }
        if (event.type == RefreshEvent.TYPE_SET_DANMU_SETTINGS) {
            setDanmuViewSettings((Boolean) event.obj);
        }
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        initView();
        initViewModel();
        initData();
        initDanmuView();
    }
    private void initDanmuView() {
        mDanmuView  = findViewById(R.id.danmaku);
        danmuPresenter = new DanmuPresenter(new DanmuPresenter.Host() {
            @Override
            public boolean isInPictureInPictureMode() {
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && PlayActivity.this.isInPictureInPictureMode();
            }

            @Override
            public DanmakuView getDanmuView() {
                return mDanmuView;
            }

            @Override
            public MyVideoView getVideoView() {
                return mVideoView;
            }
        });
        danmuPresenter.initDanmuView(mDanmuView, mVideoView);
    }

    private void setDanmuViewSettings(boolean reload) {
        danmuPresenter.setDanmuViewSettings(danmuText, reload);
    }
    private void initView() {

        // takagen99 : Hide only when video playing
        hideSystemUI(false);

        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                if (msg.what == 100) {
                    stopParse();
                    errorWithRetry(getString(R.string.sniff_error), false);
                } else if (msg.what == 200) {
                    if (mHandler.hasMessages(100)) {
                        setTip(getString(R.string.load_sniffing), true, false);
                    }
                } else if (msg.what == 300) {
                    setTip((String)msg.obj, false, true);
                }
                return false;
            }
        });
        mVideoView = findViewById(R.id.mVideoView);
        mPlayLoadTip = findViewById(R.id.play_load_tip);
        mPlayLoading = findViewById(R.id.play_loading);
        mPlayLoadErr = findViewById(R.id.play_load_error);
        mController = new VodController(this);
        trackSelector = new TrackSelector(new TrackSelector.Host() {
            @Override
            public Context context() {
                return PlayActivity.this;
            }

            @Override
            public Handler handler() {
                return mHandler;
            }
        }, mController);
        mController.setCanChangePosition(true);
        mController.setEnableInNormal(true);
        mController.setGestureEnabled(true);
        ProgressManager progressManager = new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {
                if (videoDuration == 0) return;
                // DB 写移后台（每几秒回调一次，主线程写库会造成持续 GC/IO 压力）
                AppTaskExecutor.deskIO().execute(() -> CacheManager.save(MD5.string2MD5(url), progress));
            }

            @Override
            public long getSavedProgress(String url) {
                int st = 0;
                try {
                    st = mVodPlayerCfg.getInt("st");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                long skip = st * 1000L;
                // 播放器起播路径需要同步返回值，这里保留同步读（单条主键查询，开销可控）
                Object cached = CacheManager.getCache(MD5.string2MD5(url));
                if (cached == null) {
                    return skip;
                }
                long rec = (long) cached;
                if (rec < skip)
                    return skip;
                return rec;
            }
        };
        mVideoView.setProgressManager(progressManager);
        mController.setListener(new VodController.VodControlListener() {

            @Override
            public void showDanmuSetting() {
                DanmuSettingDialog dialog = new DanmuSettingDialog(PlayActivity.this, mDanmuView);
                dialog.show();
            }

            @Override
            public void playNext(boolean rmProgress) {
                if (videoSegmentationURL.size() > 0) {
                    for (int i = 0; i < videoSegmentationURL.size() - 1; i++) {
                        if (videoSegmentationURL.get(i).equals(videoURL)) {
                            mVideoView.setPlayFromZeroPositionOnce(true);
                            startPlayUrl(videoSegmentationURL.get(i + 1), new HashMap<>());//todo header
                            return;
                        }
                    }
                }
                if (mVodInfo.reverseSort) {
                    PlayActivity.this.playPrevious();
                } else {
                    String preProgressKey = progressKey;
                    PlayActivity.this.playNext(rmProgress);
                    if (rmProgress && preProgressKey != null)
                        AppTaskExecutor.deskIO().execute(() -> CacheManager.delete(MD5.string2MD5(preProgressKey), 0));
                }
            }

            @Override
            public void playPre() {
                if (videoSegmentationURL.size() > 0) {
                    for (int i = 1; i < videoSegmentationURL.size(); i++) {
                        if (videoSegmentationURL.get(i).equals(videoURL)) {
                            mVideoView.setPlayFromZeroPositionOnce(true);
                            startPlayUrl(videoSegmentationURL.get(i - 1), new HashMap<>());//todo header
                            return;
                        }
                    }
                }
                if (mVodInfo.reverseSort) {
                    PlayActivity.this.playNext(false);
                } else {
                    PlayActivity.this.playPrevious();
                }
            }

            @Override
            public void changeParse(ParseBean pb) {
                autoRetryCount = 0;
                doParse(pb);
            }

            @Override
            public void updatePlayerCfg() {
                mVodInfo.playerCfg = mVodPlayerCfg.toString();
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfg));
            }

            @Override
            public void replay(boolean replay) {
                autoRetryCount = 0;
                play(replay);
            }

            @Override
            public void errReplay() {
                errorWithRetry(getString(R.string.play_error), false);
            }

            @Override
            public void selectSubtitle() {
                try {
                    selectMySubtitle();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void selectAudioTrack() {
                selectMyAudioTrack();
            }

            @Override
            public void openVideo() {
                openMyVideo();
            }

            @Override
            public void prepared() {
                initSubtitleView();
            }

        });
        mVideoView.setVideoController(mController);
        mVideoView.setmHandler(mHandler);
    }

    //设置字幕
    void setSubtitle(String path) {
        if (path != null && path.length() > 0) {
            // 设置字幕
            mController.mSubtitleView.setVisibility(View.GONE);
            mController.mSubtitleView.setSubtitlePath(path);
            mController.mSubtitleView.setVisibility(View.VISIBLE);
        }
    }

    void selectMySubtitle() throws Exception {
        SubtitleDialog subtitleDialog = new SubtitleDialog(PlayActivity.this);

        subtitleDialog.setSubtitleViewListener(new SubtitleDialog.SubtitleViewListener() {
            @Override
            public void setTextSize(int size) {
                mController.mSubtitleView.setTextSize(size);
            }

            @Override
            public void setSubtitleDelay(int milliseconds) {
                mController.mSubtitleView.setSubtitleDelay(milliseconds);
            }

            @Override
            public void selectInternalSubtitle() {
                selectMyInternalSubtitle();
            }

            @Override
            public void setTextStyle(int style) {
                setSubtitleViewTextStyle(style);
            }
        });
        subtitleDialog.setSearchSubtitleListener(new SubtitleDialog.SearchSubtitleListener() {
            @Override
            public void openSearchSubtitleDialog() {
                SearchSubtitleDialog searchSubtitleDialog = new SearchSubtitleDialog(PlayActivity.this);
                searchSubtitleDialog.setSubtitleLoader(new SearchSubtitleDialog.SubtitleLoader() {
                    @Override
                    public void loadSubtitle(SubtitleBean subtitle) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isFinishing() || isDestroyed()) return;
                                String zimuUrl = subtitle.getUrl();
                                LOG.i("Remote SubtitleBean Url: " + zimuUrl);
                                setSubtitle(zimuUrl); //设置字幕
                                if (searchSubtitleDialog != null) {
                                    searchSubtitleDialog.dismiss();
                                }
                            }
                        });
                    }

                    @Override
                    public void fail(String msg) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isFinishing()) {
                                    Toast.makeText(PlayActivity.this, "字幕加载失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                });
              /*  EventBus.getDefault().register(searchSubtitleDialog);
                searchSubtitleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                	@Override
                    public void onDismiss(DialogInterface dialog) {
                        EventBus.getDefault().unregister(dialog);
                    }
                });*/
                if (mVodInfo.playFlag.contains("Ali") || mVodInfo.playFlag.contains("parse")) {
                    searchSubtitleDialog.setSearchWord(mVodInfo.playNote);
                } else {
                    searchSubtitleDialog.setSearchWord(mVodInfo.name);
                }
                searchSubtitleDialog.show();
            }
        });
        subtitleDialog.setLocalFileChooserListener(new SubtitleDialog.LocalFileChooserListener() {
            @Override
            public void openLocalFileChooserDialog() {
                new ChooserDialog(PlayActivity.this)
                        .withFilter(false, false, "srt", "ass", "scc", "stl", "ttml")
                        .withStartFile("/storage/emulated/0/Download")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                LOG.i("Local SubtitleBean Path: " + path);
                                setSubtitle(path);//设置字幕
                            }
                        })
                        .build()
                        .show();
            }
        });
        subtitleDialog.show();
    }

    void setSubtitleViewTextStyle(int style) {
        SubtitleHelper.upTextStyle(mController.mSubtitleView,style);
    }

    void selectMyInternalSubtitle() {
        trackSelector.selectInternalSubtitle(mVideoView.getMediaPlayer());
    }

    void selectMyAudioTrack() {
        trackSelector.selectAudioTrack(mVideoView.getMediaPlayer());
    }

    void openMyVideo() {
        Intent i = new Intent();
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setAction(Intent.ACTION_VIEW);
        if (videoURL == null) return;
        i.setDataAndType(Uri.parse(videoURL), "video/*");
        startActivity(Intent.createChooser(i, "Open Video with ..."));
    }

    void setTip(String msg, boolean loading, boolean err) {
        runOnUiThread(new Runnable() { //影魔 解决解析偶发闪退
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                mPlayLoadTip.setText(msg);
                mPlayLoadTip.setVisibility(View.VISIBLE);
                mPlayLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
                mPlayLoadErr.setVisibility(err ? View.VISIBLE : View.GONE);
            }
        });
    }

    void hideTip() {
        mPlayLoadTip.setVisibility(View.GONE);
        mPlayLoading.setVisibility(View.GONE);
        mPlayLoadErr.setVisibility(View.GONE);
    }

    void errorWithRetry(String err, boolean finish) {
        if (!autoRetry()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing() || isDestroyed()) return;
                    if (finish) {
                        Toast.makeText(mContext, err, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        setTip(err, false, true);
                    }
                }
            });
        }
    }

    private boolean yxdm(String url, Map<String, String> headers) {
        if (url.startsWith("https://www.ziyuantt.com/") && url.endsWith(".mp4")) {
            int st = url.indexOf("&url=");
            if (st > 1) {
                String[] urls = url.substring(st + 5).split("\\|");
                if (urls.length < 2) return false;
                stopLoadWebView(false);
                videoSegmentationURL.clear();
                videoSegmentationURL.addAll(Arrays.asList(urls));
                HashMap<String, String> hm = new HashMap<>();
                if (headers != null && headers.keySet().size() > 0) {
                    for (String k : headers.keySet()) {
                        hm.put(k, " " + headers.get(k));
                    }
                }
                loadFoundVideoUrls.add(urls[0]);
                loadFoundVideoUrlsHeader.put(videoSegmentationURL.get(0), hm);
                startPlayUrl(videoSegmentationURL.get(0), hm);
                return true;
            }
        }
        return false;
    }

    void playUrl(String url, HashMap<String, String> headers) {
        if (!Hawk.get(HawkConfig.VIDEO_PURIFY, true)) {
            startPlayUrl(url, headers);
            return;
        }
        if (!url.contains("://127.0.0.1/") && !url.contains(".m3u8")) {
            startPlayUrl(url, headers);
            return;
        }
        OkGo.getInstance().cancelTag("m3u8-1");
        OkGo.getInstance().cancelTag("m3u8-2");
        //remove ads in m3u8
        HttpHeaders hheaders = new HttpHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> s : headers.entrySet()) {
                hheaders.put(s.getKey(), s.getValue());
            }
        }
        OkGo.<String>get(url)
                .tag("m3u8-1")
                .headers(hheaders)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String content = response.body();
                        if (!content.startsWith("#EXTM3U")) {
                            startPlayUrl(url, headers);
                            return;
                        }

                        String[] lines = null;
                        if (content.contains("\r\n"))
                            lines = content.split("\r\n", 10);
                        else
                            lines = content.split("\n", 10);
                        String forwardurl = "";
                        boolean dealedFirst = false;
                        for (String line : lines) {
                            if (!"".equals(line) && line.charAt(0) != '#') {
                                if (dealedFirst) {
                                    //跳转行后还有内容，说明不需要跳转
                                    forwardurl = "";
                                    break;
                                }
                                if (line.endsWith(".m3u8") || line.contains(".m3u8?")) {
                                    if (line.startsWith("http://") || line.startsWith("https://")) {
                                        forwardurl = line;
                                    } else if (line.charAt(0) == '/') {
                                        int ifirst = url.indexOf('/', 9);//skip https://, http://
                                        forwardurl = url.substring(0, ifirst) + line;
                                    } else {
                                        int ilast = url.lastIndexOf('/');
                                        forwardurl = url.substring(0, ilast + 1) + line;
                                    }
                                }
                                dealedFirst = true;
                            }
                        }
                        if ("".equals(forwardurl)) {
                            int ilast = url.lastIndexOf('/');

                            RemoteServer.m3u8Content = M3U8.purify(url.substring(0, ilast + 1), content);
                            if (RemoteServer.m3u8Content == null)
                                startPlayUrl(url, headers);
                            else {
                                startPlayUrl("http://127.0.0.1:" + RemoteServer.serverPort + "/m3u8", headers);
                                //Toast.makeText(getContext(), "已移除视频广告", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        final String finalforwardurl = forwardurl;
                        OkGo.<String>get(forwardurl)
                                .tag("m3u8-2")
                                .headers(hheaders)
                                .execute(new AbsCallback<String>() {
                                    @Override
                                    public void onSuccess(Response<String> response) {
                                        String content = response.body();
                                        int ilast = finalforwardurl.lastIndexOf('/');
                                        RemoteServer.m3u8Content = M3U8.purify(finalforwardurl.substring(0, ilast + 1), content);

                                        if (RemoteServer.m3u8Content == null)
                                            startPlayUrl(finalforwardurl, headers);
                                        else {
                                            startPlayUrl("http://127.0.0.1:" + RemoteServer.serverPort + "/m3u8", headers);
                                            //Toast.makeText(getContext(), "已移除视频广告", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public String convertResponse(okhttp3.Response response) throws Throwable {
                                        return response.body().string();
                                    }

                                    @Override
                                    public void onError(Response<String> response) {
                                        super.onError(response);
                                        startPlayUrl(url, headers);
                                    }
                                });
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        startPlayUrl(url, headers);
                    }
                });
    }

    void startPlayUrl(String url, HashMap<String, String> headers) {
        final String finalUrl = url;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopParse();
                if (mVideoView != null) {
                    mVideoView.release();
                    if (finalUrl != null) {
                        String url = finalUrl;
                        videoURL = url;
                        try {
                            int playerType = mVodPlayerCfg.getInt("pl");
                            // takagen99: Check for External Player
                            extPlay = false;
                            if (playerType >= 10) {
                                VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
                                String playTitle = mVodInfo.name + " : " + vs.name;
                                setTip(getString(R.string.external_player_call, PlayerHelper.getPlayerName(playerType)), true, false);
                                boolean callResult = false;
                                switch (playerType) {
                                    case 10: {
                                        extPlay = true;
                                        callResult = MXPlayer.run(PlayActivity.this, url, playTitle, playSubtitle, headers);
                                        break;
                                    }
                                    case 11: {
                                        extPlay = true;
                                        callResult = ReexPlayer.run(PlayActivity.this, url, playTitle, playSubtitle, headers);
                                        break;
                                    }
                                    case 12: {
                                        extPlay = true;
                                        callResult = Kodi.run(PlayActivity.this, url, playTitle, playSubtitle, headers);
                                        break;
                                    }
                                }
                                setTip(getString(callResult ? R.string.external_player_ok : R.string.external_player_fail, PlayerHelper.getPlayerName(playerType)), callResult, !callResult);
                                return;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        hideTip();
                        if (url.startsWith("data:application/dash+xml;base64,")) {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg, 2);
                            App.getInstance().setDashData(url.split("base64,")[1]);
                            url = ControlManager.get().getAddress(true) + "dash/proxy.mpd";
                        } else if (url.contains(".mpd") || url.contains("type=mpd")) {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg, 2);
                        } else {
                            PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg);
                        }
                        mVideoView.setProgressKey(progressKey);
                        if (headers != null) {
                            mVideoView.setUrl(url, headers);
                        } else {
                            mVideoView.setUrl(url);
                        }
                        mVideoView.start();
                        mController.resetSpeed();
                    }
                }
            }
        });
    }

    private void initSubtitleView() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        // prepared 回调与 release 竞态（切集瞬间）时这里会是 null；
        // 继续绑定 null 会让字幕引擎 start() 直接 return，本集外挂字幕完全不显示且无任何提示
        if (mediaPlayer == null) {
            LOG.e("initSubtitleView: mediaPlayer 为空，跳过字幕初始化");
            return;
        }
        TrackInfo trackInfo = null;
        if (mVideoView.getMediaPlayer() instanceof IjkmPlayer) {
            trackInfo = ((IjkmPlayer) (mVideoView.getMediaPlayer())).getTrackInfo();
            if (trackInfo != null && trackInfo.getSubtitle().size() > 0) {
                mController.mSubtitleView.hasInternal = true;
            }
            ((IjkmPlayer) (mVideoView.getMediaPlayer())).setOnTimedTextListener(new IMediaPlayer.OnTimedTextListener() {
                @Override
                public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
                    if (mController.mSubtitleView.isInternal) {
                        Subtitle subtitle = new Subtitle();
                        subtitle.content = text.getText();
                        mController.mSubtitleView.onSubtitleChanged(subtitle);
                    }
                }
            });
        }

        if (mVideoView.getMediaPlayer() instanceof EXOmPlayer) {
            trackInfo = ((EXOmPlayer) (mVideoView.getMediaPlayer())).getTrackInfo();
            if (trackInfo != null && trackInfo.getSubtitle().size() > 0) {
                mController.mSubtitleView.hasInternal = true;
            }
            ((EXOmPlayer) (mVideoView.getMediaPlayer())).setOnTimedTextListener(new Player.Listener() {
                @Override
                public void onCues(@NonNull List<Cue> cues) {
                    if (cues.size() > 0) {
                        CharSequence ss = cues.get(0).text;
                        if (ss != null && mController.mSubtitleView.isInternal) {
                            Subtitle subtitle = new Subtitle();
                            subtitle.content = ss.toString();
                            mController.mSubtitleView.onSubtitleChanged(subtitle);
                        }
                    }else {
                        Subtitle subtitle = new Subtitle();
                        subtitle.content = "";
                        mController.mSubtitleView.onSubtitleChanged(subtitle);
                    }
                }
            });
        }

        mController.mSubtitleView.bindToMediaPlayer(mVideoView.getMediaPlayer());
        mController.mSubtitleView.setPlaySubtitleCacheKey(subtitleCacheKey);
        String subtitlePathCache = (String) CacheManager.getCache(MD5.string2MD5(subtitleCacheKey));
        if (subtitlePathCache != null && !subtitlePathCache.isEmpty()) {
            mController.mSubtitleView.setSubtitlePath(subtitlePathCache);
        } else {
            if (playSubtitle != null && playSubtitle.length() > 0) {
                mController.mSubtitleView.setSubtitlePath(playSubtitle);
            } else {
                if (mController.mSubtitleView.hasInternal) {
                    mController.mSubtitleView.isInternal = true;
                    if (mediaPlayer instanceof IjkmPlayer) {
                        if (trackInfo != null && trackInfo.getSubtitle().size() > 0) {
                            List<TrackInfoBean> subtitleTrackList = trackInfo.getSubtitle();
                            int selectedIndex = trackInfo.getSubtitleSelected(true);
                            boolean hasCh = false;
                            for (TrackInfoBean subtitleTrackInfoBean : subtitleTrackList) {
                                String lowerLang = subtitleTrackInfoBean.language.toLowerCase();
                                if (lowerLang.startsWith("zh") || lowerLang.startsWith("ch")) {
                                    hasCh = true;
                                    if (selectedIndex != subtitleTrackInfoBean.trackId) {
                                        ((IjkmPlayer) (mVideoView.getMediaPlayer())).setTrack(subtitleTrackInfoBean.trackId);
                                        break;
                                    }
                                }
                            }
                            if (!hasCh)
                                ((IjkmPlayer) (mVideoView.getMediaPlayer())).setTrack(subtitleTrackList.get(0).trackId);
                        }
                    }
                }
            }
        }
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.playResult.observeForever(mObserverPlayResult);
    }

    private final Observer<JSONObject> mObserverPlayResult = new Observer<JSONObject>() {
        @Override
        public void onChanged(JSONObject info) {
            if (info != null) {
                try {
                    progressKey = info.optString("proKey", null);
                    boolean parse = info.optString("parse", "1").equals("1");
                    boolean jx = info.optString("jx", "0").equals("1");
                    playSubtitle = info.optString("subt", /*"https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/ElephantsDream_en.vtt"*/"");
                    if (playSubtitle.isEmpty() && info.has("subs")) {
                        try {
                            JSONObject obj = info.getJSONArray("subs").optJSONObject(0);
                            String url = obj.optString("url", "");
                            if (!TextUtils.isEmpty(url) && !FileUtils.hasExtension(url)) {
                                String format = obj.optString("format", "");
                                String name = obj.optString("name", "字幕");
                                String ext = ".srt";
                                switch (format) {
                                    case "text/x-ssa":
                                        ext = ".ass";
                                        break;
                                    case "text/vtt":
                                        ext = ".vtt";
                                        break;
                                    case "application/x-subrip":
                                        ext = ".srt";
                                        break;
                                    case "text/lrc":
                                        ext = ".lrc";
                                        break;
                                }
                                String filename = name + (name.toLowerCase().endsWith(ext) ? "" : ext);
                                url += "#" + URLEncoder.encode(filename);
                            }
                            playSubtitle = url;
                        } catch (Throwable th) {
                        }
                    }
                    subtitleCacheKey = info.optString("subtKey", null);
                    String playUrl = info.optString("playUrl", "");
                    String msg = info.optString("msg", "");
                    if (!msg.isEmpty()) {
                        Toast.makeText(PlayActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                    String flag = info.optString("flag");
                    String url = info.getString("url");
                    String danmaku = info.optString("danmaku");
                    HashMap<String, String> headers = null;
                    webUserAgent = null;
                    webHeaderMap = null;
                    if (info.has("header")) {
                        try {
                            JSONObject hds = new JSONObject(info.getString("header"));
                            Iterator<String> keys = hds.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                if (headers == null) {
                                    headers = new HashMap<>();
                                }
                                headers.put(key, hds.getString(key));
                                if (key.equalsIgnoreCase("user-agent")) {
                                    webUserAgent = hds.getString(key).trim();
                                } else if (key.equalsIgnoreCase("cookie")) {
                                    for (String split : hds.getString(key).split(";"))
                                        CookieManager.getInstance().setCookie(url, split.trim());
                                }
                            }
                            webHeaderMap = headers;
                        } catch (Throwable th) {

                        }
                    }
                    if (parse || jx) {
                        boolean userJxList = (playUrl.isEmpty() && ApiConfig.get().getVipParseFlags().contains(flag)) || jx;
                        initParse(flag, userJxList, playUrl, url);
                    } else {
                        mController.showParse(false);
                        playUrl(playUrl + url, headers);
                    }
                    checkDanmu(danmaku);
                } catch (Throwable th) {
                    errorWithRetry(getString(R.string.get_play_info_error), true);
                }
            } else {
                errorWithRetry(getString(R.string.get_play_info_error), true);
            }
        }
    };

    private void checkDanmu(String danmu) {
        danmuText = danmu;
        if (danmuPresenter != null) danmuPresenter.checkDanmu(danmu);
        if (TextUtils.isEmpty(danmuText) || !HawkUtils.getDanmuOpen()) return;
        mController.setHasDanmu(!TextUtils.isEmpty(danmuText));
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            // 防御：进程重建/extra 缺失/类型不符时这里会拿到 null，
            // 继续往下走会在 initPlayerCfg() 与 play() 连续 NPE
            Object extra = bundle.get("VodInfo");
            if (!(extra instanceof VodInfo)) {
                errorWithRetry(getString(R.string.playlist_empty), true);
                return;
            }
            mVodInfo = (VodInfo) extra;
            sourceKey = bundle.getString("sourceKey");
            sourceBean = ApiConfig.get().getSource(sourceKey);
            initPlayerCfg();
            play(false);
        }
    }

    void initPlayerCfg() {
        try {
            mVodPlayerCfg = new JSONObject(mVodInfo.playerCfg);
        } catch (Throwable th) {
            mVodPlayerCfg = new JSONObject();
        }
        try {
            if (!mVodPlayerCfg.has("pl")) {
                int playType = Hawk.get(HawkConfig.PLAY_TYPE, 1);
                boolean configurationFile = HawkUtils.getVodPlayerPreferredConfigurationFile();
                // sourceBean 在配置被清空/站点 key 不存在时为 null，原实现会 NPE 并被下方空 catch 吞掉，
                // 导致 pl/sc/sp/st/et 全部缺失（播放参数静默失效）
                int playerType = sourceBean == null ? -1 : sourceBean.getPlayerType();
                if (configurationFile && playerType != -1) {
                    playType = playerType;
                }
                mVodPlayerCfg.put("pl", playType);
            } else {
                //如果手动修改过那么该处的默认值不生效
//                boolean configurationFile = HawkUtils.getVodPlayerPreferredConfigurationFile();
//                if (!configurationFile) {
//                    int playType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
//                    mVodPlayerCfg.put("pl", playType);
//                }
            }

            if (!mVodPlayerCfg.has("pr")) {
                mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            }
            if (!mVodPlayerCfg.has("ijk")) {
                mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, ""));
            }
            if (!mVodPlayerCfg.has("sc")) {
                mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfg.has("sp")) {
                mVodPlayerCfg.put("sp", 1.0f);
            }
            if (!mVodPlayerCfg.has("st")) {
                mVodPlayerCfg.put("st", 0);
            }
            if (!mVodPlayerCfg.has("et")) {
                mVodPlayerCfg.put("et", 0);
            }
        } catch (Throwable th) {

        }
        mController.setPlayerConfig(mVodPlayerCfg);
    }

    void initPlayerDrive() {
        try {
            if (!mVodPlayerCfg.has("pl")) {
                mVodPlayerCfg.put("pl", Hawk.get(HawkConfig.PLAY_TYPE, 1));
            }
            if (!mVodPlayerCfg.has("pr")) {
                mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            }
            if (!mVodPlayerCfg.has("ijk")) {
                mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, ""));
            }
            if (!mVodPlayerCfg.has("sc")) {
                mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfg.has("sp")) {
                mVodPlayerCfg.put("sp", 1.0f);
            }
            if (!mVodPlayerCfg.has("st")) {
                mVodPlayerCfg.put("st", 0);
            }
            if (!mVodPlayerCfg.has("et")) {
                mVodPlayerCfg.put("et", 0);
            }
        } catch (Throwable th) {

        }
        mController.setPlayerConfig(mVodPlayerCfg);
    }

    // takagen99 : Add check for external players not enter PIP    
    private boolean extPlay = false;

    @Override
    public void onUserLeaveHint() {
        if (supportsPiPMode() && !extPlay && Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 2) {
            // Calculate Video Resolution
            int vWidth = mVideoView.getVideoSize()[0];
            int vHeight = mVideoView.getVideoSize()[1];
            Rational ratio = null;
            if (vWidth != 0) {
                if ((((double) vWidth) / ((double) vHeight)) > 2.39) {
                    vHeight = (int) (((double) vWidth) / 2.35);
                }
                ratio = new Rational(vWidth, vHeight);
            } else {
                ratio = new Rational(16, 9);
            }

            List<android.app.RemoteAction> actions = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                actions.add(generateRemoteAction(android.R.drawable.ic_media_previous, BROADCAST_ACTION_PREV, "Prev", "Play Previous"));
                actions.add(generateRemoteAction(android.R.drawable.ic_media_play, BROADCAST_ACTION_PLAYPAUSE, "Play/Pause", "Play or Pause"));
                actions.add(generateRemoteAction(android.R.drawable.ic_media_next, BROADCAST_ACTION_NEXT, "Next", "Play Next"));
            }
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .setActions(actions).build();
            enterPictureInPictureMode(params);

            mController.hideBottom();
            mVideoView.postDelayed(() -> {
                // 400ms 窗口内 activity 可能已销毁，mVideoView 置空后 NPE
                if (mVideoView == null || isFinishing() || isDestroyed()) return;
                // 仅画中画真正成功后才恢复播放：PiP 失败（部分 ROM 会抛异常）时
                // Activity 已进入后台，这里恢复会造成后台偷跑耗电
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode()) return;
                if (!mVideoView.isPlaying()) {
                    mController.togglePlay();
                }
            }, 400);
        }
        super.onUserLeaveHint();
    }

    @Override
    public void onBackPressed() {
        if (mController.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            if (mController.onKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // takagen99 : Use onStopCalled to track close activity
    private boolean onStopCalled;
    // 记录进入后台前的播放态：用户手动暂停后短暂切后台再回来，不应被强行恢复播放
    private boolean wasPlayingBeforePause;
    // 播放器已释放：避免 PiP 关闭/销毁后 onResume 再对已 release 的实例 resume
    private boolean playerReleased;

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null && !playerReleased) {
            onStopCalled = false;
            if (wasPlayingBeforePause) mVideoView.resume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        onStopCalled = true;
    }

    // takagen99
    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) {
            wasPlayingBeforePause = mVideoView.isPlaying();
            mVideoView.pause();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private android.app.RemoteAction generateRemoteAction(int iconResId, int actionCode, String title, String desc) {
        final PendingIntent intent = PendingIntent.getBroadcast(
                        PlayActivity.this,
                        actionCode,
                        new Intent(BROADCAST_ACTION).putExtra("action", actionCode),
                        0);
        final Icon icon = Icon.createWithResource(PlayActivity.this, iconResId);
        return (new android.app.RemoteAction(icon, title, desc, intent));
    }

    // takagen99 : PIP fix to close video when close window
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (supportsPiPMode() && isInPictureInPictureMode) {
            pipActionReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !intent.getAction().equals(BROADCAST_ACTION) || mController == null) {
                        return;
                    }

                    int currentStatus = intent.getIntExtra("action", 1);
                    if (currentStatus == BROADCAST_ACTION_PREV) {
                        playPrevious();
                    } else if (currentStatus == BROADCAST_ACTION_PLAYPAUSE) {
                        mController.togglePlay();
                    } else if (currentStatus == BROADCAST_ACTION_NEXT) {
                        playNext(false);
                    }
                }
            };
            registerReceiver(pipActionReceiver, new IntentFilter(BROADCAST_ACTION));

        } else {
            // Closed playback
            // mVideoView 在 fragment 视图销毁后可能为 null；
            // pipActionReceiver 从未注册时 unregisterReceiver 会抛 IllegalArgumentException
            if (onStopCalled && mVideoView != null && !playerReleased) {
                mVideoView.release();
                // 标记已释放：否则回到前台 onResume 会对已 release 的实例 resume
                playerReleased = true;
            }
            if (pipActionReceiver != null) {
                try {
                    unregisterReceiver(pipActionReceiver);
                } catch (IllegalArgumentException ignored) {
                }
                pipActionReceiver = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 弹幕线程池此前从未销毁：每进一次播放页泄漏一个永不回收的单线程 ExecutorService
        if (danmuPresenter != null) {
            danmuPresenter.destroy();
            danmuPresenter = null;
        }
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        if(executorService!=null){
            executorService.shutdownNow();
            executorService = null;
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        // PIP 接收器：销毁时统一注销，防止匿名内部类持有 Activity 泄漏
        if (pipActionReceiver != null) {
            try {
                unregisterReceiver(pipActionReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            pipActionReceiver = null;
        }
        //手动注销
        sourceViewModel.playResult.removeObserver(mObserverPlayResult);
        // 控制器内每秒自递归任务（网速/时间/隐藏底栏等）仅靠 onDetachedFromWindow 清理并不可靠，
        // Activity 直接 finish 时该回调可能不触发，残留任务会持有 controller → Activity
        if (mController != null) {
            mController.destroy();
        }
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        stopLoadWebView(true);
        stopParse();
        Thunder.stop(false); // 停止磁力下载
        Jianpian.finish();//停止p2p下载
        App.getInstance().setDashData(null);
    }

    private VodInfo mVodInfo;
    private JSONObject mVodPlayerCfg;
    private String sourceKey;
    private SourceBean sourceBean;

    public void playNext(boolean inProgress) {
        boolean hasNext = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasNext = false;
        } else {
            hasNext = mVodInfo.getplayIndex() + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        }
        if (!hasNext) {
            //mVodInfo 为 null 时 reverseSort 不可访问，按默认（非倒序）提示
            if (mVodInfo != null && mVodInfo.reverseSort) {
                Toast.makeText(this, getString(R.string.first_episode), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.last_episode), Toast.LENGTH_SHORT).show();
            }
            if (inProgress) {
                this.finish();
            }
            return;
        }
        mVodInfo.playIndex++;
        // playGroupCount 来自外部数据，为 0 时除零崩溃（与 PlayFragment 同步的修复）
        if (mVodInfo.playGroupCount > 0) {
            mVodInfo.playGroup += mVodInfo.playIndex / mVodInfo.playGroupCount;
            mVodInfo.playIndex = mVodInfo.playIndex % mVodInfo.playGroupCount;
        }
        play(false);
    }

    public void playPrevious() {
        boolean hasPre = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasPre = false;
        } else {
            hasPre = mVodInfo.getplayIndex() - 1 >= 0;
        }
        if (!hasPre) {
            if (mVodInfo != null && mVodInfo.reverseSort) {
                Toast.makeText(this, getString(R.string.last_episode), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.first_episode), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (mVodInfo.playIndex == 0) {
            mVodInfo.playGroup--;
            mVodInfo.playIndex = mVodInfo.playGroupCount - 1;
        } else {
            mVodInfo.playIndex--;
        }
        play(false);
    }

    private int autoRetryCount = 0;

    boolean autoRetry() {
        switchPlayer();
        if (loadFoundVideoUrls != null && loadFoundVideoUrls.size() > 0) {
            autoRetryFromLoadFoundVideoUrls();
            return true;
        }
        if (autoRetryCount < 1) {
            autoRetryCount++;
            play(false);
            return true;
        } else {
            autoRetryCount = 0;
            return false;
        }
    }

    void switchPlayer() {
        try {
            int playerType = mVodPlayerCfg.getInt("pl") == 1 ? 2 : 1;
            mVodPlayerCfg.put("pl", playerType);
            mController.setPlayerConfig(mVodPlayerCfg);
            mVodInfo.playerCfg = mVodPlayerCfg.toString();
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void autoRetryFromLoadFoundVideoUrls() {
        String videoUrl = loadFoundVideoUrls.poll();
        HashMap<String, String> header = loadFoundVideoUrlsHeader.get(videoUrl);
        playUrl(videoUrl, header);
    }

    void initParseLoadFound() {
        loadFoundCount.set(0);
        // WebView IO 线程 add 与主线程 poll 并发访问，必须使用线程安全容器
        loadFoundVideoUrls = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        loadFoundVideoUrlsHeader = new java.util.concurrent.ConcurrentHashMap<String, HashMap<String, String>>();
    }

    public void play(boolean reset) {
        // 防御：playFlag 与 seriesMap 不匹配、或 playIndex 越界时直接崩溃；
        // 且 autoRetry() 会自动再次调用 play()，会形成崩溃循环
        List<VodInfo.VodSeries> seriesList = mVodInfo == null || mVodInfo.seriesMap == null
                ? null : mVodInfo.seriesMap.get(mVodInfo.playFlag);
        if (seriesList == null || seriesList.isEmpty()
                || mVodInfo.getplayIndex() < 0 || mVodInfo.getplayIndex() >= seriesList.size()) {
            errorWithRetry(getString(R.string.playlist_empty), true);
            return;
        }
        VodInfo.VodSeries vs = seriesList.get(mVodInfo.getplayIndex());
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo.getplayIndex()));
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH_NOTIFY, mVodInfo.name + "&&" + vs.name));
        String playTitleInfo = mVodInfo.name + " : " + vs.name;
        setTip(getString(R.string.getting_play_info), true, false);
        mController.setTitle(playTitleInfo);

        stopParse();
        initParseLoadFound();
        if (mVideoView != null) mVideoView.release();
        subtitleCacheKey = mVodInfo.sourceKey + "-" + mVodInfo.id + "-" + mVodInfo.playFlag + "-" + mVodInfo.getplayIndex() + "-" + vs.name + "-subt";
        progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.getplayIndex();
        // 重新播放清除现有进度（DB 删除移后台）
        if (reset) {
            final String pk = progressKey, sk = subtitleCacheKey;
            AppTaskExecutor.deskIO().execute(() -> {
                CacheManager.delete(MD5.string2MD5(pk), 0);
                CacheManager.delete(MD5.string2MD5(sk), "");
            });
        }
        if (vs.url.startsWith("tvbox-drive://")) {
            progressKey = vs.url.replace("tvbox-drive://", "");
            // takagen99: Quick Fix for Media Options in Drive playback
            initPlayerDrive();
            mController.showParse(false);
            HashMap<String, String> headers = null;
            if (mVodInfo.playerCfg != null && mVodInfo.playerCfg.length() > 0) {
                JsonObject playerConfig = JsonParser.parseString(mVodInfo.playerCfg).getAsJsonObject();
                if (playerConfig.has("headers")) {
                    headers = new HashMap<>();
                    for (JsonElement headerEl : playerConfig.getAsJsonArray("headers")) {
                        JsonObject headerJson = headerEl.getAsJsonObject();
                        headers.put(headerJson.get("name").getAsString(), headerJson.get("value").getAsString());
                    }
                }
            }
            playUrl(vs.url.replace("tvbox-drive://", ""), headers);
            return;
        }
        if(Jianpian.isJpUrl(vs.url)){//荐片地址特殊判断
            String jp_url= vs.url;
            mController.showParse(false);
            if(vs.url.startsWith("tvbox-xg:")){
                playUrl(Jianpian.JPUrlDec(jp_url.substring(9)), null);
            }else {
                playUrl(Jianpian.JPUrlDec(jp_url), null);
            }

            return;
        }
        if (Thunder.play(vs.url, new Thunder.ThunderCallback() {
            @Override
            public void status(int code, String info) {
                if (code < 0) {
                    setTip(info, false, true);
                } else {
                    setTip(info, true, false);
                }
            }

            @Override
            public void list(Map<Integer, String> urlMap) {
            }

            @Override
            public void play(String url) {
                playUrl(url, null);
            }
        })) {
            mController.showParse(false);
            return;
        }
        sourceViewModel.getPlay(sourceKey, mVodInfo.playFlag, progressKey, vs.url, subtitleCacheKey);
    }

    private String playSubtitle;
    private String subtitleCacheKey;
    private String progressKey;
    private String parseFlag;
    private String webUrl;
    private String webUserAgent;
    private Map<String, String> webHeaderMap;

    private void initParse(String flag, boolean useParse, String playUrl, final String url) {
        parseFlag = flag;
        webUrl = url;
        ParseBean parseBean = null;
        mController.showParse(useParse);
        if (useParse) {
            parseBean = ApiConfig.get().getDefaultParse();
        } else {
            if (playUrl.startsWith("json:")) {
                parseBean = new ParseBean();
                parseBean.setType(1);
                parseBean.setUrl(playUrl.substring(5));
            } else if (playUrl.startsWith("parse:")) {
                String parseRedirect = playUrl.substring(6);
                for (ParseBean pb : ApiConfig.get().getParseBeanList()) {
                    if (pb.getName().equals(parseRedirect)) {
                        parseBean = pb;
                        break;
                    }
                }
            }
            if (parseBean == null) {
                parseBean = new ParseBean();
                parseBean.setType(0);
                parseBean.setUrl(playUrl);
            }
        }
        doParse(parseBean);
    }

    JSONObject jsonParse(String input, String json) throws JSONException {
        JSONObject jsonPlayData = new JSONObject(json);
        String url;
        if (jsonPlayData.has("data")) {
            url = jsonPlayData.getJSONObject("data").getString("url");
        } else {
            url = jsonPlayData.getString("url");
        }
        if (url.startsWith("//")) {
            url = "http:" + url;
        }
        if (!url.startsWith("http")) {
            return null;
        }
        JSONObject headers = new JSONObject();
        String ua = jsonPlayData.optString("user-agent", "");
        if (ua.trim().length() > 0) {
            headers.put("User-Agent", " " + ua);
        }
        String referer = jsonPlayData.optString("referer", "");
        if (referer.trim().length() > 0) {
            headers.put("Referer", " " + referer);
        }
        JSONObject taskResult = new JSONObject();
        taskResult.put("header", headers);
        taskResult.put("url", url);
        return taskResult;
    }

    void stopParse() {
        mHandler.removeMessages(100);
        stopLoadWebView(false);
        OkGo.getInstance().cancelTag("json_jx");
        if (parseThreadPool != null) {
            try {
                parseThreadPool.shutdown();
                parseThreadPool = null;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    ExecutorService parseThreadPool;

    /**
     * 线程池可能在 stopParse 中被并发置空/关闭，提交任务时做防护（与 PlayFragment 同步的修复）
     */
    void safeParseExecute(Runnable task) {
        ExecutorService pool = parseThreadPool;
        if (pool == null || pool.isShutdown()) return;
        try {
            pool.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    private String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    private void doParse(ParseBean pb) {
        stopParse();
        initParseLoadFound();
        if (pb.getType() == 4) {
            parseMix(pb,true);
        }else if (pb.getType() == 0) {
            setTip(getString(R.string.sniffing_url), true, false);
            mHandler.removeMessages(100);
            mHandler.sendEmptyMessageDelayed(100, 20 * 1000);
            if (pb.getExt() != null) {
                // 解析ext
                try {
                    HashMap<String, String> reqHeaders = new HashMap<>();
                    JSONObject jsonObject = new JSONObject(pb.getExt());
                    if (jsonObject.has("header")) {
                        JSONObject headerJson = jsonObject.optJSONObject("header");
                        Iterator<String> keys = headerJson.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (key.equalsIgnoreCase("user-agent")) {
                                webUserAgent = headerJson.getString(key).trim();
                            } else {
                                reqHeaders.put(key, headerJson.optString(key, ""));
                            }
                        }
                        if (reqHeaders.size() > 0) webHeaderMap = reqHeaders;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            loadWebView(pb.getUrl() + webUrl);
        } else if (pb.getType() == 1) { // json 解析
            setTip(getString(R.string.parsing_url), true, false);
            // 解析ext
            HttpHeaders reqHeaders = new HttpHeaders();
            try {
                JSONObject jsonObject = new JSONObject(pb.getExt());
                if (jsonObject.has("header")) {
                    JSONObject headerJson = jsonObject.optJSONObject("header");
                    Iterator<String> keys = headerJson.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        reqHeaders.put(key, headerJson.optString(key, ""));
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            OkGo.<String>get(pb.getUrl() + encodeUrl(webUrl))
                    .tag("json_jx")
                    .headers(reqHeaders)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            if (response.body() != null) {
                                return response.body().string();
                            } else {
                                throw new IllegalStateException(getString(R.string.network_error));
                            }
                        }

                        @Override
                        public void onSuccess(Response<String> response) {
                            String json = response.body();
                            try {
                                JSONObject rs = jsonParse(webUrl, json);
                                HashMap<String, String> headers = null;
                                if (rs.has("header")) {
                                    try {
                                        JSONObject hds = rs.getJSONObject("header");
                                        Iterator<String> keys = hds.keys();
                                        while (keys.hasNext()) {
                                            String key = keys.next();
                                            if (headers == null) {
                                                headers = new HashMap<>();
                                            }
                                            headers.put(key, hds.getString(key));
                                        }
                                    } catch (Throwable th) {

                                    }
                                }
                                playUrl(rs.getString("url"), headers);
                            } catch (Throwable e) {
                                e.printStackTrace();
                                errorWithRetry(getString(R.string.parse_error), false);
//                                setTip(getString(R.string.parse_error), false, true);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            errorWithRetry(getString(R.string.parse_error), false);
//                            setTip(getString(R.string.parse_error), false, true);
                        }
                    });
        } else if (pb.getType() == 2) { // json 扩展
            setTip(getString(R.string.parsing_url), true, false);
            if (parseThreadPool != null) {
                try {
                    parseThreadPool.shutdownNow();
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
            parseThreadPool = Executors.newSingleThreadExecutor();
            LinkedHashMap<String, String> jxs = new LinkedHashMap<>();
            for (ParseBean p : ApiConfig.get().getParseBeanList()) {
                if (p.getType() == 1) {
                    jxs.put(p.getName(), p.mixUrl());
                }
            }
            safeParseExecute(new Runnable() {
                @Override
                public void run() {
                    JSONObject rs = ApiConfig.get().jsonExt(pb.getUrl(), jxs, webUrl);
                    if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
//                        errorWithRetry(getString(R.string.parse_error), false);//没有url重试也没有重新获取
                        setTip(getString(R.string.parse_error), false, true);
                    } else {
                        HashMap<String, String> headers = null;
                        if (rs.has("header")) {
                            try {
                                JSONObject hds = rs.getJSONObject("header");
                                Iterator<String> keys = hds.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    if (headers == null) {
                                        headers = new HashMap<>();
                                    }
                                    headers.put(key, hds.getString(key));
                                }
                            } catch (Throwable th) {

                            }
                        }
                        if (rs.has("jxFrom")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, getString(R.string.parse_from, rs.optString("jxFrom")), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        boolean parseWV = rs.optInt("parse", 0) == 1;
                        if (parseWV) {
                            String wvUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                            loadUrl(wvUrl);
                        } else {
                            playUrl(rs.optString("url", ""), headers);
                        }
                    }
                }
            });
        } else if (pb.getType() == 3) { // json 聚合
            parseMix(pb,false);
        }
    }
    private void parseMix(ParseBean pb,boolean isSuper){
        setTip(getString(R.string.parsing_url), true, false);
        if (parseThreadPool != null) {
            try {
                parseThreadPool.shutdownNow();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        parseThreadPool = Executors.newSingleThreadExecutor();
        LinkedHashMap<String, HashMap<String, String>> jxs = new LinkedHashMap<>();
        String extendName = "";
        for (ParseBean p : ApiConfig.get().getParseBeanList()) {
            HashMap<String, String> data = new HashMap<String, String>();
            data.put("url", p.getUrl());
            if (p.getUrl().equals(pb.getUrl())) {
                extendName = p.getName();
            }
            data.put("type", p.getType() + "");
            data.put("ext", p.getExt());
            jxs.put(p.getName(), data);
        }
        String finalExtendName = extendName;
        safeParseExecute(new Runnable() {
            @Override
            public void run() {
                if(isSuper){
                    JSONObject rs = SuperParse.parse(jxs,parseFlag+"123",webUrl);
                    if (!rs.has("url") || rs.optString("url").isEmpty()) {
                        setTip(getString(R.string.parse_error), false, true);
                    } else {
                        if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                            if (rs.has("ua")) {
                                webUserAgent = rs.optString("ua").trim();
                            }
                            setTip(getString(R.string.super_parsing), true, false);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                                    stopParse();
                                    mHandler.removeMessages(100);
                                    mHandler.sendEmptyMessageDelayed(100, 20 * 1000);
                                    loadWebView(mixParseUrl);
                                }
                            });
                            safeParseExecute(new Runnable() {
                                @Override
                                public void run() {
                                    JSONObject res = SuperParse.doJsonJx(webUrl);
                                    rsJsonJx(res, true);
                                }
                            });
                        } else {
                            rsJsonJx(rs,false);
                        }
                    }
                }else {
                    JSONObject rs = ApiConfig.get().jsonExtMix(parseFlag + "111", pb.getUrl(), finalExtendName, jxs, webUrl);
                    if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
                        setTip(getString(R.string.parse_error), false, true);
                    } else {
                        if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                            if (rs.has("ua")) {
                                webUserAgent = rs.optString("ua").trim();
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                                    stopParse();
                                    setTip(getString(R.string.sniffing_url), true, false);
                                    mHandler.removeMessages(100);
                                    mHandler.sendEmptyMessageDelayed(100, 20 * 1000);
                                    loadWebView(mixParseUrl);
                                }
                            });
                        } else {
                           rsJsonJx(rs,false);
                        }
                    }
                }
            }
        });
    }
    private void rsJsonJx(JSONObject rs,boolean isSuper)
    {
        if(isSuper){
            if(rs==null || !rs.has("url"))return;
            stopLoadWebView(false);
        }
        HashMap<String, String> headers = null;
        if (rs.has("header")) {
            try {
                JSONObject hds = rs.getJSONObject("header");
                Iterator<String> keys = hds.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (headers == null) {
                        headers = new HashMap<>();
                    }
                    headers.put(key, hds.getString(key));
                }
            } catch (Throwable th) {

            }
        }
        if (rs.has("jxFrom")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, getString(R.string.parse_from, rs.optString("jxFrom")), Toast.LENGTH_SHORT).show();
                }
            });
        }
        playUrl(rs.optString("url", ""), headers);
    }

    // webview
    private XWalkView mXwalkWebView;
    private XWalkWebClient mX5WebClient;
    private WebView mSysWebView;
    private SysWebClient mSysWebClient;
    private final Map<String, Boolean> loadedUrls = new HashMap<>();
    // WebView IO 线程 add 与主线程 poll 并发访问，使用线程安全容器（与 PlayFragment 同步的修复）
    private java.util.concurrent.ConcurrentLinkedQueue<String> loadFoundVideoUrls = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private java.util.concurrent.ConcurrentHashMap<String, HashMap<String, String>> loadFoundVideoUrlsHeader = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicInteger loadFoundCount = new AtomicInteger(0);

    void loadWebView(String url) {
        if (mSysWebView == null && mXwalkWebView == null) {
            boolean useSystemWebView = Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
            if (!useSystemWebView) {
                XWalkUtils.tryUseXWalk(mContext, new XWalkUtils.XWalkState() {
                    @Override
                    public void success() {
                        initWebView(!sourceBean.getClickSelector().isEmpty());
                        loadUrl(url);
                    }

                    @Override
                    public void fail() {
                        Toast.makeText(mContext, getString(R.string.xwalk_incompatible), Toast.LENGTH_SHORT).show();
                        initWebView(true);
                        loadUrl(url);
                    }

                    @Override
                    public void ignore() {
                        Toast.makeText(mContext, getString(R.string.xwalk_not_downloaded), Toast.LENGTH_SHORT).show();
                        initWebView(true);
                        loadUrl(url);
                    }
                });
            } else {
                initWebView(true);
                loadUrl(url);
            }
        } else {
            loadUrl(url);
        }
    }

    void initWebView(boolean useSystemWebView) {
        if (useSystemWebView) {
            mSysWebView = new MyWebView(mContext);
            configWebViewSys(mSysWebView);
        } else {
            mXwalkWebView = new MyXWalkView(mContext);
            configWebViewX5(mXwalkWebView);
        }
    }

    void loadUrl(String url) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mXwalkWebView != null) {
                    mXwalkWebView.stopLoading();
                    if (webUserAgent != null) {
                        mXwalkWebView.getSettings().setUserAgentString(webUserAgent);
                    }
                    //mXwalkWebView.clearCache(true);
                    if (webHeaderMap != null) {
                        mXwalkWebView.loadUrl(url, webHeaderMap);
                    } else {
                        mXwalkWebView.loadUrl(url);
                    }
                }
                if (mSysWebView != null) {
                    mSysWebView.stopLoading();
                    if (webUserAgent != null) {
                        mSysWebView.getSettings().setUserAgentString(webUserAgent);
                    }
                    //mSysWebView.clearCache(true);
                    if (webHeaderMap != null) {
                        mSysWebView.loadUrl(url, webHeaderMap);
                    } else {
                        mSysWebView.loadUrl(url);
                    }
                }
            }
        });
    }

    void stopLoadWebView(boolean destroy) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mXwalkWebView != null) {
                    mXwalkWebView.stopLoading();
                    mXwalkWebView.loadUrl("about:blank");
                    if (destroy) {
//                        mXwalkWebView.clearCache(true);
                        mXwalkWebView.removeAllViews();
                        mXwalkWebView.onDestroy();
                        mXwalkWebView = null;
                    }
                }
                if (mSysWebView != null) {
                    mSysWebView.stopLoading();
                    mSysWebView.loadUrl("about:blank");
                    if (destroy) {
//                        mSysWebView.clearCache(true);
                        mSysWebView.removeAllViews();
                        mSysWebView.destroy();
                        mSysWebView = null;
                    }
                }
            }
        });
    }

    boolean checkVideoFormat(String url) {
        // 委托公共 SniffEngine（补齐 sourceBean 判空与异常兜底，与 Fragment 侧统一）
        return SniffEngine.checkVideoFormat(sourceBean, webUrl, url);
    }

    class MyWebView extends WebView {
        public MyWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    class MyXWalkView extends XWalkView {
        public MyXWalkView(Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewSys(WebView webView) {
        if (webView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        settings.setBlockNetworkImage(!Hawk.get(HawkConfig.DEBUG_OPEN, false));
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        /* 添加webView配置 */
        //设置编码
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(webView.getSettings().getUserAgentString());
        // settings.setUserAgentString(ANDROID_UA);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                return true;
            }
        });
        mSysWebClient = new SysWebClient();
        webView.setWebViewClient(mSysWebClient);
        webView.setBackgroundColor(Color.BLACK);
    }

    private class SysWebClient extends WebViewClient {

        @Override
        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
            sslErrorHandler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view,url);
            //String click=sourceBean.getClickSelector().trim();
            LOG.i("echo-onPageFinished url:" + url);
            //if(!click.isEmpty()){
             //   String selector;
             //   if(click.contains(";") && !click.endsWith(";")){
              //      if(!url.contains(click.split(";")[0]))return;
             //       selector=click.split(";")[1];
             //   }else {
             //       selector=click;
              //  }
              //  String js = selector;
              //  if(!selector.contains("click()"))js+=".click();";
             //   LOG.i("echo-javascript:" + js);
             //   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
             //       view.evaluateJavascript(js, null);
             //   } else {
              //      view.loadUrl("javascript:" + js);
              //  }
            if(!url.equals("about:blank")){
                mController.evaluateScript(sourceBean,url,view,null);
            }
            mHandler.sendEmptyMessage(200);
        }

        WebResourceResponse checkIsVideo(String url, HashMap<String, String> headers) {
            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return new WebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i("shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = loadedUrls.get(url);
            }

            if (!ad) {
                if (yxdm(url, headers)) return null;
                if (checkVideoFormat(url)) {
                    loadFoundVideoUrls.add(url);
                    loadFoundVideoUrlsHeader.put(url, headers);
                    LOG.i("loadFoundVideoUrl:" + url);
                    if (loadFoundCount.incrementAndGet() == 1) {
                        url = loadFoundVideoUrls.poll();
                        mHandler.removeMessages(100);
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if (!TextUtils.isEmpty(cookie))
                            headers.put("Cookie", " " + cookie);//携带cookie
                        playUrl(url, headers);
                        SuperParse.stopJsonJx();
                        stopLoadWebView(false);
                    }
                }
            }

            return ad || loadFoundCount.get() > 0 ?
                    AdBlocker.createEmptyResource() :
                    null;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return null;
        }

        @Nullable
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("shouldInterceptRequest url:" + url);
            HashMap<String, String> webHeaders = new HashMap<>();
            Map<String, String> hds = request.getRequestHeaders();
            if (hds != null && hds.keySet().size() > 0) {
                for (String k : hds.keySet()) {
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        webHeaders.put(k, " " + hds.get(k));
                    }
                }
            }
            return checkIsVideo(url, webHeaders);
        }

        @Override
        public void onLoadResource(WebView webView, String url) {
            super.onLoadResource(webView, url);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewX5(XWalkView webView) {
        if (webView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final XWalkSettings settings = webView.getSettings();
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        settings.setBlockNetworkImage(!Hawk.get(HawkConfig.DEBUG_OPEN, false));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // settings.setUserAgentString(ANDROID_UA);

        webView.setBackgroundColor(Color.BLACK);
        webView.setUIClient(new XWalkUIClient(webView) {
            @Override
            public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
                return false;
            }

            @Override
            public boolean onJsAlert(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(XWalkView view, String url, String message, XWalkJavascriptResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(XWalkView view, String url, String message, String defaultValue, XWalkJavascriptResult result) {
                return true;
            }
        });
        mX5WebClient = new XWalkWebClient(webView);
        webView.setResourceClient(mX5WebClient);
    }

    private class XWalkWebClient extends XWalkResourceClient {
        public XWalkWebClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onDocumentLoadedInFrame(XWalkView view, long frameId) {
            super.onDocumentLoadedInFrame(view, frameId);
        }

        @Override
        public void onLoadStarted(XWalkView view, String url) {
            super.onLoadStarted(view, url);
        }

        @Override
        public void onLoadFinished(XWalkView view, String url) {

            super.onLoadFinished(view, url);
            LOG.i("echo-onPageFinished url:" + url);
            if(!url.equals("about:blank")){
                mController.evaluateScript(sourceBean,url,null,view);
            }
        }

        @Override
        public void onProgressChanged(XWalkView view, int progressInPercent) {
            super.onProgressChanged(view, progressInPercent);
        }

        @Override
        public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("shouldInterceptLoadRequest url:" + url);
            // suppress favicon requests as we don't display them anywhere
            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return createXWalkWebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i("shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = loadedUrls.get(url);
            }
            if (!ad) {
                if (checkVideoFormat(url)) {
                    HashMap<String, String> webHeaders = new HashMap<>();
                    Map<String, String> hds = request.getRequestHeaders();
                    if (hds != null && hds.keySet().size() > 0) {
                        for (String k : hds.keySet()) {
                            if (k.equalsIgnoreCase("user-agent")
                                    || k.equalsIgnoreCase("referer")
                                    || k.equalsIgnoreCase("origin")) {
                                webHeaders.put(k, " " + hds.get(k));
                            }
                        }
                    }
                    loadFoundVideoUrls.add(url);
                    loadFoundVideoUrlsHeader.put(url, webHeaders);
                    LOG.i("loadFoundVideoUrl:" + url);
                    if (loadFoundCount.incrementAndGet() == 1) {
                        mHandler.removeMessages(100);
                        url = loadFoundVideoUrls.poll();
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if (!TextUtils.isEmpty(cookie))
                            webHeaders.put("Cookie", " " + cookie);//携带cookie
                        playUrl(url, webHeaders);
                        SuperParse.stopJsonJx();
                        stopLoadWebView(false);
                    }
                }
            }
            return ad || loadFoundCount.get() > 0 ?
                    createXWalkWebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes())) :
                    null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(XWalkView view, String s) {
            return false;
        }

        @Override
        public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
            callback.onReceiveValue(true);
        }
    }

}
