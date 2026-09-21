package com.github.catvod.net;

import android.net.Uri;

import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class ProxySelector extends java.net.ProxySelector {

    private volatile List<String> hosts;
    private volatile Proxy proxy;

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }

    public void setProxy(String proxy) {
        this.proxy = getProxy(proxy);
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (proxy == null || hosts == null || hosts.isEmpty() || uri.getHost() == null) return Collections.singletonList(Proxy.NO_PROXY);
        String host = uri.getHost();
        // 同时豁免 127.0.0.1、127.x.x.x、localhost、::1 等 loopback，避免本机服务无法访问
        if ("127.0.0.1".equals(host) || host.startsWith("127.") || "localhost".equalsIgnoreCase(host) || "::1".equals(host))
            return Collections.singletonList(Proxy.NO_PROXY);
        for (String h : hosts) if (Util.containOrMatch(host, h)) return Collections.singletonList(proxy);
        return Collections.singletonList(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
        // 输出日志便于代理问题定位
        android.util.Log.w("ProxySelector", "connect failed: " + uri, e);
    }

    private Proxy getProxy(String proxy) {
        Uri uri = Uri.parse(proxy);
        String userInfo = uri.getUserInfo();
        if (userInfo != null && userInfo.contains(":")) setAuthenticator(userInfo);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getPort() <= 0) return Proxy.NO_PROXY;
        if (uri.getScheme().startsWith("http")) return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        if (uri.getScheme().startsWith("socks")) return new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        return Proxy.NO_PROXY;
    }

    private void setAuthenticator(String userInfo) {
        String[] parts = userInfo.split(":", 2);
        if (parts.length != 2) return;
        final String user = parts[0];
        final char[] password = parts[1].toCharArray();
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                // 每次返回克隆数组，避免密码常驻内存且能被 GC 清理
                return new PasswordAuthentication(user, password.clone());
            }
        });
    }
}
