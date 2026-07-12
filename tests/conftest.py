import importlib
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

ADMIN_TOKEN = 'test-admin-token'


class FakeN8N:
    """Records webhook deliveries and can be told to fail the next N attempts."""

    def __init__(self):
        self.requests = []
        self.status_code = 200
        self.fail_times = 0
        self._lock = threading.Lock()
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *a):
                return

            def do_POST(self):
                length = int(self.headers.get('Content-Length') or 0)
                body = self.rfile.read(length)
                with outer._lock:
                    fail = outer.fail_times > 0
                    if fail:
                        outer.fail_times -= 1
                    code = 500 if fail else outer.status_code
                    outer.requests.append({
                        'body': json.loads(body.decode() or '{}'),
                        'headers': dict(self.headers),
                    })
                response = b''
                if not fail and 200 <= code < 300:
                    event_id = json.loads(body.decode() or '{}').get('event_id', '')
                    response = json.dumps({'ok': True, 'accepted': True, 'duplicate': False, 'event_id': event_id}).encode()
                self.send_response(code)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(response)))
                self.end_headers()
                self.wfile.write(response)

        self.httpd = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.port = self.httpd.server_address[1]
        self.url = f'http://127.0.0.1:{self.port}/webhook/test'
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    def stop(self):
        self.httpd.shutdown()


@pytest.fixture
def fake_n8n():
    srv = FakeN8N()
    yield srv
    srv.stop()


@pytest.fixture
def load_portal_api(tmp_path, monkeypatch):
    """Returns a loader that (re)imports portal_api with isolated env/db per test."""

    def _load(n8n_url='', extra_env=None):
        monkeypatch.setenv('HH_ADMIN_TOKEN', ADMIN_TOKEN)
        monkeypatch.setenv('HH_COOKIE_SECURE', '0')
        monkeypatch.setenv('HH_DB', str(tmp_path / 'portal.db'))
        monkeypatch.setenv('HH_N8N_WEBHOOK', n8n_url)
        monkeypatch.setenv('HH_AUTOMATION_TIMEOUT', '3')
        for k, v in (extra_env or {}).items():
            monkeypatch.setenv(k, v)
        import portal_api as mod
        importlib.reload(mod)
        mod.init_db()
        return mod

    return _load


@pytest.fixture
def server(load_portal_api):
    def _start(n8n_url=''):
        portal = load_portal_api(n8n_url)
        httpd = ThreadingHTTPServer(('127.0.0.1', 0), portal.Handler)
        port = httpd.server_address[1]
        thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        thread.start()
        base = f'http://127.0.0.1:{port}'
        _start.httpd = httpd
        return base, portal

    yield _start
    if hasattr(_start, 'httpd'):
        _start.httpd.shutdown()


def fetch_json(base, path, method='GET', payload=None, admin=False):
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode()
    req = Request(base.rstrip('/') + path, data=data, method=method)
    req.add_header('Content-Type', 'application/json')
    if admin:
        req.add_header('Authorization', f'Bearer {ADMIN_TOKEN}')
    try:
        with urlopen(req, timeout=5) as resp:
            return resp.status, json.loads(resp.read().decode() or '{}')
    except HTTPError as e:
        return e.code, json.loads(e.read().decode() or '{}')
