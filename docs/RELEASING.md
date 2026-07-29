# Releasing

## Before tagging

1. Update `versionCode` and `versionName` in both `app/build.gradle` and
   `probe/build.gradle`.
2. Move the relevant entries from `Unreleased` into a dated section in
   `CHANGELOG.md`.
3. Run the complete lint and build command from `CONTRIBUTING.md`.
4. Verify the module with Camera Intent Probe on a physical LSPosed device.

## Signing

GitHub Actions intentionally publishes debug CI artifacts only. A stable
release should be signed with a private Android signing key stored outside the
repository and provided through protected release secrets or an offline
release machine.

Never commit a keystore, signing password, base64-encoded signing key, or
`signing.properties` file.

Verify each signed APK before publishing:

```shell
apksigner verify --verbose CameraPicker-LSPosed.apk
apksigner verify --verbose CameraIntentProbe.apk
sha256sum CameraPicker-LSPosed.apk CameraIntentProbe.apk > SHA256SUMS.txt
```

## GitHub release

For a first-time local setup:

```shell
gh auth login
gh repo create camera-picker-lsposed --public --source . \
  --remote origin --push \
  --description "LSPosed module that replaces Android camera capture Intents with a gallery picker"
git push origin v0.1.0
```

Then upload the assets from the prepared `outputs/v0.1.0` directory:

```shell
cd ../outputs/v0.1.0
gh release create v0.1.0 \
  CameraPicker-LSPosed-v0.1.0-debug.apk \
  CameraIntentProbe-v0.1.0-debug.apk \
  SHA256SUMS.txt \
  --title "Camera Picker v0.1.0" \
  --notes "Initial verified learning release. The APKs are debug-signed; see the repository releasing guide for stable signing."
```

On Windows PowerShell, use the equivalent backtick line continuation or put
the command on one line. State whether artifacts are release-signed or
debug-signed, and copy the matching changelog section into the release notes.
