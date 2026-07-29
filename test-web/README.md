# Camera Capture Lab

Static Android browser test page for the Camera Picker LSPosed module.

Open `index.html` directly for a desktop check, or serve this directory on the
local network so the Android phone can reach it:

```text
python -m http.server 8787 --bind 0.0.0.0 --directory test-web
```

The page contains three inputs:

- `accept="image/*" capture="environment"`
- `accept="image/*" capture="user"`
- `accept="image/*"` as a gallery baseline

Selected files stay in the browser and are not uploaded.

## Important limitation

This page cannot prove that the LSPosed module intercepted an Android camera
Intent. The HTML `capture` attribute is only a browser hint, and Chrome may
show a gallery option even when the module is not installed.

For a deterministic test, build and install `Camera Intent Probe` from the
`probe` Gradle module. It sends a native `MediaStore.ACTION_IMAGE_CAPTURE`
Intent with `MediaStore.EXTRA_OUTPUT`:

1. Run the probe with the module disabled for `com.lyra.camerapicker.probe`;
   the real system camera should open.
2. Enable the module only for `com.lyra.camerapicker.probe` in LSPosed.
3. Run the probe again; the module picker should open and the selected image
   should appear in the probe result screen.
