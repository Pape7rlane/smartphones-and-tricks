package com.ppsr.control;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class SceneCatalog {
    private SceneCatalog() {}

    static List<Planet> load(Context context) throws Exception {
        byte[] buffer = new byte[16 * 1024];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = context.getAssets().open("scenes.json")) {
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        JSONObject root = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        JSONArray sourcePlanets = root.getJSONArray("planets");
        List<Planet> planets = new ArrayList<>();
        for (int i = 0; i < sourcePlanets.length(); i++) {
            JSONObject sourcePlanet = sourcePlanets.getJSONObject(i);
            JSONArray sourceScenes = sourcePlanet.getJSONArray("scenes");
            List<Scene> scenes = new ArrayList<>();
            for (int j = 0; j < sourceScenes.length(); j++) {
                scenes.add(new Scene(sourceScenes.getJSONObject(j)));
            }
            planets.add(new Planet(sourcePlanet.optString("name"),
                    sourcePlanet.optString("name_zh"), scenes));
        }
        return planets;
    }

    static final class Planet {
        final String name;
        final String nameZh;
        final List<Scene> scenes;

        Planet(String name, String nameZh, List<Scene> scenes) {
            this.name = name;
            this.nameZh = nameZh;
            this.scenes = scenes;
        }

        String localizedName(boolean english) {
            return english || nameZh.isEmpty() ? name : nameZh;
        }
    }

    static final class Scene {
        final JSONObject data;
        final String name;
        final String nameZh;
        final String description;
        final String descriptionZh;
        final int planeId;
        final int x;
        final int y;
        final int z;
        final int mapLayer;
        final boolean combat;

        Scene(JSONObject data) throws Exception {
            this.data = data;
            name = data.getString("name");
            nameZh = data.optString("name_zh");
            description = data.optString("desc");
            descriptionZh = data.optString("desc_zh");
            planeId = data.getInt("plane_id");
            x = data.getInt("x");
            y = data.getInt("y");
            z = data.getInt("z");
            mapLayer = data.optInt("map_layer", 1);
            combat = data.optBoolean("calyx_supported", false);
        }

        String localizedName(boolean english) {
            return english || nameZh.isEmpty() ? name : nameZh;
        }

        String localizedDescription(boolean english) {
            return english || descriptionZh.isEmpty() ? description : descriptionZh;
        }
    }
}
