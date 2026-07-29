# Changelog

All notable changes to this project are documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-07-29

### Added

- LSPosed/Xposed hook for `ACTION_IMAGE_CAPTURE` and
  `ACTION_IMAGE_CAPTURE_SECURE`.
- Support for capture Intents wrapped by `ACTION_CHOOSER`.
- Gallery-backed replacement Activity using Android's document picker.
- `MediaStore.EXTRA_OUTPUT` byte-copy behavior and legacy thumbnail results.
- Read-only module content provider for capture requests without an output URI.
- Native Camera Intent Probe application for deterministic verification.
- Static browser compatibility page with image metadata and SHA-256 display.
- English and Simplified Chinese documentation.
