package com.ppsr.control;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** Fetches the public announcement and update metadata without blocking the UI. */
final class ReleaseInfo {
    static final String CONFIG_URL =
            "https://raw.githubusercontent.com/Pape7rlane/smartphones-and-tricks/termux-fast-startup/update.json";
    private static final String PREFS = "release_info";
    private static final String CACHED_JSON = "cached_json";
    private static final int MAX_CONFIG_BYTES = 64 * 1024;

    final long latestVersionCode;
    final String latestVersionName;
    final String downloadUrl;
    final String announcementId;
    final String announcementTitle;
    final String announcementBody;

    private ReleaseInfo(JSONObject json) {
        latestVersionCode = json.optLong("latest_version_code", 0);
        latestVersionName = json.optString("latest_version_name", "").trim();
        String candidateUrl = json.optString("download_url", "").trim();
        Uri uri = Uri.parse(candidateUrl);
        downloadUrl = "https".equalsIgnoreCase(uri.getScheme()) ? candidateUrl : "";
        announcementId = json.optString("announcement_id", "").trim();
        announcementTitle = json.optString("announcement_title", "").trim();
        announcementBody = json.optString("announcement_body", "").trim();
    }

    static ReleaseInfo loadCachedOrBundled(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = preferences.getString(CACHED_JSON, null);
        if (cached != null) {
            try { return parse(cached); }
            catch (Exception ignored) { /* Fall back to the bundled announcement. */ }
        }
        try (InputStream input = context.getAssets().open("update.json")) {
            return parse(new String(readLimited(input), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new ReleaseInfo(new JSONObject());
        }
    }

    static ReleaseInfo fetchAndCache(Context context) throws IOException {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(CONFIG_URL + "?t=" + System.currentTimeMillis());
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            String jsonText;
            try (InputStream input = connection.getInputStream()) {
                jsonText = new String(readLimited(input), java.nio.charset.StandardCharsets.UTF_8);
            }
            ReleaseInfo info = parse(jsonText);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(CACHED_JSON, jsonText).apply();
            return info;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static ReleaseInfo parse(String value) throws IOException {
        try {
            JSONObject json = new JSONObject(value);
            long versionCode = json.optLong("latest_version_code", -1);
            if (versionCode < 1) throw new IOException("Invalid latest_version_code");
            return new ReleaseInfo(json);
        } catch (org.json.JSONException error) {
            throw new IOException("Invalid update.json", error);
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_CONFIG_BYTES) {
                throw new IOException("update.json is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
