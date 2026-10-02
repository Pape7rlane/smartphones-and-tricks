package com.ppsr.control;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.chaquo.python.PyException;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PythonServerService extends Service {
    static final String ACTION_START = "com.ppsr.control.START";
    static final String ACTION_STOP = "com.ppsr.control.STOP";
    static final String ACTION_STATE = "com.ppsr.control.STATE";
    static final String EXTRA_RUNNING = "running";
    static final String EXTRA_ERROR = "error";
    private static final String CHANNEL = "ppsr_server";
    private static final int NOTIFICATION_ID = 21000;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "ppSR 服务",
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServer();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, notification("正在校验密码并启动..."));
        if (!StorageAccess.isGranted(this)) {
            broadcast(false, "存储权限已失效，请打开 ppSR 并重新授予所有文件访问权限");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        startServer();
        return START_STICKY;
    }

    private void startServer() {
        if (running) { broadcast(true, null); return; }
        executor.execute(() -> {
            try {
                File directory = ServerFiles.install(this);
                if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
                Python.getInstance().getModule("app_bridge").callAttr("start", directory.getAbsolutePath());
                running = true;
                getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,
                        notification("运行中 · 127.0.0.1:21000"));
                broadcast(true, null);
            } catch (Throwable error) {
                running = false;
                broadcast(false, error.toString());
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
    }

    private void stopServer() {
        executor.execute(() -> {
            try {
                if (Python.isStarted()) Python.getInstance().getModule("app_bridge").callAttr("stop");
            } catch (PyException ignored) {
            } finally {
                running = false;
                broadcast(false, null);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
    }

    private Notification notification(String status) {
        PendingIntent content = PendingIntent.getActivity(this, 0,
                new Intent(this, PasswordMainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopAction = PendingIntent.getService(this, 1,
                new Intent(this, PythonServerService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("ppSR 游戏服务器").setContentText(status)
                .setContentIntent(content).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "停止", stopAction).build()).build();
    }

    private void broadcast(boolean isRunning, String error) {
        Intent state = new Intent(ACTION_STATE).setPackage(getPackageName());
        state.putExtra(EXTRA_RUNNING, isRunning);
        if (error != null) state.putExtra(EXTRA_ERROR, error);
        sendBroadcast(state);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { executor.shutdown(); super.onDestroy(); }
}
