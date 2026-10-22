<div align="center">

# AmberLauncher

**A lightweight, minimal Minecraft: Java Edition launcher for Android, built with performance in mind.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

</div>

> **Not an official Minecraft product.** AmberLauncher is an independent project. It is not affiliated with, endorsed, or approved by Mojang Studios, Microsoft, or the upstream project teams mentioned below.

---

## About

AmberLauncher is designed to run smoothly on lower- and mid-range Android phones, while also working well on more powerful devices. It has a lightweight interface, a small set of clear settings, and default game RAM and render distance values tailored to the device's RAM. Its visual theme is translucent dark with amber accents. The launcher currently focuses on **Fabric**.

This project is a fork of **NUX Launcher**, which is based on **Zalith Launcher 2** and **PojavLauncher**. See [License and Credits](#license-and-credits).

## Features

- **Minimal dark-glass home screen** with a customizable background.
- **Multiple isolated instances**: each profile has its own version, mods, and settings.
- **Mods, resource packs, shaders, and datapacks from Modrinth**: browse and install them in the app, or install files manually.
- **Fabric focus**: choose Vanilla or Fabric when creating an instance. Mod and modpack searches are automatically filtered for Fabric, and modpacks for other loaders are rejected with a clear message.
- **Device-appropriate defaults**: initial game RAM and render distance adapt to the device's RAM. Tested models (currently the Oppo A3x 4G) get a dedicated performance profile; other phones use the standard settings.
- **Bundled renderer and Java runtime options** (Java 8, 17, 21, and 25).
- **Touch control editor**: adjust on-screen control positions and sizes, choose buttons by category, and toggle touch attack.
- **Accounts**: Microsoft, Ely.by, and offline.
- **Crash reports** with optional log upload to mclo.gs.

Not available yet: Forge, NeoForge, and Quilt (the current focus is Fabric, but this may change), as well as downloads from CurseForge (which require your own API key).

> The features above are built through GitHub Actions and may not have been tested on every device. If something is broken, open an *issue* and include the logs.

## Installation

Build APKs are available on this repository's **Releases** page (each successful build creates a pre-release automatically) and as artifacts in **Actions** for 14 days. Download the APK on your phone, allow installation from this source, and install it.

**Requirements:** Android 8.0 (API 26) or later. At least 3 GB of RAM is recommended; 4 GB or more is more comfortable. Make sure you have several gigabytes of free space for the app, bundled Java runtimes, and game data.

**Choose the APK that matches your phone.** Each release has two files:

- `AmberLauncher-<version>.apk` for 64-bit CPUs (arm64-v8a). Almost all phones released in recent years use this, so choose it if you are unsure.
- `AmberLauncher-<version>-32bit.apk` only for older phones with 32-bit CPUs (armeabi-v7a). This version is more limited: game RAM is capped at around 1.2 GB and some renderers are unavailable, so FPS may be lower than on the 64-bit version.

### Getting Started

1. Open AmberLauncher. On Android 11 and later, a storage permission explanation will appear; grant permission to store data in the `nuxlauncher` folder (you can skip this, in which case data is stored in the app's private folder).
2. Open **Accounts** and add an account: Microsoft to play online, or offline to play on your own.
3. Create a new **instance**: choose a Minecraft version, then Vanilla or Fabric (choose Fabric if you want to use mods).
4. On **Home**, tap **DOWNLOAD** and wait for it to finish (an internet connection is required; the first download can be several hundred MB), then tap **PLAY**.
5. Install mods, resource packs, and shaders from the **Mods** menu.

### Already Installed an Older Build?

The application ID has changed to `com.itsvynzzzz.amberlauncher`, so this version installs as a separate app and does not replace the old one. Game data in the `nuxlauncher` folder is still detected, but accounts need to be added again. You can remove the old app once everything is safe.

### Troubleshooting

- **The game closes or crashes**: do not allocate more than about half of your phone's RAM to the game, close other apps, or lower the resolution ratio in **Settings**. Crash reports are available in the app.
- **Low FPS**: lower the in-game render distance, reduce the resolution ratio, or try another renderer in **Settings**.
- **Mods do not work**: make sure the instance uses Fabric and that the Minecraft version matches the mod's version.

### Data Location

App data is stored in `/storage/emulated/0/nuxlauncher` (the folder name is retained so existing data can still be detected). If storage permission is not granted, data is stored in the app's private folder. The location follows the permission state: if permission is changed after an instance is created, the instance remains in its original location and will only appear again once that permission state is restored.

## Build It Yourself

### With GitHub Actions (no computer required)

1. Fork or use this repository, and enable **Actions**.
2. Push to the `main` branch or manually run the **Build APK** workflow.
3. Get the APK from **Releases** or the build artifact.

A fork without its own signing key produces an APK signed with the debug key. This is fine for testing, but do not distribute it as a release. To distribute it, create a private key and set the `AMBER_KEYSTORE_BASE64` secret (a base64-encoded PKCS12 keystore with the alias `amber`) and the `AMBER_KEYSTORE_PASSWORD` secret. Keep a backup of the key in a safe place: if it is lost, users cannot update the app and will have to reinstall it.

### With Android Studio / Gradle

Prerequisites: JDK 17, Android SDK (compile SDK 37, min SDK 26), and the NDK if compiling native code as well.

```bash
git clone https://github.com/ItsVynzzzz/AmberLauncher.git
cd AmberLauncher
cp local.properties.example local.properties   # then set sdk.dir
chmod +x gradlew
./gradlew assembleRelease
```

The APK is in `app/build/outputs/apk/release/`.

## License and Credits

AmberLauncher is released under the **GNU General Public License v3.0 (GPL-3.0)** (see [LICENSE](LICENSE)).

**Copyright**
- Changes and new code in AmberLauncher: Copyright © 2026 ItsVynzzzz.
- Upstream code remains the property of its respective authors, and its copyright notices must not be removed.

Because the upstream projects are licensed under GPL-3.0, all derivative works must also remain GPL-3.0 and their source code must remain available. Anyone may use, study, modify, and share the project as long as they follow the same terms.

**Additional terms from Zalith Launcher 2 (GPL-3.0 section 7)** that apply to derivatives:
- The names "ZalithLauncher" and "ZL" may not be used, which is why this project is named AmberLauncher.
- Modified versions must display the notice *Unofficial Modified Version* on the main screen; it appears on Home and About.
- Copyright notices displayed by the program must not be removed (see the About screen).

**Upstream projects and libraries used**

| Project | Authors | License |
|---|---|---|
| [NUX Launcher](https://github.com/IsraaDeveloper/nuxlabs) (fork source) | IsraaDeveloper | GPL-3.0 |
| [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) | MovTery and contributors | GPL-3.0 |
| [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) | artdeell, khanhtran, and team | LGPL-3.0 |
| [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher) | FCL Team | GPL-3.0 |
| [MobileGlues](https://github.com/MobileGlues/MobileGlues) | MobileGlues Team | LGPL-2.1 |
| [GL4ES](https://github.com/ptitSeb/gl4es) | ptitSeb | MIT |

Thanks also to [Modrinth](https://modrinth.com) for its public API and [Ely.by](https://ely.by) for its skin and authentication system.

## Privacy and Security

- Microsoft account passwords are never stored; sign-in uses Microsoft's official OAuth window.
- Minecraft assets and libraries are downloaded directly from Mojang's official servers, then verified against their official SHA-1 checksums.
- All internet connections use HTTPS. The offline account and skin servers are only reachable from the phone itself.
- Logs are scrubbed of tokens and passwords before they are shared or uploaded.
- API keys, signing keys, and other secrets must not be added to the repository.
- Instructions for verifying official APKs and reporting security vulnerabilities are in [SECURITY.md](SECURITY.md).

## Disclaimer

*Minecraft* is a trademark of Mojang AB / Microsoft Corporation. AmberLauncher does not include or distribute copies of the game; players must own a legitimate Minecraft: Java Edition account to play online.