# Release crash regression tests

Unit tests do not run R8. Before publishing, CI now installs the signed,
minified APK on an Android emulator and runs `scripts/android_release_smoke.py`.
The script checks Sonarr and Radarr library data, server switching, settings
client creation, duplicate warm deep links, and repeated cold starts. Its local
fixture server rejects SignalR negotiation to exercise failure handling too.

Run the same checks locally on a dedicated test emulator:

```sh
./gradlew :app:assembleRelease
python3 scripts/android_release_smoke.py --apk app/build/outputs/apk/release/app-release.apk
```

The emulator must have an APK signed with the same key, or no previous install.
The script does not uninstall the app or clear its data. It only adds fixture
servers. Screenshots and crash stacks go under `build/release-smoke/`.

For read-only checks against the configured real Sonarr and Radarr servers:

```sh
python3 scripts/android_release_smoke.py --live-properties local.arr-test.properties
```

Never commit that properties file. The script does not print its credentials.

The original opening crash reproduced in a minified build at
`RadarrConnectApis.indexerFlag`. R8 removed the unused interface methods and
replaced the dynamically implemented proxy cast with a throw. The API-interface
keep rule covers interfaces even when no screen currently calls their methods.
Opening a settings client bundle in the APK smoke test exercises this case.

A second reproduced crash came from calling SignalR `stop` while negotiation
was pending and later failed. The hub URL is `/signalr/messages`, not `/signalr`.
The connection manager no longer restarts a new connection for the initial
online event, and the hub adapter serializes start and stop until negotiation
settles. `SignalRHubPortIntegrationTest` checks rejected negotiation with the
real SignalR client, rather than a fake hub.
