package com.github.tvbox.osc.subtitle;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.player.EXOmPlayer;
import com.github.tvbox.osc.subtitle.model.Subtitle;
import com.github.tvbox.osc.subtitle.model.Time;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.SubtitleHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import static com.lzy.okgo.utils.HttpUtils.runOnUiThread;

/**
 * @author AveryZhong.
 */

public class DefaultSubtitleEngine implements SubtitleEngine {
    private static final String TAG = DefaultSubtitleEngine.class.getSimpleName();
    private static final int MSG_REFRESH = 0x888;
    private static final int REFRESH_INTERVAL = 100;

    @Nullable
    private HandlerThread mHandlerThread;
    @Nullable
    private Handler mWorkHandler;
    @Nullable
    private List<Subtitle> mSubtitles;
    // 未应用偏移的原始时间轴，用于每次从原始值重新应用总偏移
    @Nullable
    private List<Subtitle> mOriginalSubtitles;
    // 累计偏移（毫秒）
    private int currentDelayMillis = 0;
    private UIRenderTask mUIRenderTask;
    private AbstractPlayer mMediaPlayer;
    private OnSubtitlePreparedListener mOnSubtitlePreparedListener;
    private OnSubtitleChangeListener mOnSubtitleChangeListener;

    public DefaultSubtitleEngine() {

    }

    @Override
    public void bindToMediaPlayer(AbstractPlayer mediaPlayer) {
        mMediaPlayer = mediaPlayer;
    }

    @Override
    public void setSubtitlePath(final String path) {
        initWorkThread();
        reset();
        if (TextUtils.isEmpty(path)) {
            Log.w(TAG, "loadSubtitleFromRemote: path is null.");
            return;
        }

        SubtitleLoader.loadSubtitle(path, new SubtitleLoader.Callback() {
            @Override
            public void onSuccess(final SubtitleLoadSuccessResult subtitleLoadSuccessResult) {
                if (subtitleLoadSuccessResult == null) {
                    Log.d(TAG, "onSuccess: subtitleLoadSuccessResult is null.");
                    return;
                }
                if (subtitleLoadSuccessResult.timedTextObject == null) {
                    Log.d(TAG, "onSuccess: timedTextObject is null.");
                    return;
                }
                final TreeMap<Integer, Subtitle> captions = subtitleLoadSuccessResult.timedTextObject.captions;
                if (captions == null) {
                    Log.d(TAG, "onSuccess: captions is null.");
                    return;
                }
                mSubtitles = new ArrayList<>(captions.values());
                setSubtitleDelay(SubtitleHelper.getTimeDelay());
                notifyPrepared();

                // 写盘与 CacheManager 落盘移到后台线程：数 MB 字幕 getBytes()+写文件在主线程可触发 ANR
                final String finalPath = path;
                AppTaskExecutor.deskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        String subtitlePath = subtitleLoadSuccessResult.subtitlePath;
                        if (subtitlePath.startsWith("http://") || subtitlePath.startsWith("https://")) {
                            String subtitleFileCacheDir = App.getInstance().getCacheDir().getAbsolutePath() + "/zimu/";
                            File cacheDir = new File(subtitleFileCacheDir);
                            if (!cacheDir.exists()) {
                                cacheDir.mkdirs();
                            }
                            // 防御目录穿越：仅取 fileName 的 basename 部分，且要求仅包含安全字符
                            String safeFileName = sanitizeFileName(subtitleLoadSuccessResult.fileName);
                            if (safeFileName == null) {
                                Log.w(TAG, "onSuccess: illegal subtitle fileName, skip cache write.");
                                return;
                            }
                            String subtitleFile = subtitleFileCacheDir + safeFileName;
                            File cacheSubtitleFile = new File(subtitleFile);
                            boolean writeResult = FileUtils.writeSimple(subtitleLoadSuccessResult.content.getBytes(), cacheSubtitleFile);
                            if (writeResult && playSubtitleCacheKey != null) {
                                CacheManager.save(MD5.string2MD5(getPlaySubtitleCacheKey()), subtitleFile);
                            }
                        } else {
                            CacheManager.save(MD5.string2MD5(getPlaySubtitleCacheKey()), finalPath);
                        }
                    }
                });
            }

