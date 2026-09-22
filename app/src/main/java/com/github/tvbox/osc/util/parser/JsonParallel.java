package com.github.tvbox.osc.util.parser;
import android.util.Base64;
import com.github.catvod.crawler.SpiderDebug;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 并发解析，直到获得第一个结果
 */
public class JsonParallel {

    private static final String PARSE_TAG = "ParseTag";
    // 复用单例，避免每次解析都创建新的连接池
    private static final OkHttpClient client = new OkHttpClient();
    // 复用线程池，避免每次解析都创建/销毁
    private static final ExecutorService executorService = Executors.newFixedThreadPool(5);
    // 保护共享的 futures 列表
    private static final Object LOCK = new Object();
    // 所有进行中的任务，供 cancelTasks 终止
    private static final List<Future<JSONObject>> futures = new ArrayList<>();

    public static JSONObject parse(LinkedHashMap<String, String> jx, String url) {
        final List<Future<JSONObject>> localFutures = new ArrayList<>();
        final List<Call> calls = Collections.synchronizedList(new ArrayList<Call>());
        JSONObject pTaskResult = null;
        try {
            if (jx != null && !jx.isEmpty()) {
                CompletionService<JSONObject> completionService = new ExecutorCompletionService<>(executorService);

                // 遍历所有的解析配置
                for (final String jxName : jx.keySet()) {
                    final String parseUrl = jx.get(jxName);
                    localFutures.add(completionService.submit(new Callable<JSONObject>() {
                        @Override
                        public JSONObject call() {
                            Call call = null;
                            try {
                                // 获取请求头，并从中取出实际url
                                HashMap<String, String> reqHeaders = JsonParallel.getReqHeader(parseUrl);
                                String realUrl = reqHeaders.remove("url");
                                // 防御：realUrl null 时 Headers.of() 抛 NPE
                                if (realUrl == null) realUrl = "";
                                Headers headers = Headers.of(reqHeaders);
                                Request request = new Request.Builder()
                                        .url(realUrl + url)
                                        .headers(headers)
                                        .tag(PARSE_TAG)
                                        .build();

                                call = client.newCall(request);
                                calls.add(call);
                                Response response = call.execute();
                                // 防御：response.body() 可能为 null
                                String json = response.body() != null ? response.body().string() : null;
                                // 防御：null body 跳过
                                if (json == null || json.isEmpty()) {
                                    return null;
                                }

                                JSONObject taskResult = Utils.jsonParse(url, json);
                                if (taskResult == null) {
                                    return null;
                                }
                                taskResult.put("jxFrom", jxName);
                                return taskResult;
                            } catch (Throwable th) {
                                return null;
                            } finally {
                                if (call != null) {
                                    calls.remove(call);
                                }
                            }
                        }
                    }));
                }

                synchronized (LOCK) {
                    futures.addAll(localFutures);
                }

                try {
                    for (int i = 0; i < localFutures.size(); ++i) {
                        Future<JSONObject> completed = completionService.take();
                        try {
                            pTaskResult = completed.get();
                            if (pTaskResult != null) {
                                break;
                            }
                        } catch (Throwable th) {
                            SpiderDebug.log(th);
                        }
                    }
                } finally {
                    if (pTaskResult != null) {
                        // 拿到结果后终止其余未完成的请求
                        cancelCalls(calls);
                        cancelFutures(localFutures);
                    }
                    synchronized (LOCK) {
                        futures.removeAll(localFutures);
                    }
                }

                if (pTaskResult != null) {
                    return pTaskResult;
                }
            }
        } catch (Throwable th) {
            SpiderDebug.log(th);
        }
        return new JSONObject();
    }

    public static void cancelTasks() {
        List<Future<JSONObject>> pending;
        synchronized (LOCK) {
            pending = new ArrayList<>(futures);
            futures.clear();
        }
        cancelFutures(pending);
        // 终止所有仍在执行的解析请求
        for (Call call : client.dispatcher().runningCalls()) {
            if (PARSE_TAG.equals(call.request().tag())) {
                call.cancel();
            }
        }
        for (Call call : client.dispatcher().queuedCalls()) {
            if (PARSE_TAG.equals(call.request().tag())) {
                call.cancel();
            }
        }
    }

    private static void cancelFutures(List<Future<JSONObject>> futureList) {
        for (Future<JSONObject> future : futureList) {
            try {
                future.cancel(true);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void cancelCalls(List<Call> calls) {
        for (Call call : new ArrayList<>(calls)) {
            try {
                call.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    public static HashMap<String, String> getReqHeader(String url) {
        HashMap<String, String> reqHeaders = new HashMap<>();
        if (url == null) url = "";
        reqHeaders.put("url", url);
        if (url.contains("cat_ext")) {
            try {
                int start = url.indexOf("cat_ext=");
                if (start < 0) return reqHeaders;
                int end = url.indexOf("&", start);
                if (end < 0) {
                    end = url.length();
                }
                // 防御：start + 8 超过 end 时 substring 抛 IOOBE
                if (start + 8 > end) return reqHeaders;
                String ext = url.substring(start + 8, end);
                ext = new String(Base64.decode(ext, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP));
                String newUrl = url.substring(0, start) + (end < url.length() ? url.substring(end + 1) : "");
                JSONObject jsonObject = new JSONObject(ext);
                if (jsonObject.has("header")) {
                    JSONObject headerJson = jsonObject.optJSONObject("header");
                    if (headerJson != null) {
                        Iterator<String> keys = headerJson.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            reqHeaders.put(key, headerJson.optString(key, ""));
                        }
                    }
                }
                reqHeaders.put("url", newUrl);
            } catch (Throwable th) {

            }
        }
        return reqHeaders;
    }
}
