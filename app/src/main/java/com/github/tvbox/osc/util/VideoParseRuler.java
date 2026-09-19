package com.github.tvbox.osc.util;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class VideoParseRuler {

    // 嗅探线程读取 / 配置解析线程重建，必须线程安全
    private static final Map<String, ArrayList<ArrayList<String>>> HOSTS_RULE = new ConcurrentHashMap<>();
    private static final Map<String, ArrayList<ArrayList<String>>> HOSTS_FILTER = new ConcurrentHashMap<>();
    private static final Map<String, ArrayList<String>> HOSTS_REGEX = new ConcurrentHashMap<>();
    private static final Map<String, ArrayList<String>> HOSTS_SCRIPT = new ConcurrentHashMap<>();
    public static void clearRule() {
        HOSTS_RULE.clear();
        HOSTS_FILTER.clear();
        HOSTS_REGEX.clear();
        HOSTS_SCRIPT.clear();
    }

    public static void addHostRule(String host, ArrayList<String> rule) {
        if (rule == null || rule.size() == 0) return;
        HOSTS_RULE.computeIfAbsent(host, k -> new ArrayList<>()).add(rule);
    }

    public static ArrayList<ArrayList<String>> getHostRules(String host) {
        if (HOSTS_RULE.containsKey(host)) {
            return HOSTS_RULE.get(host);
        }
        return null;
    }

    public static void addHostFilter(String host, ArrayList<String> rule) {
        HOSTS_FILTER.computeIfAbsent(host, k -> new ArrayList<>()).add(rule);
    }

    public static ArrayList<ArrayList<String>> getHostFilters(String host) {
        if (HOSTS_FILTER.containsKey(host)) {
            return HOSTS_FILTER.get(host);
        }
        return null;
    }

    public static void addHostRegex(String host, ArrayList<String> regex) {
        if (regex == null || regex.size() == 0) return;
        HOSTS_REGEX.computeIfAbsent(host, k -> new ArrayList<>()).addAll(regex);
    }

    public static Map<String, ArrayList<String>> getHostsRegex() {
        return HOSTS_REGEX;
    }

    public static boolean checkIsVideoForParse(String webUrl, String url) {
        try {
            boolean isVideo = DefaultConfig.isVideoFormat(url);
            if (!HOSTS_RULE.isEmpty() && !isVideo && webUrl != null) {
                Uri uri = Uri.parse(webUrl);
                if(getHostRules(uri.getHost()) != null){
                    isVideo = checkVideoForOneHostRules(uri.getHost(), url);
                }else {
                    isVideo = checkVideoForOneHostRules("*", url);
                }
            }
            return isVideo;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean checkVideoForOneHostRules(String host, String url) {
        boolean isVideo = false;
        ArrayList<ArrayList<String>> hostRules = getHostRules(host);
        if (hostRules != null && hostRules.size() > 0) {
            boolean isVideoRuleCheck = false;
            for(int i=0; i<hostRules.size(); i++) {
                boolean checkIsVideo = true;
                if (hostRules.get(i) != null && hostRules.get(i).size() > 0) {
                    for(int j=0; j<hostRules.get(i).size(); j++) {
                        //Pattern onePattern = Pattern.compile("" + hostRules.get(i).get(j));
                        Pattern onePattern = getPattern("" + hostRules.get(i).get(j));
                        if (!onePattern.matcher(url).find()) {
                            checkIsVideo = false;
                            break;
                        }
                        LOG.i("VIDEO RULE:" + hostRules.get(i).get(j));
                    }
                } else {
                    checkIsVideo = false;
                }
                if (checkIsVideo) {
                    isVideoRuleCheck = true;
                    break;
                }
            }
            if (isVideoRuleCheck) {
                isVideo = true;
            }
        }
        return isVideo;
    }

    public static boolean isFilter(String webUrl, String url) {
        try {
            boolean isFilter = false;
            if (!HOSTS_FILTER.isEmpty() && webUrl != null) {
                Uri uri = Uri.parse(webUrl);
                if(getHostFilters(uri.getHost()) != null){
                    isFilter = checkIsFilterForOneHostRules(uri.getHost(), url);
                }
            }
            return isFilter;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean checkIsFilterForOneHostRules(String host, String url) {
        boolean isFilter = false;
        ArrayList<ArrayList<String>> hostFilters = getHostFilters(host);
        if (hostFilters != null && hostFilters.size() > 0) {
            boolean isFilterRuleCheck = false;
            for(int i=0; i<hostFilters.size(); i++) {
                boolean checkIsFilter = true;
                if (hostFilters.get(i) != null && hostFilters.get(i).size() > 0) {
                    for(int j=0; j<hostFilters.get(i).size(); j++) {
                        //Pattern onePattern = Pattern.compile("" + hostFilters.get(i).get(j));
                        Pattern onePattern = getPattern("" + hostFilters.get(i).get(j));
                        if (!onePattern.matcher(url).find()) {
                            checkIsFilter = false;
                            break;
                        }
                        LOG.i("FILTER RULE:" + hostFilters.get(i).get(j));
                    }
                } else {
                    checkIsFilter = false;
                }
                if (checkIsFilter) {
                    isFilterRuleCheck = true;
                    break;
                }
            }
            if (isFilterRuleCheck) {
                isFilter = true;
            }
        }
        return isFilter;
    }
    public static void addHostScript(String host, ArrayList<String> script) {
        if (script == null || script.size() == 0) return;
        HOSTS_SCRIPT.computeIfAbsent(host, k -> new ArrayList<>()).addAll(script);
    }
    public static String getHostScript(String url) {
        for (Map.Entry<String, ArrayList<String>> entry : HOSTS_SCRIPT.entrySet()) {
            String host = entry.getKey();
            if (url.contains(host)) {
                List<String> list = entry.getValue();
                if (list != null && !list.isEmpty()) {
                    return list.get(0);
                }
            }
        }
        return "";
    }

}
