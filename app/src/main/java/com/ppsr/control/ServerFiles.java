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
import java.nio.file.StandardCopyOption;

final class ServerFiles {
    private static final String PAYLOAD_VERSION = "4.6.53-1";
    private ServerFiles() {}

    static File directory(Context context) {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "ppSR");
    }

    static synchronized File install(Context context) throws IOException {
        if (!StorageAccess.isGranted(context)) {
            throw new IOException(UiLanguage.text(context, R.string.storage_access_required));
        }
        File destination = directory(context);
        File marker = new File(destination, ".payload-version");
        String installed = marker.isFile()
                ? new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8) : "";
        if (!PAYLOAD_VERSION.equals(installed)) {
            copyTree(context, context.getAssets(), "server", destination, destination);
            Files.deleteIfExists(new File(destination, "misc/classes_pb2.py").toPath());
            writeFile(marker, PAYLOAD_VERSION.getBytes(StandardCharsets.UTF_8));
        }
        return destination;
    }

    private static void copyTree(Context context, AssetManager assets, String assetPath, File target,
                                 File serverRoot) throws IOException {
        String[] children = assets.list(assetPath);
        if (children != null && children.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create " + target);
            for (String child : children) {
                copyTree(context, assets, assetPath + "/" + child, new File(target, child), serverRoot);
            }
            return;
        }
        if (isUserData(serverRoot, target) && target.isFile()) return;
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (InputStream input = assets.open(assetPath)) {
            writeFile(target, input);
        } catch (IOException error) {
            throw new IOException(UiLanguage.text(context, R.string.storage_write_failed,
                    target.getAbsolutePath()), error);
        }
    }

    private static void writeFile(File target, byte[] value) throws IOException {
        writeFile(target, new java.io.ByteArrayInputStream(value));
    }

    private static void writeFile(File target, InputStream input) throws IOException {
        File parent = target.getParentFile();
        if (parent == null) throw new IOException("Invalid target " + target);
        File temporary = File.createTempFile(".ppsr-", ".tmp", parent);
        try (OutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        try {
            Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicError) {
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException replaceError) {
                replaceError.addSuppressed(atomicError);
                throw replaceError;
            }
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static boolean isUserData(File serverRoot, File target) {
        File misc = new File(serverRoot, "misc");
        return target.equals(new File(misc, "persistent.json"))
                || target.equals(new File(misc, "freesr-data.json"));
    }
}
