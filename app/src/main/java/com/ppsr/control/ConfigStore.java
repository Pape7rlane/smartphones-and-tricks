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
        if (index < 0 || index >= lineup.length()) {
            throw new IllegalArgumentException(UiLanguage.text(context, R.string.invalid_lineup_index));
        }
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

    // ---- 奇缘星魂（额外星魂） ----------------------------------------------

    /** 奇缘星魂条目：对应 persistent.json 里的 avatar.ex_rank[]。 */
    static final class ExRank {
        final String name;
        final int avatarId;
        final boolean active;

        ExRank(String name, int avatarId, boolean active) {
            this.name = name;
            this.avatarId = avatarId;
            this.active = active;
        }
    }

    // 服务器只认这 3 个角色的奇缘星魂（见 misc/resources/AvatarExRankConfig.json）。
    private static final String[] DEFAULT_EX_RANK_NAMES = {"JingYuan", "DanHeng IL", "Acheron"};
    private static final int[] DEFAULT_EX_RANK_IDS = {1204, 1213, 1308};

    /**
     * 读取 avatar.ex_rank；若配置里还没有这一段，就写入默认的 3 个角色（默认开启）
     * 再返回，这样老设备升级后也能直接用奇缘星魂功能。
     */
    static synchronized List<ExRank> exRank(Context context) throws Exception {
        JSONObject root = read(context);
        JSONObject avatar = root.getJSONObject("avatar");
        JSONArray array = avatar.optJSONArray("ex_rank");
        if (array == null) {
            array = defaultExRank();
            avatar.put("ex_rank", array);
            write(context, root);
        }
        List<ExRank> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            result.add(new ExRank(item.optString("name", ""), item.optInt("avatar_id"),
                    item.optBoolean("active", true)));
        }
        return result;
    }

    /** 打开/关闭某个角色的奇缘星魂；配置里没有该角色时自动补一条。 */
    static synchronized void setExRankActive(Context context, int avatarId, boolean active)
            throws Exception {
        JSONObject root = read(context);
        JSONObject avatar = root.getJSONObject("avatar");
        JSONArray array = avatar.optJSONArray("ex_rank");
        if (array == null) {
            array = defaultExRank();
            avatar.put("ex_rank", array);
        }
        JSONObject target = null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && item.optInt("avatar_id") == avatarId) {
                target = item;
                break;
            }
        }
        if (target == null) {
            target = new JSONObject();
            target.put("name", defaultExRankName(avatarId));
            target.put("avatar_id", avatarId);
            array.put(target);
        }
        target.put("active", active);
        write(context, root);
    }

    private static JSONArray defaultExRank() throws JSONException {
        JSONArray array = new JSONArray();
        for (int i = 0; i < DEFAULT_EX_RANK_IDS.length; i++) {
            JSONObject item = new JSONObject();
            item.put("name", DEFAULT_EX_RANK_NAMES[i]);
            item.put("avatar_id", DEFAULT_EX_RANK_IDS[i]);
            item.put("active", true);
            array.put(item);
        }
        return array;
    }

    private static String defaultExRankName(int avatarId) {
        for (int i = 0; i < DEFAULT_EX_RANK_IDS.length; i++) {
            if (DEFAULT_EX_RANK_IDS[i] == avatarId) return DEFAULT_EX_RANK_NAMES[i];
        }
        return String.valueOf(avatarId);
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
