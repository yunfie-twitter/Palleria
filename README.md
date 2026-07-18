# Palleria

<p align="center">
  <img
    src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/icon.png"
    alt="Palleria icon"
    width="160"
  />
</p>

<h3 align="center">
  A modern, fast and open-source Pixiv client for Android
</h3>

<p align="center">
  Browse illustrations, manga and novels through a clean and comfortable interface.
</p>

<p align="center">



</p>

---

## About

Palleria is a free and open-source Android application that provides a modern, fast and comfortable Pixiv browsing experience.

Browse illustrations, manga and novels, search for works and users, manage bookmarks, follow artists, download images and customize the application to suit your preferences.

> [!IMPORTANT]
> Palleria is an unofficial Pixiv client and is not affiliated with, endorsed by or associated with Pixiv Inc.

## Highlights

* Modern Android interface built with Jetpack Compose
* Illustration, manga and novel support
* Powerful work, tag and user search
* Bookmark and following management
* Built-in download manager
* Browsing and search history
* Dark and AMOLED themes
* Privacy and security controls
* Japanese and English language support
* Open-source under the GPL-3.0-only license

---

## Screenshots

<table align="center">
  <tr>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/1.png"
        alt="Palleria home screen"
        width="150"
      />
      <br />
      <strong>Home</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/2.png"
        alt="Palleria search screen"
        width="150"
      />
      <br />
      <strong>Search</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/3.png"
        alt="Palleria ranking screen"
        width="150"
      />
      <br />
      <strong>Ranking</strong>
    </td>
    <td align="center">
      <img
        src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/5.png"
        alt="Palleria user profile screen"
        width="150"
      />
      <br />
      <strong>User Profile</strong>
    </td>
  </tr>
</table>

---

## Installation

### F-Droid Repository

Add the following repository to F-Droid:

```text
https://yunfi.f5.si/Palleria/repo/
```

You can also open the repository directly:

[Add the Palleria F-Droid repository](https://yunfi.f5.si/Palleria/repo/)

After adding the repository, search for **Palleria** and install the application.

### GitHub Release

You can also download the latest APK directly from GitHub Releases:

[Download from GitHub Releases](https://github.com/yunfie-twitter/Palleria/releases)

### Requirements

* Android 13 or later
* Pixiv account
* Internet connection

---

## Login

Palleria supports two authentication methods.

### Web Login

Authenticate using Pixiv's official login page displayed inside the application.

This is the recommended login method for most users.

### Refresh Token

Sign in directly using an existing Pixiv refresh token.

> [!WARNING]
> Keep your refresh token private. Anyone with access to it may be able to access your Pixiv account.

---

## Features

### Browse

* Recommended illustrations
* Daily, weekly and monthly rankings
* Latest works from followed users
* Illustration viewing
* Manga viewing
* Novel viewing
* Full-screen image viewer
* Related works
* Artist profiles

### Search

* Tag search
* Exact tag matching
* Title search
* Caption search
* User search
* Multiple sorting options
* Bookmark count filters
* Watchlist for favorite tags

### Bookmarks and Following

* Public bookmarks
* Private bookmarks
* Follow users
* Unfollow users
* Automatically bookmark downloaded works
* Automatic downloads

### Downloads

* Save individual images
* Download all pages of a work
* Configurable concurrent downloads
* Download queue management
* Automatic download support

### History

* Browsing history
* Search history
* Recently viewed works

### Personalization

* Dark theme
* AMOLED theme
* Japanese language support
* English language support
* Configurable image quality
* Pixiv image proxy support
* Mute users
* Mute tags
* Mute works
* JSON backup and restore

### Privacy and Security

* PIN lock
* Biometric authentication
* Secure window mode
* Screenshot protection

---

## Tech Stack

| Component               | Technology      |
| ----------------------- | --------------- |
| Language                | Kotlin          |
| UI Framework            | Jetpack Compose |
| Design System           | Miuix KMP       |
| Networking              | OkHttp          |
| Image Loading           | Coil 3          |
| Database                | Room            |
| Preferences             | DataStore       |
| Minimum Android Version | Android 13      |
| Minimum API Level       | API 33          |
| License                 | GPL-3.0-only    |

---

## Build

Clone the repository:

```bash
git clone https://github.com/yunfie-twitter/Palleria.git
cd Palleria
```

Build the release APK:

```bash
./gradlew :app:assembleRelease
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleRelease
```

The generated APK will normally be available under:

```text
app/build/outputs/apk/release/
```

### Release Signing

Release builds require the following environment variables:

```text
KEYSTORE_PATH
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

Example for PowerShell:

```powershell
$env:KEYSTORE_PATH = "C:\path\to\keystore.jks"
$env:KEYSTORE_PASSWORD = "your-keystore-password"
$env:KEY_ALIAS = "your-key-alias"
$env:KEY_PASSWORD = "your-key-password"

.\gradlew.bat :app:assembleRelease
```

Do not commit signing credentials, keystores or local configuration files to the repository.

---

## Contributing

Bug reports, feature requests, documentation improvements and pull requests are welcome.

Before submitting a pull request:

1. Check existing issues and pull requests.
2. Keep changes focused on a single purpose.
3. Test the application before submitting.
4. Describe the changes clearly.
5. Include screenshots for user interface changes when appropriate.

### Links

* [Repository](https://github.com/yunfie-twitter/Palleria)
* [Issues](https://github.com/yunfie-twitter/Palleria/issues)
* [Pull requests](https://github.com/yunfie-twitter/Palleria/pulls)
* [F-Droid repository](https://yunfi.f5.si/Palleria/repo/)
* [GitHub Releases](https://github.com/yunfie-twitter/Palleria/releases)

---

## Disclaimer

Palleria is an unofficial Pixiv client.

This project is not affiliated with, endorsed by or associated with Pixiv Inc.

Pixiv and related names, logos and trademarks belong to their respective owners.

Users are responsible for using the application in accordance with the Pixiv Terms of Service and all applicable laws and regulations.

---

## License

Palleria is licensed under the GNU General Public License version 3.

See the LICENSE file for details.

---

<p align="center">
  Made by <strong>ゆんふぃ</strong>
</p>

<p align="center">
  <a href="https://github.com/yunfie-twitter/Palleria">GitHub</a>
  ·
  <a href="https://github.com/yunfie-twitter/Palleria/issues">Issues</a>
  ·
  <a href="https://yunfi.f5.si/Palleria/repo/">F-Droid</a>
  ·
  <a href="https://github.com/yunfie-twitter/Palleria/releases">Releases</a>
</p>
