# KittyTune Connect community release

This is a community build by VeXEveryOne, based on alan7383's Android and desktop applications. It is not an official upstream release.

## Downloads

- **Windows Portable x64 1.4.10**: extract the ZIP and run `KittyTune/KittyTune.exe`. Java is bundled. Quit the previous player through its tray menu before starting this version. Existing Windows preferences remain in `%APPDATA%/KittyTune`.
- **Android 2.68.0-connect.1**: the universal APK supports arm64-v8a, armeabi-v7a and x86_64. It is a non-debug release signed with this fork's own certificate. Package `com.alananasss.kittytune.connect` installs alongside the official app and the earlier development build. Its profile is separate: sign in and pair the devices again. Subsequent community APKs use the same signing key.
- **Connect Relay**: source, Docker configuration, Windows launch helpers and setup instructions. No account tokens or preconfigured personal server address are included.
- **SHA256SUMS**: download checksums.

## Connect

Pair the updated clients using the existing LAN pairing screen. Use the available-devices button in the player and tap a device to transfer the track, queue and position. Remote track information, seek and play/pause controls use the normal player; Windows has a Devices tab in its right sidebar.

For Internet access, run your own relay following [relay/README.md](relay/README.md). A server on your Windows PC can use the ngrok helper and a free account's assigned domain. Save the public HTTPS/WSS address in both apps. On the hosting PC enable **Server runs on this computer**, so its connection uses loopback. Internet access requires the host PC and tunnel to be running; automatic startup is not configured by these helpers.

**Play independently** lets each device keep its own playback and queue. History/library sync stays enabled. This setting does not download music: playback without Internet still needs an already downloaded/local track.

Android's optional headphone automation takes over an already playing PC session while the app is visible, or when it is reopened with headphones connected. Disconnecting the last headphone output pauses the phone. It does not autoplay a paused source. Some Bluetooth speakers/car outputs are classified as headphones by Android. A killed app must be reopened.

## Validation and limits

- Windows: 136 selected headless tests passed; portable distribution built successfully.
- Android: 18 Connect/playback tests passed before release packaging; signed release built separately.
- Relay: four protocol/routing tests passed.
- Real Xiaomi Android 16 + Windows checks covered LAN transfers, independent playback, mobile-data transport without Wi-Fi, a 467-track queue, command acknowledgements, player device selection, and reconnecting after restarting ngrok without changing the URL.
- The live mobile-data test used the earlier development package with the same Connect implementation. The separate community release package needs a new profile/pairing. Battery drain was not measured. macOS/Linux were not exercised.

The relay forwards encrypted control/state messages, not audio. Clients stream their own music. Position is projected locally; unchanged queues are omitted and larger snapshots are compressed. An idle Android controller closes its Connect socket when it leaves the foreground. There is no additional Connect wake lock or background discovery polling.

## Building the Android community APK

With JDK 21 and the Android SDK/NDK configured, set `CONNECT_STORE_FILE` and `CONNECT_STORE_PASSWORD` in the build process environment. The keystore must contain alias `connect`. Run:

```powershell
.\gradlew.bat :app:assembleRelease -PconnectCommunityRelease=true --no-configuration-cache
```

The option controls the separate application identity, label and signing configuration. Normal upstream builds are unchanged. Keep the private signing key for future APK updates; never commit it or its password.

Desktop build instructions: [desktop/WINDOWS.md](desktop/WINDOWS.md). Desktop provenance: [desktop/UPSTREAM.md](desktop/UPSTREAM.md).
