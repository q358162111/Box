package com.github.tvbox.osc.play;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.player.IjkmPlayer;
import com.github.tvbox.osc.player.EXOmPlayer;
import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.StringUtils;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * PlayActivity/PlayFragment 共用的内置字幕/音轨选择器。
 * 两侧原为逐字符复制的双份实现（差异仅 Context 来源与个别 startProgress 分支），
 * 统一收敛到这里：取轨道信息 → 弹出选择框 → 切轨 → 恢复播放进度。
 */
public class TrackSelector {

    public interface Host {
        Context context();

        /** 播放页自己的 Handler，用于切轨后的 seek 延迟恢复 */
        Handler handler();
    }

    private final Host host;
    private final VodController controller;

    public TrackSelector(Host host, VodController controller) {
        this.host = host;
        this.controller = controller;
    }

    private TrackInfo getTrackInfo(AbstractPlayer mediaPlayer) {
        if (mediaPlayer instanceof EXOmPlayer) {
            return ((EXOmPlayer) mediaPlayer).getTrackInfo();
        }
        if (mediaPlayer instanceof IjkmPlayer) {
            return ((IjkmPlayer) mediaPlayer).getTrackInfo();
        }
        return null;
    }

    /** 内置字幕选择 */
    public void selectInternalSubtitle(AbstractPlayer mediaPlayer) {
        TrackInfo trackInfo = getTrackInfo(mediaPlayer);
        if (trackInfo == null) {
            Toast.makeText(host.context(), host.context().getString(R.string.vod_sub_na), Toast.LENGTH_SHORT).show();
            return;
        }
        final List<TrackInfoBean> bean = trackInfo.getSubtitle();
        if (bean.size() < 1) {
            Toast.makeText(host.context(), host.context().getString(R.string.vod_sub_na), Toast.LENGTH_SHORT).show();
            return;
        }
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(host.context());
        dialog.setTip(host.context().getString(R.string.vod_sub_sel));
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                controller.mSubtitleView.setVisibility(View.VISIBLE);
                try {
                    for (TrackInfoBean subtitle : bean) {
                        subtitle.selected = subtitle.trackId == value.trackId;
                    }
                    mediaPlayer.pause();
                    long progress = mediaPlayer.getCurrentPosition();//保存当前进度，ijk 切换轨道 会有快进几秒

                    if (mediaPlayer instanceof IjkmPlayer) {
                        controller.mSubtitleView.destroy();
                        controller.mSubtitleView.clearSubtitleCache();
                        controller.mSubtitleView.isInternal = true;
                        ((IjkmPlayer) mediaPlayer).setTrack(value.trackId);
                        host.handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mediaPlayer.seekTo(progress);
                                mediaPlayer.start();
                            }
                        }, 800);
                    }
                    if (mediaPlayer instanceof EXOmPlayer) {
                        controller.mSubtitleView.destroy();
                        controller.mSubtitleView.clearSubtitleCache();
                        controller.mSubtitleView.isInternal = true;
                        ((EXOmPlayer) mediaPlayer).selectExoTrack(value);
                        host.handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mediaPlayer.seekTo(progress);
                                mediaPlayer.start();
                                controller.startProgress();
                            }
                        }, 800);
                    }
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("切换内置字幕出错");
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                return val.name + (StringUtils.isEmpty(val.language) ? "" : " " + val.language);
            }
        }, new TrackDiffCallback(), bean, trackInfo.getSubtitleSelected(false));
        dialog.show();
    }

    /** 音轨选择 */
    public void selectAudioTrack(AbstractPlayer mediaPlayer) {
        TrackInfo trackInfo = getTrackInfo(mediaPlayer);
        if (trackInfo == null) {
            Toast.makeText(host.context(), host.context().getString(R.string.vod_no_audio), Toast.LENGTH_SHORT).show();
            return;
        }
        final List<TrackInfoBean> bean = trackInfo.getAudio();
        if (bean.size() < 1) return;
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(host.context());
        dialog.setTip(host.context().getString(R.string.vod_audio));
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                try {
                    for (TrackInfoBean audio : bean) {
                        audio.selected = audio.trackId == value.trackId;
                    }
                    mediaPlayer.pause();
                    long progress = mediaPlayer.getCurrentPosition();//保存当前进度，ijk 切换轨道 会有快进几秒
                    if (mediaPlayer instanceof IjkmPlayer) {
                        ((IjkmPlayer) mediaPlayer).setTrack(value.trackId);
                    }
                    if (mediaPlayer instanceof EXOmPlayer) {
                        ((EXOmPlayer) mediaPlayer).selectExoTrack(value);
                    }
                    host.handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mediaPlayer.seekTo(progress);
                            mediaPlayer.start();
                            controller.startProgress();
                        }
                    }, 800);
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("切换音轨出错");
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                String name = val.name.replace("AUDIO,", "");
                name = name.replace("N/A,", "");
                name = name.replace(" ", "");
                return name + (StringUtils.isEmpty(val.language) ? "" : " " + val.language);
            }
        }, new TrackDiffCallback(), bean, trackInfo.getAudioSelected(false));
        dialog.show();
    }

    private static class TrackDiffCallback extends DiffUtil.ItemCallback<TrackInfoBean> {
        @Override
        public boolean areItemsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
            return oldItem.trackId == newItem.trackId;
        }

        @Override
        public boolean areContentsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
            return oldItem.trackId == newItem.trackId;
        }
    }
}
