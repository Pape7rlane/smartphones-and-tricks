package com.ppsr.control;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONTokener;

public class MainActivity extends Activity {
    private static final long LARGE_JSON_SIZE = 512 * 1024;
    private static final int MAX_LIVE_LOG_BYTES = 128 * 1024;
    private static final long LIVE_LOG_REFRESH_MS = 500;
    private static final long SERVER_START_TIMEOUT_MS = 305_000;
    private static final String FIREFLY_SRTOOLS_URL = "https://srtools.punklorde.org/";
    private static final String NEONTEAM_SRTOOLS_URL = "https://srtools.neonteam.dev/1001/detail";
    private static final String RELEASE_PREFS = "release_info";
    private static final String HIDDEN_ANNOUNCEMENT_ID = "hidden_announcement_id";
    private static final String PENDING_SIGNATURE = "quark_pending_signature";
    private static final String PENDING_NAME = "quark_pending_name";
    private static final String PENDING_INSTALL_TIME = "quark_pending_install_time";
    private static final String PENDING_STARTED_AT = "quark_pending_started_at";
    private static final long UPDATE_GRACE_MS = 60_000L;
    private TextView serverState;
    private TextView startButton;
    private TextView lineupValue;
    private LinearLayout exRankContainer;
    private ServerUiState serverUiState = ServerUiState.STOPPED;
    private boolean receiverRegistered;
    private boolean storageInitialized;
    private boolean storageInitializationInProgress;
    private boolean notificationPermissionRequested;
    private boolean payloadUpdateRunning;
    private boolean updateCheckRunning;
    private AlertDialog updateLockDialog;
    private Runnable updateLockTicker;
    private boolean activityStarted;
    private ReleaseInfo releaseInfo;
    private AlertDialog announcementDialog;
    private AlertDialog forcedUpdateDialog;
    private String shownAnnouncementId;
    private long installedVersionCode;
    private final ExecutorService releaseExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ppsr-release-check");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ppsr-ui-startup");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler startupHandler = new Handler(Looper.getMainLooper());
    private final Runnable delayedStoragePermission = () -> {
        if (isFinishing() || isDestroyed()) return;
        if (isReleaseDialogShowing() || requiresUpdate(releaseInfo)) {
            scheduleStoragePermissionRetry();
            return;
        }
        if (StorageAccess.isGranted(this)) return;
        StorageAccess.request(this);
        toast(ui(R.string.storage_permission));
    };
    private final Handler liveLogHandler = new Handler(Looper.getMainLooper());
    private final Handler serverStateHandler = new Handler(Looper.getMainLooper());
    private final Runnable serverStartTimeout = () -> {
        if (serverUiState != ServerUiState.STARTING
                && serverUiState != ServerUiState.VALIDATING) return;
        setServerUiState(ServerUiState.STOPPED);
        new AlertDialog.Builder(this).setTitle(ui(R.string.start_failed))
                .setMessage(ui(R.string.server_start_timeout))
                .setPositiveButton(ui(R.string.close), null).show();
    };
    private AlertDialog liveLogDialog;
    private TextView liveLogText;
    private ScrollView liveLogScroll;
    private long liveLogLength = -1;
    private long liveLogModified = -1;
    private final Runnable liveLogRefresh = new Runnable() {
        @Override public void run() {
            if (liveLogDialog == null || !liveLogDialog.isShowing()) return;
            refreshLiveLog();
            liveLogHandler.postDelayed(this, LIVE_LOG_REFRESH_MS);
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            boolean running = intent.getBooleanExtra(PythonServerService.EXTRA_RUNNING, false);
            boolean starting = intent.getBooleanExtra(PythonServerService.EXTRA_STARTING, false);
            if (starting) showValidatingState();
            else showState(running);
            String error = intent.getStringExtra(PythonServerService.EXTRA_ERROR);
            if (error != null) new AlertDialog.Builder(MainActivity.this)
                    .setTitle(ui(R.string.start_failed)).setMessage(error)
                    .setPositiveButton(ui(R.string.close), null).show();
        }
    };

    private enum ServerUiState { STOPPED, STARTING, VALIDATING, RUNNING }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(getColor(R.color.background));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_main);
        bindViews();
        applyLanguage();
        // Draw the home screen before opening all-files settings on first launch.
        startupHandler.postDelayed(delayedStoragePermission, 250);
    }

    private void bindViews() {
        serverState = findViewById(R.id.serverState);
        startButton = findViewById(R.id.startButton);
        lineupValue = findViewById(R.id.lineupValue);
        exRankContainer = findViewById(R.id.exRankContainer);
        startButton.setOnClickListener(v -> startServer());
        findViewById(R.id.stopButton).setOnClickListener(v -> stopServer());
        findViewById(R.id.tbButton).setOnClickListener(v -> chooseTrailblazer());
        findViewById(R.id.marchButton).setOnClickListener(v -> chooseMarch());
        findViewById(R.id.selectButton).setOnClickListener(v -> choosePath(false));
        findViewById(R.id.addPathButton).setOnClickListener(v -> choosePath(true));
        findViewById(R.id.manualButton).setOnClickListener(v -> inputCharacterIds());
        findViewById(R.id.mapTeleportButton).setOnClickListener(v -> showMapTeleport());
        findViewById(R.id.mapTeleportHelpButton).setOnClickListener(v -> showMapTeleportHelp());
        findViewById(R.id.removeButton).setOnClickListener(v -> inputRemoveIndex());
        findViewById(R.id.clearButton).setOnClickListener(v -> confirmClear());
        findViewById(R.id.iconButton).setOnClickListener(v -> chooseIcon());
        findViewById(R.id.miscButton).setOnClickListener(v -> openMisc());
        findViewById(R.id.liveLogButton).setOnClickListener(v -> showLiveLog());
        findViewById(R.id.checkUpdateButton).setOnClickListener(v -> checkServerPayloadUpdate());
        applyPayloadLabel();
        findViewById(R.id.fireflySrtoolsButton).setOnClickListener(
                v -> openWebsite(FIREFLY_SRTOOLS_URL));
        findViewById(R.id.neonteamSrtoolsButton).setOnClickListener(
                v -> openWebsite(NEONTEAM_SRTOOLS_URL));
    }

    @Override protected void onStart() {
        super.onStart();
        activityStarted = true;
        IntentFilter filter = new IntentFilter(PythonServerService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, filter);
        receiverRegistered = true;
        startService(new Intent(this, PythonServerService.class)
                .setAction(PythonServerService.ACTION_STATUS));
        checkReleaseInfo();
        checkQuarkApk();
        refreshConfiguration();
    }

    @Override protected void onResume() {
        super.onResume();
        showForcedUpdateIfRequired();
        if (StorageAccess.isGranted(this)) {
            refreshConfiguration();
            requestNotificationPermissionWhenReady();
        }
    }

    @Override protected void onStop() {
        activityStarted = false;
        if (receiverRegistered) { unregisterReceiver(stateReceiver); receiverRegistered = false; }
        shownAnnouncementId = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        startupHandler.removeCallbacks(delayedStoragePermission);
        if (updateLockTicker != null) startupHandler.removeCallbacks(updateLockTicker);
        startupExecutor.shutdown();
        releaseExecutor.shutdownNow();
        closeLiveLog();
        serverStateHandler.removeCallbacks(serverStartTimeout);
        super.onDestroy();
    }

    private void scheduleStoragePermissionRetry() {
        startupHandler.postDelayed(delayedStoragePermission, 500);
    }

    private void checkQuarkApk() {
        Context appContext = getApplicationContext();
        updateCheckRunning = true;
        releaseExecutor.execute(() -> {
            try {
            SharedPreferences preferences = appContext.getSharedPreferences(RELEASE_PREFS, MODE_PRIVATE);
            long installTime = currentInstallTime();
            String pending = preferences.getString(PENDING_SIGNATURE, "");
            if (!pending.isEmpty()) {
                boolean reinstalled = installTime != 0
                        && installTime != preferences.getLong(PENDING_INSTALL_TIME, 0);
                if (reinstalled && pendingMatchesInstalledApk(preferences, installTime)) return;
                String pendingName = preferences.getString(PENDING_NAME, "");
                runOnUiThread(() -> {
                    if (activityStarted && !isFinishing() && !isDestroyed()) {
                        showQuarkUpdateDialog(pendingName);
                    }
                });
                return;
            }
            try {
                ReleaseInfo.ShareApk apk = ReleaseInfo.fetchQuarkApk();
                if (apk == null) return;
                String stored = preferences.getString(ReleaseInfo.QUARK_APK_SIGNATURE, "");
                if (apk.signature.equals(stored)) return;
                if (apk.size > 0 && apk.size == currentApkSize()) {
                    // Installed build is the one published in the share: nothing to do.
                    preferences.edit().putString(ReleaseInfo.QUARK_APK_SIGNATURE, apk.signature).apply();
                    return;
                }
                preferences.edit()
                        .putString(PENDING_SIGNATURE, apk.signature)
                        .putString(PENDING_NAME, apk.name)
                        .putLong(PENDING_INSTALL_TIME, installTime)
                        .putLong(PENDING_STARTED_AT, System.currentTimeMillis())
                        .apply();
                runOnUiThread(() -> {
                    if (activityStarted && !isFinishing() && !isDestroyed()) {
                        showQuarkUpdateDialog(apk.name);
                    }
                });
            } catch (Exception failure) {
                // Cannot verify the published APK: stay locked instead of trusting the client.
                runOnUiThread(() -> {
                    if (activityStarted && !isFinishing() && !isDestroyed()) {
                        showLockedDialog(ui(R.string.update_check_failed_title),
                                ui(R.string.update_check_failed_message));
                    }
                });
            }
            } finally {
                runOnUiThread(() -> updateCheckRunning = false);
            }
        });
    }

    /**
     * The app was reinstalled: only unlock when the installed APK is byte-for-byte the
     * package published in the share folder (same size). Anything else keeps the lock.
     */
    private boolean pendingMatchesInstalledApk(SharedPreferences preferences, long installTime) {
        try {
            ReleaseInfo.ShareApk apk = ReleaseInfo.fetchQuarkApk();
            if (apk == null) return false;
            long installedSize = currentApkSize();
            if (apk.size <= 0 || apk.size != installedSize) {
                // Wrong APK installed: keep the lock and restart the grace period.
                preferences.edit()
                        .putString(PENDING_SIGNATURE, apk.signature)
                        .putString(PENDING_NAME, apk.name)
                        .putLong(PENDING_INSTALL_TIME, installTime)
                        .putLong(PENDING_STARTED_AT, System.currentTimeMillis())
                        .apply();
                return false;
            }
            preferences.edit()
                    .putString(ReleaseInfo.QUARK_APK_SIGNATURE, apk.signature)
                    .remove(PENDING_SIGNATURE)
                    .remove(PENDING_NAME)
                    .remove(PENDING_INSTALL_TIME)
                    .remove(PENDING_STARTED_AT)
                    .apply();
            return true;
        } catch (Exception offline) {
            // Cannot verify without the share listing: stay locked.
            return false;
        }
    }

    /** Size of the installed base.apk, used to recognise the published build. */
    private long currentApkSize() {
        return new File(getPackageCodePath()).length();
    }

    private long currentInstallTime() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0;
        }
    }

    /** Blocking update prompt: stays on screen until a new APK is installed. */
    private void showQuarkUpdateDialog(String apkName) {
        if (isFinishing() || isDestroyed()) return;
        if (updateLockDialog != null && updateLockDialog.isShowing()) return;
        final String name = apkName.isEmpty() ? "ppSR" : apkName;
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(ui(R.string.update_required_title))
                .setMessage(ui(R.string.update_required_message, name, 60))
                .setPositiveButton(ui(R.string.update_required_action), null)
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseInfo.QUARK_SHARE_URL)));
                    } catch (Exception error) {
                        toast(ui(R.string.update_required_open_failed));
                    }
                }));
        updateLockDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (updateLockDialog == dialog) updateLockDialog = null;
        });
        stopServer();
        dialog.show();
        startUpdateLockCountdown(dialog, name);
    }

    private void startUpdateLockCountdown(AlertDialog dialog, String apkName) {
        long startedAt = getSharedPreferences(RELEASE_PREFS, MODE_PRIVATE)
                .getLong(PENDING_STARTED_AT, System.currentTimeMillis());
        updateLockTicker = new Runnable() {
            @Override public void run() {
                if (updateLockDialog != dialog || !dialog.isShowing()) return;
                long remaining = (UPDATE_GRACE_MS - (System.currentTimeMillis() - startedAt) + 999) / 1000;
                if (remaining > 0) {
                    dialog.setMessage(ui(R.string.update_required_message, apkName, remaining));
                    startupHandler.postDelayed(this, 1000);
                } else {
                    showLockedDialog(apkName);
                }
            }
        };
        startupHandler.post(updateLockTicker);
    }

    private void showLockedDialog(String apkName) {
        String name = apkName.isEmpty() ? "ppSR" : apkName;
        showLockedDialog(ui(R.string.update_locked_title), ui(R.string.update_locked_message, name));
    }

    /** Locked state: only an unlock code or the share link gets the user out. */
    private void showLockedDialog(String title, String text) {
        if (isFinishing() || isDestroyed()) return;
        if (updateLockDialog != null && updateLockDialog.isShowing()) updateLockDialog.dismiss();
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView message = new TextView(this);
        message.setText(text);
        message.setPadding(padding, padding, padding, 0);
        final EditText input = new EditText(this);
        input.setHint(ui(R.string.update_unlock_hint));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(padding, 0, padding, 0);
        content.addView(message);
        content.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(content)
                .setPositiveButton(ui(R.string.update_unlock_action), null)
                .setNegativeButton(ui(R.string.update_required_action), null)
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (unlockWithCode(input.getText().toString().trim())) {
                    toast(ui(R.string.update_unlock_ok));
                    dialog.dismiss();
                } else {
                    toast(ui(R.string.update_unlock_wrong));
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseInfo.QUARK_SHARE_URL)));
                } catch (Exception error) {
                    toast(ui(R.string.update_required_open_failed));
                }
            });
        });
        updateLockDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (updateLockDialog == dialog) updateLockDialog = null;
        });
        dialog.show();
    }

    /** Accepts the unlock code from update.json and remembers the current share APK. */
    private boolean unlockWithCode(String code) {
        if (code.isEmpty()) return false;
        ReleaseInfo info = releaseInfo != null ? releaseInfo : ReleaseInfo.loadCachedOrBundled(this);
        String expected = info == null ? "" : info.unlockCode;
        if (expected.isEmpty() || !expected.equalsIgnoreCase(code)) return false;
        SharedPreferences preferences = getSharedPreferences(RELEASE_PREFS, MODE_PRIVATE);
        String pending = preferences.getString(PENDING_SIGNATURE, "");
        SharedPreferences.Editor editor = preferences.edit()
                .remove(PENDING_SIGNATURE)
                .remove(PENDING_NAME)
                .remove(PENDING_INSTALL_TIME)
                .remove(PENDING_STARTED_AT);
        if (!pending.isEmpty()) editor.putString(ReleaseInfo.QUARK_APK_SIGNATURE, pending);
        editor.apply();
        return true;
    }

    private void checkReleaseInfo() {
        try {
            android.content.pm.PackageInfo packageInfo = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            installedVersionCode = Build.VERSION.SDK_INT >= 28
                    ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            installedVersionCode = 0;
        }

        applyReleaseInfo(ReleaseInfo.loadCachedOrBundled(this));
        Context appContext = getApplicationContext();
        releaseExecutor.execute(() -> {
            try {
                ReleaseInfo remote = ReleaseInfo.fetchAndCache(appContext);
                runOnUiThread(() -> {
                    if (activityStarted && !isFinishing() && !isDestroyed()) applyReleaseInfo(remote);
                });
            } catch (Exception ignored) {
                // Keep using the cached or bundled notice when the network is unavailable.
            }
        });
    }

    private void applyReleaseInfo(ReleaseInfo info) {
        releaseInfo = info;
        if (requiresUpdate(info)) {
            if (announcementDialog != null && announcementDialog.isShowing()) {
                announcementDialog.dismiss();
            }
            shownAnnouncementId = null;
            showForcedUpdateIfRequired();
            return;
        }
        if (forcedUpdateDialog != null && forcedUpdateDialog.isShowing()) {
            forcedUpdateDialog.dismiss();
        }
        showAnnouncementIfRequired(info);
    }

    private boolean requiresUpdate(ReleaseInfo info) {
        return info != null && info.latestVersionCode > installedVersionCode
                && !info.downloadUrl.isEmpty();
    }

    private boolean isReleaseDialogShowing() {
        return (announcementDialog != null && announcementDialog.isShowing())
                || (forcedUpdateDialog != null && forcedUpdateDialog.isShowing());
    }

    private void showAnnouncementIfRequired(ReleaseInfo info) {
        if (isFinishing() || isDestroyed() || info == null
                || info.announcementId.isEmpty() || info.announcementTitle.isEmpty()
                || info.announcementBody.isEmpty() || requiresUpdate(info)) return;
        String hiddenId = getSharedPreferences(RELEASE_PREFS, MODE_PRIVATE)
                .getString(HIDDEN_ANNOUNCEMENT_ID, "");
        if (info.announcementId.equals(hiddenId)
                || info.announcementId.equals(shownAnnouncementId)) return;
        if (announcementDialog != null && announcementDialog.isShowing()) {
            announcementDialog.dismiss();
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(info.announcementBody);
        body.setTextSize(16);
        body.setPadding(padding, padding, padding, padding);
        scroll.addView(body);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(300 * getResources().getDisplayMetrics().density)));
        CheckBox hide = new CheckBox(this);
        hide.setText("不再显示此公告");
        hide.setPadding(padding, 0, padding, 0);
        content.addView(hide);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(info.announcementTitle)
                .setView(content)
                .setPositiveButton("关闭", (d, which) -> {
                    if (hide.isChecked()) {
                        getSharedPreferences(RELEASE_PREFS, MODE_PRIVATE).edit()
                                .putString(HIDDEN_ANNOUNCEMENT_ID, info.announcementId).apply();
                    }
                })
                .create();
        announcementDialog = dialog;
        shownAnnouncementId = info.announcementId;
        dialog.setOnDismissListener(ignored -> {
            if (announcementDialog == dialog) announcementDialog = null;
            startupHandler.postDelayed(this::requestNotificationPermissionWhenReady, 250);
        });
        dialog.show();
    }

    private void requestNotificationPermissionWhenReady() {
        if (notificationPermissionRequested || !StorageAccess.isGranted(this)
                || requiresUpdate(releaseInfo) || isReleaseDialogShowing()
                || isFinishing() || isDestroyed()) return;
        notificationPermissionRequested = true;
        requestNotificationPermission();
    }

    private void showForcedUpdateIfRequired() {
        ReleaseInfo info = releaseInfo;
        if (isFinishing() || isDestroyed() || !requiresUpdate(info)
                || (forcedUpdateDialog != null && forcedUpdateDialog.isShowing())) return;
        String version = info.latestVersionName.isEmpty()
                ? String.valueOf(info.latestVersionCode) : info.latestVersionName;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("需要更新")
                .setMessage("发现 ppSR " + version + "。请先更新到最新版本后再继续使用。")
                .setPositiveButton("前往夸克网盘", null)
                .create();
        forcedUpdateDialog = dialog;
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (forcedUpdateDialog == dialog) forcedUpdateDialog = null;
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)));
                        dialog.dismiss();
                    } catch (Exception error) {
                        toast("无法打开夸克更新链接，请检查设备上的浏览器或夸克网盘。");
                    }
                }));
        dialog.show();
    }

    /** Shows the payload folder name (e.g. 4.7.5X) once a payload has been downloaded. */
    private void applyPayloadLabel() {
        String label = getSharedPreferences(PayloadUpdater.PREFS, MODE_PRIVATE)
                .getString(PayloadUpdater.KEY_LABEL, "");
        TextView view = findViewById(R.id.versionLabel);
        if (view != null && !label.isEmpty()) view.setText(label);
    }

    private void checkServerPayloadUpdate() {
        if (payloadUpdateRunning) return;
        payloadUpdateRunning = true;
        toast(ui(R.string.update_payload_checking));
        startupExecutor.execute(() -> {
            int files = -1;
            String error = null;
            try {
                String payloadUrl = "";
                try {
                    payloadUrl = ReleaseInfo.fetchAndCache(this).payloadUrl;
                } catch (Exception ignored) {
                    // Fall back to the built-in payload URL.
                }
                files = PayloadUpdater.update(this, payloadUrl);
            } catch (Exception failure) {
                String detail = failure.getMessage();
                error = detail == null ? failure.getClass().getSimpleName() : detail;
            }
            int count = files;
            String message = error;
            runOnUiThread(() -> {
                payloadUpdateRunning = false;
                if (isFinishing() || isDestroyed()) return;
                if (message != null) {
                    toast(ui(R.string.update_payload_failed, message));
                    return;
                }
                applyPayloadLabel();
                new AlertDialog.Builder(this)
                        .setTitle(ui(R.string.update_payload_done_title))
                        .setMessage(ui(R.string.update_payload_done, count))
                        .setPositiveButton(ui(R.string.update_payload_restart), (dialog, which) -> {
                            stopServer();
                            startupHandler.postDelayed(this::startServer, 1500);
                        })
                        .setNegativeButton(ui(R.string.close), null)
                        .show();
            });
        });
    }

    private void startServer() {
        if (updateLockDialog != null && updateLockDialog.isShowing()) return;
        if (updateCheckRunning) {
            toast(ui(R.string.update_check_wait));
            return;
        }
        if (!ensureStorageAccess()) return;
        Intent intent = new Intent(this, PythonServerService.class).setAction(PythonServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        setServerUiState(ServerUiState.STARTING);
    }

    protected boolean ensureStorageAccess() {
        if (StorageAccess.isGranted(this)) {
            if (storageInitialized) return true;
            initializeStorage();
            toast(ui(R.string.storage_initializing));
            return false;
        }
        StorageAccess.request(this);
        toast(ui(R.string.storage_permission));
        return false;
    }

    private void initializeStorage() {
        if (storageInitialized || storageInitializationInProgress
                || !StorageAccess.isGranted(this)) return;
        storageInitializationInProgress = true;
        Context appContext = getApplicationContext();
        startupExecutor.execute(() -> {
            try {
                ServerFiles.install(appContext);
                runOnUiThread(() -> {
                    storageInitializationInProgress = false;
                    storageInitialized = true;
                    refreshLineup();
                    refreshExRank();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    storageInitializationInProgress = false;
                    storageInitialized = false;
                    if (!isFinishing() && !isDestroyed()) showError(error);
                });
            }
        });
    }

    private void refreshConfiguration() {
        if (!StorageAccess.isGranted(this)) return;
        if (!storageInitialized) {
            initializeStorage();
            return;
        }
        refreshLineup();
        refreshExRank();
    }

    private void openWebsite(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void stopServer() {
        startService(new Intent(this, PythonServerService.class).setAction(PythonServerService.ACTION_STOP));
    }

    private void showState(boolean running) {
        setServerUiState(running ? ServerUiState.RUNNING : ServerUiState.STOPPED);
    }

    protected void showValidatingState() {
        setServerUiState(ServerUiState.VALIDATING);
        serverStateHandler.removeCallbacks(serverStartTimeout);
        serverStateHandler.postDelayed(serverStartTimeout, SERVER_START_TIMEOUT_MS);
    }

    private void setServerUiState(ServerUiState state) {
        serverUiState = state;
        if (state != ServerUiState.STARTING && state != ServerUiState.VALIDATING) {
            serverStateHandler.removeCallbacks(serverStartTimeout);
        }
        renderServerState();
    }

    private void renderServerState() {
        int stateText;
        if (serverUiState == ServerUiState.RUNNING) stateText = R.string.server_running;
        else if (serverUiState == ServerUiState.STARTING) stateText = R.string.server_starting;
        else if (serverUiState == ServerUiState.VALIDATING) stateText = R.string.server_validating;
        else stateText = R.string.server_stopped;
        serverState.setText(ui(stateText));
        startButton.setText(ui(serverUiState == ServerUiState.RUNNING
                ? R.string.running_at : R.string.start_server));
        startButton.setBackgroundResource(serverUiState == ServerUiState.RUNNING
                ? R.drawable.bg_primary_button_running : R.drawable.bg_primary_button);
    }

    private void applyLanguage() {
        setText(R.id.serverSectionTitle, R.string.section_server);
        setText(R.id.quickSectionTitle, R.string.section_quick_actions);
        setText(R.id.lineupSectionTitle, R.string.section_lineup);
        setText(R.id.exRankSectionTitle, R.string.section_ex_rank);
        setText(R.id.exRankHint, R.string.ex_rank_hint);
        setText(R.id.toolsSectionTitle, R.string.section_tools);
        setText(R.id.stopButton, R.string.stop_server);
        setText(R.id.tbButton, R.string.change_trailblazer_path);
        setText(R.id.marchButton, R.string.change_march_path);
        setText(R.id.selectButton, R.string.select_character_by_path);
        setText(R.id.addPathButton, R.string.add_entire_path);
        setText(R.id.manualButton, R.string.enter_character_ids);
        setText(R.id.mapTeleportButton, R.string.map_teleport);
        setText(R.id.mapTeleportHelpButton, R.string.map_teleport_help);
        setText(R.id.removeButton, R.string.remove_character);
        setText(R.id.clearButton, R.string.clear_lineup);
        setText(R.id.iconButton, R.string.change_apk_icon);
        setText(R.id.miscButton, R.string.open_misc_files);
        setText(R.id.liveLogButton, R.string.open_live_log);
        setText(R.id.fireflySrtoolsButton, R.string.open_firefly_srtools);
        setText(R.id.neonteamSrtoolsButton, R.string.open_neonteam_srtools);
        renderServerState();
    }

    private void setText(int viewId, int textId) {
        ((TextView) findViewById(viewId)).setText(ui(textId));
    }

    protected String ui(int resource, Object... arguments) {
        return UiLanguage.text(this, resource, arguments);
    }

    private void chooseTrailblazer() {
        if (!ensureStorageAccess()) return;
        chooseConfig(ui(R.string.trailblazer_path), UiLanguage.array(this, R.array.trailblazer_path_labels),
                new String[]{"Destruction", "Preservation", "Harmony", "Remembrance", "Elation"}, "tb_path");
    }

    private void chooseMarch() {
        if (!ensureStorageAccess()) return;
        chooseConfig(ui(R.string.march_path), UiLanguage.array(this, R.array.march_path_labels),
                new String[]{"Preservation", "Hunt"}, "march_path");
    }

    private void chooseConfig(String title, String[] labels, String[] values, String key) {
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (dialog, which) -> {
            try {
                ConfigStore.setPath(this, key, values[which]);
                toast(ui(R.string.switched_path, labels[which]));
            } catch (Exception error) { showError(error); }
        }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void choosePath(boolean wholePath) {
        if (!ensureStorageAccess()) return;
        String[] pathLabels = UiLanguage.array(this, R.array.path_labels);
        new AlertDialog.Builder(this).setTitle(ui(wholePath
                        ? R.string.add_entire_path : R.string.select_path))
                .setItems(pathLabels, (dialog, which) -> {
                    String path = CharacterData.PATH_VALUES[which];
                    if (wholePath) confirmAddPath(pathLabels[which], path);
                    else chooseCharacter(pathLabels[which], path);
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void chooseCharacter(String label, String path) {
        List<Integer> ids = CharacterData.PATHS.get(path);
        String[] items = new String[ids.size()];
        boolean english = UiLanguage.isEnglish(this);
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            String name = CharacterData.nameOf(id, english);
            items[i] = name == null ? String.valueOf(id) : id + " - " + name;
        }
        new AlertDialog.Builder(this).setTitle(label).setItems(items, (dialog, which) -> {
            try { ConfigStore.addIds(this, Collections.singletonList(ids.get(which))); refreshLineup(); }
            catch (Exception error) { showError(error); }
        }).setNegativeButton(ui(R.string.back), null).show();
    }

    private void confirmAddPath(String label, String path) {
        List<Integer> ids = CharacterData.PATHS.get(path);
        new AlertDialog.Builder(this).setTitle(ui(R.string.add_whole_path_title, label))
                .setMessage(ui(R.string.add_whole_path_message, ids.size()))
                .setPositiveButton(ui(R.string.add), (d, w) -> {
                    try { ConfigStore.addIds(this, ids); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void inputCharacterIds() {
        if (!ensureStorageAccess()) return;
        EditText input = input(ui(R.string.character_ids_hint), InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle(ui(R.string.add_character_ids)).setView(input)
                .setPositiveButton(ui(R.string.add), (d, w) -> {
                    try {
                        String value = input.getText().toString().trim();
                        if (!value.matches("[0-9 ]+")) {
                            throw new IllegalArgumentException(ui(R.string.invalid_ids));
                        }
                        List<Integer> ids = new ArrayList<>();
                        for (String token : value.split(" +")) ids.add(Integer.parseInt(token));
                        ConfigStore.addIds(this, ids); refreshLineup();
                    } catch (Exception error) { showError(error); }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void inputRemoveIndex() {
        if (!ensureStorageAccess()) return;
        EditText input = input(ui(R.string.remove_index_hint), InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle(ui(R.string.remove_character_title)).setView(input)
                .setPositiveButton(ui(R.string.remove), (d, w) -> {
                    try { ConfigStore.removeAt(this, Integer.parseInt(input.getText().toString())); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void showMapTeleport() {
        if (!ensureStorageAccess()) return;
        if (serverUiState != ServerUiState.STOPPED) {
            new AlertDialog.Builder(this).setTitle(ui(R.string.map_teleport))
                    .setMessage(ui(R.string.teleport_stop_server))
                    .setPositiveButton(ui(R.string.close), null).show();
            return;
        }
        try {
            List<SceneCatalog.Planet> planets = SceneCatalog.load(this);
            String[] filters = new String[]{ui(R.string.map_filter_all),
                    ui(R.string.map_filter_combat), ui(R.string.map_filter_exploration)};
            new AlertDialog.Builder(this).setTitle(ui(R.string.map_filter_title))
                    .setItems(filters, (dialog, which) -> showTeleportPlanets(planets, which))
                    .setNegativeButton(ui(R.string.cancel), null).show();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void showMapTeleportHelp() {
        new AlertDialog.Builder(this).setTitle(ui(R.string.map_teleport_help_title))
                .setMessage(ui(R.string.map_teleport_help_message))
                .setPositiveButton(ui(R.string.close), null).show();
    }

    private void showTeleportPlanets(List<SceneCatalog.Planet> catalog, int filter) {
        List<SceneCatalog.Planet> planets = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (SceneCatalog.Planet planet : catalog) {
            int count = filteredScenes(planet, filter).size();
            if (count > 0) {
                planets.add(planet);
                counts.add(count);
            }
        }
        boolean english = UiLanguage.isEnglish(this);
        String[] labels = new String[planets.size()];
        for (int i = 0; i < planets.size(); i++) {
            labels[i] = ui(R.string.map_world_count,
                    planets.get(i).localizedName(english), counts.get(i));
        }
        new AlertDialog.Builder(this).setTitle(ui(R.string.map_choose_world))
                .setItems(labels, (dialog, which) ->
                        showTeleportScenes(catalog, planets.get(which), filter))
                .setNegativeButton(ui(R.string.back), (dialog, which) -> showMapTeleport()).show();
    }

    private void showTeleportScenes(List<SceneCatalog.Planet> catalog,
                                    SceneCatalog.Planet planet, int filter) {
        List<SceneCatalog.Scene> scenes = filteredScenes(planet, filter);
        boolean english = UiLanguage.isEnglish(this);
        String[] labels = new String[scenes.size()];
        for (int i = 0; i < scenes.size(); i++) {
            SceneCatalog.Scene scene = scenes.get(i);
            labels[i] = ui(scene.combat ? R.string.map_scene_combat : R.string.map_scene_exploration,
                    scene.localizedName(english), scene.planeId);
        }
        new AlertDialog.Builder(this).setTitle(planet.localizedName(english))
                .setItems(labels, (dialog, which) -> confirmTeleport(planet, scenes.get(which)))
                .setNegativeButton(ui(R.string.back),
                        (dialog, which) -> showTeleportPlanets(catalog, filter)).show();
    }

    private List<SceneCatalog.Scene> filteredScenes(SceneCatalog.Planet planet, int filter) {
        List<SceneCatalog.Scene> result = new ArrayList<>();
        for (SceneCatalog.Scene scene : planet.scenes) {
            if (filter == 0 || (filter == 1 && scene.combat) || (filter == 2 && !scene.combat)) {
                result.add(scene);
            }
        }
        return result;
    }

    private void confirmTeleport(SceneCatalog.Planet planet, SceneCatalog.Scene scene) {
        boolean english = UiLanguage.isEnglish(this);
        String description = scene.localizedDescription(english);
        String details = ui(R.string.teleport_confirm_message, planet.localizedName(english),
                scene.localizedName(english), scene.planeId, scene.mapLayer,
                scene.x, scene.y, scene.z, description);
        new AlertDialog.Builder(this).setTitle(ui(R.string.teleport_confirm_title))
                .setMessage(details)
                .setPositiveButton(ui(R.string.teleport), (dialog, which) -> {
                    try {
                        ConfigStore.applyScene(this, scene.data);
                        toast(ui(R.string.teleport_saved, scene.localizedName(english)));
                    } catch (Exception error) {
                        showError(error);
                    }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void confirmClear() {
        if (!ensureStorageAccess()) return;
        new AlertDialog.Builder(this).setTitle(ui(R.string.clear_lineup_title))
                .setPositiveButton(ui(R.string.clear), (d, w) -> {
                    try { ConfigStore.clear(this); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void refreshLineup() {
        if (lineupValue == null) return;
        try {
            List<Integer> ids = ConfigStore.lineup(this);
            if (ids.isEmpty()) { lineupValue.setText(ui(R.string.lineup_empty)); return; }
            StringBuilder value = new StringBuilder(ui(R.string.lineup_heading)).append('\n');
            boolean english = UiLanguage.isEnglish(this);
            for (int i = 0; i < ids.size(); i++) { int id = ids.get(i); String name = CharacterData.nameOf(id, english); value.append(i + 1).append(". ").append(id); if (name != null) value.append(" - ").append(name); value.append('\n'); }
            lineupValue.setText(value.toString().trim());
        } catch (Exception error) {
            lineupValue.setText(ui(R.string.lineup_read_failed, error.getMessage()));
        }
    }

    /** 重建“奇缘星魂”逐行开关：每个角色一行（名字 + 开关），切换立即写回配置。 */
    private void refreshExRank() {
        if (exRankContainer == null) return;
        exRankContainer.removeAllViews();
        List<ConfigStore.ExRank> entries;
        try {
            entries = ConfigStore.exRank(this);
        } catch (Exception error) {
            exRankContainer.addView(exRankRow(ui(R.string.ex_rank_read_failed, error.getMessage()), null));
            return;
        }
        for (ConfigStore.ExRank entry : entries) {
            exRankContainer.addView(exRankRow(exRankName(entry), entry));
        }
    }

    /** 显示名用 App 内置的角色名（纯中文），取不到再退回配置里的名字。 */
    private String exRankName(ConfigStore.ExRank entry) {
        String name = CharacterData.nameOf(entry.avatarId, UiLanguage.isEnglish(this));
        return name == null || name.isEmpty() ? entry.name : name;
    }

    /** 生成一行：左侧角色名，右侧开关；开关一拨就写入 persistent.json。 */
    private View exRankRow(String label, ConfigStore.ExRank entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15f);
        name.setTextColor(getColor(R.color.text_primary));
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        if (entry != null) {
            Switch toggle = new Switch(this);
            // 先 setChecked 再挂监听，避免初始化时误触发一次写入。
            toggle.setChecked(entry.active);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                try {
                    ConfigStore.setExRankActive(this, entry.avatarId, checked);
                    toast(ui(checked ? R.string.ex_rank_saved_on : R.string.ex_rank_saved_off, label));
                } catch (Exception error) {
                    showError(error);
                }
            });
            row.addView(toggle);
        }
        return row;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void chooseIcon() {
        new AlertDialog.Builder(this).setTitle(ui(R.string.icon_dialog_title))
                .setItems(UiLanguage.array(this, R.array.icon_labels), (d, which) -> {
                    IconSwitcher.apply(this, which);
                    toast(ui(R.string.icon_changed));
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void openMisc() {
        if (!ensureStorageAccess()) return;
        try {
            File misc = new File(ServerFiles.install(this), "misc");
            List<File> files = new ArrayList<>();
            collectJsonFiles(misc, files);
            Collections.sort(files, (left, right) ->
                    left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath()));
            if (files.isEmpty()) {
                toast(ui(R.string.no_json_files));
                return;
            }
            String[] labels = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                labels[i] = misc.toPath().relativize(files.get(i).toPath()).toString()
                        .replace(File.separatorChar, '/');
            }
            new AlertDialog.Builder(this).setTitle(ui(R.string.misc_files))
                    .setItems(labels, (dialog, which) -> openJsonFile(labels[which], files.get(which)))
                    .setNegativeButton(ui(R.string.close), null).show();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void showLiveLog() {
        if (!ensureStorageAccess()) return;
        if (liveLogDialog != null && liveLogDialog.isShowing()) return;

        liveLogText = new TextView(this);
        liveLogText.setText(ui(R.string.live_log_waiting));
        liveLogText.setTextColor(getColor(R.color.text_secondary));
        liveLogText.setTextSize(12);
        liveLogText.setTypeface(Typeface.MONOSPACE);
        liveLogText.setTextIsSelectable(true);
        liveLogText.setPadding(24, 20, 24, 20);

        liveLogScroll = new ScrollView(this);
        liveLogScroll.setFillViewport(true);
        liveLogScroll.addView(liveLogText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        liveLogDialog = new AlertDialog.Builder(this)
                .setTitle(ui(R.string.live_log_title))
                .setView(liveLogScroll)
                .setNeutralButton(ui(R.string.clear_log), null)
                .setPositiveButton(ui(R.string.close), null)
                .create();
        liveLogDialog.setOnShowListener(ignored -> {
            liveLogDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view -> clearLiveLog());
            liveLogLength = -1;
            liveLogModified = -1;
            liveLogHandler.post(liveLogRefresh);
        });
        liveLogDialog.setOnDismissListener(ignored -> closeLiveLog());
        liveLogDialog.show();
    }

    private void refreshLiveLog() {
        if (liveLogText == null) return;
        File file = new File(ServerFiles.directory(this), "logs/server.log");
        if (!file.isFile()) {
            liveLogText.setText(ui(R.string.live_log_waiting));
            return;
        }
        long length = file.length();
        long modified = file.lastModified();
        if (length == liveLogLength && modified == liveLogModified) return;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, length - MAX_LIVE_LOG_BYTES);
            input.seek(start);
            byte[] content = new byte[(int) (length - start)];
            input.readFully(content);
            String value = new String(content, StandardCharsets.UTF_8);
            if (start > 0) {
                int firstLine = value.indexOf('\n');
                value = ui(R.string.live_log_truncated) + "\n"
                        + (firstLine >= 0 ? value.substring(firstLine + 1) : value);
            }
            liveLogText.setText(value.isEmpty() ? ui(R.string.live_log_empty) : value);
            liveLogLength = length;
            liveLogModified = modified;
            liveLogScroll.post(() -> liveLogScroll.fullScroll(View.FOCUS_DOWN));
        } catch (Exception error) {
            liveLogText.setText(ui(R.string.live_log_read_failed, error.getMessage()));
        }
    }

    private void clearLiveLog() {
        File file = new File(ServerFiles.directory(this), "logs/server.log");
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new java.io.IOException("Cannot create " + parent);
            }
            try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
                output.setLength(0);
            }
            liveLogLength = -1;
            liveLogModified = -1;
            refreshLiveLog();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void closeLiveLog() {
        liveLogHandler.removeCallbacks(liveLogRefresh);
        liveLogDialog = null;
        liveLogText = null;
        liveLogScroll = null;
    }

    private void collectJsonFiles(File directory, List<File> result) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectJsonFiles(child, result);
            else if (child.getName().toLowerCase().endsWith(".json")) result.add(child);
        }
    }

    private void openJsonFile(String relativePath, File file) {
        if (file.length() < LARGE_JSON_SIZE) {
            editMiscFile(relativePath, file);
            return;
        }
        String size = String.format(java.util.Locale.ROOT, "%.1f MB",
                file.length() / (1024.0 * 1024.0));
        new AlertDialog.Builder(this)
                .setTitle(ui(R.string.large_file_title, relativePath))
                .setMessage(ui(R.string.large_file_message, size))
                .setPositiveButton(ui(R.string.choose_external_app),
                        (dialog, which) -> openWithExternalApp(file))
                .setNegativeButton(ui(R.string.edit_in_app),
                        (dialog, which) -> editMiscFile(relativePath, file))
                .setNeutralButton(ui(R.string.cancel), null)
                .show();
    }

    private void openWithExternalApp(File file) {
        try {
            Uri uri = MiscFileProvider.uriForFile(this, file);
            Intent edit = new Intent(Intent.ACTION_EDIT)
                    .setDataAndType(uri, "text/plain")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            edit.setClipData(android.content.ClipData.newRawUri(file.getName(), uri));
            startActivity(Intent.createChooser(edit, ui(R.string.choose_editor)));
        } catch (Exception error) {
            showError(error);
        }
    }

    private void editMiscFile(String relativePath, File file) {
        try {
            EditText editor = new EditText(this);
            editor.setText(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            editor.setTextSize(13);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setHorizontallyScrolling(true);
            editor.setMinLines(16);
            editor.setMaxLines(24);
            editor.setPadding(32, 20, 32, 20);

            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(relativePath)
                    .setView(editor)
                    .setPositiveButton(ui(R.string.save), null)
                    .setNegativeButton(ui(R.string.cancel), null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        try {
                            String value = editor.getText().toString();
                            new JSONTokener(value).nextValue();
                            Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
                            refreshLineup();
                            toast(ui(R.string.saved_restart));
                            dialog.dismiss();
                        } catch (Exception error) {
                            editor.setError(ui(R.string.json_error, error.getMessage()));
                        }
                    }));
            dialog.show();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private EditText input(String hint, int type) {
        EditText input = new EditText(this); input.setHint(hint); input.setInputType(type);
        input.setPadding(48, 8, 48, 0); return input;
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private void showError(Throwable error) {
        new AlertDialog.Builder(this).setTitle(ui(R.string.operation_failed))
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton(ui(R.string.close), null).show();
    }
}
