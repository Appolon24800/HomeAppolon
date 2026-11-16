# Pocket ID passkeys and domain association

The app's passkey button starts a Pocket ID device-login request and opens its
approval page in the system browser. The browser authenticates with the user's
passkey using Pocket ID's HTTPS origin. The app exchanges the approved request
for its own session and saves the returned cookies in WebView.

Pocket ID 2.19.3 accepts its HTTPS origin. A direct Android Credential Manager
assertion uses an Android app origin, which this deployment does not accept.
The app uses the browser approval flow and keeps the server's origin checks.
The separate other-device button displays a QR code for approval on another
signed-in device.

## Domain association

The deployment serves the repository's `well-known/assetlinks.json` at
`https://auth.appolon.dev/.well-known/assetlinks.json`. It must return HTTP 200,
JSON content, and no redirect. The robots policy must permit `/.well-known/`.

The document associates `dev.appolon.homelab` with the domain through
`delegate_permission/common.get_login_creds`. It retains the existing signing
certificates and includes the certificate verified from this VM's APK.
A build signed by another key needs its SHA-256 certificate fingerprint added.

The current VM APK uses this SHA-256 certificate fingerprint:

```text
7B:61:5D:A8:52:CE:D7:4F:E3:1C:41:E8:0F:F0:4C:66:F8:78:DE:D3:AD:73:98:42:87:80:46:DD:62:D9:90:87
```

Use `apksigner verify --print-certs app-release.apk` to inspect another build.
A signing certificate is public. Never publish the signing key or its password.

## Deployment verification

The November 16, 2026 deployment through pc-appolon passed the Nginx syntax
checks before reload. The public endpoint returned HTTP 200 with
`application/json`, no redirect, the expected package, and all three certificate
fingerprints. The deployment also added the narrow robots exception and updated
`SERVER.md` on the NAS. Backups are under the Nginx Proxy Manager data directory.

This association does not create a passkey or authenticate an account. A complete
login requires approval by the account owner. The emulator has no registered
user passkey, so successful authentication still needs the user's authenticator
or approval from a signed-in device.
