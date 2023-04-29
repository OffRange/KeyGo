# Password Manager
![repository-logo](https://user-images.githubusercontent.com/42292083/219877763-f1f6b699-5f22-465d-aaf4-6f81c970ef94.png)

A secure, open-source Android password manager that stores passwords and credit card information encrypted on a local device

## Get a first impression
<div align="center">
<img src="https://user-images.githubusercontent.com/42292083/232009269-ad9e913c-55eb-48a7-986e-daa1b7e53ebd.png" alt="Dashboardside" width="300"/>
<img src="https://user-images.githubusercontent.com/42292083/232009273-d393a1a8-8698-481b-956d-9c454a003c9a.png" alt="Viewingside" width="300"/>
</div>

## Features
+ Store Passwords and Credit Card information
+ Generate secure Passwords and Passphrases
+ Estimate the Password Strength with [nbvcxz](https://github.com/GoSimpleLLC/nbvcxz)
+ AES-Encryption on your local device
+ Autofill feature for easy input
+ Supports Material 3 and Dynamic Color (Android 12+ required)

## Available On
|Platform|Status|
|--------|------|
|GitHub|[![GitHub tag](https://img.shields.io/github/release/OffRange/PasswordManager?include_prereleases=&sort=semver)](https://github.com/OffRange/PasswordManager/releases/)|
|Google Play Store|Planned for July/August 2023|

## Installation Guide
1. Download the latest [APK file](https://github.com/OffRange/PasswordManager/releases/latest) under "Assets"
2. Open the **"Downloads"** folder on your device and tap the APK file to begin the installation process.
4. A pop-up message will appear asking for your permission to install the app. Tap **"Settings"** on the pop-up message.
5. On the settings page, toggle the switch next to **"Allow from this source"** to grant permission to install apps from GitHub releases.
6. Go back to the installation screen and **tap "Install"** to continue with the installation process.
7. Installation completed

## Build it yourself
1. Clone the repository by running `git clone https://github.com/OffRange/Passwordmanager.git` in your terminal.
2. Open the `build.gradle` file in the app module and remove the `com.google.firebase:firebase-analytics:<version>` and `com.google.firebase:firebase-crashlytics:<version>` dependencies.
3. Remove the `com.google.gms.google-services` and `com.google.firebase.crashlytics` plugins from the `build.gradle` file in the app module.
4. Open the root level `build.gradle` file and remove the following dependencies:
    - `com.android.tools.build:gradle:<version>`
    - `com.google.gms:google-services:<version>`
    - `com.google.firebase:firebase-crashlytics-gradle:<version>`
5. Run `.gradlew assembleRelease`. APKs can be found under `PasswordmManager/app/build/outputs/apk/`
6. (Optional but highly recommended) [Sign the apk](https://developer.android.com/build/building-cmdline#sign_manually)
