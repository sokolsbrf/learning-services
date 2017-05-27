package ru.sberbank.learning.downloader;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.PermissionChecker;
import android.view.View;
import android.widget.ProgressBar;

public class MainActivity extends Activity {

    private View mStartButton;
    private ProgressBar mProgressBar;

    private DownloadService.LocalBinder mBinder;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = (DownloadService.LocalBinder) service;
            mBinder.setForeground(false);
            showServiceState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBinder = null;
        }
    };

    private BroadcastReceiver mChangesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showServiceState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStartButton = findViewById(R.id.button_download);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownload();
            }
        });

        mProgressBar = (ProgressBar) findViewById(R.id.progress);
        mProgressBar.setMax(100);
        mProgressBar.setProgress(0);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent service = new Intent(this, DownloadService.class);
        bindService(service, mConnection, 0);

        IntentFilter filter = new IntentFilter(DownloadService.ACTION_DOWNLOAD_STATE_CHANGED);
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(mChangesReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(mChangesReceiver);

        if (mBinder != null) {
            mBinder.setForeground(true);
            unbindService(mConnection);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDownload();
        }
    }

    private void startDownload() {
        Intent downloadIntent = new Intent(this, DownloadService.class);
        downloadIntent.setData(Uri.parse("http://core0.staticworld.net/images/article/2016/07/android-tv-100670391-orig.jpg"));
        downloadIntent.putExtra(DownloadService.EXTRA_FILE_NAME, "HugeFile.jpg");
        startService(downloadIntent);
        bindService(downloadIntent, mConnection, 0);

        mStartButton.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void requestWritePermission() {
        ActivityCompat.requestPermissions(this,
                new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
    }

    private void showServiceState() {
        if (mBinder == null) {
            mProgressBar.setVisibility(View.GONE);
            mStartButton.setVisibility(View.VISIBLE);
            return;
        } else {
            mProgressBar.setVisibility(View.VISIBLE);
            mStartButton.setVisibility(View.GONE);
        }

        if (mBinder.isHasErrors()) {
            if (PermissionChecker.checkSelfPermission(MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PermissionChecker.PERMISSION_GRANTED) {
                requestWritePermission();
                mBinder = null;
                unbindService(mConnection);
            } else {
                mStartButton.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
            }
        } else {
            mProgressBar.setProgress(mBinder.getProgress());

            if (mBinder.isComplete()) {
                mProgressBar.setVisibility(View.GONE);
                mStartButton.setVisibility(View.VISIBLE);
            }
        }
    }
}
