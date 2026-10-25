package com.github.tvbox.osc.util.js;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
//import androidx.media3.common.util.UriUtil;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;

//import com.whl.quickjs.android.QuickJSLoader;
import com.whl.quickjs.wrapper.Function;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSFunction;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.JSUtils;
import com.whl.quickjs.wrapper.QuickJSContext;
import com.whl.quickjs.wrapper.UriUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

//import java9.util.concurrent.CompletableFuture;

public class JsSpider extends Spider {

    private final ExecutorService executor;
    private final Class<?> dex;
    private QuickJSContext ctx;
    private JSObject jsObject;
    private Global global;
    private final String key;
    private final String api;
    private boolean cat;

    public JsSpider(String key, String api, Class<?> cls) throws Exception {
        this.key = "J" + MD5.encode(key);
        this.executor = Executors.newSingleThreadExecutor();
        this.api = api;
        this.dex = cls;
        initializeJS();
    }
    public void cancelByTag() {
        Connect.cancelByTag("js_okhttp_tag");
    }

    private void submit(Runnable runnable) {
        executor.submit(runnable);
    }

    private <T> Future<T> submit(Callable<T> callable) {
        return executor.submit(callable);
    }

    // 单次 JS 调用超时上限（秒）。超过则判定该 spider 不可用。
    private static final int JS_CALL_TIMEOUT_SECONDS = 30;
    // 某次调用超时后 executor 线程被僵尸任务永久占住，置 broken 让后续调用直接返回兜底，
    // 不再往队列堆积（队列里的任务会永远排在僵尸任务后面）
    private volatile boolean broken = false;
    // broken 生效时间戳，用于冷却恢复：超时可能只是网络卡顿而非真死循环，冷却期后允许重试一次
    private volatile long brokenAt = 0;
    // 冷却时长（毫秒）：超过后下一次调用清掉 broken 重新试探
    private static final long BROKEN_COOLDOWN_MS = 5 * 60 * 1000L;

    /**
     * 带超时地在 executor 上执行任务。
     * 超时后 cancel 外层任务并标记 broken：内层 JS 调用无法被真正中断，
     * 单线程 executor 会被占住，因此后续调用必须走 broken 短路而不是继续排队。
     * broken 超过冷却期后允许再试探一次；若 executor 仍被僵尸任务占住，本次调用会再次超时并重新置 broken。
     */
    private <T> T callWithTimeout(Callable<T> callable) {
        if (broken) {
            if (System.currentTimeMillis() - brokenAt < BROKEN_COOLDOWN_MS) return null;
            // 冷却期满：重置 broken 试探一次。若 executor 仍被占住会再次超时并重新标记
            broken = false;
            LOG.i("JsSpider 冷却期满，重试探: " + key);
        }
        Future<T> future = executor.submit(callable);
        try {
            return future.get(JS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            broken = true;
            brokenAt = System.currentTimeMillis();
            LOG.i("JsSpider 调用超时，标记 spider 不可用（" + BROKEN_COOLDOWN_MS / 60000 + "分钟后重试）: " + key);
            return null;
        } catch (InterruptedException | ExecutionException e) {
            LOG.i("Executor 提交或等待失败" + e);
            return null;
        }
    }

    //private Object call(String func, Object... args) throws Exception {
        //return executor.submit((FunCall.call(jsObject, func, args))).get();
      //  return CompletableFuture.supplyAsync(() -> Async.run(jsObject, func, args), executor).join().get();
    private Object call(String func, Object... args) {
//        return executor.submit((FunCall.call(jsObject, func, args))).get();
        return callWithTimeout(() -> Async.run(jsObject, func, args).get());
    }

    private JSObject cfg(String ext) {
        JSObject cfg = ctx.createJSObject();
        cfg.set("stype", 3);
        cfg.set("skey", key);
        if (Json.invalid(ext)) cfg.set("ext", ext);
        else cfg.set("ext", (JSObject) ctx.parse(ext));
        return cfg;
    }

    @Override
    //public void init(Context context, String extend) throws Exception {
     //   if (cat) call("init", submit(() -> cfg(extend)).get());
     //   else call("init", Json.valid(extend) ? ctx.parse(extend) : extend);
    public void init(Context context, String extend) {
        try {
            if (cat) {
                JSObject cfgObj = callWithTimeout(() -> cfg(extend));
                if (cfgObj != null) call("init", cfgObj);
            }
            else call("init", Json.valid(extend) ? ctx.parse(extend) : extend);
        }catch (Exception e){

        }
    }

    @Override
    //public String homeContent(boolean filter) throws Exception {
     //   return (String) call("home", filter);
    public String homeContent(boolean filter) {
        try {
            return (String) call("home", filter);
        }catch (Exception e){
            return null;
        }
    }

    @Override
    //public String homeVideoContent() throws Exception {
    //    return (String) call("homeVod");
    public String homeVideoContent() {
        try {
            return (String) call("homeVod");
        }catch (Exception e){
            return null;
        }
    }

    @Override
    //public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
     //   JSObject obj = submit(() -> new JSUtils<String>().toObj(ctx, extend)).get();
     //   return (String) call("category", tid, pg, filter, obj);
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend)  {
        try {
            JSObject obj = callWithTimeout(() -> new JSUtils<String>().toObj(ctx, extend));
            if (obj == null) return null;
            return (String) call("category", tid, pg, filter, obj);
        }catch (Exception e){
            return null;
        }
    }

