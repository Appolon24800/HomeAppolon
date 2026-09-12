# Passkeys inside the app: one-time server step

Passkeys (WebAuthn) cannot run inside an ordinary Android WebView. The app now
enables WebView WebAuthn in "APP mode" (androidx.webkit
`WEB_AUTHENTICATION_SUPPORT_FOR_APP`), which routes PocketID's passkey
ceremony through Android Credential Manager natively — but the platform will
only do that once it has verified the app belongs to the PocketID domain.

That verification is one static file on the auth host.

## What to deploy

Serve this exact JSON (it is in `well-known/assetlinks.json` in the repo) at:

    https://auth.appolon.dev/.well-known/assetlinks.json

Requirements: HTTP 200, `Content-Type: application/json`, **no redirect**,
and `robots.txt` must not disallow `/.well-known/`.

The file contains BOTH signing fingerprints (debug + release keystore), so it
works for debug installs and release APKs.

## Nginx Proxy Manager (your setup)

Option A — host the file anywhere and add a custom location to the
`auth.appolon.dev` proxy host:

- Hosts → auth.appolon.dev → Advanced (or Custom Locations)
- Add location `/.well-known/assetlinks.json` that serves the file
  (e.g. `proxy_pass` to a tiny static endpoint, or use the
  `alias` directive in Advanced config if you host the file on the box)

Option B — put the file next to your existing static content and add an
Advanced-config location block:

```nginx
location /.well-known/assetlinks.json {
    return 200 '[{ "relation": ["delegate_permission/common.get_login_creds"], "target": { "namespace": "android_app", "package_name": "dev.appolon.homelab", "sha256_cert_fingerprints": ["86:6F:9E:90:72:F7:DB:29:82:49:85:70:02:A3:44:73:67:70:73:C0:BB:2F:9F:FC:5F:D7:35:B4:0A:61:D9:AF","3C:3A:B8:2A:BB:F2:C0:2A:01:F1:33:56:DA:94:D8:6B:5A:98:0B:1B:DA:5F:5B:15:94:7F:EB:F5:AB:E4:13:B2"] } }]';
    default_type application/json;
}
```

## Verify

    curl -s https://auth.appolon.dev/.well-known/assetlinks.json | python3 -m json.tool

Must return the JSON above (not an HTML error page, not a redirect).

## Then

Open the app → ⋮ → PocketID account → Sign in. PocketID's Authenticate button
will now trigger the native passkey sheet (biometric/PIN), and after
authentication the session lands in the app's cookie jar, so every service's
"Sign in with LaPlateforme" completes automatically.

Until the file is deployed, the passkey sheet will show no usable credential
("Sign in another way") and one-time access codes remain the working fallback.
