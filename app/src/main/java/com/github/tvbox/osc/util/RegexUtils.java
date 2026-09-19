package com.github.tvbox.osc.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class RegexUtils {

    // 全局正则热路径，多线程（WebView 嗅探、spider、M3U8）并发调用，必须线程安全
    private static final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public static Pattern getPattern(String regex) {
        return patternCache.computeIfAbsent(regex, Pattern::compile);
    }

    public static Pattern getPattern(String regex, int flag) {
        // flag 拼入缓存 key，避免与无 flag 版本互相覆盖
        return patternCache.computeIfAbsent(regex + "@" + flag, k -> Pattern.compile(regex, flag));
    }
}
