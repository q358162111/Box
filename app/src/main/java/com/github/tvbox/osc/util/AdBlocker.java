package com.github.tvbox.osc.util;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AdBlocker {
    // 后台线程填充、WebView IO 线程并发读取，使用 COW 列表保证线程安全
    private static final List<String> AD_HOSTS = new CopyOnWriteArrayList<>();

    public static void clear() {
        AD_HOSTS.clear();
    }

    public static boolean isEmpty() {
        return AD_HOSTS.isEmpty();
    }

    public static void addAdHost(String host) {
        AD_HOSTS.add(host);
    }
    public static boolean hasHost(String host) {
        return AD_HOSTS.contains(host);
    }
    public static boolean isAd(String url) {
        if (url == null) return false;
        String lowered = url.toLowerCase();
        for (String adHost : AD_HOSTS) {
            if (adHost == null) continue;
            // 防御：原 substring match 在非精确域名匹配下易误判，
            // 改为精确匹配或 host/path 边界匹配（"//<host>/" / ".<host>"）
            int idx = lowered.indexOf(adHost);
            if (idx < 0) continue;
            // 已被 adHost 的子串吞掉（如 "abc.example.com" 命中 "example.com"）— 域名层级允许
            // 仅当 adHost 紧跟 "//" 或 "/." 或 ":" 时视为真命中；否则视为子域，可能放过
            int realIdx = lowered.indexOf("//" + adHost);
            if (realIdx < 0) realIdx = lowered.indexOf("." + adHost);
            if (realIdx < 0) realIdx = lowered.indexOf(":" + adHost);
            if (realIdx >= 0) return true;
        }
        return false;
    }

    public static WebResourceResponse createEmptyResource() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
    }

}
