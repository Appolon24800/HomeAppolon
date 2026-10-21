#!/usr/bin/env python3
"""Exercise the installed, minified APK through its public Android UI.

Default fixtures need no credentials or external services. --live-properties
uses the gitignored local.arr-test.properties for read-only server checks.
The script never uninstalls the app or clears its data.
"""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import shlex
import subprocess
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


PACKAGE = "dev.appolon.homelab"
KEY = "release-smoke-fixture-key-not-a-secret"


class Fixtures(BaseHTTPRequestHandler):
    requests = []
    denied = []

    def log_message(self, *args):
        pass

    def do_GET(self):
        path = urllib.parse.urlsplit(self.path).path
        self.requests.append(path)
        app = next((part for part in path.split('/') if part in ('sonarr', 'radarr')), '')
        if '/api/' not in path:
            data = f'<html><script>window.{app.title()}={{apikey:"{KEY}"}};</script></html>'.encode()
            self.send_response(200)
            self.send_header('Content-Type', 'text/html')
            self.send_header('Set-Cookie', f'fixture_session=ready; HttpOnly; SameSite=Lax; Path={path}')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if path.startswith('/auto/') and 'fixture_session=ready' not in self.headers.get('Cookie', ''):
            self.denied.append(path)
            self.send_response(401)
            self.end_headers()
            return
        resource = path.split('/api/v3/')[-1]
        payload = []
        if resource == 'system/status':
            payload = {'appName': app.title(), 'version': '1.0.0'}
        elif resource in ('movie', 'series'):
            payload = [{
                'id': 990001,
                'title': f'{"Automatic" if path.startswith("/auto/") else "Release"} smoke {app}',
                'sortTitle': 'release smoke',
                'year': 2026,
                'monitored': True,
                'path': f'/media/{app}/release-smoke',
                'images': [],
                'seasons': [],
            }]
        data = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        # Exercise rejected negotiation while opening/closing native screens.
        if '/signalr/messages/negotiate' in self.path:
            self.send_response(503)
        else:
            self.send_response(405)
        self.end_headers()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--live-properties', type=Path)
    parser.add_argument('--output', type=Path, default=Path('build/release-smoke'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*command, binary=False):
        result = subprocess.run(
            [args.adb, '-s', args.serial, *command], capture_output=True, timeout=60,
        )
        if result.returncode:
            # am start arguments can contain credentials. Never include them.
            raise RuntimeError(f'adb {command[0]} failed with exit {result.returncode}')
        return result.stdout if binary else result.stdout.decode(errors='replace')

    def check_crashes():
        log = adb('logcat', '-d', '-s', 'AndroidRuntime:E')
        if f'Process: {PACKAGE}' in log and 'FATAL EXCEPTION' in log:
            # Keep the stack only, not request URLs or intent data.
            stack = '\n'.join(l for l in log.splitlines() if 'at ' in l or 'Exception' in l)
            (args.output / 'crash.txt').write_text(stack)
            raise AssertionError('App crashed. See release-smoke/crash.txt and the APK mapping.')

    def document():
        check_crashes()
        # A busy emulator can fail or truncate a dump; retry rather than
        # aborting the run on one bad poll.
        for attempt in range(3):
            adb('shell', 'uiautomator dump /sdcard/release-smoke.xml')
            raw = adb('shell', 'cat /sdcard/release-smoke.xml')
            try:
                return ET.fromstring(raw)
            except ET.ParseError:
                if attempt == 2:
                    return ET.fromstring('<node/>')
        raise AssertionError('unreachable')

    def wait_text(text, timeout=60):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            root = document()
            for node in root.iter('node'):
                if text in (node.get('text', ''), node.get('content-desc', '')):
                    return node, root
            time.sleep(0.3)
        (args.output / 'failure.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        (args.output / 'failure.xml').write_text(ET.tostring(root, encoding='unicode'))
        if not args.live_properties:
            (args.output / 'requests.json').write_text(json.dumps(Fixtures.requests))
            (args.output / 'denied.json').write_text(json.dumps(Fixtures.denied))
        raise AssertionError(f'UI never displayed {text!r}')

    def screen_height(root):
        for node in root.iter('node'):
            bounds = node.get('bounds', '')
            if bounds == '[0,0][1080,2400]':
                return int(re.findall(r'\d+', bounds)[3])
        return None

    def check_top_padding(node, root):
        # One window inset band above the app bar is correct (cutout/
        # status clearance). Two stacked insets left a dead band twice as
        # tall before the ArrShell fix; a title starting lower than 10% of
        # the screen means padding is being applied again.
        height = screen_height(root) or 2400
        top = int(re.findall(r'\d+', node.get('bounds'))[1])
        assert top < height * 0.10, (
            f'top bar title starts at {top}px of {height}px; '
            'window insets are stacking again'
        )
    def click_through(target, appears, attempts=3):
        # A tap can land while the hub is still settling; verify the target
        # screen appeared and tap again when it did not.
        for attempt in range(attempts):
            click(target)
            try:
                wait_text(appears, timeout=15)
                return
            except AssertionError:
                if attempt == attempts - 1:
                    raise
                wait_text('Add new', timeout=15)

    def click(text):
        if text == 'Open settings':
            # With several servers, the settings button is at the end of the
            # horizontal switcher rather than necessarily on screen.
            for _ in range(6):
                root = document()
                if any(n.get('content-desc') == text for n in root.iter('node')):
                    break
                chips = [n for n in root.iter('node') if n.get('text') in ('Sonarr', 'Radarr')]
                if not chips:
                    break
                row = max(chips, key=lambda n: int(re.findall(r'\d+', n.get('bounds'))[1]))
                _, y1, _, y2 = map(int, re.findall(r'\d+', row.get('bounds')))
                width = max(int(re.findall(r'\d+', n.get('bounds'))[2]) for n in root.iter('node'))
                x1, x2 = 0, width
                adb('shell', 'input', 'swipe', str(int(x2 * 0.8)), str((y1 + y2) // 2),
                    str(int(x2 * 0.2)), str((y1 + y2) // 2), '300')
        node, _ = wait_text(text)
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))

    def ensure_hub(timeout=60):
        # A warm re-open of the server already on screen does not re-enter
        # the hub; pop once to the server home when it does not appear.
        deadline = time.monotonic() + timeout
        popped = False
        while time.monotonic() < deadline:
            try:
                return wait_text('Add new', timeout=8)
            except AssertionError:
                if not popped:
                    adb('shell', 'input', 'keyevent', '4')
                    time.sleep(2)
                    popped = True
                else:
                    continue
        raise AssertionError('server home hub never appeared')

    def open_server(app, cold=False):
        if cold:
            adb('shell', 'am', 'force-stop', PACKAGE)
        url, key = servers[app]
        link = 'homelab://arr/add?' + urllib.parse.urlencode(
            {'app': app, 'url': url, 'apikey': key},
        )
        adb('shell', 'am start -a android.intent.action.VIEW -d ' + shlex.quote(link))
        # The server home hub renders first; every main destination must be
        # listed on it before the walk continues into the library.
        _, hub_root = ensure_hub(timeout=90 if cold else 60)
        for destination in ('Library', 'Queue', 'Calendar', 'Wanted', 'History'):
            assert any(
                n.get('text') == destination for n in hub_root.iter('node')
            ), f'{app} hub is missing its {destination} entry'
        click_through('Library', 'Series' if app == 'sonarr' else 'Movies')
        title_node, root = wait_text('Series' if app == 'sonarr' else 'Movies')
        check_top_padding(title_node, root)
        if not args.live_properties:
            wait_text(f'Release smoke {app}')
        else:
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                root = document()
                if any(n.get('text', '') in live_titles[app] for n in root.iter('node')):
                    break
                time.sleep(0.3)
            else:
                raise AssertionError(f'{app} did not display any title from its real API')
        (args.output / f'{app}.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        print(f'PASS {app} native library, {"cold" if cold else "warm"} start', flush=True)

    if args.apk:
        output = adb('install', '-r', str(args.apk))
        if 'Success' not in output:
            raise RuntimeError('APK installation failed. Use an emulator with a matching signing key.')
    adb('logcat', '-c')
    fixture = None
    if args.live_properties:
        props = dict(
            line.split('=', 1) for line in args.live_properties.read_text().splitlines()
            if '=' in line and not line.startswith('#')
        )
        servers = {
            app: (props[app + '.url'].replace('\\:', ':'), props[app + '.apikey'])
            for app in ('sonarr', 'radarr')
        }
        live_titles = {}
        for app, (url, key) in servers.items():
            resource = 'series' if app == 'sonarr' else 'movie'
            request = urllib.request.Request(url.rstrip('/') + '/api/v3/' + resource,
                                             headers={'X-Api-Key': key})
            with urllib.request.urlopen(request, timeout=20) as response:
                live_titles[app] = {item['title'] for item in json.load(response)}
            assert live_titles[app], f'{app} needs a nonempty library for the live smoke test'
    else:
        fixture = ThreadingHTTPServer(('127.0.0.1', 18089), Fixtures)
        threading.Thread(target=fixture.serve_forever, daemon=True).start()
        adb('reverse', 'tcp:18089', 'tcp:18089')
        servers = {app: (f'http://127.0.0.1:18089/{app}/', KEY) for app in ('sonarr', 'radarr')}

    try:
        open_server('sonarr', cold=True)
        open_server('radarr')
        # Server switching must change arguments, not restore the previous app.
        click('Sonarr')
        wait_text('Add new')
        click_through('Library', 'Series')
        wait_text('Series')
        click('Radarr')
        wait_text('Add new')
        click_through('Library', 'Movies')
        wait_text('Movies')
        print('PASS server switch in both directions', flush=True)
        for app in ('radarr', 'sonarr'):
            open_server(app)
            # open_server leaves the library on screen; pop back to the hub
            # deterministically for the destination walk.
            adb('shell', 'input', 'keyevent', '4')
            wait_text('Add new')
            # Every hub destination must render without crashing: the queue
            # and wanted screens ride client bundles that the startup warm
            # used to provide.
            for destination, marker in (('Queue', 'The queue is empty'), ('Wanted', 'Missing')):
                click_through(destination, marker)
                adb('shell', 'input', 'keyevent', '4')
                time.sleep(2)
                wait_text('Add new')
            print(f'PASS {app} hub destinations render', flush=True)
            click('Open settings')
            click('Download clients')
            wait_text('Download clients')
            if fixture:
                deadline = time.monotonic() + 30
                expected = f'/{app}/api/v3/downloadclient'
                while expected not in Fixtures.requests and time.monotonic() < deadline:
                    check_crashes()
                    time.sleep(0.2)
                assert expected in Fixtures.requests, f'{app} settings API was never reached'
            check_crashes()
            print(f'PASS {app} settings proxy bundle', flush=True)
        for app in ('radarr', 'sonarr', 'radarr', 'sonarr'):
            open_server(app, cold=True)
        if fixture:
            for app in ('sonarr', 'radarr'):
                # No URL/key form or key-carrying link. The real headless
                # WebView must recover the key and pass its HttpOnly session
                # cookie to verification and the native feature's REST client.
                base = f'http://localhost:18089/auto/{time.monotonic_ns()}/{app}/'
                adb('shell', 'am start -n ' + PACKAGE + '/.MainActivity -a android.intent.action.VIEW -d '
                    + shlex.quote(base))
                wait_text('Add new')
                click_through('Library', f'Automatic smoke {app}')
                print(f'PASS {app} automatic discovery and cookie-gated native API', flush=True)
        # Allow pending live callbacks to run after repeated stop/start cycles.
        time.sleep(3)
        check_crashes()
        print('PASS minified release smoke checks', flush=True)
    finally:
        adb('shell', 'am', 'force-stop', PACKAGE)
        if fixture:
            fixture.shutdown()
            adb('reverse', '--remove', 'tcp:18089')


if __name__ == '__main__':
    main()
