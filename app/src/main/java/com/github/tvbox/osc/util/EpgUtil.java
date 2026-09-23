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
import java.util.HashMap;

public class EpgUtil {

    private static JsonObject epgDoc = null;
    private static HashMap<String, JsonObject> epgHashMap = new HashMap<>();

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
        try {
            AssetManager assetManager = App.getInstance().getAssets(); //获得assets资源管理器（assets中的文件无法直接访问，可以使用AssetManager访问）
            InputStreamReader inputStreamReader = new InputStreamReader(assetManager.open("epg_data.json"),"UTF-8"); //使用IO流读取json文件内容
            BufferedReader br = new BufferedReader(inputStreamReader);//使用字符高效流
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = br.readLine()) != null) {
                builder.append(line);
            }
            br.close();
            inputStreamReader.close();
            if (!builder.toString().isEmpty()) {
                epgDoc = new Gson().fromJson(builder.toString(), (Type) JsonObject.class);// 从builder中读取了json中的数据。
                for (JsonElement opt : epgDoc.get("epgs").getAsJsonArray()) {
                    JsonObject obj = (JsonObject) opt;
                    String name = obj.get("name").getAsString().trim();
                    String[] names = name.split(",");
                    for (String string : names) {
                        epgHashMap.put(string, obj);
                    }
                }
            }

        } catch (IOException e) {
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