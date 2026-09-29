package com.ppsr.control;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

final class StorageAccess {
    static final int REQUEST_CODE = 101;

    private StorageAccess() {}

    static boolean isGranted(Activity activity) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return Build.VERSION.SDK_INT < 23 || activity.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static void request(Activity activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivityForResult(intent, REQUEST_CODE);
            } catch (Exception ignored) {
                activity.startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_CODE);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
        }
    }
}
