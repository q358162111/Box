package com.github.catvod.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

public class OkhttpInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        String encoding = response.header(HttpHeaders.CONTENT_ENCODING);
        if (response.body() == null || encoding == null || !encoding.equalsIgnoreCase("deflate")) return response;
        // 在自定义 ResponseBody 的 close() 中 end() Inflater，避免每个被拦截的响应泄漏一个 Inflater（持有 native 内存）
        final Inflater inflater = new Inflater(true);
        final InflaterInputStream is = new InflaterInputStream(response.body().byteStream(), inflater) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    inflater.end();
                }
            }
        };
        return response.newBuilder().headers(response.headers()).body(new ResponseBody() {
            @Nullable
            @Override
            public MediaType contentType() {
                return response.body().contentType();
            }

            @Override
            public long contentLength() {
                return response.body().contentLength();
            }

            @NonNull
            @Override
            public BufferedSource source() {
                Source source = Okio.source(is);
                return Okio.buffer(source);
            }
        }).build();
    }
}
