# Native *arr client setup

Step-by-step guide for connecting the Homelab app to Radarr, Sonarr, Lidarr,
Prowlarr, and Bazarr. Once a server is registered, tapping its service on the
dashboard opens native screens instead of the WebView. The WebView keeps
handling every other service.

## What you need

- The base URL of each app as this phone reaches it, path prefix included.
  For example `http://192.168.1.20:7878` or `https://nas.example.org/radarr`.
- Each app's API key, copied from the app's own web UI (table below).
- Orbot installed, if you plan to route a server through Tor.

## Where to find the API key

Every app shows the key in the same place, under Settings > General in its
web UI:

| App | API key location |
| --- | --- |
| Radarr | Settings > General > Security, "API Key" |
| Sonarr | Settings > General > Security, "API Key" |
| Lidarr | Settings > General > Security, "API Key" |
| Prowlarr | Settings > General > Security, "API Key" |
| Bazarr | Settings > General > Security, "API Key" |

Copy the key to the clipboard. The setup sheet has a paste button next to the
key field. Scanning the key from a QR code needs camera integration that is
not built yet, so paste it.

## Add a server

1. In the Homelab app, tap the service card of the app you are connecting.
   The setup sheet opens the first time, when no server is registered for it.
2. Give the server a name and pick the app type from the five chips.
3. Paste the base URL. The field previews the normalized URL it will use,
   such as "Will use http://host:30025/radarr/". Keep the path prefix if a
   reverse proxy serves the app under one.
4. Paste the API key. It displays masked.
5. If something in front of the app asks for a login (a reverse proxy with
   basic authentication), fill the basic auth user and password fields too.
6. Tap "Test connection". The app sends a real request to the server's
   status endpoint through the same network stack normal traffic uses, so
   proxy, basic auth, and certificate settings all apply. Success reports
   the server's version.
7. Save. Tapping the service from then on opens the native screens.

The same app type accepts several servers, a second Radarr for instance.
Add them from the server list's add button, switch between them with the
chip row at the top of every arr screen, and reorder or delete them from the
same list. Each card shows a reachability dot, green when the last probe
succeeded.

## Proxy and Orbot

Open the proxy screen with the VPN icon in the server list's app bar.

1. Global default. Pick a type (None, HTTP, or SOCKS5), then host, port, and
   optional user and password. Every server without its own override uses it.
2. Per-server override. Turn on the override switch on a server's card and
   fill the proxy fields for that server alone. Keep it off to follow the
   global proxy.
3. Orbot. Flip "Route through Orbot" on the global default or a server
   override. Traffic in that scope then goes through Tor as SOCKS5 on
   127.0.0.1:9050, with DNS resolved through the proxy. Orbot must be
   installed; without it the affected servers connect directly.
4. Save with the button at the bottom. A snackbar confirms.

Certificate trust sits on the same screen, under each server's override
card. Turn on "Accept self-signed certificates" for servers behind self-
signed TLS, and prefer pasting the certificate's SHA-256 fingerprint so only
that certificate is trusted.

## Notification channels

Background checks post through five system notification channels:

| Channel | What it reports | Importance |
| --- | --- | --- |
| Grabs | New downloads grabbed from indexers | Default |
| Imports | Media imported into the library | Default |
| Failures | Failed downloads and processing errors | High |
| Health | Server health warnings | Low |
| Disk space | Low disk space warnings | Default |

Android 13 and later require the notification permission. Grant it when the
app asks; without it nothing posts. The notification preferences (bell icon
in the server list's app bar) hold a global switch, one toggle per channel
per server, and a preview button that posts one real notification so you can
verify delivery.

The checks themselves run through WorkManager, with no Google services
involved. The schedule adapts: every 30 minutes normally, 15 minutes when a
release is due within six hours or a download is active, and doubling up to
6 hours when nothing is pending.

## Offline queue behavior

- Every screen reads from the on-device cache first, so it opens offline and
  shows the state of the last sync.
- A change you make offline (marking an episode, deleting a movie, editing a
  profile) is stored on the phone together with the request it should send.
- When the connection returns, or when you pull to refresh, queued changes
  send oldest first. Each change carries an id so a retry cannot apply it
  twice.
- A change that fails five times stops retrying and moves to a "gave up"
  list instead of being lost.
- The sync status screen (sync icon in the server list's app bar) lists
  pending and given-up changes with their age, attempt count, and last
  error, plus buttons to retry one, retry all, or discard.

## Live updates

While a native screen is open, its server holds a live connection: SignalR
for Radarr, Sonarr, Lidarr, and Prowlarr, and socket.io events for Bazarr.
Libraries, queues, and calendars refresh on their own. Connections close
when you leave the arr screens, reopen on return, and reconnect with backoff
after a network loss.

## Troubleshooting

### Base URL with a path prefix

Include the prefix in the base URL, `http://host:30025/radarr` rather than
`http://host:30025`. The URL field previews what will be used. Matching also
handles reverse proxy aliases: a dashboard service saved as
`http://nas/apps/sonarr` opens a server registered as `http://nas:8989/sonarr`,
because the hosts match and the registered base path appears inside the
service URL. Two explicitly different ports never match, so a `host:7878`
service will not open the `host:8989` server.

### Self-signed certificates

A server with self-signed TLS fails the connection test with a network error
before anything else runs. Open the proxy screen, enable the per-server
override, turn on "Accept self-signed certificates", and preferably paste the
certificate's SHA-256 fingerprint so trust stays narrow. Test again
afterwards.

### 401 versus 403

Both codes surface as "Authentication failed"; the number tells them apart.
A 401 means the server rejected the credentials. Copy the API key again from
Settings > General, and if a proxy or the app itself requires basic
authentication, fill the basic auth fields in the setup sheet. A 403 means
the key was accepted but the request was refused. Check that the base URL
points at the app the key came from and at the right path, and review the
app's security settings (Settings > General > Security) for address
restrictions.

## Automatic first connect

Tapping a Radarr, Sonarr, Lidarr, Prowlarr, or Bazarr service in the dashboard never opens the WebView. When the server is not configured yet, the app loads it once in an invisible WebView that shares the app's existing SSO session, reads the API key the web UI exposes for itself, verifies it against the server, and saves the server automatically. You see a short "connecting" screen, then the native screens.

When the session has expired or the key cannot be read, the native setup sheet opens prefilled with the address and app type, so only the key paste remains. Links that carry the key directly (`...?apikey=...`, or `homelab://arr/add?app=radarr&url=...&apikey=...`) skip even that.
