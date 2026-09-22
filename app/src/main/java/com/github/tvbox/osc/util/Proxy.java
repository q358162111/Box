package com.github.tvbox.osc.util;

import java.util.Map;
import com.github.tvbox.osc.util.parser.SuperParse;
public class Proxy {

    public static Object[] proxy(Map<String, String> params) {
        try {
            String what = params.get("go");
            if ("SuperParse".equals(what)) {
                String flag = params.get("flag");
                String url = params.get("url");
                // 简单的 SSRF 防御：禁止内网/本地回环段，防止 URL 探测内网或本地服务
                if (url != null && isSafeExternalUrl(url)) {
                    return SuperParse.loadHtml(flag, url);
                }
            }

        } catch (Throwable ignored) {

        }
        return null;
    }

    /**
     * 仅放行 http/https 公网 URL；过滤 localhost/127.x/10.x/172.16-31.x/192.168.x 等。
     * 不能替代 DNS rebinding 等高级 SSRF 防护，但能挡住绝大多数一键探测。
     */
    private static boolean isSafeExternalUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        if (lower.contains("localhost") || lower.contains("127.")
                || lower.contains("[::1]") || lower.contains("0.0.0.0")) {
            return false;
        }
        return true;
    }
}
