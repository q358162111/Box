package com.github.tvbox.osc.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String UPDATE_URL = "https://gitee.com/hong-j/apk/raw/main/firmware/output-metadata.json";
    private static final String APK_URL_TEMPLATE = "https://gitee.com/hong-j/apk/raw/main/firmware/TVBox_release-%s-%s-%s.apk";
    private static final String APK_URL_FALLBACK = "https://gitee.com/hong-j/apk/raw/main/firmware/TVBox_release-armeabi-hisense-java.apk";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int BUFFER_SIZE = 8192;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void checkForUpdates(final Context context) {
        final WeakReference<Context> contextRef = new WeakReference<>(context);
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                final Context ctx = contextRef.get();
                if (!isContextAlive(ctx)) {
                    return;
                }
                final AlertDialog progressDialog = showProgressDialog(ctx, "正在检查更新...");
                EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        final String latestVersion = fetchLatestVersion();
                        MAIN_HANDLER.post(new Runnable() {
                            @Override
                            public void run() {
                                dismissDialog(progressDialog);
                                Context c = contextRef.get();
                                if (!isContextAlive(c)) {
                                    return;
                                }
                                if (latestVersion != null) {
                                    showUpdateDialog(c, latestVersion);
                                } else {
                                    Toast.makeText(c, R.string.you_are_up_to_date, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private static String fetchLatestVersion() {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(UPDATE_URL);
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                JSONObject jsonObject = new JSONObject(readString(connection.getInputStream()));
                JSONArray elementsArray = jsonObject.getJSONArray("elements");

                if (elementsArray.length() > 0) {
                    JSONObject elementObject = elementsArray.getJSONObject(0);
                    String latestVersionName = elementObject.optString("versionName", null);
                    if (latestVersionName != null && isNewerVersion(latestVersionName, BuildConfig.VERSION_NAME)) {
                        return latestVersionName;
                    }
                }
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "检查更新失败", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return null;
    }

    /**
     * 比较 "1.0.yyyyMMdd_HHmm" 格式的版本号，仅在远端更新时间晚于本地时提示更新
     */
    private static boolean isNewerVersion(String remote, String local) {
        Long remoteTime = parseVersionTime(remote);
        Long localTime = parseVersionTime(local);
        if (remoteTime != null && localTime != null) {
            return remoteTime > localTime;
        }
        return remote != null && !remote.equals(local);
    }

    private static Long parseVersionTime(String version) {
        if (version == null) {
            return null;
        }
        int idx = version.lastIndexOf('.');
        if (idx < 0 || idx + 1 >= version.length()) {
            return null;
        }
        String stamp = version.substring(idx + 1).replace("_", "");
        if (stamp.length() != 12) {
            return null;
        }
        try {
            return Long.parseLong(stamp);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 按当前构建变体（abi-brand-mode）动态生成下载地址，
     * normal 模式对应远端文件名中的 java
     */
    private static String getApkUrl() {
        try {
            String[] flavors = BuildConfig.FLAVOR.split("-");
            if (flavors.length >= 3) {
                String mode = "normal".equals(flavors[2]) ? "java" : flavors[2];
                return String.format(APK_URL_TEMPLATE, flavors[0], flavors[1], mode);
            }
        } catch (Throwable ignored) {
        }
        return APK_URL_FALLBACK;
    }

    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        return connection;
    }

    private static String readString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return new String(buffer.toByteArray(), "UTF-8");
    }

    private static void showUpdateDialog(final Context context, String latestVersion) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.new_version_available, latestVersion));
        builder.setMessage(context.getString(R.string.update_message));

        builder.setPositiveButton(context.getString(R.string.download), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                downloadAndInstall(context);
            }
        });

        builder.setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.show();
    }

    private static void downloadAndInstall(final Context context) {
        final WeakReference<Context> contextRef = new WeakReference<>(context);
        final Context appContext = context.getApplicationContext();
        final AlertDialog progressDialog = showProgressDialog(context, "正在下载更新...");
        final ProgressBar progressBar = progressDialog.findViewById(R.id.progressBar);

        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final String filePath = downloadApk(appContext, progressBar);
                MAIN_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        dismissDialog(progressDialog);
                        Context c = contextRef.get();
                        if (!isContextAlive(c)) {
                            return;
                        }
                        if (filePath != null) {
                            installApk(c, filePath);
                        } else {
                            Toast.makeText(c, R.string.download_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private static String downloadApk(Context context, final ProgressBar progressBar) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(getApkUrl());
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                long contentLength = connection.getContentLengthLong();
                File outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (outputDir == null) {
                    outputDir = context.getFilesDir();
                }
                File outputFile = new File(outputDir, "app-release.apk");

                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    long total = 0;
                    int lastPercent = -1;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        total += bytesRead;
                        outputStream.write(buffer, 0, bytesRead);

                        if (contentLength > 0) {
                            int percent = (int) (total * 100 / contentLength);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int progress = percent;
                                MAIN_HANDLER.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (progressBar != null) {
                                            progressBar.setProgress(progress);
                                        }
                                    }
                                });
                            }
                        }
                    }
                }

                return outputFile.getAbsolutePath();
            }
        } catch (IOException e) {
            Log.e(TAG, "下载更新失败", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return null;
    }

    private static AlertDialog showProgressDialog(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
        TextView textView = view.findViewById(R.id.tv_update_message);
        if (textView != null) {
            textView.setText(message);
        }
        builder.setView(view);

        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }

    private static void dismissDialog(AlertDialog dialog) {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private static boolean isContextAlive(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return context != null;
    }

    private static void installApk(Context context, String filePath) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        File file = new File(filePath);
        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