    @Override
    //public String detailContent(List<String> ids) throws Exception {
    //    return (String) call("detail", ids.get(0));
    public String detailContent(List<String> ids)  {
        try {
            return (String) call("detail", ids.get(0));
        }catch (Exception e){
            return null;
        }
    }

    @Override
   // public String searchContent(String key, boolean quick) throws Exception {
    //    return (String) call("search", key, quick);
    public String searchContent(String key, boolean quick)  {
        try {
            return (String) call("search", key, quick);
        }catch (Exception e){
            return null;
        }
    }

    @Override
    //public String searchContent(String key, boolean quick, String pg) throws Exception {
     //   return (String) call("search", key, quick, pg);
    public String searchContent(String key, boolean quick, String pg)  {
        try {
            return (String) call("search", key, quick, pg);
        }catch (Exception e){
            return null;
        }
    }

    @Override
    //public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
    //    JSArray array = submit(() -> new JSUtils<String>().toArray(ctx, vipFlags)).get();
     //   return (String) call("play", flag, id, array);
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSArray array = callWithTimeout(() -> new JSUtils<String>().toArray(ctx, vipFlags));
            if (array == null) return null;
            return (String) call("play", flag, id, array);
        }catch (Exception e){
            return null;
        }
    }

    @Override
    //public boolean manualVideoCheck() throws Exception {
    //    return (Boolean) call("sniffer");
    public boolean manualVideoCheck()  {
        try {
            // call 超时/异常时返回 null，Boolean.TRUE.equals 避免拆箱 NPE
            return Boolean.TRUE.equals(call("sniffer"));
        }catch (Exception e){
            return false;
        }
    }

    @Override
    //public boolean isVideoFormat(String url) throws Exception {
    //    return (Boolean) call("isVideo", url);
    public boolean isVideoFormat(String url) {
        try {
            // 同 manualVideoCheck：null 拆箱会 NPE，用 equals 兜底
            return Boolean.TRUE.equals(call("isVideo", url));
        }catch (Exception e){
            return false;
        }
    }

    @Override
    //public Object[] proxyLocal(Map<String, String> params) throws Exception {
     //   if ("catvod".equals(params.get("from"))) return proxy2(params);
     //   else return submit(() -> proxy1(params)).get();
    public Object[] proxyLocal(Map<String, String> params)  {
        try {
            if ("catvod".equals(params.get("from"))) return proxy2(params);
            else {
                Object[] rs = callWithTimeout(() -> proxy1(params));
                return rs != null ? rs : new Object[0];
            }
        }catch (Exception E){
            return new Object[0];
        }
    }

    @Override
    public void destroy() {
        // ctx 绑定在 executor 线程上（QuickJSContext 有 checkSameThread 校验），
        // 外部线程直接调 ctx.destroy() 必抛异常且被 catch 吞掉 → native runtime 永不释放。
        // 正确顺序：先把销毁任务投递回绑定线程执行并等待完成，再关停线程池。
        boolean ctxDestroyed = false;
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.submit(() -> {
                    try {
                        if (ctx != null) ctx.destroy();
                    } catch (Throwable ignored) {
                    }
                }).get(3, TimeUnit.SECONDS);
                ctxDestroyed = true;
            } catch (Throwable ignored) {
                // 提交失败/超时（线程被僵尸任务占住）：此时线程仍在 QuickJS native 调用中，
                // 直接销毁 ctx 是 use-after-free（SIGSEGV/SIGABRT），宁可选择泄漏也不能销毁
            }
        }
        // 先释放 Global 持有的 Timer 线程与 setTimeout 的 native 引用（须在 ctx 销毁前、绑定线程上）
        if (global != null) {
            try {
                global.destroy();
            } catch (Throwable ignored) {
            }
            global = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 兜底：销毁任务未能投递到绑定线程时（池已关闭/提交失败），尝试直接销毁；
        // 若被 checkSameThread 拒绝则仅泄漏 native ctx，不会崩溃
        if (!ctxDestroyed) {
            try {
                if (ctx != null) ctx.destroy();
            } catch (Throwable ignored) {
            }
        }
        ctx = null;
        jsObject = null;
    }

    private static final String SPIDER_STRING_CODE = "import * as spider from '%s'\n\n" +
            "if (!globalThis.__JS_SPIDER__) {\n" +
            "    if (spider.__jsEvalReturn) {\n" +
            "        globalThis.req = http\n" +
            "        globalThis.__JS_SPIDER__ = spider.__jsEvalReturn()\n" +
            "        globalThis.__JS_SPIDER__.is_cat = true\n" +
            "    } else if (spider.default) {\n" +
            "        globalThis.__JS_SPIDER__ = typeof spider.default === 'function' ? spider.default() : spider.default\n" +
            "    }\n" +
            "}";
    private void initializeJS() throws Exception {
        try {
            submit(() -> {
                if (ctx == null) createCtx();
                if (dex != null) createDex();

            String content = FileUtils.loadModule(api);            
            if (TextUtils.isEmpty(content)) {return null;}
            
            if(content.startsWith("//bb")){
                cat = true;
                byte[] b = Base64.decode(content.replace("//bb",""), 0);
                ctx.execute(byteFF(b), key + ".js");
                ctx.evaluateModule(String.format(SPIDER_STRING_CODE, key + ".js") + "globalThis." + key + " = __JS_SPIDER__;", "tv_box_root.js");
                //ctx.execute(byteFF(b), key + ".js","__jsEvalReturn");
                //ctx.evaluate("globalThis." + key + " = __JS_SPIDER__;");
            } else {
                if (content.contains("__JS_SPIDER__")) {
                    content = content.replaceAll("__JS_SPIDER__\\s*=", "export default ");
                }
                String moduleExtName = "default";
                if (content.contains("__jsEvalReturn") && !content.contains("export default")) {
                    moduleExtName = "__jsEvalReturn";
                    cat = true;
                }
                ctx.evaluateModule(content, api);
                ctx.evaluateModule(String.format(SPIDER_STRING_CODE, api) + "globalThis." + key + " = __JS_SPIDER__;", "tv_box_root.js");
                //ctx.evaluateModule(content, api, moduleExtName);
                //ctx.evaluate("globalThis." + key + " = __JS_SPIDER__;");                
            }
            jsObject = (JSObject) ctx.get(ctx.getGlobalObject(), key);
                return null;
            }).get();
        } catch (Throwable initError) {
            // 构造中途失败时释放已分配的资源，避免 executor 线程泄漏
            destroy();
            throw initError instanceof Exception ? (Exception) initError : new Exception(initError);
        }
    }

    public static byte[] byteFF(byte[] bytes) {
        // 长度校验：原代码对 < 5 字节输入会抛 NegativeArraySizeException / ArrayIndexOutOfBoundsException
        if (bytes == null || bytes.length < 5) return new byte[0];
        byte[] newBt = new byte[bytes.length - 4];
        newBt[0] = 1;
        System.arraycopy(bytes, 5, newBt, 1, bytes.length - 5);
        return newBt;
    }

    private void createCtx() {
        ctx = QuickJSContext.create();
        ctx.setModuleLoader(new QuickJSContext.BytecodeModuleLoader() {
            @Override
            public byte[] getModuleBytecode(String moduleName) {
                try {
                    return getModuleBytecodeInner(moduleName);
                } catch (Throwable e) {
                    // 编译失败（如新版 cheerio 的正则语法不被当前 QuickJS 支持）必须在此吞掉：
                    // 若带着挂起的 QuickJSException 返回，native 侧随后调 JNI FindClass 会触发
                    // ART 断言 SIGABRT 直接闪退。降级为空模块，让源以功能缺失的方式继续运行。
                    LOG.i("getModuleBytecode 编译失败，降级为空模块: " + moduleName + " , " + e);
                    return ctx.compileModule("", moduleName);
                }
            }

            private byte[] getModuleBytecodeInner(String moduleName) {
                String ss = FileUtils.loadModule(moduleName);
                //if (TextUtils.isEmpty(ss)) {return null;}
                if (TextUtils.isEmpty(ss)) {
                    LOG.i("echo-getModuleBytecode empty :"+ moduleName);
                    return ctx.compileModule("", moduleName);
                }
                if(ss.startsWith("//DRPY")){
                    return Base64.decode(ss.replace("//DRPY",""), Base64.URL_SAFE);
                } else if(ss.startsWith("//bb")){
                    byte[] b = Base64.decode(ss.replace("//bb",""), 0);
                    return byteFF(b);
                } else {
                    if (moduleName.contains("cheerio.min.js")) {
                        FileUtils.setCacheByte("cheerio.min", ctx.compileModule(ss, "cheerio.min.js"));
                    } else if (moduleName.contains("crypto-js.js")) {
                        FileUtils.setCacheByte("crypto-js", ctx.compileModule(ss, "crypto-js.js"));
                    }
                    return ctx.compileModule(ss, moduleName);
                }
            }

            @Override
            public String moduleNormalizeName(String moduleBaseName, String moduleName) {
                return UriUtil.resolve(moduleBaseName, moduleName);
            }
        });
        ctx.setConsole(new QuickJSContext.Console() {
            @Override
            public void log(String s) {

                //LOG.i("QuJs", s);
                LOG.i("QuJs"+s);
            }
        });

        global = new Global(executor);
        ctx.getGlobalObject().bind(global);

        JSObject local = ctx.createJSObject();
        ctx.getGlobalObject().set("local", local);
        local.bind(new local());

        ctx.getGlobalObject().getContext().evaluate(FileUtils.loadModule("net.js"));
    }

    private void createDex() {
        try {
            JSObject obj = ctx.createJSObject();
            Class<?> clz = dex;
            Class<?>[] classes = clz.getDeclaredClasses();
            ctx.getGlobalObject().set("jsapi", obj);
            if (classes.length == 0) invokeSingle(clz, obj);
            if (classes.length >= 1) invokeMultiple(clz, obj);
        } catch (Throwable e) {
            e.printStackTrace();
            //LOG.e(e);
        }
    }

    private void invokeSingle(Class<?> clz, JSObject jsObj) throws Throwable {
        invoke(clz, jsObj, clz.getDeclaredConstructor(QuickJSContext.class).newInstance(ctx));
    }

    private void invokeMultiple(Class<?> clz, JSObject jsObj) throws Throwable {
        for (Class<?> subClz : clz.getDeclaredClasses()) {
            Object javaObj = subClz.getDeclaredConstructor(clz).newInstance(clz.getDeclaredConstructor(QuickJSContext.class).newInstance(ctx));
            JSObject subObj = ctx.createJSObject();
            invoke(subClz, subObj, javaObj);
            jsObj.set(subClz.getSimpleName(), subObj);
        }
    }

    private void invoke(Class<?> clz, JSObject jsObj, Object javaObj) {
        for (Method method : clz.getMethods()) {
            if (!method.isAnnotationPresent(Function.class)) continue;
            invoke(jsObj, method, javaObj);
        }
    }

    private void invoke(JSObject jsObj, Method method, Object javaObj) {
        jsObj.set(method.getName(), new JSCallFunction() {
            @Override
            public Object call(Object... objects) {
                try {
                    return method.invoke(javaObj, objects);
                } catch (Throwable e) {
                    return null;
                }
            }
        });
    }

    private String getContent() {
        String global = "globalThis." + key;
        String content = FileUtils.loadModule(api);
        if (TextUtils.isEmpty(content)) {return null;}
        if (content.contains("__jsEvalReturn")) {
            ctx.evaluate("req = http");
            return content.concat(global).concat(" = __jsEvalReturn()");
        } else if (content.contains("__JS_SPIDER__")) {
            return content.replace("__JS_SPIDER__", global);
        } else {
            return content.replaceAll("export default.*?[{]", global + " = {");
        }
    }

    private Object[] proxy1(Map<String, String> params) {
        JSObject object = new JSUtils<String>().toObj(ctx, params);
        JSFunction proxyFn = jsObject.getJSFunction("proxy");
        if (proxyFn == null) return new Object[0];
        JSONArray array = ((JSArray) proxyFn.call(object)).toJsonArray();
        //Object[] result = new Object[3];
        boolean headerAvailable = array.length() > 3 && array.opt(3) != null;
        Object[] result = new Object[4];
        result[0] = array.opt(0);
        result[1] = array.opt(1);
        result[2] = getStream(array.opt(2));
        result[3] = headerAvailable ? getHeader(array.opt(3)) : null;
        if (array.length() > 4) {
            try {
                if ( array.optInt(4) == 1) {
                    String content = array.optString(2);
                    if (content.contains("base64,")) content = content.substring(content.indexOf("base64,") + 7);
                    result[2] = new ByteArrayInputStream(Base64.decode(content, Base64.DEFAULT));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }
    private Map<String, String> getHeader(Object headerRaw) {
        Map<String, String> headers = new HashMap<>();
        if (headerRaw instanceof JSONObject) {
            JSONObject json = (JSONObject) headerRaw;
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                headers.put(key, json.optString(key));
            }
        } else if (headerRaw instanceof String) {
            try {
                JSONObject json = new JSONObject((String) headerRaw);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    headers.put(key, json.optString(key));
                }
            } catch (JSONException e) {
                LOG.i("getHeader: 无法解析 String 为 JSON"+ e);
            }
        } else if (headerRaw instanceof Map) {
            //noinspection unchecked
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) headerRaw).entrySet()) {
                headers.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return headers;
    }

    
    private Object[] proxy2(Map<String, String> params) throws Exception {
        String url = params.get("url");
        String header = params.get("header");
        JSArray array = callWithTimeout(() -> new JSUtils<String>().toArray(ctx, Arrays.asList(url.split("/"))));
        if (array == null) return new Object[0];
        Object object = callWithTimeout(() -> ctx.parse(header));
        if (object == null) return new Object[0];
        String json = (String) call("proxy", array, object);
        Res res = Res.objectFrom(json);
        String contentType = res.getContentType();
        if (TextUtils.isEmpty(contentType)) contentType = "application/octet-stream";
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = contentType;
        if (res.getBuffer() == 2) {
            result[2] = new ByteArrayInputStream(Base64.decode(res.getContent(), Base64.DEFAULT));
        } else {
            result[2] = new ByteArrayInputStream(res.getContent().getBytes());
        }
        return result;
    }

   /* private Object[] proxy2(Map<String, String> params) throws Exception {
        String url = params.get("url");
        String header = params.get("header");
        JSArray array = submit(() -> new JSUtils<String>().toArray(ctx, Arrays.asList(url.split("/")))).get();
        Object object = submit(() -> ctx.parse(header)).get();
        String json = (String) call("proxy", array, object);
        Res res = Res.objectFrom(json);
        Object[] result = new Object[3];
        result[0] = 200;
        result[1] = "application/octet-stream";
        result[2] = new ByteArrayInputStream(Base64.decode(res.getContent(), Base64.DEFAULT));
        return result;
    }*/

    private ByteArrayInputStream getStream(Object o) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            byte[] bytes = new byte[a.length()];
            for (int i = 0; i < a.length(); i++) bytes[i] = (byte) a.optInt(i);
            return new ByteArrayInputStream(bytes);
        } else {
            return new ByteArrayInputStream(o.toString().getBytes());
        }
    }
}