package com.lyra.camerapicker;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Replaces camera capture intents inside each LSPosed-scoped application.
 *
 * This intentionally works at the Intent boundary. It covers the common
 * browser and application flow that launches the system camera for a result,
 * while leaving apps that render a camera preview through Camera/Camera2 alone.
 */
public final class CameraIntentHook implements IXposedHookLoadPackage {
    private static final String TAG = "[CameraPicker] ";
    private static final ThreadLocal<Boolean> REPLACING = new ThreadLocal<>();
    private static final Set<String> IMAGE_ACTIONS = new HashSet<>();

    static {
        IMAGE_ACTIONS.add(MediaStore.ACTION_IMAGE_CAPTURE);
        IMAGE_ACTIONS.add(MediaStore.ACTION_IMAGE_CAPTURE_SECURE);
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || shouldSkipPackage(lpparam.packageName)) {
            return;
        }

        hookActivityMethods(lpparam.packageName);
        hookInstrumentationMethods(lpparam.packageName);
        XposedBridge.log(TAG + "enabled in " + lpparam.packageName);
    }

    private static void hookActivityMethods(final String packageName) {
        try {
            XposedBridge.hookAllMethods(Activity.class, "startActivityForResult", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    replaceIntentArgument(param, packageName);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "cannot hook Activity.startActivityForResult in "
                    + packageName + ": " + t);
        }
    }

    private static void hookInstrumentationMethods(final String packageName) {
        try {
            XposedBridge.hookAllMethods(Instrumentation.class, "execStartActivity", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    replaceIntentArgument(param, packageName);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "cannot hook Instrumentation.execStartActivity in "
                    + packageName + ": " + t);
        }
    }

    private static void replaceIntentArgument(XC_MethodHook.MethodHookParam param, String packageName) {
        if (Boolean.TRUE.equals(REPLACING.get())) {
            return;
        }

        int intentIndex = findIntentIndex(param.args);
        if (intentIndex < 0) {
            return;
        }

        Intent original = (Intent) param.args[intentIndex];
        if (original.getBooleanExtra(ModuleContract.EXTRA_HOOKED, false)) {
            return;
        }

        Intent captureIntent = unwrapChooser(original);
        if (captureIntent == null || !isImageCapture(captureIntent)) {
            return;
        }

        Context context = param.thisObject instanceof Context
                ? (Context) param.thisObject
                : findContext(param.args);
        if (context == null) {
            XposedBridge.log(TAG + "found camera intent but no Context in " + packageName);
            return;
        }

        Uri outputUri = readOutputUri(captureIntent);
        Intent replacement = buildReplacementIntent(context, packageName, captureIntent, outputUri);
        if (replacement == null) {
            return;
        }

        try {
            REPLACING.set(Boolean.TRUE);
            param.args[intentIndex] = replacement;
        } finally {
            REPLACING.remove();
        }

        XposedBridge.log(TAG + "replaced " + captureIntent.getAction()
                + " for " + packageName + (outputUri == null ? "" : " with EXTRA_OUTPUT"));
    }

    private static Intent buildReplacementIntent(
            Context context,
            String packageName,
            Intent captureIntent,
            Uri outputUri) {
        Intent replacement = new Intent();
        replacement.setComponent(new ComponentName(
                ModuleContract.MODULE_PACKAGE,
                PickerActivity.class.getName()));
        replacement.putExtra(ModuleContract.EXTRA_HOOKED, true);
        replacement.putExtra(ModuleContract.EXTRA_ORIGINAL_ACTION, captureIntent.getAction());
        replacement.putExtra(ModuleContract.EXTRA_ORIGINAL_TYPE, captureIntent.getType());
        replacement.putExtra(ModuleContract.EXTRA_CALLING_PACKAGE, packageName);

        if (outputUri != null) {
            replacement.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
            replacement.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // ClipData makes the URI grant visible to ActivityManager on modern Android.
            replacement.setClipData(ClipData.newRawUri("camera-output", outputUri));
            try {
                context.grantUriPermission(
                        ModuleContract.MODULE_PACKAGE,
                        outputUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Throwable ignored) {
                // The grant on the replacement Intent is the normal path. Some providers
                // reject an explicit grant from the caller, so keep going here.
            }
        }
        return replacement;
    }

    private static boolean isImageCapture(Intent intent) {
        return intent != null && IMAGE_ACTIONS.contains(intent.getAction());
    }

    private static Intent unwrapChooser(Intent intent) {
        Intent current = intent;
        for (int i = 0; i < 2 && current != null; i++) {
            if (!Intent.ACTION_CHOOSER.equals(current.getAction())) {
                return current;
            }
            Bundle extras = current.getExtras();
            if (extras == null) {
                return null;
            }
            Object nested = extras.get(Intent.EXTRA_INTENT);
            if (!(nested instanceof Intent)) {
                return null;
            }
            current = (Intent) nested;
        }
        return current;
    }

    private static Uri readOutputUri(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return null;
        }
        Object value = extras.get(MediaStore.EXTRA_OUTPUT);
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

    private static int findIntentIndex(Object[] args) {
        if (args == null) {
            return -1;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Intent) {
                return i;
            }
        }
        return -1;
    }

    private static Context findContext(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Context) {
                return (Context) arg;
            }
        }
        return null;
    }

    private static boolean shouldSkipPackage(String packageName) {
        if (packageName == null || ModuleContract.MODULE_PACKAGE.equals(packageName)) {
            return true;
        }
        return "android".equals(packageName)
                || "com.android.systemui".equals(packageName)
                || packageName.startsWith("com.android.permissioncontroller")
                || packageName.startsWith("com.android.documentsui")
                || packageName.startsWith("com.google.android.documentsui");
    }
}
