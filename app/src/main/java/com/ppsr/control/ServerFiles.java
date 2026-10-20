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
    private static final String PAYLOAD_VERSION = "4.6.53-4";
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
            // Wipe the previous payload before copying the new one, so files that
            // the new build no longer ships cannot linger on the device. User data
            // (persistent.json, freesr-data.json) and the log directory survive.
            clearStalePayload(destination, destination);
            copyTree(context, context.getAssets(), "server", destination, destination);
            Files.deleteIfExists(new File(destination, "misc/classes_pb2.py").toPath());
            writeFile(marker, PAYLOAD_VERSION.getBytes(StandardCharsets.UTF_8));
        }
        return destination;
    }


    /** Replaces the installed payload with a directory downloaded from the network. */
    static synchronized void installFromDirectory(Context context, File source) throws IOException {
        if (!StorageAccess.isGranted(context)) {
            throw new IOException(UiLanguage.text(context, R.string.storage_access_required));
        }
        File destination = directory(context);
        if (!destination.isDirectory() && !destination.mkdirs()) {
            throw new IOException("Cannot create " + destination);
        }
        clearStalePayload(destination, destination);
        copyDirectory(source, destination, destination);
        Files.deleteIfExists(new File(destination, "misc/classes_pb2.py").toPath());
        writeFile(new File(destination, ".payload-version"),
                PAYLOAD_VERSION.getBytes(StandardCharsets.UTF_8));
    }

    private static void copyDirectory(File source, File target, File serverRoot) throws IOException {
        File[] children = source.listFiles();
        if (children == null) return;
        if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create " + target);
        for (File child : children) {
            File destination = new File(target, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, destination, serverRoot);
                continue;
            }
            if (isUserData(serverRoot, destination) && destination.isFile()) continue;
            try (InputStream input = new java.io.FileInputStream(child)) {
                writeFile(destination, input);
            }
        }
    }

    /**
     * Deletes every file under {@code dir} except user data and {@code logs/}, so
     * a payload update leaves no stale files from the previous build behind.
     * Directories that end up empty are removed too.
     */
    private static void clearStalePayload(File serverRoot, File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (isUserData(serverRoot, child)) continue;
            if (child.equals(new File(serverRoot, "logs"))) continue;
            if (child.isDirectory()) {
                clearStalePayload(serverRoot, child);
                File[] remaining = child.listFiles();
                if (remaining != null && remaining.length == 0) child.delete();
            } else {
                child.delete();
            }
        }
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
