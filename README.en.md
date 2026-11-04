<div align="center">

# AmberLauncher

**A Minecraft Java Edition launcher for Android: lightweight, simple, and performance-first.**

[![Latest release](https://img.shields.io/github/v/release/ItsVynzzzz/AmberLauncher?include_prereleases&label=release)](https://github.com/ItsVynzzzz/AmberLauncher/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

[**Download**](#download) · [Getting started](#getting-started) · [Features](#features) · [Help](#help) · [Credits](#license-and-credits) · [Bahasa Indonesia](README.md)

</div>

> AmberLauncher is an independent project and is not affiliated with Mojang Studios, Microsoft, or the third-party projects mentioned below.

## About

AmberLauncher aims to keep Minecraft Java running smoothly on low- and mid-range Android phones, while still feeling great on stronger ones.

- **Lightweight**: a simple interface and a small set of clear settings.
- **Smart defaults**: game RAM, resolution scale, and render distance adapt to your phone's RAM.
- **Ready for mods**: Vanilla, Fabric, Forge, and NeoForge, with mods from Modrinth and CurseForge.

It uses a translucent dark theme with an amber accent. It is still in beta, so your feedback means a lot.

## Download

Grab the latest APK from the **[Releases](https://github.com/ItsVynzzzz/AmberLauncher/releases)** page and install it on your phone.

**Requirements:** Android 8.0 (API 26) or newer, 3 GB of RAM or more (4 GB is more comfortable), and a few GB of free storage.

| File | Who it is for |
|---|---|
| `AmberLauncher-<version>-64bit-arm64-v8a-RECOMMENDED.apk` | **The default choice.** Almost every phone made in the last several years. Pick this one if you are unsure. |
| `AmberLauncher-<version>-32bit-armeabi-v7a-OLD-PHONES-ONLY.apk` | Only for old phones with a 32-bit CPU. Game RAM is capped at about 1.2 GB and some renderers are unavailable. Not present in every release. |

Later updates can be installed from inside the launcher (**Settings > Updates**).

## Getting started

1. **Open AmberLauncher.** On Android 11 and newer, allow storage access so data is kept in the `nuxlauncher` folder (you can skip it; data then stays in the app's private folder).
2. **Add an account** under **Accounts**: Microsoft to play online, or offline to play on your own.
3. **Create an instance** with **Create Instance**: choose a Minecraft version, then Vanilla, Fabric, Forge, or NeoForge.
4. On **Home**, press **DOWNLOAD** and wait for it to finish (internet required), then press **PLAY**.
5. Install mods, resource packs, and shaders from the **Mods** menu.

## Features

**Game**
- **Many separate instances**, each with its own version, mods, and settings.
- **Vanilla, Fabric, Forge (1.13+), and NeoForge (1.20.2+)**. Mod and modpack search automatically matches the instance's loader.
- **Bundled Java**: Java 8, 17, 21, and 25, with nothing else to install.
- **Renderer choices**: Auto (recommended), Krypton, MobileGlues, GL4ES, Kopper Zink, and Freedreno (Snapdragon Turnip).

**Mods and content**
- **Modrinth and CurseForge** right inside the app: mods, modpacks, resource packs, shaders, and datapacks, including their dependencies. Manual file installation works too.

**Controls**
- **Touch control editor**: adjust the position, size, and buttons on screen however you like.
- **Touch hotbar** (Bedrock style): tap the hotbar to select a slot. It can be turned off under **Settings > Controls**.
- **Controller (gamepad) support**, including mods such as Controlify.
- **Joystick auto-sprint** can be turned off, and on-screen Shift/Ctrl/Alt buttons are sent as modifiers (for example Shift + click in the inventory).

**More**
- **Accounts**: Microsoft, Ely.by, and offline.
- **Built-in screen recording**: choose *Game only* or *With buttons*. It uses the hardware encoder (720p/30 fps by default) and saves to the Gallery (Movies/AmberLauncher). Game audio is not recorded yet.
- **Crash reports** with optional log upload to mclo.gs.
- **In-app updates**, with the 32-bit/64-bit APK chosen automatically.
- **Multilingual interface**: Bahasa Indonesia, English, Español, and Tiếng Việt.
- **Optional**: TouchController mod support and the Rust engine (Horizon), both still experimental.

**Not supported yet:** Forge for Minecraft 1.12.2 and older, NeoForge for 1.20.1 and older, and Quilt.

## Help

- **The game closes by itself or crashes**: give the game no more than about half of your phone's RAM, close other apps, or lower the resolution ratio in **Settings**. Crash reports can be viewed inside the app.
- **Low FPS**: lower the render distance, lower the resolution ratio, or try another renderer in **Settings**.
- **A mod does not work**: make sure the instance's loader and Minecraft version match what the mod requires.
- **Found a bug?** Open an [issue](https://github.com/ItsVynzzzz/AmberLauncher/issues) with the AmberLauncher version, your phone model and Android version, the steps that cause the problem, and the log. Logs shared from the launcher are scrubbed of tokens and passwords.

## Privacy and security

- Microsoft passwords are never stored; sign-in uses Microsoft's official OAuth window.
- Minecraft assets and libraries are downloaded straight from Mojang's official servers and checked against their official SHA-1 fingerprints.
- All internet connections use HTTPS.
- How to verify that an APK is genuine and how to report a security vulnerability are in [SECURITY.md](SECURITY.md) (Indonesian).

## For developers

Requires JDK 17 and the Android SDK (compile SDK 37, min SDK 26).

```bash
git clone https://github.com/ItsVynzzzz/AmberLauncher.git
cd AmberLauncher
cp local.properties.example local.properties   # fill in sdk.dir
./gradlew assembleRelease
```

## License and credits

AmberLauncher is released under the **GNU General Public License v3.0** (see [LICENSE](LICENSE)). Copyright © 2026 ItsVynzzzz for changes and new code; code from the upstream projects remains the property of its respective authors.

AmberLauncher is a fork of NUX Launcher, which is based on Zalith Launcher 2 and PojavLauncher. In line with Zalith Launcher 2's additional terms, the names "ZalithLauncher" and "ZL" are not used, and the *Unofficial Modified Version* notice and copyright notices remain visible in the app.

| Project | Author | License |
|---|---|---|
| [NUX Launcher](https://github.com/IsraaDeveloper/nuxlabs) (fork source) | IsraaDeveloper | GPL-3.0 |
| [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) | MovTery and contributors | GPL-3.0 |
| [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) | artdeell, khanhtran, and team | LGPL-3.0 |
| [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher) | FCL Team | GPL-3.0 |
| [MobileGlues](https://github.com/MobileGlues/MobileGlues) | MobileGlues Team | LGPL-2.1 |
| [GL4ES](https://github.com/ptitSeb/gl4es) | ptitSeb | MIT |
| [Horizon](https://github.com/horizon-rust/horizon) and its [Android port](https://github.com/Vasysik/horizon-android) (optional) | Ludovic Marie, Vasysik, and contributors | MIT |

Thanks also to [Modrinth](https://modrinth.com) for its public API and to [Ely.by](https://ely.by) for its skin and authentication system.

*Minecraft* is a trademark of Mojang AB / Microsoft Corporation. AmberLauncher does not include or distribute a copy of the game; to play online you need a valid Minecraft Java Edition account.
