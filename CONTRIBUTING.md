# Contributing

Contributions are welcome. Please keep changes focused on the capture path
being modified and describe how the behavior was verified.

## Development setup

- JDK 17
- Android SDK 35
- Android Studio or Gradle 8.7
- A rooted Android test device with LSPosed for runtime verification

Build and lint both applications:

```shell
./gradlew :app:lintDebug :probe:lintDebug \
  :app:assembleDebug :probe:assembleDebug
```

## Runtime verification

1. Run Camera Intent Probe without the module enabled for
   `com.lyra.camerapicker.probe`; the real camera should open.
2. Enable the module for that package in LSPosed and force-stop the probe.
3. Run the probe again; the document picker should replace the camera.
4. Select an image and verify that the probe displays the bytes written to its
   original `EXTRA_OUTPUT` URI.
5. Include relevant `[CameraPicker]` LSPosed log lines in bug reports.

## Pull requests

- Do not commit APKs, build output, logs, keystores, or local SDK paths.
- Add or update documentation when changing supported capture behavior.
- Keep Camera1/Camera2 frame injection separate from the Intent replacement
  layer unless the change explicitly introduces that new subsystem.
- Update `CHANGELOG.md` for user-visible changes.

By submitting a contribution, you agree that it is licensed under the
Apache License 2.0 used by this repository.
