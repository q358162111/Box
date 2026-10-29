package com.github.tvbox.osc.util;

import android.content.res.AssetManager;
import android.net.Uri;

import com.github.tvbox.osc.base.App;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EpgUtil {

    private static volatile JsonObject epgDoc = null;
    // init 在后台线程写、getEpgInfo 在主线程读，必须用并发容器避免半填充结构
    private static final Map<String, JsonObject> epgHashMap = new ConcurrentHashMap<>();

    /**
     * 台标 URL 模板，由 lives[].logo 配置注入。
     * 含 {name} 占位符时按频道名替换；为空时回退到内置 assets/epg_data.json。
     * 支持不带占位符的固定 URL（适用于单一频道场景）。
     */
    private static volatile String logoUrlTemplate = "";

    public static void init() {
        if (epgDoc != null)
            return;

        //credit by 龍
        // 原实现只 catch IOException：epg_data.json 缺 epgs 字段 / 元素非 object / name 缺失
        // 会抛 NPE/IllegalStateException 直接逃逸；且读取异常时两个流都不关闭。
        // 这里改 try-with-resources + catch Throwable + 逐字段 opt 防御
        try (InputStreamReader inputStreamReader = new InputStreamReader(
                     App.getInstance().getAssets().open("epg_data.json"), "UTF-8");
             BufferedReader br = new BufferedReader(inputStreamReader)) {
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = br.readLine()) != null) {
                builder.append(line);
            }
            if (builder.length() > 0) {
                epgDoc = new Gson().fromJson(builder.toString(), (Type) JsonObject.class);// 从builder中读取了json中的数据。
                if (epgDoc != null && epgDoc.has("epgs") && epgDoc.get("epgs").isJsonArray()) {
                    for (JsonElement opt : epgDoc.getAsJsonArray("epgs")) {
                        if (opt == null || !opt.isJsonObject()) continue;
                        JsonObject obj = opt.getAsJsonObject();
                        if (!obj.has("name") || obj.get("name").isJsonNull()) continue;
                        String name = obj.get("name").getAsString().trim();
                        if (name.isEmpty()) continue;
                        String[] names = name.split(",");
                        for (String string : names) {
                            epgHashMap.put(string, obj);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 由 ApiConfig 在解析 lives[].logo 时调用，覆盖默认台标获取方式。
     * 设为 null / 空 将回退到内置 epg_data.json。
     */
    public static void setLogoUrlTemplate(String template) {
        logoUrlTemplate = template == null ? "" : template.trim();
    }

    public static String getLogoUrlTemplate() {
        return logoUrlTemplate;
    }

    public static String[] getEpgInfo(String channelName) {
        try {
            String logoUrl = resolveLogoUrl(channelName);
            String epgId = resolveEpgId(channelName);
            // 至少 logo 或 epgid 任一能解析到才算命中
            if (logoUrl != null || epgId != null) {
                return new String[]{
                        logoUrl == null ? "" : logoUrl,
                        epgId == null ? "" : epgId
                };
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * 优先按 lives[].logo 模板生成台标 URL；模板为空时回退到内置 epg_data.json。
     * 频道名经 Uri 编码以安全处理中文/特殊字符。
     */
    private static String resolveLogoUrl(String channelName) {
        if (channelName == null || channelName.isEmpty()) return null;
        String template = logoUrlTemplate;
        if (!template.isEmpty()) {
            if (template.contains("{name}")) {
                return template.replace("{name}", Uri.encode(channelName));
            }
            // 模板无占位符，作为固定 URL 返回（适用于单频道 logo 场景）
            return template;
        }
        JsonObject obj = epgHashMap.get(channelName);
        if (obj != null && obj.has("logo") && !obj.get("logo").isJsonNull()) {
            return obj.get("logo").getAsString();
        }
        return null;
    }

    private static String resolveEpgId(String channelName) {
        JsonObject obj = epgHashMap.get(channelName);
        if (obj != null && obj.has("epgid") && !obj.get("epgid").isJsonNull()) {
            return obj.get("epgid").getAsString();
        }
        return null;
    }
}