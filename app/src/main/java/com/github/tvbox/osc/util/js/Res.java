package com.github.tvbox.osc.util.js;

import android.text.TextUtils;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Res {

    // 同 Req：复用 Gson，避免每次请求重建 TypeAdapter 缓存
    private static final Gson GSON = new Gson();

    @SerializedName("code")
    private Integer code;
    @SerializedName("buffer")
    private Integer buffer;
    @SerializedName("content")
    private String content;
    @SerializedName("headers")
    private JsonElement headers;

    public static Res objectFrom(String json) {
        return GSON.fromJson(json, Res.class);
    }

    public int getCode() {
        return code == null ? 200 : code;
    }

    public int getBuffer() {
        return buffer == null ? 0 : buffer;
    }

    public String getContent() {
        return TextUtils.isEmpty(content) ? "" : content;
    }

    private JsonElement getHeaders() {
        return headers;
    }

    public Map<String, String> getHeader() {
        return Json.toMap(getHeaders());
    }

    public String getContentType() {
        Map<String, String> header = getHeader();
        List<String> keys = Arrays.asList("Content-Type", "content-type");
        for (String key : keys) if (header.containsKey(key)) return Objects.requireNonNull(header.get(key));
        return "application/octet-stream";
    }

    public ByteArrayInputStream getStream() {
        if (getBuffer() == 2) return new ByteArrayInputStream(Base64.decode(getContent(), Base64.DEFAULT));
        return new ByteArrayInputStream(getContent().getBytes());
    }
}
