package com.github.tvbox.osc.util;

import java.net.InetAddress;
import java.net.URL;
import java.util.Map;
import android.util.Log;
import com.github.tvbox.osc.util.parser.SuperParse;

public class Proxy {

    public static Object[] proxy(Map<String, String> params) {
        try {
            String what = params.get("go");
            if ("SuperParse".equals(what)) {
                String flag = params.get("flag");
                String url = params.get("url");
                // SSRF 防御：禁止内网/本地回环/链路本地段，防止 URL 探测内网、本地服务或云元数据
                if (url != null && isSafeExternalUrl(url)) {
                    return SuperParse.loadHtml(flag, url);
                }
            }

        } catch (Throwable ignored) {
            // 解析/校验失败时静默降级，但保留日志便于定位 cat_ext 配置问题
            Log.e("Proxy", "proxy 解析失败", ignored);
        }
        return null;
    }

    /**
     * 校验 URL 是否指向公网地址。
     * 仅放行 http/https；解析 host 后拦截回环(127/8, ::1)、私网(10/8, 172.16/12, 192.168/16, fc00::/7)、
     * 链路本地(169.254/16, fe80::/10)、任意(0.0.0.0, ::) 等地址。
     * 域名会解析一次后按 IP 判断；无法解析时 fail-closed（拒绝）。
     * 注意：不能替代 DNS rebinding 等高级 SSRF 防护，但能挡住绝大多数一键探测。
     */
    public static boolean isSafeExternalUrl(String url) {
        if (url == null) return false;
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        try {
            URL parsed = new URL(trimmed);
            String host = parsed.getHost();
            if (host == null || host.isEmpty()) return false;
            if ("localhost".equals(host)) return false;
            InetAddress address = InetAddress.getByName(host);
            return !isBlockedAddress(address);
        } catch (Exception e) {
            // 非法 URL 或无法解析 host：fail-closed
            return false;
        }
    }

    /**
     * 判断地址是否属于应拦截的回环 / 私网 / 链路本地 / 任意段。
     * 供 RemoteServer 代理分支复用，统一 {@code do=} 与 {@code go=} 的过滤口径。
     */
    public static boolean isBlockedAddress(InetAddress address) {
        if (address == null) return true;
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress();
    }

    /**
     * 供 RemoteServer 的 {@code do=} 代理分支复用：判断客户端提供的 url 是否放行。
     *
     * @param url          待校验的 URL（可为 null/空，表示该请求不携带目标 url）
     * @param fromLoopback 请求是否来自回环（应用自身）。为 true 时放行——
     *                     应用内部 JS/Python spider 网络栈需要经本代理回环访问自身端点。
     */
    public static boolean isAllowedProxyUrl(String url, boolean fromLoopback) {
        if (fromLoopback) return true;
        if (url == null || url.isEmpty()) return true;
        return isSafeExternalUrl(url);
    }
}
