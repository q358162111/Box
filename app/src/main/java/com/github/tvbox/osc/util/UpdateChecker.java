package com.github.tvbox.osc.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String UPDATE_URL = "https://codeberg.org/caihj/apk/raw/branch/main/firmware/output-metadata.json";
    private static final String APK_URL = "https://codeberg.org/caihj/apk/raw/branch/main/firmware/TVBox_release-arm64-generic.apk";

    public static void checkForUpdates(Context context) {
        new CheckUpdateTask(context).execute();
    }

    public static class CheckUpdateTask extends AsyncTask<Void, Void, String> {
        private final Context context;
        private AlertDialog progressDialog;

        public CheckUpdateTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog();
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL(UPDATE_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    StringBuilder response = new StringBuilder();

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        response.append(new String(buffer, 0, bytesRead));
                    }

                    inputStream.close();

                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONArray elementsArray = jsonObject.getJSONArray("elements");

                    if (elementsArray.length() > 0) {
                        JSONObject elementObject = elementsArray.getJSONObject(0);
                        String latestVersion = elementObject.getString("versionName");
                        String currentVersion = BuildConfig.VERSION_NAME;

                        if (!latestVersion.equals(currentVersion)) {
                            return latestVersion;
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                Log.e(TAG, "检查更新失败", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(String latestVersion) {
            super.onPostExecute(latestVersion);
            dismissProgressDialog();

            if (latestVersion != null) {
                showUpdateDialog(context, latestVersion);
            } else {
                Toast.makeText(context, R.string.you_are_up_to_date, Toast.LENGTH_SHORT).show();
            }
        }

        private void showProgressDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setCancelable(false);

            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(R.layout.dialog_progress, null);
            ProgressBar progressBar = view.findViewById(R.id.progressBar);
            builder.setView(view);


            progressDialog = builder.create();
            progressDialog.show();
        }

        private void dismissProgressDialog() {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }

    private static void showUpdateDialog(Context context, String latestVersion) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.new_version_available, latestVersion));
        builder.setMessage(context.getString(R.string.update_message));

        builder.setPositiveButton(context.getString(R.string.download), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new DownloadTask(context).execute();
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

    private static class DownloadTask extends AsyncTask<Void, Integer, String> {
        private final Context context;
        private AlertDialog progressDialog;
        private TextView updateMessageTextView;
        public DownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog();
            // 获取 TextView 实例
            updateMessageTextView = progressDialog.findViewById(R.id.tv_update_message);
            // 更新 TextView 的文本
            updateMessageTextView.setText("正在下载更新...");
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL(APK_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    int contentLength = connection.getContentLength();
                    InputStream inputStream = connection.getInputStream();
                    File outputFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "app-release.apk");
                    FileOutputStream outputStream = new FileOutputStream(outputFile);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long total = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        total += bytesRead;
                        publishProgress((int) ((total * 100) / contentLength));
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    outputStream.close();
                    inputStream.close();

                    return outputFile.getAbsolutePath();
                }
            } catch (IOException e) {
                Log.e(TAG, "下载更新失败", e);
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (progressDialog != null && progressDialog.isShowing()) {
                ProgressBar progressBar = progressDialog.findViewById(R.id.progressBar);
                if (progressBar != null) {
                    progressBar.setProgress(values[0]);
                }
            }
        }

        @Override
        protected void onPostExecute(String filePath) {
            super.onPostExecute(filePath);
            dismissProgressDialog();

            if (filePath != null) {
                // 更新 TextView 的文本
                updateMessageTextView.setText("下载完成");
                // 安装 APK
                installApk(context, filePath);
            } else {
                Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show();
            }
        }

        private void showProgressDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setCancelable(false);

            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(R.layout.dialog_progress, null);
            ProgressBar progressBar = view.findViewById(R.id.progressBar);
            builder.setView(view);

            progressDialog = builder.create();
            progressDialog.show();
        }

        private void dismissProgressDialog() {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }

        private void installApk(Context context, String filePath) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            File file = new File(filePath);
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
