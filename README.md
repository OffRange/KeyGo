# KeyGo - Digital Vault

![KeyGo Banner](https://github.com/OffRange/PasswordManager/assets/42292083/41b29728-b68c-457f-bea4-1cf4c90e9c52)

KeyGo is a secure, open-source Android password manager that allows you to store passwords and credit card information encrypted on your local device.

## Modern, Innovative Design

<p align="center">
  <img src="https://github.com/OffRange/PasswordManager/assets/42292083/19362d0a-8c60-4b12-9602-261d376f318c" width="334" alt="Screenshot 1"/>
  <img src="https://github.com/OffRange/PasswordManager/assets/42292083/7e2bbad5-558f-4fe8-b247-38d0c0b163e6" width="334" alt="Screenshot 2"/>
  <img src="https://github.com/OffRange/PasswordManager/assets/42292083/d69f7321-c4b2-455c-80c7-b8a297ae2d05" width="334" alt="Screenshot 3"/>
</p>

## Features

- Store Passwords and Credit Card information securely
- Generate secure Passwords and Passphrases
- Estimate Password Strength with [nbvcxz](https://github.com/GoSimpleLLC/nbvcxz)
- AES-Encryption on your local device for added security
- Autofill feature for easy input
- Supports Material 3 and Dynamic Color (Android 12+ required)

## Available On

| Platform          | Status                                                                                                                                                                  |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| GitHub            | [![GitHub tag](https://img.shields.io/github/release/OffRange/PasswordManager?include_prereleases=&sort=semver)](https://github.com/OffRange/PasswordManager/releases/) |
| Google Play Store | Planned for July/August 2023                                                                                                                                            |

## Installation Guide

1. Download the latest [APK file](https://github.com/OffRange/PasswordManager/releases/latest) under "Assets."
2. Open the **"Downloads"** folder on your device and tap the APK file to begin the installation process.
3. A pop-up message will appear asking for your permission to install the app. Tap **"Settings"** on the pop-up message.
4. On the settings page, toggle the switch next to **"Allow from this source"** to grant permission to install apps from GitHub releases.
5. Go back to the installation screen and **tap "Install"** to continue with the installation process.
6. The installation is now completed.

## Build it yourself

1. Clone the repository by running `git clone https://github.com/OffRange/Passwordmanager.git` in your terminal.
2. Run `.gradlew assembleGithubRelease`. APKs can be found under `PasswordManager/app/github/release`.
3. (Optional but highly recommended) [Sign the apk](https://developer.android.com/build/building-cmdline#sign_manually).

### Install the apk

1. Connect your phone to your computer using a USB cable.
2. [Enable USB debugging on your device](https://developer.android.com/studio/debug/dev-options#Enable-debugging).
3. Install the APK using adb by running `adb install <path to apk file>`.
