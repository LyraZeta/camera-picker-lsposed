package com.lyra.camerapicker;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Activity used as the replacement result-producing camera. */
public final class PickerActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 4101;
    private static final int BUFFER_SIZE = 64 * 1024;

    private Uri requestedOutputUri;
    private TextView statusView;
    private ExecutorService worker;
    private boolean pickerStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(com.lyra.camerapicker.R.string.picker_title));
        requestedOutputUri = readRequestedOutput(getIntent());
        showStatus(getString(com.lyra.camerapicker.R.string.picker_title), false);

        if (savedInstanceState == null) {
            openDocumentPicker();
        }
    }

    private void openDocumentPicker() {
        if (pickerStarted) {
            return;
        }
        pickerStarted = true;

        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"
        });
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            startActivityForResult(picker, REQUEST_PICK_IMAGE);
        } catch (ActivityNotFoundException firstFailure) {
            // A few vendor ROMs omit the document picker. ACTION_PICK is a useful fallback.
            Intent fallback = new Intent(Intent.ACTION_PICK);
            fallback.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(fallback, REQUEST_PICK_IMAGE);
            } catch (ActivityNotFoundException secondFailure) {
                Toast.makeText(this, R.string.picker_failed, Toast.LENGTH_LONG).show();
                finishCanceled();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE) {
            return;
        }

        Uri selectedUri = readSelectedUri(data);
        if (resultCode != RESULT_OK || selectedUri == null) {
            Toast.makeText(this, R.string.picker_cancelled, Toast.LENGTH_SHORT).show();
            finishCanceled();
            return;
        }

        persistReadPermission(data, selectedUri);
        showStatus(getString(R.string.picker_processing), true);
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor();
        }
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent result = buildCameraResult(selectedUri);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setResult(RESULT_OK, result);
                            finish();
                        }
                    });
                } catch (final Throwable failure) {
                    log("failed to prepare selected image: " + failure);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(PickerActivity.this, R.string.picker_failed, Toast.LENGTH_LONG).show();
                            finishCanceled();
                        }
                    });
                }
            }
        });
    }

    private Intent buildCameraResult(Uri selectedUri) throws IOException {
        if (requestedOutputUri != null) {
            copyUriToUri(selectedUri, requestedOutputUri);

            // Camera apps normally return an empty result Intent when EXTRA_OUTPUT
            // was supplied. The bytes have already been written to the caller's URI.
            Intent result = new Intent();
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            result.setClipData(ClipData.newRawUri("camera-output", requestedOutputUri));
            return result;
        }

        File outgoingDir = new File(getCacheDir(), "outgoing");
        if (!outgoingDir.exists() && !outgoingDir.mkdirs()) {
            throw new IOException("cannot create outgoing cache directory");
        }
        File outgoing = new File(outgoingDir,
                String.format(Locale.US, "camera-%d.jpg", System.currentTimeMillis()));
        copyUriToFile(selectedUri, outgoing);

        Uri exposedUri = PickerFileProvider.getUriForFile(this, outgoing);
        Intent result = new Intent();
        result.setDataAndType(exposedUri, "image/*");
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        result.setClipData(ClipData.newRawUri("camera-result", exposedUri));

        Bitmap thumbnail = loadThumbnail(selectedUri);
        if (thumbnail != null) {
            result.putExtra("data", thumbnail);
        }
        return result;
    }

    private void copyUriToUri(Uri source, Uri destination) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = getContentResolver().openInputStream(source);
            if (input == null) {
                throw new IOException("cannot open selected image");
            }
            output = getContentResolver().openOutputStream(destination, "w");
            if (output == null) {
                throw new IOException("cannot open camera output URI");
            }
            copy(input, output);
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private void copyUriToFile(Uri source, File destination) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = getContentResolver().openInputStream(source);
            if (input == null) {
                throw new IOException("cannot open selected image");
            }
            output = new FileOutputStream(destination);
            copy(input, output);
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        output.flush();
    }

    private Bitmap loadThumbnail(Uri source) {
        InputStream boundsStream = null;
        InputStream bitmapStream = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            boundsStream = getContentResolver().openInputStream(source);
            if (boundsStream == null) {
                return null;
            }
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            closeQuietly(boundsStream);
            boundsStream = null;

            int width = Math.max(1, bounds.outWidth);
            int height = Math.max(1, bounds.outHeight);
            int sample = 1;
            while (width / sample > 512 || height / sample > 512) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            bitmapStream = getContentResolver().openInputStream(source);
            return BitmapFactory.decodeStream(bitmapStream, null, options);
        } catch (Throwable failure) {
            log("thumbnail unavailable: " + failure);
            return null;
        } finally {
            closeQuietly(boundsStream);
            closeQuietly(bitmapStream);
        }
    }

    private static Uri readRequestedOutput(Intent intent) {
        if (intent == null || intent.getExtras() == null) {
            return null;
        }
        Object value = intent.getExtras().get(MediaStore.EXTRA_OUTPUT);
        if (value instanceof Uri) {
            return (Uri) value;
        }
        if (value instanceof String) {
            try {
                return Uri.parse((String) value);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Uri readSelectedUri(Intent data) {
        if (data == null) {
            return null;
        }
        Uri direct = data.getData();
        if (direct != null) {
            return direct;
        }
        ClipData clipData = data.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            return clipData.getItemAt(0).getUri();
        }
        return null;
    }

    @SuppressLint("WrongConstant")
    private void persistReadPermission(Intent data, Uri selectedUri) {
        if (data == null || selectedUri == null) {
            return;
        }
        int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0 && takeFlags != 0) {
            try {
                getContentResolver().takePersistableUriPermission(selectedUri, takeFlags);
            } catch (Throwable ignored) {
                // Not every DocumentsProvider offers persistable grants.
            }
        }
    }

    private void showStatus(String message, boolean showProgress) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int padding = (int) (32 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(message);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        statusView = title;

        if (showProgress) {
            ProgressBar progress = new ProgressBar(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = padding / 2;
            root.addView(progress, params);
        }
        setContentView(root);
    }

    private void finishCanceled() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static void log(String message) {
        Log.d("CameraPicker", message);
    }

    @Override
    protected void onDestroy() {
        if (worker != null) {
            worker.shutdownNow();
        }
        super.onDestroy();
    }
}
