package com.github.tvbox.osc.server;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 资源文件加载
 */
public class RawRequestProcess implements RequestProcess {
    private Context mContext;
    private String fileName;
    private int resourceId;
    private String mimeType;

    public RawRequestProcess(Context context, String fileName, int resourceId, String mimeType) {
        this.mContext = context;
        this.fileName = fileName;
        this.resourceId = resourceId;
        this.mimeType = mimeType;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return session.getMethod() == NanoHTTPD.Method.GET && this.fileName.equalsIgnoreCase(fileName);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        InputStream inputStream = null;
        try {
            inputStream = mContext.getResources().openRawResource(this.resourceId);
            // available() 对资源文件不可靠，改为完整读入内存后再返回
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            byte[] data = bos.toByteArray();
            return RemoteServer.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType + "; charset=utf-8", new java.io.ByteArrayInputStream(data), (long) data.length);
        } catch (IOException IOExc) {
            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + IOExc.getMessage());
        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (IOException ignored) { }
            }
        }
    }
}