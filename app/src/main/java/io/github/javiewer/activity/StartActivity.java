package io.github.javiewer.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import io.github.javiewer.Configurations;
import io.github.javiewer.JAViewer;
import io.github.javiewer.Properties;
import io.github.javiewer.R;
import io.github.javiewer.adapter.item.DataSource;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class StartActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        checkPermissions(); //检查权限，创建配置
    }

    public void readProperties() {
        Request request = new Request.Builder()
                .url("https://raw.githubusercontent.com/SplashCodes/JAViewer/master/properties.json")
                .build();
        JAViewer.HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final Properties properties = JAViewer.parseJson(Properties.class, response.body().string());
                if (properties != null) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                handleProperties(properties);
                            } catch (URISyntaxException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        });
    }

    public void handleProperties(Properties properties) throws URISyntaxException {
        JAViewer.DATA_SOURCES.clear();
        JAViewer.DATA_SOURCES.addAll(properties.getDataSources());

        JAViewer.hostReplacements.clear();
        for (DataSource source : JAViewer.DATA_SOURCES) {
            String host = new URI(source.getLink()).getHost();
            for (String h : source.legacies) {
                JAViewer.hostReplacements.put(h, host);
            }
        }

        int currentVersion;
        try {
            currentVersion = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Hacked???");
        }

        if (properties.getLatestVersionCode() > 0 && currentVersion < properties.getLatestVersionCode()) {

            String message = "新版本：" + properties.getLatestVersion();
            if (properties.getChangelog() != null) {
                message += "\n\n更新日志：\n\n" + properties.getChangelog() + "\n";
            }

            final boolean[] update = {false};
            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("发现更新")
                    .setMessage(message)
                    .setNegativeButton("忽略更新", null)
                    .setPositiveButton("更新", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            update[0] = true;
                        }
                    })
                    .create();
            dialog.show();

            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    start();
                    if (update[0]) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SplashCodes/JAViewer/releases")));
                    }
                }
            });
        } else {
            start();
        }

    }

    public void start() {
        startActivity(new Intent(StartActivity.this, MainActivity.class));
        finish();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Dexter.withActivity(this)
                    .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .withListener(new PermissionListener() {
                        @Override
                        public void onPermissionGranted(PermissionGrantedResponse response) {
                            checkPermissions();
                        }

                        @Override
                        public void onPermissionDenied(PermissionDeniedResponse response) {
                            new AlertDialog.Builder(StartActivity.this)
                                    .setTitle("权限申请")
                                    .setCancelable(false)
                                    .setMessage("JAViewer 需要储存空间权限，储存用户配置。请您允许。")
                                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            checkPermissions();
                                        }
                                    })
                                    .show();
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    })
                    .onSameThread()
                    .check();
            return;
        }

        File oldConfig = new File(StartActivity.this.getExternalFilesDir(null), "configurations.json");
        File config = new File(JAViewer.getStorageDir(), "configurations.json");
        if (oldConfig.exists()) {
            oldConfig.renameTo(config);
        }

        File noMedia = new File(JAViewer.getStorageDir(), ".nomedia");
        try {
            noMedia.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        JAViewer.CONFIGURATIONS = Configurations.load(config);

        readProperties();
    }

}
