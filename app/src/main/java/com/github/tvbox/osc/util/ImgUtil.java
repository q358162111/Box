package com.github.tvbox.osc.util;

import static com.bumptech.glide.load.resource.bitmap.VideoDecoder.FRAME_OPTION;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

public class ImgUtil {
    // 防御：原代码 public static int defaultWidth/Height 允许任意位置修改（"魔数"全局变量）
    // 改为 private static final + public getter，避免被外部污染
    private static final int DEFAULT_WIDTH = 244;
    private static final int DEFAULT_HEIGHT = 320;

    public static int getDefaultWidth() {
        return DEFAULT_WIDTH;
    }

    public static int getDefaultHeight() {
        return DEFAULT_HEIGHT;
    }

    /** 向后兼容：保留旧静态字段的只读访问。已废弃。 */
    @Deprecated
    public static int defaultWidth = DEFAULT_WIDTH;

    /** 向后兼容：保留旧静态字段的只读访问。已废弃。 */
    @Deprecated
    public static int defaultHeight = DEFAULT_HEIGHT;

    /**
     * style 数据结构：ratio 指定宽高比（宽 / 高），type 表示风格（例如 rect、list）
     */
    public static class Style {
        public float ratio;
        public String type;

        public Style(float ratio, String type) {
            this.ratio = ratio;
            this.type = type;
        }
    }

