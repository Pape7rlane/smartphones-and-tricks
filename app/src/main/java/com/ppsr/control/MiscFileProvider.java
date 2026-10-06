package com.ppsr.control;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MiscFileProvider extends ContentProvider {
    private static final String AUTHORITY_SUFFIX = ".misc-files";

    static Uri uriForFile(Context context, File file) throws IOException {
        File root = miscRoot(context);
        String rootPath = root.getCanonicalPath() + File.separator;
        String filePath = file.getCanonicalPath();
        if (!filePath.startsWith(rootPath)) {
            throw new SecurityException(UiLanguage.text(context, R.string.misc_file_outside));
        }
        String relative = filePath.substring(rootPath.length());
        Uri.Builder builder = new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX);
        for (String part : relative.split("/")) builder.appendPath(part);
        return builder.build();
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "application/json"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        try {
            File file = resolve(uri);
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null
                    ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                    : projection;
            MatrixCursor cursor = new MatrixCursor(columns, 1);
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : columns) {
                if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
                else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
                else row.add(null);
            }
            return cursor;
        } catch (IOException error) {
            return null;
        }
    }

    private File resolve(Uri uri) throws IOException {
        Context context = getContext();
        if (context == null) throw new IOException("Provider context is unavailable");
        File root = miscRoot(context);
        File file = root;
        for (String part : uri.getPathSegments()) file = new File(file, part);
        String rootPath = root.getCanonicalPath() + File.separator;
        String filePath = file.getCanonicalPath();
        if (!filePath.startsWith(rootPath) || !file.isFile()) {
            throw new FileNotFoundException("Invalid misc file");
        }
        return file;
    }

    private static File miscRoot(Context context) throws IOException {
        return new File(ServerFiles.install(context), "misc").getCanonicalFile();
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
