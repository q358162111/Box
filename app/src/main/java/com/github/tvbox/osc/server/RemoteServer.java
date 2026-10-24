package com.github.tvbox.osc.server;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Base64;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.OkGoHelper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.github.tvbox.osc.util.Proxy;
import org.greenrobot.eventbus.EventBus;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class RemoteServer extends NanoHTTPD {
    private Context mContext;
    public static volatile int serverPort = 9978;
    private boolean isStarted = false;
    private DataReceiver mDataReceiver;
    private ArrayList < RequestProcess > getRequestList = new ArrayList < > ();
    private ArrayList < RequestProcess > postRequestList = new ArrayList < > ();
    private static final String PATTERN_ETH_STR = "^eth\\d+$";
    private static final Pattern ETH_PATTERN = Pattern.compile(PATTERN_ETH_STR);
    // volatile：主线程写入、NanoHTTPD 服务端线程读取，保证跨线程可见性
    public static volatile String m3u8Content;
    // python 插件缓存端点（/cache）的内存存储：仅服务环回客户端（python 插件经本地回环访问）
    private static final ConcurrentHashMap<String, String> pluginCache = new ConcurrentHashMap<>();
    // 单个缓存值大小上限（字节），防止插件写入超大值撑爆内存
    private static final int CACHE_VALUE_MAX_BYTES = 1024 * 1024;
    // 单个缓存 key 长度上限
    private static final int CACHE_KEY_MAX_LEN = 256;

    /**
     * 校验 /cache 的 key：非空、长度受限、不含控制字符（防日志/解析注入）
     */
    private static boolean isSafeCacheKey(String key) {
        if (key == null || key.isEmpty() || key.length() > CACHE_KEY_MAX_LEN) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x20 || c == 0x7f) return false;
        }
        return true;
    }

    public RemoteServer(int port, Context context) {
        super(port);
        mContext = context;
        addGetRequestProcess();
        addPostRequestProcess();
    }

    /**
     * 校验目标路径未逃逸出存储根目录，防止局域网请求通过 ../ 进行路径穿越
     */
    private boolean isPathSafe(File file) {
        return isPathSafe(file, false);
    }

    /**
     * 校验目标路径未逃逸出存储根目录，防止局域网请求通过 ../ 进行路径穿越
     *
     * @param disallowRoot 为 true 时额外禁止操作存储根目录本身（用于删除等破坏性操作）
     */
    private boolean isPathSafe(File file, boolean disallowRoot) {
        try {
            String root = Environment.getExternalStorageDirectory().getCanonicalPath();
            String canonical = file.getCanonicalPath();
            if (disallowRoot && (canonical.equals(root) || root.equals(canonical + "/"))) {
                return false;
            }
            return canonical.equals(root) || canonical.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private Response forbidden() {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Forbidden: path traversal detected");
    }

    /**
     * 读取文件全部字节，供 /file 端点以内存流方式返回，避免持有 FileInputStream 导致 fd 泄漏
     */
    private static byte[] readFileBytes(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, n);
            }
            return bos.toByteArray();
        } finally {
            fis.close();
        }
    }

    private void addGetRequestProcess() {
        getRequestList.add(new RawRequestProcess(this.mContext, "/", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/index.html", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/style.css", R.raw.style, "text/css"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/ui.css", R.raw.ui, "text/css"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/jquery.js", R.raw.jquery, "application/x-javascript"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/script.js", R.raw.script, "application/x-javascript"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/favicon.ico", R.drawable.app_icon, "image/x-icon"));
    }

    private void addPostRequestProcess() {
        postRequestList.add(new InputRequestProcess(this));
    }

    @Override
    public void start(int timeout, boolean daemon) throws IOException {
        super.start(timeout, daemon);
        isStarted = true;
        EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_SUCCESS));
    }

    @Override
    public void stop() {
        super.stop();
        isStarted = false;
    }

    private Response getProxy(Object[] rs){
        try {
            //源未配置或解析异常时 proxyLocal/proxy 可能返回 null
            if (rs == null || rs.length < 3) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "proxy error");
            }
            if (rs[0] instanceof NanoHTTPD.Response) return (NanoHTTPD.Response) rs[0];
            int code = (int) rs[0];
            String mime = (String) rs[1];
            InputStream stream = rs[2] != null ? (InputStream) rs[2] : null;
            Response response = NanoHTTPD.newChunkedResponse(
                    Response.Status.lookup(code),
                    mime,
                    stream
            );
            // 添加头部信息
            if (rs.length >= 4 && rs[3] instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> mapHeader = (Map<String, String>) rs[3];
                if(!mapHeader.isEmpty()){
                    for (String key : mapHeader.keySet()) {
                        response.addHeader(key, mapHeader.get(key));
                    }
                }
            }
            return response;
        } catch (Throwable th) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "500");
        }
    }
    /**
     * 判断请求是否来自回环地址（本机/应用自身）。供 SSRF 过滤分支使用。
     */
    private boolean isLoopbackClient(IHTTPSession session) {
        String host = session.getRemoteIpAddress();
        return "127.0.0.1".equals(host) || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host);
    }

    @Override
    public Response serve(IHTTPSession session) {
        EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_CONNECTION));
        return handleRequest(session);
    }

    private Response handleRequest(IHTTPSession session) {
        if (!session.getUri().isEmpty()) {
            String fileName = session.getUri().trim();
            if (fileName.indexOf('?') >= 0) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }
            if (session.getMethod() == Method.GET) {
                for (RequestProcess process: getRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.getParms(), null);
                    }
                }
                if (fileName.equals("/proxy")) {
                    Map < String, String > params = session.getParms();
                    // 反向合并:query 参数优先,header 仅作补充。
                    // 若 putAll(headers),客户端可用 "url:"/"do:" 等请求头覆盖同名参数或凭空触发代理分支(参数污染)
                    for (Map.Entry<String, String> h : session.getHeaders().entrySet()) {
                        params.putIfAbsent(h.getKey(), h.getValue());
                    }
                    params.put("request-headers", new Gson().toJson(session.getHeaders()));
                    boolean fromLoopback = isLoopbackClient(session);
                    if (params.containsKey("do")) {
                        // SSRF 防御：外部客户端不得经 proxyLocal 让 spider 网络栈抓取内网/回环地址
                        if (!Proxy.isAllowedProxyUrl(params.get("url"), fromLoopback)) {
                            return forbidden();
                        }
                        Object[] rs = ApiConfig.get().proxyLocal(params);
                        return getProxy(rs);
                    }
                    if (params.containsKey("go")) {
                        Object[] rs = Proxy.proxy(params);
                        return getProxy(rs);
                    }
                } else if (fileName.startsWith("/file/")) {
                    try {
                        String f = fileName.substring(6);
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        String file = root + "/" + f;
                        File localFile = new File(file);
                        if (!isPathSafe(localFile)) {
                            return forbidden();
                        }
                        if (localFile.exists()) {
                            if (localFile.isFile()) {
                                //读入内存后用 ByteArrayInputStream，流无需显式关闭，避免 fd 泄漏
                                byte[] fileBytes = readFileBytes(localFile);
                                return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "application/octet-stream", new ByteArrayInputStream(fileBytes));
                            } else {
                                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, fileList(root, f));
                            }
                        } else {
                            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "File not found!");
                        }
                    } catch (Throwable th) {
                        // 异常消息可能含本地绝对路径，只进日志，不回显给局域网客户端
                        android.util.Log.e("RemoteServer", "file read failed: " + fileName, th);
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "File read error");
                    }
                } else if (fileName.equals("/dns-query")) {
                    String name = session.getParms().get("name");
                    if (name == null || name.isEmpty()) {
                        return createPlainTextResponse(Response.Status.BAD_REQUEST, "missing name");
                    }
                    byte[] rs;
                    try {
                        rs = OkGoHelper.lookupHttpsForwardSync(name);
                    } catch (Throwable th) {
                        android.util.Log.e("RemoteServer", "dns lookup failed: " + name, th);
                        rs = new byte[0];
                    }
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/dns-message", new ByteArrayInputStream(rs), rs.length);
                } else if (fileName.equals("/m3u8")) {
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, m3u8Content == null ? "" : m3u8Content);
                } else if (fileName.equals("/cache")) {
                    // python 插件缓存读取（spider.py getCache）：仅 do=get/del，且仅限环回客户端（应用自身插件）
                    if (!isLoopbackClient(session)) return forbidden();
                    Map < String, String > cacheParams = session.getParms();
                    String doType = cacheParams.get("do");
                    String key = cacheParams.get("key");
                    if (!isSafeCacheKey(key)) {
                        return forbidden();
                    }
                    if (!"get".equals(doType) && !"del".equals(doType)) {
                        return createPlainTextResponse(Response.Status.BAD_REQUEST, "bad do");
                    }
                    String value = pluginCache.get(key);
                    if ("del".equals(doType)) {
                        pluginCache.remove(key);
                    }
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, value == null ? "" : value);
                } else if (fileName.startsWith("/dash/")) {
                    String dashData = App.getInstance().getDashData();
                    try {
                        String data = new String(Base64.decode(dashData, Base64.DEFAULT | Base64.NO_WRAP), "UTF-8");
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/dash+xml", data);
                    } catch (Throwable th) {
                        //解码失败时不得把原始 dashData 回写网络端点，避免内部数据泄漏
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "dash decode error");
                    }
                }
            } else if (session.getMethod() == Method.POST) {
                Map < String, String > files = new HashMap < String, String > ();
                try {
                    if (session.getHeaders().containsKey("content-type")) {
                        String hd = session.getHeaders().get("content-type");
                        if (hd != null) {
                            // cuke: 修正中文乱码问题
                            if (hd.toLowerCase().contains("multipart/form-data") && !hd.toLowerCase().contains("charset=")) {
                                //Matcher matcher = Pattern.compile("[ |\t]*(boundary[ |\t]*=[ |\t]*['|\"]?[^\"^'^;^,]*['|\"]?)", Pattern.CASE_INSENSITIVE).matcher(hd);
                                Matcher matcher = getPattern("[ |\t]*(boundary[ |\t]*=[ |\t]*['|\"]?[^\"^'^;^,]*['|\"]?)", Pattern.CASE_INSENSITIVE).matcher(hd);
                                String boundary = matcher.find() ? matcher.group(1) : null;
                                if (boundary != null) {
                                    session.getHeaders().put("content-type", "multipart/form-data; charset=utf-8; " + boundary);
                                }
                            }
                        }
                    }
                    session.parseBody(files);
                } catch (IOException IOExc) {
                    // 异常消息可能含内部信息，只进日志
                    android.util.Log.e("RemoteServer", "parse body failed", IOExc);
                    return createPlainTextResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException");
                } catch (NanoHTTPD.ResponseException rex) {
                    android.util.Log.e("RemoteServer", "ResponseException", rex);
                    return createPlainTextResponse(rex.getStatus(), "Request error");
                }
                for (RequestProcess process: postRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.getParms(), files);
                    }
                }
                try {
                    Map < String, String > params = session.getParms();
                    if (fileName.equals("/upload")) {
                        String path = params.get("path");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File targetDir = new File(root + "/" + path);
                        if (!isPathSafe(targetDir)) {
                            return forbidden();
                        }
                        for (String k: files.keySet()) {
                            if (k.startsWith("files-")) {
                                String fn = params.get(k);
                                String tmpFile = files.get(k);
                                File tmp = new File(tmpFile);
                                File file = new File(root + "/" + path + "/" + fn);
                                if (!isPathSafe(file)) {
                                    return forbidden();
                                }
                                if (file.exists()) file.delete();
                                if (tmp.exists()) {
                                    if (fn.toLowerCase().endsWith(".zip")) {
                                        unzip(tmp, root + "/" + path);
                                    } else {
                                        FileUtils.copyFile(tmp, file);
                                    }
                                }
                                if (tmp.exists()) tmp.delete();
                            }
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/newFolder")) {
                        String path = params.get("path");
                        String name = params.get("name");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path + "/" + name);
                        if (!isPathSafe(file)) {
                            return forbidden();
                        }
                        if (!file.exists()) {
                            file.mkdirs();
                            File flag = new File(root + "/" + path + "/" + name + "/.tvbox_folder");
                            if (!flag.exists()) flag.createNewFile();
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/delFolder")) {
                        String path = params.get("path");
                        if (path == null || path.trim().isEmpty() || path.equals("/") || path.equals(".")) {
                            return forbidden();
                        }
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path);
                        // 删除操作禁止作用于存储根目录本身，防止空路径删除整个外部存储
                        if (!isPathSafe(file, true) || file.equals(new File(root))) {
                            return forbidden();
                        }
                        if (file.exists()) {
                            FileUtils.recursiveDelete(file);
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/delFile")) {
                        String path = params.get("path");
                        if (path == null || path.trim().isEmpty() || path.equals("/") || path.equals(".")) {
                            return forbidden();
                        }
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path);
                        if (!isPathSafe(file, true)) {
                            return forbidden();
                        }
                        if (file.exists()) {
                            file.delete();
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/cache")) {
                        // python 插件缓存写入（spider.py setCache）：do=set，value 从表单取，仅限环回客户端
                        if (!isLoopbackClient(session)) return forbidden();
                        String doType = params.get("do");
                        String key = params.get("key");
                        if (!isSafeCacheKey(key)) {
                            return forbidden();
                        }
                        if (!"set".equals(doType)) {
                            return createPlainTextResponse(Response.Status.BAD_REQUEST, "bad do");
                        }
                        String value = files.get("value");
                        if (value != null && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > CACHE_VALUE_MAX_BYTES) {
                            return createPlainTextResponse(Response.Status.BAD_REQUEST, "value too large");
                        }
                        if (value != null) {
                            pluginCache.put(key, value);
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    }
                } catch (Throwable th) {
                    // 记录真实错误并返回失败状态，不再伪装成功；消息只进日志，不回显
                    android.util.Log.e("RemoteServer", "file op failed: " + fileName, th);
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "ERROR");
                }
            }
        }
        //default page: index.html
        return getRequestList.get(0).doResponse(session, "", null, null);
    }

    public void setDataReceiver(DataReceiver receiver) {
        mDataReceiver = receiver;
    }

    public DataReceiver getDataReceiver() {
        return mDataReceiver;
    }

    public boolean isStarting() {
        return isStarted;
    }

    public String getServerAddress() {
        String ipAddress = getLocalIPAddress(mContext);
        return "http://" + ipAddress + ":" + RemoteServer.serverPort + "/";
    }

    public String getLoadAddress() {
        return "http://127.0.0.1:" + RemoteServer.serverPort + "/";
    }

    public static Response createPlainTextResponse(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, text);
    }

    public static Response createJSONResponse(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, "application/json", text);
    }

    @SuppressLint("DefaultLocale")
    public static String getLocalIPAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        if (ipAddress == 0) {
            try {
                Enumeration < NetworkInterface > enumerationNi = NetworkInterface.getNetworkInterfaces();
                while (enumerationNi.hasMoreElements()) {
                    NetworkInterface networkInterface = enumerationNi.nextElement();
                    String interfaceName = networkInterface.getDisplayName();
                    if (ETH_PATTERN.matcher(interfaceName).matches() || interfaceName.equals("wlan0")) {
                        Enumeration < InetAddress > enumIpAddr = networkInterface.getInetAddresses();
                        while (enumIpAddr.hasMoreElements()) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        } else {
            return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        }
        return "0.0.0.0";
    }

    String fileTime(long time, String fmt) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        Date date = calendar.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat(fmt);
        return sdf.format(date);
    }

    String fileList(String root, String path) {
        File file = new File(root + "/" + path);
        File[] list = file.listFiles();
        JsonObject info = new JsonObject();
        info.addProperty("remote", getServerAddress().replace("http://", "clan://"));
        info.addProperty("del", 0);
        if (path.isEmpty()) {
            info.addProperty("parent", ".");
        } else {
            info.addProperty("parent", file.getParentFile().getAbsolutePath().replace(root + "/", "").replace(root, ""));
        }
        if (list == null || list.length == 0) {
            info.add("files", new JsonArray());
            return info.toString();
        }
        Arrays.sort(list, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && o2.isFile()) return -1;
                return o1.isFile() && o2.isDirectory() ? 1 : o1.getName().compareTo(o2.getName());
            }
        });
        JsonArray result = new JsonArray();
        for (File f: list) {
            if (f.getName().startsWith(".")) {
                if (f.getName().equals(".tvbox_folder")) {
                    info.addProperty("del", 1);
                }
                continue;
            }
            JsonObject fileObj = new JsonObject();
            fileObj.addProperty("name", f.getName());
            fileObj.addProperty("path", f.getAbsolutePath().replace(root + "/", ""));
            fileObj.addProperty("time", fileTime(f.lastModified(), "yyyy/MM/dd aHH:mm:ss"));
            fileObj.addProperty("dir", f.isDirectory() ? 1 : 0);
            result.add(fileObj);
        }
        info.add("files", result);
        return info.toString();
    }

    void unzip(File zipFilePath, String destDirectory) throws Throwable {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        String destCanonical = destDir.getCanonicalPath() + File.separator;
        try (ZipFile zip = new ZipFile(zipFilePath)) {
            Enumeration<ZipEntry> iter = (Enumeration<ZipEntry>) zip.entries();
            while (iter.hasMoreElements()) {
                ZipEntry entry = iter.nextElement();
                try (InputStream is = zip.getInputStream(entry)) {
                    File outFile = new File(destDir, entry.getName());
                    // 防 Zip Slip 路径穿越
                    if (!outFile.getCanonicalPath().startsWith(destCanonical)) {
                        throw new SecurityException("非法的 zip 条目: " + entry.getName());
                    }
                    if (!entry.isDirectory()) {
                        extractFile(is, outFile);
                    } else {
                        if (!outFile.exists()) outFile.mkdirs();
                        File flag = new File(outFile, ".tvbox_folder");
                        if (!flag.exists()) flag.createNewFile();
                    }
                }
            }
        }
    }

    void extractFile(InputStream inputStream, File destFile) throws Throwable {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile))) {
            byte[] bytesIn = new byte[2048];
            int len;
            while ((len = inputStream.read(bytesIn)) > 0) {
                bos.write(bytesIn, 0, len);
            }
        }
    }

}
