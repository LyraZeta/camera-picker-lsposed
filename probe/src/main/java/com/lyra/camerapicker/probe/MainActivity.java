package com.lyra.camerapicker.probe;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sends a real camera capture Intent so the LSPosed hook can be tested unambiguously. */
public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 9201;
    private static final int REQUEST_STORAGE_PERMISSION = 9202;
    private static final String STATE_OUTPUT_URI = "output_uri";
    private static final String STATE_WAITING = "waiting";
    private static final int MAX_PREVIEW_DIMENSION = 1600;

    private Uri outputUri;
    private boolean waitingForResult;
    private TextView statusView;
    private ImageView previewView;
    private Button captureButton;
    private ExecutorService worker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.app_name));

        if (savedInstanceState != null) {
            String savedUri = savedInstanceState.getString(STATE_OUTPUT_URI);
            if (savedUri != null) {
                outputUri = Uri.parse(savedUri);
            }
            waitingForResult = savedInstanceState.getBoolean(STATE_WAITING, false);
        }

        buildUi();
        if (outputUri != null && !waitingForResult) {
            loadResult(outputUri);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.screen_title);
        title.setTextSize(24);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText(R.string.description);
        description.setTextSize(16);
        description.setPadding(0, dp(12), 0, dp(12));
        root.addView(description, matchWrap());

        captureButton = new Button(this);
        captureButton.setText(R.string.capture_button);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestStoragePermissionOrLaunch();
            }
        });
        root.addView(captureButton, matchWrap());

        Button clearButton = new Button(this);
        clearButton.setText(R.string.clear_button);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearResult();
            }
        });
        LinearLayout.LayoutParams clearParams = matchWrap();
        clearParams.topMargin = dp(8);
        root.addView(clearButton, clearParams);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setText(R.string.ready_status);
        statusView.setPadding(0, dp(16), 0, dp(16));
        root.addView(statusView, matchWrap());

        previewView = new ImageView(this);
        previewView.setAdjustViewBounds(true);
        previewView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewView.setVisibility(View.GONE);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320));
        root.addView(previewView, previewParams);

        setContentView(scroll);
        updateCaptureButton();
    }

    private void requestStoragePermissionOrLaunch() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            statusView.setText(R.string.permission_required);
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_PERMISSION);
            return;
        }
        launchNativeCameraIntent();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE_PERMISSION) {
            return;
        }
        boolean granted = grantResults.length > 0;
        for (int grantResult : grantResults) {
            granted &= grantResult == PackageManager.PERMISSION_GRANTED;
        }
        if (granted) {
            launchNativeCameraIntent();
        } else {
            statusView.setText(R.string.permission_denied);
        }
    }

    private void launchNativeCameraIntent() {
        if (waitingForResult) {
            return;
        }

        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (camera.resolveActivity(getPackageManager()) == null) {
            statusView.setText(R.string.camera_unavailable);
            return;
        }

        deleteOutputUri();
        try {
            outputUri = createOutputUri();
            camera.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            camera.setClipData(ClipData.newRawUri("camera-probe-output", outputUri));
            waitingForResult = true;
            updateCaptureButton();
            statusView.setText(R.string.starting_status);
            startActivityForResult(camera, REQUEST_CAPTURE);
        } catch (Throwable failure) {
            deleteOutputUri();
            waitingForResult = false;
            updateCaptureButton();
            statusView.setText(getString(R.string.launch_failed, failure.getMessage()));
        }
    }

    private Uri createOutputUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                String.format(Locale.US, "camera-probe-%d.jpg", System.currentTimeMillis()));
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/CameraIntentProbe");
        }
        Uri created = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (created == null) {
            throw new IllegalStateException("MediaStore.insert returned null");
        }
        return created;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) {
            return;
        }

        waitingForResult = false;
        updateCaptureButton();
        if (resultCode != RESULT_OK || outputUri == null) {
            deleteOutputUri();
            statusView.setText(R.string.cancelled_status);
            return;
        }

        loadResult(outputUri);
    }

    private void loadResult(final Uri completedUri) {
        statusView.setText(R.string.result_loading);
        getWorker().execute(new Runnable() {
            @Override
            public void run() {
                final long size = querySize(completedUri);
                final Bitmap bitmap = decodePreview(completedUri);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (size == 0) {
                            statusView.setText(R.string.result_empty);
                            previewView.setVisibility(View.GONE);
                            return;
                        }
                        if (bitmap == null) {
                            statusView.setText(R.string.image_unreadable);
                            previewView.setVisibility(View.GONE);
                            return;
                        }
                        previewView.setImageBitmap(bitmap);
                        previewView.setVisibility(View.VISIBLE);
                        String sizeText = size < 0 ? getString(R.string.size_unknown)
                                : formatBytes(size);
                        statusView.setText(getString(R.string.result_status,
                                completedUri.toString(), sizeText));
                    }
                });
            }
        });
    }

    private Bitmap decodePreview(Uri uri) {
        InputStream boundsStream = null;
        InputStream bitmapStream = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            boundsStream = getContentResolver().openInputStream(uri);
            if (boundsStream == null) {
                return null;
            }
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            closeQuietly(boundsStream);
            boundsStream = null;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sample = 1;
            while (bounds.outWidth / sample > MAX_PREVIEW_DIMENSION
                    || bounds.outHeight / sample > MAX_PREVIEW_DIMENSION) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            bitmapStream = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(bitmapStream, null, options);
        } catch (Throwable ignored) {
            return null;
        } finally {
            closeQuietly(boundsStream);
            closeQuietly(bitmapStream);
        }
    }

    private long querySize(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Throwable ignored) {
            // Some MediaStore implementations do not expose SIZE through query().
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1;
    }

    private void clearResult() {
        deleteOutputUri();
        outputUri = null;
        previewView.setImageDrawable(null);
        previewView.setVisibility(View.GONE);
        statusView.setText(R.string.ready_status);
    }

    private void deleteOutputUri() {
        if (outputUri == null) {
            return;
        }
        try {
            getContentResolver().delete(outputUri, null, null);
        } catch (Throwable ignored) {
            // A completed image may be read-only on some vendor providers.
        }
    }

    private void updateCaptureButton() {
        if (captureButton != null) {
            captureButton.setEnabled(!waitingForResult);
        }
    }

    private ExecutorService getWorker() {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor();
        }
        return worker;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (outputUri != null) {
            outState.putString(STATE_OUTPUT_URI, outputUri.toString());
        }
        outState.putBoolean(STATE_WAITING, waitingForResult);
    }

    @Override
    protected void onDestroy() {
        if (worker != null) {
            worker.shutdownNow();
        }
        super.onDestroy();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KiB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
