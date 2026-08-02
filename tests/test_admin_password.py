from urllib.error import HTTPError
from urllib.request import Request, urlopen
import json


def request_json(base, path, payload=None, password=None, method='POST'):
    body = None if payload is None else json.dumps(payload).encode()
    req = Request(base + path, data=body, method=method)
    req.add_header('Content-Type', 'application/json')
    if password:
        req.add_header('Authorization', f'Bearer {password}')
    try:
        with urlopen(req, timeout=5) as response:
            return response.status, json.loads(response.read().decode())
    except HTTPError as error:
        return error.code, json.loads(error.read().decode())


def test_first_admin_can_create_password_and_use_it(server):
    base, _ = server()

    status, created = request_json(base, '/api/admin/auth/setup', {'password': 'test-password-123'})
    assert status == 201
    assert created == {'ok': True}

    status, catalog = request_json(base, '/api/admin/v2/catalog', password='test-password-123', method='GET')
    assert status == 200
    assert catalog['ok'] is True

    status, duplicate = request_json(base, '/api/admin/auth/setup', {'password': 'another-password'})
    assert status == 409
    assert duplicate['error'] == 'admin_password_already_set'


def test_admin_password_setup_rejects_short_password(server):
    base, _ = server()
    status, response = request_json(base, '/api/admin/auth/setup', {'password': 'short'})
    assert status == 400
    assert response['error'] == 'admin_password_too_short'
