# Palleria

<p align="center">
  <img
    src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/icon.png"
    alt="Palleria icon"
    width="160"
  />
</p>

<h3 align="center">
  A modern, high-performance, and open-source Pixiv client for Android
</h3>

<p align="center">
  Explore illustrations, manga, and novels through an ultra-fast, fluid interface crafted with Jetpack Compose, Miuix UI, and native Rust.
</p>

<p align="center">

[![License](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Rust](https://img.shields.io/badge/Rust-Native%20Core-DEA584.svg?style=flat-square&logo=rust&logoColor=white)](https://www.rust-lang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Miuix KMP](https://img.shields.io/badge/Design-Miuix%20KMP-FF6900.svg?style=flat-square)](https://github.com/miuix-kotlin-multiplatform/miuix)
[![F-Droid](https://img.shields.io/badge/F--Droid-Repository-1976D2.svg?style=flat-square&logo=fdroid&logoColor=white)](https://yunfi.f5.si/Palleria/repo/)
[![GitHub Release](https://img.shields.io/github/v/release/yunfie-twitter/Palleria?style=flat-square&logo=github&label=Release)](https://github.com/yunfie-twitter/Palleria/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/yunfie-twitter/Palleria/total?style=flat-square&logo=github&label=Downloads)](https://github.com/yunfie-twitter/Palleria/releases)
[![GitHub Stars](https://img.shields.io/github/stars/yunfie-twitter/Palleria?style=flat-square&logo=github)](https://github.com/yunfie-twitter/Palleria/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/yunfie-twitter/Palleria?style=flat-square&logo=github)](https://github.com/yunfie-twitter/Palleria/issues)
[![Architecture diagram](https://gitdiagram.com/diagram-badge.svg)](https://gitdiagram.com/yunfie-twitter/palleria?utm_source=readme&utm_medium=badge)
</p>

---

## About

**Palleria** is a free, privacy-first, and open-source Android application designed to deliver the best Pixiv browsing experience on mobile devices.

Built from the ground up with Jetpack Compose, Miuix design language, and a native Rust backend via UniFFI, Palleria combines desktop-class performance, low battery consumption, rich tactile micro-interactions, and end-to-end encrypted synchronization.

> [!IMPORTANT]
> Palleria is an unofficial Pixiv client and is not affiliated with, endorsed by, or associated with Pixiv Inc.

---

## Highlights

* ⚡ **High-Performance Rust Core**: Native UniFFI engine for accelerated Ugoira decoding, APNG/WebP/MP4 conversion, and zero-overhead data handling.
* 🎨 **Modern Miuix & Material You UI**: Fluid animations, AMOLED & dynamic artwork themes, and customizable multi-column adaptive grids.
* ✨ **Stealth Micro-UX Innovations**:
  * **Pre-DNS & Socket Warming**: Pre-resolves DNS and establishes TLS 1.3 socket pools during splash display for instantaneous first-image loading.
  * **Delta & ETag Sync**: Differential following feed synchronization that never re-downloads known artworks and pre-warms high-res images in background.
  * **Velocity-Landing Prefetch**: Predicts fling inertia stop points to fetch destination artworks ahead of time.
  * **Stealth Download Resume**: Resumes interrupted downloads using HTTP 206 `Range` byte offsets without restarting from scratch.
  * **Manga Adaptive Preload**: Detects reader direction habits (RTL / LTR) within two flips and automatically adjusts preload queuing.
  * **Courtesy Audio Fade**: 100ms logarithmic volume fade-out when earphones disconnect to protect ears and prevent public audio leaks.
  * **Tactile Haptic Feedback**: Boundary collision thuds (zoom & grid limits), dismiss threshold tick feedback, and bookmark tactile bursts.
  * **Wide Color Gamut (Display P3)**: 10-bit wide color gamut support for vibrant artwork reproduction on OLED screens.
* 🔄 **PallaSync Cross-Device Synchronization**: End-to-end encrypted peer-to-peer sync of bookmarks, history, settings, and mutes across Android devices and browser extensions.
* 📖 **Deep Reading Experience**: Dedicated Manga viewer and Novel reader with customizable typography, progress resume, and background Text-to-Speech (TTS) audiobook narration.
* 📦 **Built-in Download & Updater**: Multi-threaded downloader with EXIF privacy stripping, template naming, and seamless background APK updates with Shizuku support.
* 🛡️ **Privacy & Security**: PIN lock, biometric authentication, recents task snapshot stealth blur, and tracking URL cleaner.
* 🌐 **Trilingual Localization**: Full Japanese (日本語), English, and Korean (한국어) language support.

---

## Screenshots

<table align="center">
  <tr>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/1.png"
        alt="Palleria home screen"
        width="160"
      />
      <br />
      <strong>Home & Feed</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/2.png"
        alt="Palleria search screen"
        width="160"
      />
      <br />
      <strong>Discovery & Search</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/3.png"
        alt="Palleria ranking screen"
        width="160"
      />
      <br />
      <strong>Rankings</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/5.png"
        alt="Palleria user profile screen"
        width="160"
      />
      <br />
      <strong>Artist Profile</strong>
    </td>
  </tr>
</table>

---

## Features Overview

### 🖼️ Artwork & Media Browsing
* **Explore**: Recommended feeds, daily/weekly/monthly rankings, and latest works from followed creators.
* **Vertical Shorts Feed**: Immersive full-screen vertical swipe feed for discovering illustrations.
* **Ugoira Animations**: Power-saving sliding-window decoder with inBitmap buffer recycling and export to WebP, APNG, GIF, or MP4.
* **Manga & Comics**: Dual-reading modes (horizontal & vertical webtoon), auto-detected page orientation, and volume key page navigation.
* **Novels & TTS Audiobook**: Customizable typography, series navigation, reading progress auto-save, and background TTS narration with sleep timer.
* **Full-Screen Viewer**: Pinch-to-zoom (up to 6x) with boundary collision resistance, swipe-to-dismiss with tactile ticks, and quick peek previews.

### 🔍 Search & Discovery
* **Comprehensive Search**: Search by tags, exact tag matching, titles, captions, or artists.
* **Smart Filtering**: Filter by work type (Illustration, Manga, Ugoira, Novel), age restriction (All-ages, R-18, R-18G), bookmark count thresholds, and date ranges.
* **Tag Watchlist**: Track your favorite tags and artists with one-tap quick access.

### 📥 Downloads & Media Management
* **Smart Downloads**: Save individual pages or complete multi-page sets with configurable concurrent workers.
* **Stealth Byte-Range Resume**: Automatic recovery from network interruptions via HTTP 206 `Range` requests.
* **Custom Path & SAF**: Store images in custom SAF directories or standard Pictures gallery.
* **Privacy EXIF Stripping**: Strips personal metadata from downloaded files before saving.
* **Smart Filename Templates**: Format filenames using custom patterns (`{id}`, `{title}`, `{artist}`, `{page}`).

### 🔄 PallaSync (Multi-Device Sync)
* **Secure Synchronization**: End-to-end encrypted synchronization without third-party data tracking.
* **Ecosystem Integration**: Sync bookmarks, view history, search history, settings, and mute rules between Palleria Android apps and the PallaSync browser extension.
* **Pairing**: Quick pairing via QR codes or secure pairing phrases.

### ⚙️ Micro-UX & Customization
* **Theming**: System default, Light, Dark, AMOLED Pure Black, and Dynamic Color matching artwork accents.
* **Adaptive Layouts**: 1 to 4 customizable grid columns for phones, foldables, and tablets.
* **Mute Filters**: Filter unwanted tags, users, and artwork IDs across feeds and search results.
* **Stamps & Reactions**: View and send Pixiv reaction stamps in comment sections.
* **Backup & Restore**: Export and import complete settings and local databases as JSON.

---

## Installation

### F-Droid Repository (Recommended)

Add the official Palleria repository to F-Droid or any compatible client (Droid-ify, Neo Store):

```text
https://yunfi.f5.si/Palleria/repo/
```

<p align="left">

[![Add to F-Droid](https://img.shields.io/badge/F--Droid-Add%20Repository-1976D2.svg?style=for-the-badge&logo=fdroid&logoColor=white)](https://yunfi.f5.si/Palleria/repo?fingerprint=28A7F64F373AC1AD5FBB4822870E2E07B2B204C7EC71E58CE40F9D54EF2727D9)

</p>

### GitHub Releases

Download the latest standalone APK directly from GitHub Releases:

<p align="left">

[![Download APK](https://img.shields.io/badge/GitHub-Download%20Latest%20APK-181717.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/yunfie-twitter/Palleria/releases/latest)

</p>

### System Requirements

* **OS**: Android 7.0 (Nougat, API 24) or newer (Targeting Android 16, API 36)
* **Account**: Pixiv account (Web login or Refresh token)
* **Network**: Active internet connection (Custom image proxy & DoH supported)

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.x, Rust (1.80+) |
| **UI Framework** | Jetpack Compose, Compose Foundation & Material 3 |
| **Design System** | Miuix KMP (Xiaomi HyperOS / MIUI Design Guidelines) |
| **Native Engine** | Rust (`pixiv-api`) via UniFFI, Tokio async runtime |
| **Networking** | OkHttp 4 / 5, DNS over HTTPS (DoH), ConnectionPool Warming |
| **Image Pipeline** | Coil 3, Hardware Bitmaps, Display P3 Color Gamut, `inBitmap` recycling |
| **Persistence** | Room (SQLite with migration indexes), Jetpack DataStore Preferences, AtomicFile |
| **Background Sync** | WorkManager (Periodic ETag delta sync & background updates) |
| **System Integration** | Shizuku API (rootless silent installs), Android SAF, Glance AppWidget |

---

## Building from Source

### Prerequisites

1. **Android Studio**: Ladybug / Meerkat or newer
2. **JDK**: Java 17 or Java 21
3. **Android NDK**: NDK 26+ installed via Android SDK Manager
4. **Rust Toolchain**: Stable Rust with `cargo-ndk`:
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
   cargo install cargo-ndk
   ```

### Clone and Compile

```bash
git clone https://github.com/yunfie-twitter/Palleria.git
cd Palleria
```

**Build Debug APK:**

```bash
# Linux / macOS
./gradlew :app:assembleDebug

# Windows PowerShell
.\gradlew.bat :app:assembleDebug
```

**Build Release APK:**

```bash
# Linux / macOS
./gradlew :app:assembleRelease

# Windows PowerShell
.\gradlew.bat :app:assembleRelease
```

Generated APKs will be located at:
```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
```

### Release Signing

To sign release builds, configure the following environment variables:

```powershell
$env:KEYSTORE_PATH = "C:\path\to\keystore.jks"
$env:KEYSTORE_PASSWORD = "your-keystore-password"
$env:KEY_ALIAS = "your-key-alias"
$env:KEY_PASSWORD = "your-key-password"

.\gradlew.bat :app:assembleRelease
```

---

## Contributing

Contributions, bug reports, feature suggestions, and localization improvements are very welcome!

1. Fork the repository and create a feature branch (`git checkout -b feat/my-feature`).
2. Run code style checks and unit tests before committing:
   ```bash
   ./gradlew ktlintCheck detekt testDebugUnitTest
   ```
3. Commit your changes and open a Pull Request against `main`.

<p align="left">

[![Issues](https://img.shields.io/badge/GitHub-Report%20Issue-1F883D.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/yunfie-twitter/Palleria/issues)
[![Pull Requests](https://img.shields.io/badge/GitHub-Pull%20Requests-8250DF.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/yunfie-twitter/Palleria/pulls)

</p>

---

## Disclaimer

Palleria is an unofficial open-source application and is not associated with Pixiv Inc. All Pixiv trademarks, logos, and artwork assets belong to their respective owners. Please respect Pixiv's Terms of Service when using this application.

---

## License

Palleria is licensed under the [GNU General Public License v3.0 (GPL-3.0-only)](LICENSE).

<p align="center">
  Crafted with care by <strong>ゆんふぃ</strong>
</p>
