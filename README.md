# Fotos

A minimal, extremely lightweight, Rust-powered native Android photo and video gallery.

## Core Philosophy

- **Private & Offline-First**: Zero network requests, no `INTERNET` permission, no ads, no accounts, no analytics, no telemetry, and no cloud sync.
- **Lightweight**: Native Kotlin + XML/ViewBinding UI paired with a compact Rust media engine (`arm64-v8a`).
- **Fast**: Bounded LRU + disk thumbnail caching, SQLite metadata indexing, paginated queries, and asynchronous cancellable decoding.

## Architecture

- `rust/` — Rust media engine (`fotos_engine`):
  - `database.rs`: SQLite (`rusqlite` bundled) with indexed queries, incremental upsert, and pagination.
  - `media_discovery.rs`: Incremental sync coordination with Android MediaStore.
  - `albums.rs`: Folder/bucket discovery, cover resolution, and sorting.
  - `search.rs`: Offline search by filename, date (`YYYY-MM-DD`), and album name.
  - `cache.rs` & `thumbnails.rs`: Bounded in-memory LRU + disk thumbnail cache.
  - `file_ops.rs`: Favorites, hidden albums, and storage statistics.
  - `jni_bridge.rs`: Safe JNI bindings to `com.fotos.app.engine.RustEngine`.
- `android/` — Native Android application (Android 11 / API 30+, `arm64-v8a`):
  - **Photos**: Chronological grid grouped by date with lazy thumbnail loading and pagination.
  - **Albums**: Auto-discovered folders with covers, counts, and long-press to hide.
  - **Search**: Instant offline search across filenames, dates, and albums.
  - **Viewer & Video**: Full-screen pinch-to-zoom, double-tap zoom, swipe navigation, immersive mode, EXIF info, share, delete (`MediaStore.createDeleteRequest`), favorite, open-with, and native `VideoView` playback.
  - **Settings**: Dark/Light/System theme, sort order, hidden albums management, cache stats & clear cache, and storage info.
- `.github/workflows/android.yml` — Automated CI/CD pipeline building `Fotos-arm64.apk`.

## Downloading the APK

### From GitHub Actions Artifacts
1. Open the **Actions** tab in this GitHub repository.
2. Select the latest **Build Fotos Android APK** workflow run.
3. Scroll down to **Artifacts** and download **Fotos-arm64** (contains `Fotos-arm64.apk`).

### From GitHub Releases
1. Open the **Releases** section of this repository.
2. Under the desired version tag (`v*`), download **Fotos-arm64.apk** from **Assets**.

## Building Locally

### Prerequisites
- JDK 17
- Android SDK (API 34) & NDK (`26.3.11579264`)
- Rust (`stable`) with `aarch64-linux-android` target
- `cargo-ndk` (`cargo install cargo-ndk`)
- Gradle 8.7+

### Steps
```bash
# 1. Build Rust engine for arm64-v8a
cd rust
cargo test
cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release

# 2. Build Android Release APK
cd ../android
gradle testDebugUnitTest
gradle assembleRelease
```
The release APK will be generated at `android/app/build/outputs/apk/release/app-release.apk`.

## Performance Targets
- **ARM64 APK**: 3–6 MB
- **Idle PSS**: 20–35 MB
- **Normal Browsing PSS**: 30–55 MB
- **Cold Launch**: < 500 ms
- **Scrolling**: 60 FPS
- **Network Requests**: 0
