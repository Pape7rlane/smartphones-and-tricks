package com.ppsr.control;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads the published server payload zip and swaps it in. */
final class PayloadUpdater {
    static final String DEFAULT_ZIP_URL = "https://file.garden/aewBviVxx2XDWgM-/4.6.5X/build.zip";
    static final String PREFS = "payload_update";
    static final String KEY_LABEL = "label";
    private static final String ZIP_ROOT = "build/";
    private static final long MAX_ARCHIVE_BYTES = 64L * 1024 * 1024;

    private PayloadUpdater() {}

    /** Downloads and installs the payload; returns how many files were written. */
    static int update(Context context, String url) throws IOException {
        String source = url == null || url.trim().isEmpty() ? DEFAULT_ZIP_URL : url.trim();
        File cache = context.getCacheDir();
        File archive = new File(cache, "ppsr-payload.zip");
        File staging = new File(cache, "ppsr-payload");
        try {
            download(archive, source);
            deleteTree(staging);
            int files = extract(archive, staging);
            if (!new File(staging, "ppsr.pyc").isFile()
                    || !new File(staging, "game/gameserver.pyc").isFile()) {
                throw new IOException("压缩包里没有服务器文件");
            }
            ServerFiles.installFromDirectory(context, staging);
            String label = labelOf(source);
            if (!label.isEmpty()) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_LABEL, label).apply();
            }
            return files;
        } finally {
            archive.delete();
            deleteTree(staging);
        }
    }

    /** "…/4.7.5X/build.zip" -> "4.7.5X", used as the version label. */
    static String labelOf(String url) {
        String value = url.trim();
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        value = value.substring(0, end);
        int lastSlash = value.lastIndexOf('/');
        if (lastSlash <= 0) return "";
        value = value.substring(0, lastSlash);
        int previousSlash = value.lastIndexOf('/');
        String segment = previousSlash < 0 ? value : value.substring(previousSlash + 1);
        return segment.contains("://") ? "" : segment;
    }

    private static void download(File target, String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "ppSR-android");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            long total = 0;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_ARCHIVE_BYTES) throw new IOException("安装包过大");
                    output.write(buffer, 0, count);
                }
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static int extract(File archive, File staging) throws IOException {
        int files = 0;
        byte[] buffer = new byte[16384];
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith(ZIP_ROOT)) continue;
                String relative = name.substring(ZIP_ROOT.length());
                if (relative.isEmpty() || relative.contains("..")) continue;
                File target = new File(staging, relative);
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("无法创建 " + target);
                    }
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("无法创建 " + parent);
                }
                try (OutputStream output = new FileOutputStream(target)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                files++;
            }
        }
        return files;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
