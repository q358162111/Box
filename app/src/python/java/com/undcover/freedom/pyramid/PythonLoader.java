package com.undcover.freedom.pyramid;

import android.app.Application;
import android.content.Context;
import android.util.Base64;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.urlhttp.OKCallBack;
import com.github.tvbox.osc.util.urlhttp.OkHttpUtil;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Call;
import okhttp3.Response;

public class PythonLoader {
    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private static volatile PythonLoader sInstance;
    private Application app;
    // 共享的 Spider 初始化线程池:避免每次 getSpider 创建/销毁一次性线程池
    private volatile ExecutorService spiderInitExecutor = newSpiderInitExecutor();

    private static ExecutorService newSpiderInitExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "spider-init");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * init 超时后单线程池被僵尸任务(无法中断的 Python 调用)永久占住，
     * 后续所有 Python 源的 init 都会排队超时且永不恢复；
     * 只能 shutdownNow 并重建线程池恢复服务。
     */
    private synchronized void rebuildSpiderInitExecutor() {
        ExecutorService old = spiderInitExecutor;
        spiderInitExecutor = newSpiderInitExecutor();
        old.shutdownNow();
    }
    Python pyInstance;
    PyObject pyApp;
    Python.Platform androidPlatform;

    public PythonLoader() {
    }

    public static PythonLoader getInstance() {
        if (sInstance == null) {
            synchronized (PythonLoader.class) {
                if (sInstance == null) {
                    sInstance = new PythonLoader();
                }
            }
        }
        return sInstance;
    }

    private void setSdk(Context context) {
        int logLevel = PyLog.LEVEL_V;
        PyLog.getInstance().setLogLevel(logLevel).setFilter(PyLog.FILTER_NW | PyLog.FILTER_LC);
        PyLog.TagConstant.TAG_APP = "PythonLoader";

        PyToast.init(context);
    }

    /**
     * 清空内层 Spider 缓存（绑定旧配置的 PythonSpider 持有 PyObject，配置切换后必须清理）。
     * 供 pyLoader.clear() 联动调用，避免只清外层缓存后内层仍返回旧 Spider。
     */
    public void clearSpiders() {
        spiders.clear();
    }

    public PythonLoader setApplication(Application app) {
        this.app = app;
        setSdk(this.app);
        if (pyInstance == null) {
            if (!Python.isStarted()) {
                androidPlatform = new AndroidPlatform(app);
                Python.start(androidPlatform);
            }
            pyInstance = Python.getInstance();
            pyApp = pyInstance.getModule("app");
        }
        // 默认用应用私有外部目录,兼容 Android 10+ scoped storage;
        // setPluginConfig 仍可显式覆盖,但必须位于应用私有目录内(见 setPluginConfig)
        this.cache = new File(app.getExternalFilesDir(null), "plugin") + File.separator;
        return this;
    }

    // 插件缓存根目录:仅允许应用私有目录(setApplication 时初始化,不再使用共享外部存储默认值,
    // 避免共享路径上的 *.py 被第三方 App 篡改后被 loadFromDisk 直接执行)
    String cache;

    public PythonLoader setPluginConfig(String config) {
        // 安全校验:插件缓存目录必须位于应用私有目录内。共享存储上的 *.py 可被第三方
        // App 篡改,loadFromDisk 会直接执行被篡改的代码(任意代码执行面)
        if (config == null || config.isEmpty()) {
            PyLog.e("setPluginConfig: 路径为空，保留默认私有目录");
            return this;
        }
        if (app != null) {
            File privateRoot = app.getExternalFilesDir(null);
            File privateRootInternal = app.getFilesDir();
            try {
                String canonical = new File(config).getCanonicalPath();
                // 用带分隔符的前缀比较:裸 startsWith 会放行同级兄弟目录
                // (如 .../files_evil/、.../files2/),绕过私有目录限制
                boolean insideExternal = privateRoot != null && canonical.startsWith(privateRoot.getCanonicalPath() + File.separator);
                boolean insideInternal = privateRootInternal != null && canonical.startsWith(privateRootInternal.getCanonicalPath() + File.separator);
                if (!insideExternal && !insideInternal) {
                    PyLog.e("setPluginConfig: 拒绝非应用私有目录，保留默认私有目录");
                    return this;
                }
            } catch (Exception e) {
                PyLog.e("setPluginConfig: 路径解析失败，保留默认私有目录");
                return this;
            }
        }
        this.cache = config;
        return this;
    }

    public Spider getSpider(String key, String url) throws Exception {
        if (app == null) throw new Exception("set application first");
        if (spiders.containsKey(key)) {
            PyLog.d(key + " :缓存加载成功！");
            return spiders.get(key);
        }

        Future<?> future = null;
        try {
            PythonSpider sp = new PythonSpider(key, cache);

            // 在共享线程池中执行初始化,避免每次创建/销毁一次性线程池
            future = spiderInitExecutor.submit(() -> {
                try {
                    sp.init(app, url);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // 等待线程完成，最多10秒
            future.get(10, TimeUnit.SECONDS);

            // 初始化失败（插件下载失败/依赖缺失）的 Spider 不入缓存，避免该站点永久失效
            if (sp.loadSuccess) {
                spiders.put(key, sp);
                return sp;
            }
            PyLog.e(key + " :插件初始化失败，返回空 Spider");
        } catch (TimeoutException e) {
            PyLog.e("echo-init方法执行超时");
            // 超时后 cancel 任务；若线程仍被不可中断的 Python 调用占住，
            // 重建线程池恢复服务，否则后续所有 Python 源的 init 都会排队超时且永不恢复
            if (future != null) {
                future.cancel(true);
            }
            rebuildSpiderInitExecutor();
        } catch (ExecutionException | InterruptedException e) {
            PyLog.e("echo-init:ExecutionException|InterruptedException");
            if (future != null) {
                future.cancel(true);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return new SpiderNull();
    }

    public Map<String, String> str2map(String header) {
        Map<String, String> map = new HashMap<>();
        if (header == null || header.isEmpty())
            return map;
        try {
            JSONObject jo = new JSONObject(header);
            for (Iterator<String> it = jo.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = jo.optString(key);
                map.put(key, value);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return map;
    }

    public InputStream getFileStream(String url, String param, String header) {
        if (streamCallback != null) {
            return streamCallback.get(url, str2map(param), str2map(header));
        } else {
            OKCallBack.OKCallBackDefault callBack = new OKCallBack.OKCallBackDefault() {
                @Override
                protected void onFailure(Call call, Exception e) {

                }

                @Override
                protected void onResponse(Response response) {

                }
            };
            OkHttpUtil.get(OkGoHelper.getDefaultClient(), url, str2map(param), str2map(header), callBack);
            Response response = callBack.getResult();
            if (response == null || response.body() == null) {
                return new ByteArrayInputStream(new byte[0]);
            }
            // 注意：调用方负责关闭返回值，这里取走 InputStream 后调用方用完需自行关闭 Response
            // byteStream() 会持有 Response，调用方不可多次读取 InputStream
            return response.body().byteStream();
        }
    }

    public String getFileString(String url, String header) {
        if (stringCallback != null) {
            return stringCallback.get(url, str2map(header));
        } else {
            return OkHttpUtil.string(url, str2map(header));
        }
    }

    FileStreamCallback streamCallback;
    FileStringCallback stringCallback;

    public PythonLoader setFileStreamCallback(FileStreamCallback callback) {
        streamCallback = callback;
        return this;
    }

    public PythonLoader setFileStringCallback(FileStringCallback callback) {
        stringCallback = callback;
        return this;
    }

    public interface FileStreamCallback {
        InputStream get(String url, Map<String, String> paramsMap, Map<String, String> headerMap);
    }

    public interface FileStringCallback {
        String get(String url, Map<String, String> headerMap);
    }
}
