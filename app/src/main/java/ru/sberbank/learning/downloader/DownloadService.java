package ru.sberbank.learning.downloader;

import android.Manifest;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.PermissionChecker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


public class DownloadService extends IntentService {

    public static final String ACTION_DOWNLOAD = DownloadService.class.getCanonicalName()
            + ".ACTION_DOWNLOAD";
    public static final String EXTRA_FILE_NAME = DownloadService.class.getCanonicalName()
            + ".EXTRA_FILE_NAME";

    public static final String ACTION_DOWNLOAD_STATE_CHANGED = "download_state_changed";
    private static final int NOTIFICATION_PERMISSION = R.id.button_download;
    private static final int NOTIFICATION_MAIN = R.id.progress;


    private boolean mDownloadCompleted = false;
    private boolean mHasErrors = false;
    private int mProgress = 0;

    private boolean mCurrentlyBound = false;
    private boolean mIsForeground = false;

    public DownloadService() {
        super("DownloadService");
        setIntentRedelivery(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        mCurrentlyBound = true;
        return new LocalBinder();
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        mCurrentlyBound = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mCurrentlyBound = false;
        return true;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            URL url;

            try {
                url = new URL(intent.getDataString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return;
            }

            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);

            try {
                downloadFile(url, fileName);
            } catch (IOException e) {
                mHasErrors = true;
                mProgress = 0;
            } catch (SecurityException noAccessToSD) {
                mHasErrors = true;
                mProgress = 0;
                requestPermissions();
            } finally {
                mDownloadCompleted = true;
            }
        }
    }

    private void downloadFile(URL url, String fileName) throws IOException, SecurityException {

        mDownloadCompleted = false;
        mHasErrors = false;
        mProgress = 0;
        notifyDownloadStateChange();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int size = connection.getContentLength();
        int downloaded = 0;

        byte[] buffer = new byte[8096];
        BufferedInputStream is = new BufferedInputStream(connection.getInputStream());

        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PermissionChecker.PERMISSION_GRANTED) {
            throw new SecurityException();
        }

        File directory = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        directory.mkdirs();

        File outFile = new File(directory, fileName);
        BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outFile));

        int readed;
        while ((readed = is.read(buffer)) > 0) {
            os.write(buffer, 0, readed);
            downloaded += readed;
            int percent = computePercent(size, downloaded);

            if (percent != mProgress) {
                mProgress = percent;
                notifyDownloadStateChange();
                updateMainIcon();
            }
        }

        mDownloadCompleted = true;
        mHasErrors = false;
        mProgress = 100;
    }

    private int computePercent(int total, int progress) {
        int part = Math.max(1, total / 100);
        return Math.min(100, progress / part);
    }

    /**
     * Оповещает заинтересованных подписчиков об изменении статуса загрузки (процент, успех).
     */
    private void notifyDownloadStateChange() {
        Intent notification = new Intent(ACTION_DOWNLOAD_STATE_CHANGED);
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.sendBroadcast(notification);
    }

    /**
     * Запросить разрешения, иконкой или в UI
     */
    private void requestPermissions() {
        if (mCurrentlyBound) {
            mHasErrors = true;
            mProgress = 0;
            notifyDownloadStateChange();
        } else {
            showNoPermissionIcon();
        }
    }

    private void showNoPermissionIcon() {
        Intent openMain = new Intent(this, MainActivity.class);
        openMain.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, openMain,
                PendingIntent.FLAG_ONE_SHOT);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_no_permission);
        builder.setAutoCancel(true);
        builder.setContentIntent(pending);
        builder.setContentTitle(getString(R.string.error_no_permission));
        builder.setContentText(getString(R.string.error_no_permission_details));
        Notification notification = builder.build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_PERMISSION, notification);
    }

    private void updateMainIcon() {
        if (!mIsForeground) {
            return;
        }
        
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_MAIN, buildMainIcon());
    }

    private Notification buildMainIcon() {
        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_download, mProgress * 100);
        builder.setContentText(getString(R.string.status_downloading));
        builder.setProgress(100, mProgress, false);

        Intent runMain = new Intent(this, MainActivity.class);
        runMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, runMain,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(pendingIntent);

        return builder.build();
    }

    public class LocalBinder extends Binder {

        public boolean isComplete() {
            return mDownloadCompleted;
        }

        public boolean isHasErrors() {
            return mHasErrors;
        }

        public int getProgress() {
            return mProgress;
        }

        public void setForeground(boolean foreground) {
            if (foreground) {
                mIsForeground = true;
                startForeground(NOTIFICATION_MAIN, buildMainIcon());
            } else {
                mIsForeground = false;
                stopForeground(true);
            }
        }
    }

}
