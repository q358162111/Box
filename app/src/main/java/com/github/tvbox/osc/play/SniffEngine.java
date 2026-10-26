package com.github.tvbox.osc.play;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.VideoParseRuler;

/**
 * PlayActivity/PlayFragment 共用的嗅探判定工具（SniffEngine 第一片）。
 * 视频格式判定两侧原为双份实现，且 Activity 侧缺少 sourceBean 判空、
 * 异常兜底行为不一致，统一收敛到这里（采用 Fragment 侧更安全的版本）。
 * WebView 容器/WebClient 的完整引擎抽取见后续批次。
 */
public class SniffEngine {

    /**
     * 判定嗅探到的 URL 是否为视频流：
     * 1. 明显的网页特征直接排除；
     * 2. 源声明 manualVideoCheck 时交给 Spider 判定；
     * 3. 否则走嗅探规则（VideoParseRuler）。
     */
    public static boolean checkVideoFormat(SourceBean sourceBean, String webUrl, String url) {
        try {
            if (url.contains("url=http") || url.contains(".html")) {
                return false;
            }
            if (sourceBean != null && sourceBean.getType() == 3) {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                if (sp != null && sp.manualVideoCheck()) {
                    return sp.isVideoFormat(url);
                }
            }
            return VideoParseRuler.checkIsVideoForParse(webUrl, url);
        } catch (Throwable e) {
            return false;
        }
    }
}
