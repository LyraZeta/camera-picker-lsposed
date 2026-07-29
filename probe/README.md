# Camera Intent Probe

This is a small native Android test app for the Camera Picker LSPosed module.
It sends exactly one capture request:

```java
Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
camera.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
startActivityForResult(camera, REQUEST_CAPTURE);
```

## Test sequence

1. Install the probe APK and the LSPosed module.
2. Launch the probe while the module is disabled for
   `com.lyra.camerapicker.probe`. The real system camera should open.
3. Enable the module only for `com.lyra.camerapicker.probe` in LSPosed.
4. Force-stop and relaunch the probe. The module's document picker should open
   instead, and the selected image should appear in the probe screen.

The result is read from the `EXTRA_OUTPUT` URI. The callback Intent's
`data` field is intentionally ignored because camera apps normally write to
the supplied URI and return an otherwise empty result Intent.
