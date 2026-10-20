package com.ppsr.control;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
    final String payloadUrl;
    final String announcementId;
    final String announcementTitle;
    final String announcementBody;
    final String unlockCode;

    private ReleaseInfo(JSONObject json) {
        latestVersionCode = json.optLong("latest_version_code", 0);
        latestVersionName = json.optString("latest_version_name", "").trim();
        String candidateUrl = json.optString("download_url", "").trim();
        Uri uri = Uri.parse(candidateUrl);
        downloadUrl = "https".equalsIgnoreCase(uri.getScheme()) ? candidateUrl : "";
        String candidatePayload = json.optString("payload_url", "").trim();
        Uri payloadUri = Uri.parse(candidatePayload);
        payloadUrl = "https".equalsIgnoreCase(payloadUri.getScheme()) ? candidatePayload : "";
        announcementId = json.optString("announcement_id", "").trim();
        announcementTitle = json.optString("announcement_title", "").trim();
        announcementBody = json.optString("announcement_body", "").trim();
        unlockCode = json.optString("unlock_code", "").trim();
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

    static final String QUARK_SHARE_URL = "https://pan.quark.cn/s/e99add200b01";
    private static final String QUARK_SHARE_ID = "e99add200b01";
    private static final String QUARK_API = "https://drive-pc.quark.cn/1/clouddrive/share/sharepage";
    static final String QUARK_APK_SIGNATURE = "quark_apk_signature";

    /** Newest APK published in the public share folder. */
    static final class ShareApk {
        final String signature;
        final String name;
        final long size;

        ShareApk(String signature, String name, long size) {
            this.signature = signature;
            this.name = name;
            this.size = size;
        }
    }

    /** Lists the share folder and fingerprints its newest APK. */
    static ShareApk fetchQuarkApk() throws IOException {
        try {
            JSONObject token = new JSONObject(httpString(QUARK_API + "/token?pr=ucpro&fr=pc",
                    "{\"pwd_id\":\"" + QUARK_SHARE_ID + "\",\"passcode\":\"\"}"));
            JSONObject tokenData = token.optJSONObject("data");
            String stoken = tokenData == null ? "" : tokenData.optString("stoken", "");
            if (stoken.isEmpty()) throw new IOException("Quark share token missing");
            String encodedToken = URLEncoder.encode(stoken, "UTF-8");
            JSONArray root = quarkList(encodedToken, "0");
            String folderFid = "";
            for (int i = 0; root != null && i < root.length(); i++) {
                JSONObject item = root.optJSONObject(i);
                if (item != null && item.optBoolean("dir", false)) {
                    folderFid = item.optString("fid", "");
                    break;
                }
            }
            JSONArray files = folderFid.isEmpty() ? root : quarkList(encodedToken, folderFid);
            JSONObject newest = null;
            for (int i = 0; files != null && i < files.length(); i++) {
                JSONObject file = files.optJSONObject(i);
                if (file == null || file.optBoolean("dir", false)) continue;
                if (!file.optString("file_name", "").toLowerCase(Locale.US).endsWith(".apk")) continue;
                if (newest == null || file.optLong("updated_at", 0) >= newest.optLong("updated_at", 0)) {
                    newest = file;
                }
            }
            if (newest == null) throw new IOException("No APK in the Quark share");
            String signature = newest.optString("fid", "") + ":"
                    + newest.optLong("updated_at", 0) + ":" + newest.optLong("size", 0);
            return new ShareApk(signature, newest.optString("file_name", ""), newest.optLong("size", 0));
        } catch (JSONException error) {
            throw new IOException("Invalid Quark response", error);
        }
    }

    private static JSONArray quarkList(String encodedToken, String parentFid)
            throws IOException, JSONException {
        String url = QUARK_API + "/detail?pr=ucpro&fr=pc&pwd_id=" + QUARK_SHARE_ID
                + "&stoken=" + encodedToken + "&pdir_fid=" + parentFid
                + "&force=0&_page=1&_size=50&_fetch_banner=0&_fetch_share=1&_fetch_total=1";
        JSONObject data = new JSONObject(httpString(url, null)).optJSONObject("data");
        return data == null ? null : data.optJSONArray("list");
    }

    private static String httpString(String url, String postBody) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)");
            if (postBody != null) {
                byte[] body = postBody.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                return new String(readLimited(input), StandardCharsets.UTF_8);
            }
        } finally {
            if (connection != null) connection.disconnect();
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
