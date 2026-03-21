# Table of Contents
- [Overview](#Overview)
- [Requirement](#Requirement)
- [App Installation](#App-Installation)
- [Tutorial](#Tutorial)
- [Security & Privacy](#Security-&-Privacy)

# Overview
This guide covers how to install the mobile application required to communicate with the hardware, followed by a quick tutorial on how to use it.

<img width="540" height="1200" alt="Screenshot_20260321-015320" src="https://github.com/user-attachments/assets/a7326739-c17e-403e-92b4-dc293d8e188c" />

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
![GIF_20260321_042605_078](https://github.com/user-attachments/assets/930730e2-7d61-487c-9aed-34b4818fbcff)

2. Tap the Add (+) icon to open the configuration page, then select the Listen icon.
![GIF_20260321_042408_951](https://github.com/user-attachments/assets/c60fcb01-3b74-424a-a07f-ce539db3b873)

3. Point your physical remote at the ESP32 hardware and press the button you wish to save. The app will automatically populate the data section with the received signal.
![GIF_20260321_043631_906](https://github.com/user-attachments/assets/969a31f0-bc9e-4ad0-b58c-eebb5608449c)

4. Adjust the button style and tap the Add icon to save. Finally, drag the new button to your preferred location on the screen. When finished, tap the "X" icon.
![GIF_20260321_044021_931](https://github.com/user-attachments/assets/349dcde6-5dca-4f1f-a38a-12d8a84ff38b)

5. Simply tap the button to transmit the IR signal. If you ever need to change the style or position, tap the Dotted (Menu) icon.
![GIF_20260321_044116_242](https://github.com/user-attachments/assets/3aa5a863-a1ef-408f-8051-254df06d27f1)

# Security & Privacy
- The app only requests permissions essential for its core functionality (refer to the AndroidManifest.xml):
  - Bluetooth/Nearby Devices: Required to communicate with your ESP32 hardware.
  - Location (Legacy Android only): Required only by older Android versions to perform Bluetooth scanning. No GPS data is ever recorded or shared.
- This app operates entirely locally. It does not have internet permissions, meaning your remote codes and device information never leave your phone.
