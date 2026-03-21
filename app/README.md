# Table of Contents
- [Overview](#Overview)
- [Requirement](#Requirement)
- [App Installation](#App-Installation)
- [Tutorial](#Tutorial)
- [Security & Privacy](#Security-&-Privacy)

# Overview
This guide covers how to install the mobile application required to communicate with the hardware, followed by a quick tutorial on how to use it.

# Requirement
Minimum Android Version: Android 7.0

Not sure what version you have? Go to Settings > About Phone > Android Version

# App Installation
If you want to install the app directly without building the source code, follow these steps:
1. Navigate to the /release folder in this repository.
2. Download the app-release.apk file directly to your Android device.
3. Upon first launch, ensure you press "Allow" when prompted for Nearby Devices (or Bluetooth/Location). These permissions are required for the app to scan for and connect to the hardware.

# Tutorial
Follow these steps to set up your IR Remote and begin capturing signals:
1. Tap the Bluetooth icon and select the device named "MyRemote" from the discovery list. Note This is a one-time setup; the app will automatically reconnect to this device in the future.
2. Tap the Add (+) icon to open the configuration page, then select the Listen icon.
3. Point your physical remote at the ESP32 hardware and press the button you wish to save. The app will automatically populate the data section with the received signal.
4. (Optional) Adjust the button style. Once satisfied, tap the Add icon to save.
5. Drag the new button to your preferred location on the screen. When finished, tap the "X" icon.
6. Simply tap the button to transmit the IR signal. If you ever need to change the style or position, tap the Dotted (Menu) icon.

# Security & Privacy
- The app only requests permissions essential for its core functionality (refer to the AndroidManifest.xml):
  - Bluetooth/Nearby Devices: Required to communicate with your ESP32 hardware.
  - Location (Legacy Android only): Required only by older Android versions to perform Bluetooth scanning. No GPS data is ever recorded or shared.
- This app operates entirely locally. It does not have internet permissions, meaning your remote codes and device information never leave your phone.
