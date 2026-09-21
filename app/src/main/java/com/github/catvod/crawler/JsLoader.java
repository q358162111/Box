package com.github.catvod.crawler;


import android.util.Log;
import com.github.tvbox.osc.base.App;

import com.github.tvbox.osc.util.FileUtils;

import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;

import com.github.tvbox.osc.util.js.JsSpider;
import com.lzy.okgo.OkGo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import okhttp3.Response;

public class JsLoader {
    //private static ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    //private static ConcurrentHashMap<String, Class<?>> classs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Class<?>> classes = new ConcurrentHashMap<>();
    //当前的Js爬虫key
    private volatile String recentKey = "";

    //public static void load()
    public static void destroy() {
        for (Spider spider : spiders.values()) {
            spider.cancelByTag();
            spider.destroy();
        }
    }
    public void clear() {
        spiders.clear();
        //classs.clear();
        classes.clear();
    }

    public static void stopAll() {
        for (Spider spider : spiders.values()){
            spider.cancelByTag();
        }
    }

    private boolean loadClassLoader(String jar, String key) {
        boolean success = false;
        Class<?> classInit = null;
        try {
            File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_jsapi");
            if (!cacheDir.exists())
                cacheDir.mkdirs();
            DexClassLoader classLoader = new DexClassLoader(jar, cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            // make force wait here, some device async dex load
            int count = 0;
            do {
                try {
                    classInit = classLoader.loadClass("com.github.catvod.js.Method");
                    if (classInit != null) {
                        //System.out.println("自定义jsapi加载成功!");
                        Log.i("JSLoader", "echo-自定义jsapi代码加载成功!");
                        success = true;
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                count++;
            } while (count < 5);

            if (success) {
                //classs.put(key, classInit);
                classes.put(key, classInit);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return success;
    }

    private Class<?> loadJarInternal(String jar, String md5, String key) {
        //if (classs.contains(key))
          //  return classs.get(key);
        //File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + key + ".jar");
        if (classes.containsKey(key)){
            Log.i("JSLoader", "echo-loadJarInternal cached");
            return classes.get(key);
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp/" + key + ".jar");

        if (!md5.isEmpty()) {
            if (cache.exists() && MD5.getFileMd5(cache).equalsIgnoreCase(md5)) {
                loadClassLoader(cache.getAbsolutePath(), key);
                //return classs.get(key);
                return classes.get(key);
            }
        }else {
            if (cache.exists() && !FileUtils.isWeekAgo(cache)) {
                if(loadClassLoader(cache.getAbsolutePath(), key)){
                    return classes.get(key);
                }
            }
        }
        try (Response response = OkGo.<File>get(jar).execute()) {
            // 校验 HTTP 状态码，避免把 404/500 错误页写入缓存损坏 jar
            if (!response.isSuccessful() || response.body() == null) {
                cache.delete();
                return classes.get(key);
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
            //return classs.get(key);
            return classes.get(key);
        } catch (Throwable e) {
            e.printStackTrace();
            // 下载失败时清理半成品，避免损坏文件残留缓存
            cache.delete();
        }
        return null;
    }


    public Spider getSpider(String key, String api, String ext, String jar) {
        if (spiders.containsKey(key)){
            Log.i("JSLoader", "echo-getSpider cached");
            return spiders.get(key);
        }
        Class<?> classLoader = null;
        if (!jar.isEmpty()) {
            String[] urls = jar.split(";md5;");
            String jarUrl = urls[0];
            String jarKey = MD5.string2MD5(jarUrl);
            String jarMd5 = urls.length > 1 ? urls[1].trim() : "";
            classLoader = loadJarInternal(jarUrl, jarMd5, jarKey);
        }
        //recentJarKey = key;
        //if (spiders.containsKey(key))
        //    return spiders.get(key);
        recentKey = key;
        try {
            Log.i("JSLoader", "echo-getSpider load");
            Spider sp = new JsSpider(key, api, classLoader);
            sp.init(App.getInstance(), ext);
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
            LOG.e("QuJS", th);
        }
        return new SpiderNull();
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            //Spider proxyFun = spiders.get(recentJarKey);
            Spider proxyFun = spiders.get(recentKey);
            if (proxyFun != null) {
                return proxyFun.proxyLocal(params);
            }
        } catch (Throwable th) {
            LOG.e("proxyInvoke", th);
        }
        return null;
    }
}