            @Override
            public void onError(final Exception exception) {
                Log.e(TAG, "onError: " + exception.getMessage());
            }
        });
    }

    @Override
    public void setSubtitleDelay(Integer milliseconds) {
        if (milliseconds == null) {
            return;
        }
        if (mSubtitles == null || mSubtitles.size() == 0) {
            return;
        }
        // 调用方传的是本次增量（±0.5s），SubtitleHelper 持久化的是累计总偏移；
        // 这里在引擎内累计，每次从原始时间轴重新应用总偏移，避免偏移永久漂移、无法归零
        if (mOriginalSubtitles == null) {
            mOriginalSubtitles = new ArrayList<>(mSubtitles);
        }
        currentDelayMillis += milliseconds;
        List<Subtitle> thisSubtitles = new ArrayList<>();
        for (Subtitle original : mOriginalSubtitles) {
            // Subtitle 无拷贝构造，手动深拷贝时间轴（Time 仅含 mseconds 状态）
            Subtitle subtitle = new Subtitle();
            subtitle.style = original.style;
            subtitle.region = original.region;
            subtitle.content = original.content;
            Time start = new Time("", null);
            start.mseconds = original.start.mseconds;
            Time end = new Time("", null);
            end.mseconds = original.end.mseconds;
            subtitle.start = start;
            subtitle.end = end;
            start.mseconds += currentDelayMillis;
            end.mseconds += currentDelayMillis;
            if (start.mseconds <= 0) {
                start.mseconds = 0;
            }
            if (end.mseconds <= 0) {
                end.mseconds = 0;
            }
            thisSubtitles.add(subtitle);
        }
        mSubtitles = thisSubtitles;
    }

    /**
     * 规范化字幕缓存文件名：仅取 basename 部分，且只接受字母/数字/点/下划线/短横线。
     * 防止远程字幕 URL 中的目录穿越或特殊字符污染本地文件路径。
     */
    private static String sanitizeFileName(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        String name = slash >= 0 ? raw.substring(slash + 1) : raw;
        if (name.isEmpty() || name.contains("..") || !name.matches("[A-Za-z0-9._\\-]+")) {
            return null;
        }
        return name;
    }

    private String playSubtitleCacheKey;
    public void setPlaySubtitleCacheKey(String cacheKey) {
        playSubtitleCacheKey = cacheKey;
    }

    public String getPlaySubtitleCacheKey() {
        return playSubtitleCacheKey;
    }

    @Override
    public void reset() {
        stop();
        mSubtitles = null;
        mOriginalSubtitles = null;
        currentDelayMillis = 0;
        mUIRenderTask = null;
    }

    @Override
    public void start() {
        Log.d(TAG, "start: ");
        if (mMediaPlayer == null) {
            Log.w(TAG, "MediaPlayer is not bind, You must bind MediaPlayer to "
                    + SubtitleEngine.class.getSimpleName()
                    + " before start() method be called,"
                    + " you can do this by call " +
                    "bindToMediaPlayer(MediaPlayer mediaPlayer) method.");
            return;
        }
        stop();
        if (mWorkHandler != null) {
            mWorkHandler.sendEmptyMessageDelayed(MSG_REFRESH, REFRESH_INTERVAL);
        }

    }

    @Override
    public void pause() {
        stop();
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public void stop() {
        if (mWorkHandler != null) {
            mWorkHandler.removeMessages(MSG_REFRESH);
        }
    }

    @Override
    public void destroy() {
        Log.d(TAG, "destroy: ");
        stopWorkThread();
        reset();

    }

    private void initWorkThread() {
        stopWorkThread();
        mHandlerThread = new HandlerThread("SubtitleFindThread");
        mHandlerThread.start();
        mWorkHandler = new Handler(mHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(final Message msg) {
                try {
                	if (mMediaPlayer != null && mMediaPlayer instanceof EXOmPlayer) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                long delay = REFRESH_INTERVAL;
                                long position = mMediaPlayer.getCurrentPosition();
                                Subtitle subtitle = SubtitleFinder.find(position, mSubtitles);
                                notifyRefreshUI(subtitle);
                                if (subtitle != null) {
                                    delay = subtitle.end.mseconds - position;
                                }

                                // 防御：负 delay 会让 sendEmptyMessageDelayed 立即触发下一轮，
                                // 导致 Handler 自递归空转、消息队列膨胀直至 ANR
                                if (delay <= 0) delay = REFRESH_INTERVAL;

                                if (mWorkHandler != null) {
                                    mWorkHandler.sendEmptyMessageDelayed(MSG_REFRESH, delay);
                                }
                            }
                        });
                        return true;
                    }
                    long delay = REFRESH_INTERVAL;
                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        long position = mMediaPlayer.getCurrentPosition();
                        Subtitle subtitle = SubtitleFinder.find(position, mSubtitles);
                        notifyRefreshUI(subtitle);
                        if (subtitle != null) {
                            delay = subtitle.end.mseconds - position;
                        }
                    }

                    // 防御：负 delay 自递归
                    if (delay <= 0) delay = REFRESH_INTERVAL;
                    if (mWorkHandler != null) {
                        mWorkHandler.sendEmptyMessageDelayed(MSG_REFRESH, delay);
                    }
                } catch (Exception e) {
                    // ignored
                }
                return true;
            }
        });
    }

    private void stopWorkThread() {
        if (mHandlerThread != null) {
            // quitSafely 优于 quit：会处理完队列中已派发的消息再退出，避免正在执行的 find() 中途被终止
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
        if (mWorkHandler != null) {
            mWorkHandler.removeCallbacksAndMessages(null);
            mWorkHandler = null;
        }
    }

    private void notifyRefreshUI(final Subtitle subtitle) {
        if (mUIRenderTask == null) {
            mUIRenderTask = new UIRenderTask(mOnSubtitleChangeListener);
        }
        mUIRenderTask.execute(subtitle);
    }

    private void notifyPrepared() {
        if (mOnSubtitlePreparedListener != null) {
            mOnSubtitlePreparedListener.onSubtitlePrepared(mSubtitles);
        }
    }

    @Override
    public void setOnSubtitlePreparedListener(final OnSubtitlePreparedListener listener) {
        mOnSubtitlePreparedListener = listener;
    }

    @Override
    public void setOnSubtitleChangeListener(final OnSubtitleChangeListener listener) {
        mOnSubtitleChangeListener = listener;
    }

}
