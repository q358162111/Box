// 文件: app/src/python/java/com/github/catvod/crawler/python/PyLoader.java
package com.github.catvod.crawler;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.github.catvod.crawler.python.IPyLoader;
import com.github.tvbox.osc.base.App;
import com.undcover.freedom.pyramid.PythonLoader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ConcurrentHashMap;

public class pyLoader implements IPyLoader {
    private final PythonLoader pythonLoader;
    private final ConcurrentHashMap<String, Spider> spiders;

    public pyLoader() {
        pythonLoader = PythonLoader.getInstance().setApplication(App.getInstance());
        spiders = new ConcurrentHashMap<>();
    }

    @Override
    public void clear() {
        // 切换配置是高频操作：只清 Map 会泄漏每个 Python 源的资源，必须逐个 destroy
        for (Spider spider : spiders.values()) {
            try {
                spider.cancelByTag();
                spider.destroy();
            } catch (Throwable ignored) {
            }
        }
        spiders.clear();
        // 联动清空 PythonLoader 内层缓存，避免配置切换后内层仍返回绑定旧配置的 Spider
        pythonLoader.clearSpiders();
    }

    @Override
    public Spider getSpider(String key, String cls, String ext) {
        if (spiders.containsKey(key)) {
            Log.i("PyLoader", "echo-getSpider spider缓存: " + key);
            return spiders.get(key);
        }
        try {
            if (ContextCompat.checkSelfPermission(App.getInstance(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.i("PyLoader", "无存储权限，终止执行");
                return new SpiderNull();
            }
            Log.i("PyLoader", "echo-getSpider url: " + getPyUrl(cls, ext));
            Spider sp = pythonLoader.getSpider(key, getPyUrl(cls, ext));
            spiders.put(key, sp);
            Log.i("PyLoader", "echo-getSpider 加载spider: " + key);
            return sp;
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return new SpiderNull();
    }

    private String getPyUrl(String api, String ext) throws UnsupportedEncodingException {
        StringBuilder urlBuilder = new StringBuilder(api);
        if (!ext.isEmpty()) {
            urlBuilder.append(api.contains("?") ? "&" : "?").append("extend=").append(ext);
        }
        return urlBuilder.toString();
    }
}
