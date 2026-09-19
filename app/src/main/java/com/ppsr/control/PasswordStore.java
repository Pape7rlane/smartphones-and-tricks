package com.ppsr.control;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class PasswordStore {
    private PasswordStore() {}

    static synchronized void save(Context context, String password) throws Exception {
        File file = file(context);
        JSONObject root = new JSONObject(new String(
                Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        root.put("key", password);
        Files.write(file.toPath(), root.toString(4).getBytes(StandardCharsets.UTF_8));
    }

    static synchronized boolean hasSaved(Context context) {
        try {
            File file = file(context);
            JSONObject root = new JSONObject(new String(
                    Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return !root.optString("key", "").trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static File file(Context context) throws Exception {
        return new File(ServerFiles.install(context), "misc/persistent.json");
    }
}
