#!/usr/bin/env python3
"""Health Hand loop-engineering smoke check.

Default mode starts an isolated local static+API server with a temporary SQLite DB.
Use --base-url for a deployed/staging URL; add --admin-token when checking admin endpoints.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from datetime import date, timedelta
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
SENSITIVE_PREFIXES = (
    '/portal_api.py', '/portal.db', '/migrations/', '/deploy/', '/docs/',
    '/automation/', '/automation.backup/', '/__pycache__/'
)


def ok(label: str, detail: str = '') -> None:
    print(f'OK   {label}{": " + detail if detail else ""}')


def fail(label: str, detail: str = '') -> None:
    print(f'FAIL {label}{": " + detail if detail else ""}')
    raise SystemExit(1)


def run(cmd: list[str], cwd: Path = ROOT) -> str:
    res = subprocess.run(cmd, cwd=cwd, text=True, capture_output=True)
    if res.returncode != 0:
        fail('command ' + ' '.join(cmd), (res.stderr or res.stdout).strip())
    return res.stdout.strip()


def fetch_json(base: str, path: str, method: str = 'GET', payload: dict | None = None, headers: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode()
    req = urllib.request.Request(base.rstrip('/') + path, data=data, method=method)
    req.add_header('Content-Type', 'application/json')
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode() or '{}')


def next_weekday() -> str:
    d = date.today() + timedelta(days=1)
    while d.weekday() >= 5:
        d += timedelta(days=1)
    return d.isoformat()


def start_local_server(port: int, admin_token: str):
    os.environ['HH_DB'] = tempfile.mkdtemp(prefix='hh-smoke-db-') + '/portal.db'
    os.environ['HH_COOKIE_SECURE'] = '0'
    os.environ['HH_ADMIN_TOKEN'] = admin_token
    sys.path.insert(0, str(ROOT))
    import portal_api

    class CombinedHandler(portal_api.Handler, SimpleHTTPRequestHandler):
        def translate_path(self, path: str) -> str:
            return str(ROOT / urlparse(path).path.lstrip('/'))

        def log_message(self, fmt, *args):
            return

        def do_GET(self):
            path = urlparse(self.path).path
            if path.startswith('/api/'):
                return portal_api.Handler.do_GET(self)
            if path == '/portal/':
                self.path = '/portal/index.html'
            if any(path.startswith(prefix) for prefix in SENSITIVE_PREFIXES):
                self.send_error(404)
                return
            return SimpleHTTPRequestHandler.do_GET(self)

        def do_POST(self):
            path = urlparse(self.path).path
            if path.startswith('/api/'):
                return portal_api.Handler.do_POST(self)
            self.send_error(404)

        def do_OPTIONS(self):
            path = urlparse(self.path).path
            if path.startswith('/api/'):
                return portal_api.Handler.do_OPTIONS(self)
            self.send_response(204)
            self.end_headers()

    portal_api.init_db()
    httpd = ThreadingHTTPServer(('127.0.0.1', port), CombinedHandler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    return httpd, os.environ['HH_DB']


def chromium_dom(base: str) -> str:
    chromium = shutil.which('chromium') or shutil.which('chromium-browser') or shutil.which('google-chrome')
    if not chromium:
        ok('browser smoke skipped', 'chromium not installed')
        return ''
    res = subprocess.run([
        chromium, '--headless', '--no-sandbox', '--disable-gpu', '--virtual-time-budget=5000',
        '--dump-dom', base.rstrip('/') + '/#rezervace'
    ], text=True, capture_output=True, timeout=30)
    if res.returncode != 0:
        fail('browser smoke', res.stderr[-1000:])
    return res.stdout


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='')
    ap.add_argument('--port', type=int, default=8891)
    ap.add_argument('--admin-token', default='smoke-token')
    args = ap.parse_args()

    run(['python3', '-m', 'py_compile', 'portal_api.py'])
    ok('python syntax')
    run(['node', '--check', 'app.js'])
    ok('app.js syntax')
    run(['node', '--check', 'admin/admin.js'])
    ok('admin.js syntax')

    httpd = None
    db_path = None
    if args.base_url:
        base = args.base_url.rstrip('/')
    else:
        httpd, db_path = start_local_server(args.port, args.admin_token)
        base = f'http://127.0.0.1:{args.port}'
        time.sleep(0.2)

    try:
        health = fetch_json(base, '/api/portal/health')
        if not health.get('ok'):
            fail('api health', str(health))
        ok('api health')

        services = fetch_json(base, '/api/v2/services').get('services') or []
        if not services:
            fail('services', 'empty')
        ok('services', f'{len(services)} active')

        day = next_weekday()
        slots = fetch_json(base, f'/api/v2/availability?service_id={services[0]["id"]}&date={day}').get('slots') or []
        if not slots:
            fail('availability', f'no slots for {day}')
        ok('availability', f'{len(slots)} slots for {day}')

        event = fetch_json(base, '/api/events', 'POST', {
            'event_name': 'smoke_event', 'session_id': 'smoke', 'service_id': services[0]['id'],
            'page': base, 'source': 'smoke', 'meta': {'date': day}
        })
        if not event.get('ok'):
            fail('event capture', str(event))
        ok('event capture', f'id={event.get("event_id")}')

        first_slot = slots[0]
        booking = fetch_json(base, '/api/bookings', 'POST', {
            'id': 'smoke-booking', 'session_id': 'smoke', 'name': 'Smoke Client',
            'contact': 'smoke@example.test', 'channel': 'Email', 'service': services[0]['name'],
            'service_id': services[0]['id'], 'employee_id': first_slot.get('employee_id'),
            'date': first_slot.get('date'), 'time': first_slot.get('time'),
            'start_at': first_slot.get('start_at'), 'end_at': first_slot.get('end_at'),
            'note': 'Automated smoke booking', 'source': 'smoke', 'page': base
        })
        if not booking.get('ok') or not booking.get('booking_id'):
            fail('structured booking', str(booking))
        ok('structured booking', f'id={booking.get("booking_id")}')
        after_slots = fetch_json(base, f'/api/v2/availability?service_id={services[0]["id"]}&date={day}').get('slots') or []
        if any(slot.get('start_at') == first_slot.get('start_at') and slot.get('employee_id') == first_slot.get('employee_id') for slot in after_slots):
            fail('availability blocks booked slot', first_slot.get('start_at', ''))
        ok('availability blocks booked slot')

        admin_headers = {'Authorization': 'Bearer ' + args.admin_token}
        status = fetch_json(base, '/api/admin/bookings/status', 'POST', {
            'booking_id': booking['booking_id'], 'status': 'confirmed',
            'feedback_note': 'Smoke feedback: клієнт підтвердив слот.'
        }, headers=admin_headers)
        if not status.get('ok') or status.get('booking', {}).get('feedback_note') != 'Smoke feedback: клієнт підтвердив слот.':
            fail('admin feedback note', str(status))
        ok('admin feedback note')

        catalog = fetch_json(base, '/api/admin/v2/catalog', headers=admin_headers)
        if not catalog.get('ok') or not catalog.get('services'):
            fail('admin catalog', str(catalog))
        ok('admin catalog')

        summary = fetch_json(base, '/api/admin/v2/events/summary?days=7', headers=admin_headers)
        if not summary.get('ok') or 'smoke_event' not in summary.get('events', {}):
            fail('events summary', str(summary))
        ok('events summary')

        landing = urllib.request.urlopen(base + '/', timeout=10).read().decode(errors='ignore')
        if 'bookingForm' not in landing or 'app.js?v=20260629-loop-events' not in landing:
            fail('landing markup', 'booking form or cache-busted app missing')
        ok('landing markup')

        portal = urllib.request.urlopen(base + '/portal/', timeout=10).read().decode(errors='ignore')
        if 'portalAuth' not in portal:
            fail('portal route', 'portalAuth marker missing')
        ok('portal route')

        admin_page = urllib.request.urlopen(base + '/admin/', timeout=10).read().decode(errors='ignore')
        if 'Admin loop dashboard' not in admin_page or 'admin/admin.js?v=20260629-loop-events' not in admin_page:
            fail('admin route', 'admin dashboard markers missing')
        ok('admin route')

        dom = chromium_dom(base)
        if dom:
            if 'Класичний масаж — 60 хв' not in dom or 'Спочатку оберіть послугу і дату' not in dom:
                fail('browser DOM', 'dynamic services/time prompt missing')
            ok('browser DOM dynamic booking')

        for path in SENSITIVE_PREFIXES[:5]:
            try:
                urllib.request.urlopen(base + path, timeout=5)
                fail('sensitive URL blocked', path + ' returned 200')
            except urllib.error.HTTPError as e:
                if e.code not in (403, 404):
                    fail('sensitive URL blocked', f'{path} returned {e.code}')
        ok('sensitive URL denylist')

        print('\nHealth Hand smoke: PASS')
    finally:
        if httpd:
            httpd.shutdown()
        if db_path:
            shutil.rmtree(str(Path(db_path).parent), ignore_errors=True)


if __name__ == '__main__':
    main()
