package com.ppsr.control;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class ServerFiles {
    private static final String PAYLOAD_VERSION = "4.6.51-1";
    private ServerFiles() {}

    static File directory(Context context) {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "ppSR");
    }

    static synchronized File install(Context context) throws IOException {
        File destination = directory(context);
        File marker = new File(destination, ".payload-version");
        String installed = marker.isFile()
                ? new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8) : "";
        if (!PAYLOAD_VERSION.equals(installed)) {
            copyTree(context.getAssets(), "server", destination, destination);
            Files.write(marker.toPath(), PAYLOAD_VERSION.getBytes(StandardCharsets.UTF_8));
        }
        return destination;
    }

    private static void copyTree(AssetManager assets, String assetPath, File target,
                                 File serverRoot) throws IOException {
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create " + target);
            for (String child : children) {
                copyTree(assets, assetPath + "/" + child, new File(target, child), serverRoot);
            }
            return;
        }
        if (isUserData(serverRoot, target) && target.isFile()) return;
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (InputStream input = assets.open(assetPath);
             OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private static boolean isUserData(File serverRoot, File target) {
        File misc = new File(serverRoot, "misc");
        return target.equals(new File(misc, "persistent.json"))
                || target.equals(new File(misc, "freesr-data.json"));
    }
}
