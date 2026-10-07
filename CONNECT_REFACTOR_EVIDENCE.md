# Connect optimization and network migration checkpoint

This checkpoint implements event-driven Android network migration, a desktop relay standby connection while LAN is active, generation checks for obsolete sockets, bounded per-session workers and pending commands, cached queue capture/publication, compact snapshot persistence, peer caching and callback-based player preparation. Device rows show Local network / Internet instead of duplicating the player track.

The queue capture cache is initialized before PlayerViewModel's init block; both installed applications were launched successfully with a restored nonempty queue after correcting that initialization order.

## Verification

- Android focused Connect/SyncPlayback tests: 34, zero failures/errors. Debug APK built successfully.
- Windows headless tests: 152, zero failures/errors. Release distributable built successfully in the desktop fork.
- Live LAN handoff: 467 tracks, matching track/index/position, only the destination playing.
- Wi-Fi/cellular migration while already using the Internet transport: fresh phone state and full queue reached the desktop in approximately 4–5 seconds.
- The user reported that the installed build works well and requested a commit/push checkpoint. A final automated LAN-to-cellular-to-LAN cycle was not completed because USB ADB repeatedly became offline.

The debug-only diagnostics provider is read-only and protected by Android's DUMP permission. It does not expose pairing secrets or track URLs and is absent from release source sets. Credentials, device profiles, signing keys, generated binaries and private device logs are not part of this checkpoint.

Windows implementation and the isolated before/after microbenchmark are maintained in [the desktop fork](https://github.com/VeXEveryOne/KittyTuneDesktop/tree/codex/windows-connect). These measurements are not a battery or whole-application performance test. The broader refactor and optional SoundCloud/local-only Lite builds remain in progress; this checkpoint does not claim to deliver Lite artifacts.
