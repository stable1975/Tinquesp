# Tinquesp

A local music player for Android based on [Tinque](https://github.com/nanbeimerco/Tinque). 
It features a linked/independent L/R equalizer, channel swap, folder view, and modified player open/close animations.

[![Supported OS](https://img.shields.io/badge/Supported%20OS-Android%208.0%2B-3DDC84?style=flat-square)](https://developer.android.com)
[![Package ID](https://img.shields.io/badge/App%20ID-com.music.tinque-blue?style=flat-square)](#editions)
[![Forked from](https://img.shields.io/badge/Forked%20from-MusicBeatSP-gray?style=flat-square)](https://github.com/Kdroidwin/MusicBeatSP)
[![License](https://img.shields.io/badge/License-GPL%20v3-green?style=flat-square)](LICENSE)

<br/>

[**Key Features & Changes**](#key-features--changes) · [**Editions**](#editions) · [**Build Instructions**](#build-instructions) · [**Credits**](#credits)

---

## Key Features & Changes

- **Parametric Equalizer (Linked & Independent L/R Support)**
  - In addition to standard settings applied equally to both left and right channels, it supports independent left/right configuration (individual L/R PEQ) allowing custom frequency, gain, and Q values for each channel. (Supports `.eel` or Equalizer APO text files)
- **Channel Swap**
  - Flip left and right audio channels with a single tap to suit your earphones or audio source.
- **Animation Changes**
  - Modified open/close animation behavior between the bottom bar and the full-screen player (including transition behavior when full-screen cover art is displayed).
  - Updated transition animations for lyrics on the playback bar and the full-screen lyrics view.
- **Keep Bottom Bar Expanded Option**
  - Added a setting to keep the bottom mini-player pinned on screen in an expanded state.
- **Folder View**
  - Browse your device's folder hierarchy directly to select and play tracks.
- **Independent Package ID**
  - Changed the package ID to `com.music.tinque`, allowing it to be installed on the same device alongside MusicBeatSP or BitChord.

---

## Editions

| Edition | Package ID | Overview |
| :--- | :--- | :--- |
| **Standard** | `com.music.tinque` | For standard Android devices (recommended) |
| **QQ Music Compatible** | `com.tencent.qqmusic` | Package for enabling system Dolby Atmos, etc. on OnePlus / OPPO devices |
| **KuGou Compatible** | `com.kugou.android` | Alternative compatible package for OnePlus / OPPO devices |
| **Offline** | `com.music.tinque.nointernet` | Version without internet access permissions |

---

## Build Instructions

### Requirements
- JDK 17
- Android SDK Platform 37
- CMake 3.22.1 or higher

### Build Commands
```bash
git clone [https://github.com/nanbeimerco/Tinque.git](https://github.com/nanbeimerco/Tinque.git)
cd Tinque

# Build standard debug APK
./gradlew assembleStandardDebug

# Build standard release APK
./gradlew assembleStandardRelease
