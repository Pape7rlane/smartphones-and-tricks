package com.ppsr.control;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class ConfigStore {
    private ConfigStore() {}

    static synchronized void setPath(Context context, String key, String value) throws Exception {
        JSONObject root = read(context);
        root.getJSONObject("avatar").put(key, value);
        write(context, root);
    }

    static synchronized void addIds(Context context, List<Integer> ids) throws Exception {
        JSONObject root = read(context);
        JSONArray lineup = root.getJSONObject("avatar").getJSONArray("lineup");
        for (int id : ids) if (!contains(lineup, id)) lineup.put(id);
        write(context, root);
    }

    static synchronized void removeAt(Context context, int oneBasedIndex) throws Exception {
        JSONObject root = read(context);
        JSONArray lineup = root.getJSONObject("avatar").getJSONArray("lineup");
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= lineup.length()) throw new IllegalArgumentException("无效序号");
        lineup.remove(index);
        write(context, root);
    }

    static synchronized void clear(Context context) throws Exception {
        JSONObject root = read(context);
        root.getJSONObject("avatar").put("lineup", new JSONArray());
        write(context, root);
    }

    static synchronized List<Integer> lineup(Context context) throws Exception {
        JSONArray array = read(context).getJSONObject("avatar").getJSONArray("lineup");
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) result.add(array.getInt(i));
        return result;
    }

    private static JSONObject read(Context context) throws IOException, JSONException {
        File file = new File(ServerFiles.install(context), "misc/persistent.json");
        JSONObject object = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        if (!object.has("avatar")) object.put("avatar", new JSONObject());
        JSONObject avatar = object.getJSONObject("avatar");
        if (!avatar.has("lineup")) avatar.put("lineup", new JSONArray());
        return object;
    }

    private static void write(Context context, JSONObject object) throws IOException, JSONException {
        File file = new File(ServerFiles.directory(context), "misc/persistent.json");
        Files.write(file.toPath(), object.toString(4).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean contains(JSONArray values, int target) throws JSONException {
        for (int i = 0; i < values.length(); i++) if (values.getInt(i) == target) return true;
        return false;
    }
}