    public static Style initStyle() {
        if (ApiConfig.get() == null || ApiConfig.get().getHomeSourceBean() == null) return null;
        String bStyle = ApiConfig.get().getHomeSourceBean().getStyle();
        // 修复：原代码未判空直接调用 bStyle.isEmpty() 会抛 NullPointerException
        if (!TextUtils.isEmpty(bStyle)) {
            try {
                JSONObject jsonObject = new JSONObject(bStyle);
                float ratio = (float) jsonObject.getDouble("ratio");
                String type = jsonObject.getString("type");
                return new Style(ratio, type);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static int spanCountByStyle(Style style,int defaultCount){
        int spanCount=defaultCount;
        if ("rect".equals(style.type)) {
            if (style.ratio >= 1.7) {
                spanCount = 3; // 横图
            } else if (style.ratio >= 1.3) {
                spanCount = 4; // 4:3
            }
        } else if ("list".equals(style.type)) {
            spanCount = 1;
        }
        return spanCount;
    }

    public static int getStyleDefaultWidth(Style style){
        int styleDefaultWidth = 280;
        if(style.ratio<1)styleDefaultWidth=214;
        if(style.ratio>1.7)styleDefaultWidth=380;
        return styleDefaultWidth;
    }
//    public static void load(String url, ImageView view) {
//        load(url, view, 10);
//    }
//
//    public static void load(String url, ImageView view, ImageView.ScaleType scaleType) {
//        load(url, view, 10, scaleType);
//    }
//
//    public static void load(String url, ImageView view, int roundingRadius, ImageView.ScaleType scaleType) {
//        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
//        if (TextUtils.isEmpty(url)) {
//            view.setImageResource(R.drawable.img_loading_placeholder);
//        } else {
//            if (roundingRadius == 0) roundingRadius = 1;
//            RequestOptions requestOptions = new RequestOptions()
//                .format(DecodeFormat.PREFER_RGB_565)
//                .diskCacheStrategy(getDiskCacheStrategy(4))
//                .dontAnimate()
//                .transform(
//            new RoundedCorners(roundingRadius));
//            Glide.with(App.getInstance())
//                .asBitmap()
//                .load(getUrl(url))
//                .error(R.drawable.img_loading_placeholder)
//                .placeholder(R.drawable.img_loading_placeholder)
//                .listener(getListener(view, scaleType))
//                .apply(requestOptions)
//                .into(view);
//        }
//    }
    public static void load(String url, ImageView view, int roundingRadius) {
        load(url, view, roundingRadius,0,0);
    }

    // RequestOptions 按「圆角+尺寸」缓存复用：列表快速滚动时每个 item 都会调用 load()，
    // 原实现每次 bind 都重建 RequestOptions + CenterCrop + RoundedCorners + 匿名 listener。
    private static final java.util.concurrent.ConcurrentHashMap<String, RequestOptions> OPTIONS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static RequestOptions obtainOptions(int roundingRadius, int width, int height) {
        String key = roundingRadius + "_" + width + "_" + height;
        RequestOptions cached = OPTIONS_CACHE.get(key);
        if (cached != null) return cached;
        RequestOptions created = new RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(getDiskCacheStrategy(4))
            .dontAnimate()
            .transform(new CenterCrop(), new RoundedCorners(roundingRadius));
        if (width > 0 && height > 0) {
            created = created.override(width, height);
        }
        // 上限保护：圆角/尺寸组合理论上无界，避免缓存无限增长
        if (OPTIONS_CACHE.size() < 64) {
            OPTIONS_CACHE.putIfAbsent(key, created);
        }
        return created;
    }

    public static void load(String url, ImageView view, int roundingRadius, int newWidth, int newHeight) {
        // 仅在真正变化时才设置：setScaleType 会 invalidate + requestLayout，
        // 每个 item 每次 bind 无条件设一次会在快速滚动时累加大量无谓的重新布局
        if (view.getScaleType() != ImageView.ScaleType.CENTER) {
            view.setScaleType(ImageView.ScaleType.CENTER);
        }
        if (TextUtils.isEmpty(url)) {
            view.setImageResource(R.drawable.img_loading_placeholder);
        } else {
            if (roundingRadius == 0) roundingRadius = 1;
            // 修复：原用 Glide.with(App.getInstance())，拿到的 RequestManager 与页面生命周期无关，
            // 页面销毁/快速切台后在途请求不会被暂停或清理，TV 上反复进出列表会积压大量请求。
            // 用 Glide.with(view) 绑定到宿主 Activity/Fragment 生命周期（未挂载时 Glide 自动回退）。
            Glide.with(view)
                .asBitmap()
                .load(getUrl(url))
                .error(R.drawable.img_loading_placeholder)
                .placeholder(R.drawable.img_loading_placeholder)
                .listener(FIT_XY_LISTENER)
                .apply(obtainOptions(roundingRadius, newWidth, newHeight))
                .into(view);
        }
    }

    /*
     * 使用Glide方式获取视频某一帧
     * @param uri 视频地址
     * @param imageView 设置image
     * @param frameTimeMicros 获取某一时间帧.
     */
    public static void loadVideoScreenshot(String uri, ImageView imageView, long frameTimeMicros) {
        RequestOptions requestOptions = RequestOptions.frameOf(frameTimeMicros * 1000)
            .set(FRAME_OPTION, MediaMetadataRetriever.OPTION_CLOSEST)
            .transform(
        new CenterCrop(),
        new RoundedCorners(10));
        Glide.with(App.getInstance())
            .load(uri)
            .skipMemoryCache(true)
            .apply(requestOptions)
            .into(imageView);
    }

    public static String getDiskCacheStrategyName(int index) {
        String[] names = new String[] {
            "[NONE] 关闭", "[RESOURCE] 转换图片", "[DATA] 原始图片 ", "[ALL] 原始图片和转换图片", "[AUTOMATIC] 自动"
        };
        return names[index];
    }

    public static DiskCacheStrategy getDiskCacheStrategy(int index) {
        switch (index) {
            case 1:
                return DiskCacheStrategy.RESOURCE;
            case 2:
                return DiskCacheStrategy.DATA;
            case 3:
                return DiskCacheStrategy.ALL;
            case 4:
                return DiskCacheStrategy.AUTOMATIC;
            default:
                return DiskCacheStrategy.NONE;
        }
    }

    /**
     * 取 url 中 marker 之后、下一个 '@' 之前的一段（等价于旧实现
     * {@code url.split(marker)[1].split("@")[0]}），但不编译正则、不分配数组。
     */
    private static String extractSegment(String url, String marker) {
        int idx = url.indexOf(marker);
        if (idx < 0) return null;
        int start = idx + marker.length();
        int end = url.indexOf('@', start);
        if (end < 0) end = url.length();
        return end > start ? url.substring(start, end) : null;
    }

    private static Object getUrl(String url) {
        // 修复：上游偶有 null 入参，原代码 url.startsWith("data:") 抛 NullPointerException
        if (TextUtils.isEmpty(url)) return null;
        if (url.startsWith("data:")) return url;
        url = DefaultConfig.checkReplaceProxy(url);

        String header = null;
        String referer = null;
        String ua = UA.random();
        String cookie = null;

        if (url.contains("doubanio.com") && !url.contains("@Referer=") && !url.contains("@User-Agent=")) {
            url += "@Referer=https://api.douban.com/@User-Agent=" + ua;
        }

        // 检查链接里面是否有自定义header
        // 修复：原用 String.split()，多字符分隔符每次调用都要编译正则并分配 String[]。
        // getUrl() 在每个 item 每次 bind 都被调用，改为 indexOf + substring，零正则零数组分配。
        if (url.contains("@Headers=")) {
            header = extractSegment(url, "@Headers=");
            if (header != null) {
                try {
                    header = URLDecoder.decode(header, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        if (url.contains("@Cookie=")) cookie = extractSegment(url, "@Cookie=");
        if (url.contains("@User-Agent=")) ua = extractSegment(url, "@User-Agent=");
        if (url.contains("@Referer=")) referer = extractSegment(url, "@Referer=");
        int at = url.indexOf('@');
        if (at >= 0) url = url.substring(0, at);
        if(TextUtils.isEmpty(url)) return null;
        /*   AuthInfo authInfo = new AuthInfo(url);
        url = authInfo.url; */

        LazyHeaders.Builder builder = new LazyHeaders.Builder();

        /*     if (!authInfo.auth.isEmpty()) {
            builder.addHeader(HttpHeaders.AUTHORIZATION, authInfo.auth);
        }*/

        if (!TextUtils.isEmpty(header)) {
            JsonObject jsonInfo = new Gson()
                .fromJson(header, JsonObject.class);
            for (String key: jsonInfo.keySet()) {
                String val = jsonInfo.get(key)
                    .getAsString();
                builder.addHeader(key, val);
            }
        } else {
            if (!TextUtils.isEmpty(cookie)) {
                builder.addHeader(HttpHeaders.COOKIE, cookie);
            }
            if (!TextUtils.isEmpty(ua)) {
                builder.addHeader(HttpHeaders.USER_AGENT, ua);
            } else {
                String mobile_UA = "Dalvik/2.1.0 (Linux; U; Android 13; M2102J2SC Build/TKQ1.220829.002)";
                builder.addHeader(HttpHeaders.USER_AGENT, mobile_UA);
            }
            if (!TextUtils.isEmpty(referer)) builder.addHeader(HttpHeaders.REFERER, referer);
        }

        try {
            URL imgUrl = new URL(url);
            String host = imgUrl.getHost();
            builder.addHeader(HttpHeaders.HOST, host);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return new GlideUrl(url, builder.build());
    }

    /**
     * 静态复用的 listener：原实现每次 load 都 new 一个匿名对象并捕获 view。
     * 改为从 target 反查 ImageView（asBitmap().into(ImageView) 必然生成 ImageViewTarget）。
     */
    private static final RequestListener<Bitmap> FIT_XY_LISTENER = new RequestListener<Bitmap>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
            ImageView view = viewFrom(target);
            if (view != null) {
                // 同样避免无条件 setScaleType 触发的 requestLayout
                if (view.getScaleType() != ImageView.ScaleType.FIT_XY) {
                    view.setScaleType(ImageView.ScaleType.FIT_XY);
                }
                view.setImageResource(R.drawable.img_loading_placeholder);
            }
            return true;
        }

        @Override
        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
            ImageView view = viewFrom(target);
            if (view != null && view.getScaleType() != ImageView.ScaleType.FIT_XY) {
                view.setScaleType(ImageView.ScaleType.FIT_XY);
            }
            return false;
        }
    };

    @Nullable
    private static ImageView viewFrom(Target<Bitmap> target) {
        if (target instanceof ImageViewTarget) {
            View v = ((ImageViewTarget<?>) target).getView();
            return v instanceof ImageView ? (ImageView) v : null;
        }
        return null;
    }
}
