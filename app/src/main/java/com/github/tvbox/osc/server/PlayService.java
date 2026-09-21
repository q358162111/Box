package com.github.tvbox.osc.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;

public class PlayService extends Service {
	static String videoInfo = "TVBox&&第一集";
	// 使用弱引用避免 Service 生命周期内强持有 VideoView（其持有 Activity Context）导致 Activity 泄漏
    private static WeakReference<MyVideoView> videoViewRef;

    public static void start(MyVideoView controller,String currentVideoInfo) {
        videoInfo = currentVideoInfo;
        PlayService.videoViewRef = new WeakReference<>(controller);
        ContextCompat.startForegroundService(App.getInstance(), new Intent(App.getInstance(), PlayService.class));
    }

    public static void stop() {
        App.getInstance().stopService(new Intent(App.getInstance(), PlayService.class));
    }

    private static MyVideoView peekVideoView() {
        return videoViewRef != null ? videoViewRef.get() : null;
    }


    private static final String CHANNEL_ID = "MyChannelId";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, "My Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	startForeground(NOTIFICATION_ID, buildNotification());
        MyVideoView videoView = peekVideoView();
        if (videoView != null) videoView.start();
        return START_NOT_STICKY;
    }
    
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event){
        if (event.type == RefreshEvent.TYPE_REFRESH_NOTIFY){
            if (event.obj != null) {
                videoInfo = event.obj.toString();
            }
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        }
    }
    
    private Notification buildNotification(){
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification_player);
        // 防止 videoInfo 不含 "&&" 时数组越界导致前台服务启动失败
        String[] parts = videoInfo == null ? new String[0] : videoInfo.split("&&");
        String title = parts.length > 0 ? parts[0] : "";
        String subtitle = parts.length > 1 ? parts[1] : "";
        remoteViews.setTextViewText(R.id.tv_title, title);
        remoteViews.setTextViewText(R.id.tv_subtitle, "正在播放: "+ subtitle);
        MyVideoView videoView = peekVideoView();
        remoteViews.setImageViewResource(R.id.iv_play_pause,
                videoView != null && videoView.isPlaying() ? R.drawable.ic_notify_pause : R.drawable.ic_notify_play);

        // 创建通知栏操作
        remoteViews.setOnClickPendingIntent(R.id.iv_previous, getPendingIntent(DetailActivity.BROADCAST_ACTION_PREV));
        remoteViews.setOnClickPendingIntent(R.id.iv_play_pause, getPendingIntent(DetailActivity.BROADCAST_ACTION_PLAYPAUSE));
        remoteViews.setOnClickPendingIntent(R.id.iv_next, getPendingIntent(DetailActivity.BROADCAST_ACTION_NEXT));
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_icon)
                .setContent(remoteViews)
                .setCustomContentView(remoteViews)
                .setContentIntent(getPendingIntentActivity())
                .setOngoing(true);

        return builder.build();
    }

    private NotificationCompat.Action buildNotificationAction(int iconResId, String title, PendingIntent intent) {
    	final androidx.core.graphics.drawable.IconCompat icon = androidx.core.graphics.drawable.IconCompat.createWithResource(App.getInstance(), iconResId);
        // 创建通知栏操作
        return new NotificationCompat.Action.Builder(icon, title, intent).build();
    }

    private PendingIntent getPendingIntentActivity() {
        Intent intent = new Intent(this, DetailActivity.class);
        return PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static PendingIntent getPendingIntent(int actionCode) {
        // Android 12+ 要求 PendingIntent 必须显式声明 FLAG_IMMUTABLE，否则抛 IllegalArgumentException
        return PendingIntent.getBroadcast(App.getInstance(), actionCode, new Intent(DetailActivity.BROADCAST_ACTION).putExtra("action", actionCode).setPackage(App.getInstance().getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
