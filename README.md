# KeyGo - Digital Vault
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Download from Google Play" height="80">](https://play.google.com/store/apps/details?id=de.davis.passwordmanager)

![KeyGo Banner](https://github.com/OffRange/KeyGo/assets/42292083/c8366557-e24e-413d-be17-d6f02b2de740)

KeyGo is a secure, open-source Android password manager that allows you to store passwords and credit card information encrypted on your local device.

## Modern, Innovative Design

<p align="center">
  <img src="https://github.com/OffRange/KeyGo/assets/42292083/424f0602-ede6-4501-bec7-90fb52396e83" width="32.5%" alt="Screenshot 1"/>
  <img src="https://github.com/OffRange/KeyGo/assets/42292083/4e26e4c6-7167-4e66-9b9b-676a7470ba7a" width="32.5%" alt="Screenshot 2"/>
  <img src="https://github.com/OffRange/KeyGo/assets/42292083/badae815-6c0a-4758-8d88-ca8aa5fb5688" width="32.5%" alt="Screenshot 3"/>
</p>

## Features


- Store Passwords and Credit Card information securely
- Generate secure Passwords and Passphrases
- Estimate Password Strength with [nbvcxz](https://github.com/GoSimpleLLC/nbvcxz)
- AES-Encryption on your local device for added security
- Autofill feature for easy input
- Supports Material 3 and Dynamic Color (Android 12+ required)

## Available On

| Platform          | Status                                                                                                                                                                                                                    |
|:-----------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| GitHub            | [![GitHub tag](https://img.shields.io/github/release/OffRange/KeyGo?include_prereleases=&sort=semver)](https://github.com/OffRange/KeyGo/releases/)                                                                       |
| Google Play Store | [<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Download from Google Play" height="80">](https://play.google.com/store/apps/details?id=de.davis.passwordmanager) |

## Installation Guide

1. Download the latest [APK file](https://github.com/OffRange/KeyGo/releases/latest) under "Assets."
2. Open the **"Downloads"** folder on your device and tap the APK file to begin the installation process.
3. A pop-up message will appear asking for your permission to install the app. Tap **"Settings"** on the pop-up message.
4. On the settings page, toggle the switch next to **"Allow from this source"** to grant permission to install apps from GitHub releases.
5. Go back to the installation screen and **tap "Install"** to continue with the installation process.
6. The installation is now completed.

## Build it yourself

1. Clone the repository by running `git clone https://github.com/OffRange/KeyGo.git` in your terminal.
2. Run `.gradlew assembleGithubRelease`. APKs can be found under `KeyGo/app/github/release`.
3. (Optional but highly recommended) [Sign the apk](https://developer.android.com/build/building-cmdline#sign_manually).

### Install the apk

1. Connect your phone to your computer using a USB cable.
2. [Enable USB debugging on your device](https://developer.android.com/studio/debug/dev-options#Enable-debugging).
3. Install the APK using adb by running `adb install <path to apk file>`.
