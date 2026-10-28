package com.github.catvod.net;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.github.tvbox.osc.bean.Doh;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.github.tvbox.osc.util.OkGoHelper;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.CacheControl;
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class OkHttp {

    private static final int TIMEOUT = 30 * 1000;
    private static final int CACHE = 100 * 1024 * 1024;

    // 懒初始化字段可能被多线程读写，volatile 防止半初始化实例可见
    private volatile DnsOverHttps dns;
    private volatile OkHttpClient client;
    private volatile ProxySelector selector;

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    public static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static Dns dns() {
        if (get().dns != null) return get().dns;
        if (OkGoHelper.dnsOverHttps != null) return OkGoHelper.dnsOverHttps;
        return Dns.SYSTEM;
    }

    public void setDoh(Doh doh) {
        OkHttpClient dohClient = new OkHttpClient.Builder().cache(new Cache(Path.doh(), CACHE)).hostnameVerifier(SSLCompat.VERIFIER).sslSocketFactory(new SSLCompat(), SSLCompat.TM).build();
        dns = doh.getUrl().isEmpty() ? null : new DnsOverHttps.Builder().client(dohClient).url(HttpUrl.get(doh.getUrl())).bootstrapDnsHosts(doh.getHosts()).build();
        client = null;
    }

    public void setProxy(String proxy) {
        ProxySelector.setDefault(selector());
        selector().setProxy(proxy);
        client = null;
    }

    public static ProxySelector selector() {
        if (get().selector != null) return get().selector;
        return get().selector = new ProxySelector();
    }

    public static OkHttpClient client() {
        if (get().client != null) return get().client;
        return get().client = getBuilder().build();
    }

    public static OkHttpClient client(int timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient noRedirect(int timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();
    }

    public static OkHttpClient client(boolean redirect, int timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    public static String string(String url) {
        if (url == null || !url.startsWith("http")) return "";
        // 注意：该方法同步阻塞网络，必须在工作线程调用，禁止在主线程调用以免触发 NetworkOnMainThreadException
        try (Response response = newCall(url).execute()) {
            ResponseBody body = response.body();
            return body != null ? body.string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static Call newCall(String url) {
        Uri uri = Uri.parse(url);
        if (uri.getUserInfo() != null) return newCall(url, Headers.of(HttpHeaders.AUTHORIZATION, Util.basic(uri)));
        return client().newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url, Headers headers) {
        return client.newCall(new Request.Builder().url(url).headers(headers).build());
    }

    public static Call newCall(String url, Headers headers) {
        return client().newCall(new Request.Builder().url(url).headers(headers).build());
    }

    public static Call newCall(String url, Headers headers, ArrayMap<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(headers).build());
    }

    public static Call newCall(String url, Headers headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(headers).post(body).build());
    }

    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    public static FormBody toBody(ArrayMap<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) body.add(entry.getKey(), entry.getValue());
        return body.build();
    }

    private static HttpUrl buildUrl(String url, ArrayMap<String, String> params) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) builder.addQueryParameter(entry.getKey(), entry.getValue());
        return builder.build();
    }

    private static OkHttpClient.Builder getBuilder() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(new OkhttpInterceptor()).connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS).readTimeout(TIMEOUT, TimeUnit.MILLISECONDS).writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS).dns(dns()).hostnameVerifier(SSLCompat.VERIFIER).sslSocketFactory(new SSLCompat(), SSLCompat.TM);
        builder.proxySelector(selector());
        // 默认连接池仅 5 个空闲连接，多源并发搜索时连接被反复创建/回收；TV 端多源场景放大为 16
        builder.connectionPool(new ConnectionPool(16, 5, TimeUnit.MINUTES));
        try {
            builder.cache(new Cache(Path.cache("okhttp"), CACHE));
        } catch (Throwable e) {
            // 缓存目录不可用时降级为无缓存，不影响正常请求
            e.printStackTrace();
        }
        // 用 network interceptor 而非 application interceptor：它运行在 CacheInterceptor 之后，
        // 改写响应头才能真正影响写入磁盘缓存的决策
        builder.addNetworkInterceptor(new CachePolicyInterceptor());
        return builder;
    }

    /**
     * 只对服务端明确声明可缓存的响应启用磁盘缓存。
     * 爬虫接口多返回动态 JSON 且不带任何缓存头，若沿用 OkHttp 的默认启发式缓存，
     * 会让列表/详情/搜索结果返回陈旧数据；因此未声明缓存策略的一律标记 no-store，行为与加缓存前一致。
     */
    public static class CachePolicyInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            CacheControl control = response.cacheControl();
            if (control.noStore() || control.maxAgeSeconds() > 0 || response.header("Expires") != null) {
                return response;
            }
            return response.newBuilder()
                    .removeHeader("Cache-Control")
                    .header("Cache-Control", "no-store")
                    .build();
        }
    }
}
