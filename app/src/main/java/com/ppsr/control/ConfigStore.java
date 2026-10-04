package com.ppsr.control;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        for (int id : ids) lineup.put(id);
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

    static synchronized void applyScene(Context context, JSONObject source) throws Exception {
        JSONObject root = read(context);
        JSONObject scene = root.optJSONObject("scene");
        if (scene == null) {
            scene = new JSONObject();
            root.put("scene", scene);
        }

        int x = source.getInt("x");
        int y = source.getInt("y");
        int z = source.getInt("z");
        scene.put("plane_id", source.getInt("plane_id"));
        scene.put("x", x);
        scene.put("y", y);
        scene.put("z", z);
        scene.put("calyx_x", source.optInt("calyx_x", x));
        scene.put("calyx_y", source.optInt("calyx_y", y));
        scene.put("calyx_z", source.optInt("calyx_z", z + 1800));
        scene.put("map_layer", source.optInt("map_layer", 1));
        scene.put("calyx_group_id", source.optInt("calyx_group_id", 186));
        scene.put("calyx_inst_id", source.optInt("calyx_inst_id", 300001));
        scene.put("calyx_entity_id", source.optInt("calyx_entity_id", 1337));
        scene.put("calyx_prop_id", source.optInt("calyx_prop_id", 808));

        File file = persistentFile(context);
        Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        write(context, root);
    }

    private static JSONObject read(Context context) throws IOException, JSONException {
        File file = persistentFile(context);
        JSONObject object = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        if (!object.has("avatar")) object.put("avatar", new JSONObject());
        JSONObject avatar = object.getJSONObject("avatar");
        if (!avatar.has("lineup")) avatar.put("lineup", new JSONArray());
        return object;
    }

    private static void write(Context context, JSONObject object) throws IOException, JSONException {
        File file = persistentFile(context);
        File temporary = File.createTempFile(".persistent-", ".tmp", file.getParentFile());
        try {
            Files.write(temporary.toPath(), object.toString(4).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicError) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private static File persistentFile(Context context) throws IOException {
        return new File(ServerFiles.install(context), "misc/persistent.json");
    }

    private static boolean contains(JSONArray values, int target) throws JSONException {
        for (int i = 0; i < values.length(); i++) if (values.getInt(i) == target) return true;
        return false;
    }
}
