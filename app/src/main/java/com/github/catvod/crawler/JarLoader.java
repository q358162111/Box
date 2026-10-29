
package com.github.catvod.crawler;

import android.content.Context;
import android.util.Log;
import com.github.tvbox.osc.util.LOG;


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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import dalvik.system.DexClassLoader;
import okhttp3.Response;

public class JarLoader {
    // 缓存上限：DexClassLoader 一旦加载就无法卸载，Spider 实例也常持有网络资源。
    // 原本两个 Map 都是无上限的 ConcurrentHashMap，多 jar 源反复切换会持续泄漏 native 内存。
    // 改为 accessOrder 的 LRU，超限时淘汰最久未使用的条目。
    private static final int MAX_LOADED_JARS = 12;
    private static final int MAX_SPIDERS = 64;

    private final Map<String, DexClassLoader> classLoaders = Collections.synchronizedMap(
            new LinkedHashMap<String, DexClassLoader>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, DexClassLoader> eldest) {
                    return size() > MAX_LOADED_JARS;
                }
            });
    private final ConcurrentHashMap<String, Method> proxyMethods = new ConcurrentHashMap<>();
    private final Map<String, Spider> spiders = Collections.synchronizedMap(
            new LinkedHashMap<String, Spider>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Spider> eldest) {
                    if (size() <= MAX_SPIDERS) return false;
                    // 淘汰前必须 destroy，否则 Spider 持有的连接/线程随引用一起泄漏
                    try {
                        eldest.getValue().cancelByTag();
                        eldest.getValue().destroy();
                    } catch (Throwable ignored) {
                    }
                    return true;
                }
            });
    // siteKey -> jarKey 映射，供 proxyInvoke 按 params 中的 siteKey 精确路由，避免多源并发时按「最后加载的源」串数据
    private final ConcurrentHashMap<String, String> siteJarKeys = new ConcurrentHashMap<>();
    // getSpider 的 check-then-act 竞态防护锁
    private final Object spiderLoadLock = new Object();
    // 每个 jarKey 一把下载锁：不同 jar 的下载可并行，同一 jar 仍串行避免重复下载。
    // 修复：此前只有一把全局 spiderLoadLock，而 loadJarInternal 内部是同步网络下载，
    // 导致多源首次加载时所有 jar 下载被完全串行化，叠加 SourceViewModel 的 15s/20s 超时
    // 会表现为「多源首页集体加载失败」。
    private final ConcurrentHashMap<String, Object> jarLoadLocks = new ConcurrentHashMap<>();
    private volatile String recentJarKey = "";
    // 首页预热专用：原实现在 spiderLoadLock 内同步发起完整网络请求，多源并发加载时全部串行阻塞
    private static final ExecutorService warmUpExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "spider-warmup");
            t.setDaemon(true);
            return t;
        }
    });

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
        // 修复：必须与 createSpider 的发布（spiders.put）互斥。
        // 此前 clear() 不持有 spiderLoadLock，切配置时正在 createSpider 的线程仍会在
        // clear 之后把新 spider 塞回 map —— 该实例永远不会被 destroy，DexClassLoader 泄漏。
        synchronized (spiderLoadLock) {
            // 切换配置是高频操作：只清 Map 会导致每个 jar 源泄漏，必须逐个 destroy
            // spiders 是 synchronizedMap，迭代期间必须持有其自身锁，否则并发 put 会触发 ConcurrentModificationException
            synchronized (spiders) {
                for (Spider spider : spiders.values()) {
                    try {
                        spider.cancelByTag();
                        spider.destroy();
                    } catch (Throwable ignored) {
                    }
                }
                spiders.clear();
            }
            proxyMethods.clear();
            synchronized (classLoaders) {
                classLoaders.clear();
            }
            siteJarKeys.clear();
            jarLoadLocks.clear();
        }
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
                        // 原实现起子线程后立即 join()，是假异步：调用方（getSpider/spiderCallExecutor）
                        // 本就运行在后台线程，直接在当前线程执行即可，行为等价、语义诚实
                        try {
                            initMethod.invoke(null, App.getInstance());
                        } catch (Throwable e) {
                            LOG.e("JarLoader spider init " + key, e);
                        }
                        Log.i("JarLoader", "echo-自定义爬虫代码加载成功!");
                        success = true;
                        try {
                            Class<?> proxy = classLoader.loadClass("com.github.catvod.spider.Proxy");
                            Method proxyMethod = proxy.getMethod("proxy", Map.class);
                            proxyMethods.put(key, proxyMethod);
                        } catch (Throwable th) {
                            LOG.e("JarLoader proxy class " + key, th);
                        }
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    LOG.e("JarLoader spider class load", th);
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
            LOG.e("JarLoader loadJarInternal " + key, e);
            // 下载失败时清理半成品，避免损坏文件残留
            cache.delete();
        }
        return null;
    }

    public Spider getSpider(String key, String cls, String ext, String jar) {
        Spider cached = spiders.get(key);
        if (cached != null) {
            Log.i("JarLoader", "echo-getSpider spider缓存: " + key);
            return cached;
        }
        // 先在 per-jar 锁内完成可能耗时数秒的 jar 下载/校验；
        // 同一 jar 的并发请求在此串行（只下载一次），不同 jar 互不阻塞。
        String jarKey = resolveJarKey(jar);
        if (jarKey != null && !"main".equals(jarKey)) {
            Object jarLock = jarLoadLocks.computeIfAbsent(jarKey, k -> new Object());
            synchronized (jarLock) {
                if (!classLoaders.containsKey(jarKey)) {
                    String[] urls = jar.split(";md5;");
                    loadJarInternal(urls[0], urls.length > 1 ? urls[1].trim() : "", jarKey);
                }
            }
        }
        // check-then-put 竞态防护：多源并发首次加载同一 key 时只创建一个实例
        synchronized (spiderLoadLock) {
            Spider doubleChecked = spiders.get(key);
            if (doubleChecked != null) return doubleChecked;
            return createSpider(key, cls, ext, jar);
        }
    }

    /** 由 jar 字段推导 jarKey（纯字符串运算，不涉及网络）。 */
    private String resolveJarKey(String jar) {
        if (jar == null || jar.isEmpty()) return "main";
        String[] urls = jar.split(";md5;");
        return MD5.string2MD5(urls[0]);
    }

    private Spider createSpider(String key, String cls, String ext, String jar) {
        String clsKey = cls.replace("csp_", "");
        // 仅允许字母数字下划线，防止加载任意类
        if (!clsKey.matches("[A-Za-z0-9_]+")) {
            Log.w("JarLoader", "echo-getSpider 非法的 clsKey: " + clsKey);
            return new SpiderNull();
        }
        // 与 getSpider 的 resolveJarKey 保持同一推导规则（下载已在 per-jar 锁内完成）
        String jarKey = resolveJarKey(jar);
        recentJarKey = jarKey;
        // 判空必须早于 put：ConcurrentHashMap 不允许 null 值，否则这里先抛 NPE 使下方防守成为死代码
        if (jarKey == null) {
            Log.w("JarLoader", "echo-getSpider jarKey 为空，返回 SpiderNull");
            return new SpiderNull();
        }
        // 记录 siteKey -> jarKey 路由，proxyInvoke 用它精确定位该源的 proxy 方法
        siteJarKeys.put(key, jarKey);
        DexClassLoader classLoader;
        if (jarKey.equals("main")) {
            classLoader = classLoaders.get("main");
        } else if (classLoaders.containsKey(jarKey)) {
            // getSpider 已在 per-jar 锁内完成下载/校验，此处直接取缓存
            classLoader = classLoaders.get(jarKey);
        } else {
            // 预加载阶段已尝试且失败。这里不能调用 loadJarInternal 重试 ——
            // 其内部是同步网络下载，会把所有并发请求重新串到全局锁上，并造成重复下载。
            Log.w("JarLoader", "echo-getSpider jar 加载失败，跳过重试: " + jarKey);
            return new SpiderNull();
        }
        if (classLoader == null) return new SpiderNull();
        try {
            Log.i("JarLoader", "echo-getSpider 加载spider: " + key);
            final Spider sp = (Spider) classLoader.loadClass("com.github.catvod.spider." + clsKey).newInstance();
            sp.init(App.getInstance(), ext);
            if (!jar.isEmpty()) {
                // 预热该源首页：可解决部分写的有问题源的历史记录问题（不需要可整块删掉）。
                // 原实现直接在持有 spiderLoadLock 时同步发起一次完整网络请求，
                // 多源并发加载时被串行阻塞，首次加载耗时成倍放大；改为后台异步预热。
                warmUpExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sp.homeContent(false);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
            spiders.put(key, sp);
            return sp;
        } catch (Throwable th) {
            LOG.e("JarLoader getSpider init " + key, th);
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
            // 多源并发时按 params 中的 siteKey 精确路由到对应 jar 的 proxy，
            // 避免按「最后加载的源」(recentJarKey) 路由导致串源
            String jarKey = null;
            Object siteKey = params != null ? params.get("siteKey") : null;
            if (siteKey != null) {
                jarKey = siteJarKeys.get(siteKey.toString());
            }
            if (jarKey == null) jarKey = recentJarKey;
            Method proxyFun = proxyMethods.get(jarKey);
            if (proxyFun != null) {
                return (Object[]) proxyFun.invoke(null, params);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }
}
