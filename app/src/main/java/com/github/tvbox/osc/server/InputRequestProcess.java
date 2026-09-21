package com.github.tvbox.osc.server;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    private String trimParam(Map<String, String> params, String key) {
        String v = params.get(key);
        return v == null ? "" : v.trim();
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action":
                if (params.get("do") != null && mDataReceiver != null) {
                    String action = params.get("do");

                    switch (action) {
                        case "search": {
                            String word = trimParam(params, "word");
                            if (!word.isEmpty()) mDataReceiver.onTextReceived(word);
                            break;
                        }
                        case "api": {
                            String url = trimParam(params, "url");
                            if (!url.isEmpty()) mDataReceiver.onApiReceived(url);
                            break;
                        }
                        case "live": {
                            String url = trimParam(params, "url");
                            if (!url.isEmpty()) mDataReceiver.onLiveReceived(url);
                            break;
                        }
                        case "epg": {
                            String url = trimParam(params, "url");
                            if (!url.isEmpty()) mDataReceiver.onEpgReceived(url);
                            break;
                        }
                        case "proxys": {
                            String url = trimParam(params, "url");
                            if (!url.isEmpty()) mDataReceiver.onProxysReceived(url);
                            break;
                        }
                        case "push": {
                            String url = trimParam(params, "url");
                            if (!url.isEmpty()) mDataReceiver.onPushReceived(url);
                            break;
                        }
                        case "mirror": {
                            //推送当前电影、电视剧……
                            String id = trimParam(params, "id");
                            String sourceKey = trimParam(params, "sourceKey");
                            if (id.isEmpty() || sourceKey.isEmpty()) {
                                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "missing id/sourceKey");
                            }
                            mDataReceiver.onMirrorReceived(id, sourceKey);
                            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "mirrored");
                        }
                    }
                }
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }
}
