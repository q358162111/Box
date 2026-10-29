package com.github.tvbox.osc.play;

import android.os.Build;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.player.danmu.Parser;
import com.github.tvbox.osc.util.HawkUtils;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;
import master.flame.danmaku.danmaku.model.IDisplayer;

import com.github.tvbox.osc.player.MyVideoView;
/**
 * PlayActivity/PlayFragment 共用的弹幕控制器：弹幕视图初始化、样式设置、
 * 解析装载与进度同步。两侧原为双份实现（差异仅 PiP 判断来源与空视图防护），
 * 统一收敛到这里；Fragment 侧的空视图防护对所有宿主都安全，故统一保留。
 */
public class DanmuPresenter {

    public interface Host {
        /** PiP 模式下不加载弹幕 */
        boolean isInPictureInPictureMode();

        /** 弹幕视图由宿主持有（生命周期不同），可能为 null */
        DanmakuView getDanmuView();

        MyVideoView getVideoView();
    }

    private final Host host;
    private DanmakuContext danmakuContext;
    private ExecutorService executorService;

    public DanmuPresenter(Host host) {
        this.host = host;
    }

    public void initDanmuView(DanmakuView danmuView, MyVideoView videoView) {
        danmakuContext = DanmakuContext.create();
        videoView.setDanmuView(danmuView);
    }

    public void setDanmuViewSettings(final String danmuText, boolean reload) {
        DanmakuView danmuView = host.getDanmuView();
        if (danmuView == null || danmakuContext == null) return;
        float speed = HawkUtils.getDanmuSpeed();
        float alpha = HawkUtils.getDanmuAlpha();
        float sizeScale = HawkUtils.getDanmuSizeScale();
        int maxLine = HawkUtils.getDanmuMaxLine();
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLine);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLine);
        danmakuContext.setMaximumLines(maxLines).setScrollSpeedFactor(speed).setDanmakuTransparency(alpha).setScaleTextSize(sizeScale);
        danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3).setDanmakuMargin(8);
        if (reload) {
            if (executorService != null) {
                executorService.shutdownNow();
                executorService = null;
            }
            executorService = Executors.newSingleThreadExecutor();
            executorService.execute(() -> {
                // 视图可能在任务排队期间被宿主销毁/置空，先判空
                if (host.getDanmuView() == null) return;
                // Parser 构造内部会同步发起弹幕文件下载，留在子线程；
                // DanmakuView 的 release()/prepare() 会同步操作 DrawTask/DrawHandler，
                // 必须在主线程调用，否则与渲染线程并发导致渲染竞态/崩溃
                final Parser parser = new Parser(danmuText);
                App.post(() -> {
                    DanmakuView v = host.getDanmuView();
                    if (v == null) return;
                    v.release();
                    v.prepare(parser, danmakuContext);
                    MyVideoView videoView = host.getVideoView();
                    if (videoView != null && videoView.isPlaying()) {
                        v.seekTo(videoView.getCurrentPosition());
                    }
                });
            });
        }
    }

    public void checkDanmu(final String danmu) {
        DanmakuView danmuView = host.getDanmuView();
        if (danmuView == null) return;
        danmuView.release();
        danmuView.setVisibility(android.text.TextUtils.isEmpty(danmu) || !HawkUtils.getDanmuOpen()
                ? android.view.View.GONE : android.view.View.VISIBLE);
        if (android.text.TextUtils.isEmpty(danmu)
                || !HawkUtils.getDanmuOpen()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && host.isInPictureInPictureMode())) return;
        if (!danmu.isEmpty()) {
            setDanmuViewSettings(danmu, true);
        }
    }

    public void destroy() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }
}