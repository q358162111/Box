package com.github.tvbox.osc.util.parser;

import android.annotation.TargetApi;
import android.os.Build;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class Utils {

    // 修复：原正则 "http((?!http).){12,}?\\.(m3u8|mp4|...)\\?.*" 含嵌套否定环视 + 可变次数量词，
    // 在长 URL（含连续非 'h' 字符）上会指数级回溯，可能造成 ANR。
    // 改写：去掉环视，使用受控字符集 [\\w./:?=&%#~+-] 替代 ".+?" 配合后续锚点：
    public static final Pattern RULE = Pattern.compile(
            "http[s]?://[\\w.\\-]+(?:/|\\?)[^\\s\"'<>]*\\.(?:m3u8|mp4|flv|avi|mkv|rm|wmv|mpg|m4a|mp3)(?:[?#][^\\s\"'<>]*)?"
                    + "|http[s]?://[\\w.\\-]+(?:/|\\?)[^\\s\"'<>]*video/tos(?:[?#][^\\s\"'<>]*)?"
    );
    public static final String UaWinChrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";
    public static final String UaMobile = "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.1 Mobile/15E148 Safari/604.1";

    public static boolean isVip(String url) {
        List<String> hosts = Arrays.asList("iqiyi.com", "v.qq.com", "youku.com", "le.com", "tudou.com", "mgtv.com", "sohu.com", "acfun.cn", "bilibili.com", "baofeng.com", "pptv.com");
        for (String host : hosts) if (url.contains(host)) return true;
        return false;
    }

    public static boolean isVideoFormat(String url) {
        if (url.contains("url=http") || url.contains(".js") || url.contains(".css") || url.contains(".html")) return false;
        return RULE.matcher(url).find();
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) {
            return text.substring(0, text.length() - num);
        } else {
            return text;
        }
    }

    public static void loadUrl(WebView webView, String script) {
        loadUrl(webView, script, null);
    }

    public static void loadUrl(WebView webView, String script, ValueCallback<String> callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) webView.evaluateJavascript(script, callback);
        else webView.loadUrl(script);
    }



    public static boolean isBlackVodUrl(String input, String url) {
        if (url.contains("973973.xyz") || url.contains(".fit:"))
            return true;
        return false;
    }

    public static JSONObject fixJsonVodHeader(JSONObject headers, String input, String url) throws JSONException {
        if (headers == null)
            headers = new JSONObject();
        if (input.contains("www.mgtv.com")) {
            headers.put("Referer", " ");
            headers.put("User-Agent", " Mozilla/5.0");
        } else if (url.contains("titan.mgtv")) {
            headers.put("Referer", " ");
            headers.put("User-Agent", " Mozilla/5.0");
        } else if (input.contains("bilibili")) {
            headers.put("Referer", " https://www.bilibili.com/");
            headers.put("User-Agent", " " + UaWinChrome);
        }
        return headers;
    }

    public static JSONObject jsonParse(String input, String json) throws JSONException {
        if (json == null) return null;
        JSONObject jsonPlayData = new JSONObject(json);
        String url;
        if (jsonPlayData.has("data") && !jsonPlayData.isNull("data")) {
            // 防御：data 字段可能是 array/null/string 而非 object，统一用 optString 防御
            JSONObject dataObj = jsonPlayData.optJSONObject("data");
            if (dataObj == null) {
                url = jsonPlayData.optString("url", "");
            } else {
                url = dataObj.optString("url", "");
            }
        } else {
            url = jsonPlayData.optString("url", "");
        }
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        if (!url.startsWith("http")) {
            return null;
        }
        if (url.equals(input)) {
            if (isVip(url) || !isVideoFormat(url)) {
                return null;
            }
        }
        if (Utils.isBlackVodUrl(input, url)) {
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

        headers = Utils.fixJsonVodHeader(headers, input, url);
        JSONObject taskResult = new JSONObject();
        taskResult.put("header", headers);
        taskResult.put("url", url);
        return taskResult;
    }
}
