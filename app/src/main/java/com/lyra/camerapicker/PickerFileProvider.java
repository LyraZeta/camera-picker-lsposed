package com.lyra.camerapicker;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/** Minimal read-only provider for image results returned to the target app. */
public final class PickerFileProvider extends ContentProvider {
    private static final String AUTHORITY = ModuleContract.FILE_PROVIDER_AUTHORITY;
    private static final String ROOT_SEGMENT = "outgoing";

    @Override
    public boolean onCreate() {
        File root = getRoot();
        return root.exists() || root.mkdirs();
    }

    public static Uri getUriForFile(android.content.Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(ROOT_SEGMENT)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = file.length();
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode == null || !mode.startsWith("r")) {
            throw new FileNotFoundException("read-only provider");
        }
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            return resolve(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    private File getRoot() {
        File root = new File(getContext().getCacheDir(), ROOT_SEGMENT);
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri == null || !AUTHORITY.equals(uri.getAuthority())) {
            throw new FileNotFoundException("invalid provider URI");
        }
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2 || !ROOT_SEGMENT.equals(segments.get(0))) {
            throw new FileNotFoundException("invalid provider path");
        }

        try {
            File root = getRoot().getCanonicalFile();
            File file = new File(root, segments.get(1)).getCanonicalFile();
            String rootPath = root.getPath();
            if (!file.getPath().startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("path escapes provider root");
            }
            if (!file.isFile()) {
                throw new FileNotFoundException("file does not exist");
            }
            return file;
        } catch (IOException e) {
            FileNotFoundException error = new FileNotFoundException(e.toString());
            error.initCause(e);
            throw error;
        }
    }
}
