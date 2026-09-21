
package com.github.catvod.crawler;

import android.content.Context;
import android.util.Log;


import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.MD5;
import com.lzy.okgo.OkGo;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import okhttp3.Response;

public class JarLoader {
    private final ConcurrentHashMap<String, DexClassLoader> classLoaders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> proxyMethods = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private volatile String recentJarKey = "";

    /**
     * 不要在主线程调用我
     *
     * @param cache
     */
    public boolean load(String cache) {
        recentJarKey = "main";
        return loadClassLoader(cache, recentJarKey);
    }

    public void clear() {
        spiders.clear();
        proxyMethods.clear();
        classLoaders.clear();
    }

    private boolean loadClassLoader(String jar, String key) {
        if (classLoaders.containsKey(key)){
            Log.i("JarLoader", "echo-loadClassLoader jar缓存: " + key);
            return true;
        }
        boolean success = false;
        try {
            File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_csp");
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            final DexClassLoader classLoader = new DexClassLoader(jar, cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            int count = 0;
            do {
                try {
                    final Class<?> classInit = classLoader.loadClass("com.github.catvod.spider.Init");
                    if (classInit != null) {
                        final Method initMethod = classInit.getMethod("init", Context.class);
                        // 在子线程中调用 init 方法，避免网络请求在主线程中执行
                        Thread initThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    initMethod.invoke(null, App.getInstance());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        initThread.start();
                        initThread.join();
                        Log.i("JarLoader", "echo-自定义爬虫代码加载成功!");
                        success = true;
                        try {
                            Class<?> proxy = classLoader.loadClass("com.github.catvod.spider.Proxy");
                            Method proxyMethod = proxy.getMethod("proxy", Map.class);
                            proxyMethods.put(key, proxyMethod);
                        } catch (Throwable th) {
                            // 可以记录错误日志
                            th.printStackTrace();
                        }
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                count++;
            } while (count < 2);

            if (success) {
                classLoaders.put(key, classLoader);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return success;
    }

    private DexClassLoader loadJarInternal(String jar, String md5, String key) {
        if (classLoaders.containsKey(key)){
            Log.i("JarLoader", "echo-loadJarInternal jar缓存: " + key);
            return classLoaders.get(key);
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp/" + key + ".jar");
        if (!md5.isEmpty()) {
            if (cache.exists() && MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                if(loadClassLoader(cache.getAbsolutePath(), key)){
                    return classLoaders.get(key);
                }else {
                    return null;
                }
            }
        }else {
            if (cache.exists() && !FileUtils.isWeekAgo(cache)) {
                if(loadClassLoader(cache.getAbsolutePath(), key)){
                    return classLoaders.get(key);
                }
            }
        }
        try (Response response = OkGo.<File>get(jar).execute()) {
            // release 构建中 assert 会被禁用，显式校验避免 NPE
            if (!response.isSuccessful() || response.body() == null) {
                cache.delete();
                return null;
            }
            try (InputStream is = response.body().byteStream();
                 OutputStream os = new FileOutputStream(cache)) {
                byte[] buffer = new byte[8192];
                int length;
                // != -1 比 > 0 更可靠，避免 length 恰好为 0 时丢失后续数据
                while ((length = is.read(buffer)) != -1) {
                    os.write(buffer, 0, length);
                }
            }
            loadClassLoader(cache.getAbsolutePath(), key);
            return classLoaders.get(key);
        } catch (Throwable e) {
            e.printStackTrace();
            // 下载失败时清理半成品，避免损坏文件残留
            cache.delete();
        }
        return null;
    }

    public Spider getSpider(String key, String cls, String ext, String jar) {
        if (spiders.containsKey(key)) {
            Log.i("JarLoader", "echo-getSpider spider缓存: " + key);
            return spiders.get(key);
        }
        String clsKey = cls.replace("csp_", "");
        // 仅允许字母数字下划线，防止加载任意类
        if (!clsKey.matches("[A-Za-z0-9_]+")) {
            Log.w("JarLoader", "echo-getSpider 非法的 clsKey: " + clsKey);
            return new SpiderNull();
        }
        String jarUrl = "";
        String jarMd5 = "";
        String jarKey;
        if (jar.isEmpty()) {
            jarKey = "main";
        } else {
            String[] urls = jar.split(";md5;");
            jarUrl = urls[0];
            jarKey = MD5.string2MD5(jarUrl);
            jarMd5 = urls.length > 1 ? urls[1].trim() : "";
        }
        recentJarKey = jarKey;
        // release 构建中 assert 会被禁用，使用显式校验
        if (jarKey == null) {
            Log.w("JarLoader", "echo-getSpider jarKey 为空，返回 SpiderNull");
            return new SpiderNull();
        }
        DexClassLoader classLoader = jarKey.equals("main")? classLoaders.get("main"):loadJarInternal(jarUrl, jarMd5, jarKey);
        if (classLoader == null) return new SpiderNull();
        try {
            Log.i("JarLoader", "echo-getSpider 加载spider: " + key);
            Spider sp = (Spider) classLoader.loadClass("com.github.catvod.spider." + clsKey).newInstance();
            sp.init(App.getInstance(), ext);
            if (!jar.isEmpty()) {
                sp.homeContent(false); // 增加此行 应该可以解决部分写的有问题源的历史记录问题 但会增加这个源的首次加载时间 不需要可以已删掉
            }
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return new SpiderNull();
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        try {
            DexClassLoader classLoader = classLoaders.get("main");
            String clsKey = "Json" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            assert classLoader != null;
            Class<?> jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, url);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        try {
            DexClassLoader classLoader = classLoaders.get("main");
            String clsKey = "Mix" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            assert classLoader != null;
            Class<?> jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, name, flag, url);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public Object[] proxyInvoke(Map<String,String> params) {
        try {
            Method proxyFun = proxyMethods.get(recentJarKey);
            if (proxyFun != null) {
                return (Object[]) proxyFun.invoke(null, params);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }
}
