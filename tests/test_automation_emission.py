import json as _json

from conftest import fetch_json


def test_user_registration_emits_automation_event_without_password(server):
    base, portal = server()
    status, resp = fetch_json(base, '/api/portal/register', 'POST', {
        'name': 'Тест Клієнт', 'contact': 'client@example.com', 'password': 'supersecret123'
    })
    assert status == 201, resp

    with portal.db() as c:
        row = c.execute("SELECT * FROM automation_events WHERE event_name='user_registered'").fetchone()
    assert row is not None
    payload = __import__('json').loads(row['payload_json'])
    assert 'contact' not in payload
    assert 'name' not in payload
    assert payload['user_id']
    assert 'password' not in payload
    assert 'password_hash' not in str(row['payload_json'])


def test_public_booking_creation_emits_event_and_succeeds_when_n8n_unreachable(server):
    base, portal = server('http://127.0.0.1:1')  # unreachable webhook
    with portal.db() as c:
        employee_id = c.execute(
            "INSERT INTO employees(name,created_at) VALUES(?,?)",
            ('Олена Майстер', portal.now_ts()),
        ).lastrowid
    status, resp = fetch_json(base, '/api/bookings', 'POST', {
        'name': 'Клієнт Без Кабінету', 'contact': 'lead@example.com',
        'service': 'Класичний масаж', 'date': '2026-07-20', 'time': '14:30',
        'channel': 'Email', 'note': 'Прошу зосередитися на спині', 'employee_id': employee_id,
    })
    assert status == 201, resp
    assert resp['ok'] is True
    booking_id = resp['booking_id']

    with portal.db() as c:
        row = c.execute("SELECT * FROM automation_events WHERE event_name='booking_created'").fetchone()
        confirmation = c.execute("SELECT * FROM automation_events WHERE event_name='customer_confirmation_requested'").fetchone()
    assert row is not None
    assert confirmation is not None
    payload = __import__('json').loads(row['payload_json'])
    assert payload['booking_id'] == booking_id
    assert payload['service'] == 'Класичний масаж'
    assert payload['lead_name'] == 'Клієнт Без Кабінету'
    assert payload['lead_contact'] == 'lead@example.com'
    assert payload['note'] == 'Прошу зосередитися на спині'
    assert payload['date'] == '2026-07-20'
    assert payload['time'] == '14:30'
    assert payload['channel'] == 'Email'
    assert payload['employee_id'] == employee_id
    assert payload['employee_name'] == 'Олена Майстер'

    # booking row itself must exist regardless of n8n reachability
    with portal.db() as c:
        booking_row = c.execute('SELECT * FROM bookings WHERE id=?', (booking_id,)).fetchone()
    assert booking_row is not None


def test_portal_booking_creation_emits_event(server):
    import http.cookiejar
    import json as _json
    import urllib.request

    base, portal = server()
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    req = urllib.request.Request(base + '/api/portal/register', data=_json.dumps({
        'name': 'Кабінет Клієнт', 'contact': 'portal@example.com', 'password': 'supersecret123'
    }).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    with opener.open(req, timeout=5) as resp:
        assert resp.status == 201

    req2 = urllib.request.Request(base + '/api/portal/bookings', data=_json.dumps({
        'service': 'Релакс масаж'
    }).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    with opener.open(req2, timeout=5) as resp2:
        assert resp2.status == 201
        booking_id = _json.loads(resp2.read().decode())['booking_id']

    with portal.db() as c:
        row = c.execute("SELECT * FROM automation_events WHERE event_name='booking_created' AND payload_json LIKE ?",
                         (f'%{booking_id}%',)).fetchone()
    assert row is not None


def test_removed_channels_are_rejected_for_new_requests(server):
    base, _portal = server()

    for channel in ('WhatsApp', 'Viber'):
        status, response = fetch_json(base, '/api/bookings', 'POST', {
            'name': 'Новий Клієнт', 'contact': 'client@example.com',
            'service': 'Масаж', 'channel': channel,
        })
        assert status == 400, response

        status, response = fetch_json(base, '/api/portal/register', 'POST', {
            'name': 'Новий Клієнт', 'contact': f'{channel.lower()}@example.com',
            'password': 'supersecret123', 'channel': channel,
        })
        assert status == 400, response


def test_removed_channels_are_rejected_for_profile_updates(server):
    import http.cookiejar
    import urllib.error
    import urllib.request

    base, _portal = server()
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    register = urllib.request.Request(base + '/api/portal/register', data=_json.dumps({
        'name': 'Клієнт Профілю', 'contact': 'profile@example.com',
        'password': 'supersecret123', 'channel': 'Telegram',
    }).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    with opener.open(register, timeout=5) as response:
        assert response.status == 201

    for channel in ('WhatsApp', 'Viber'):
        update = urllib.request.Request(base + '/api/portal/profile', data=_json.dumps({
            'name': 'Клієнт Профілю', 'channel': channel,
        }).encode(), headers={'Content-Type': 'application/json'}, method='POST')
        try:
            opener.open(update, timeout=5)
            assert False, f'{channel} profile update unexpectedly succeeded'
        except urllib.error.HTTPError as error:
            assert error.code == 400



def _automation_event_names(portal):
    with portal.db() as c:
        return [r['event_name'] for r in c.execute('SELECT event_name FROM automation_events ORDER BY created_at').fetchall()]


def test_service_create_update_disable_emit_events(server):
    base, portal = server()
    status, created = fetch_json(base, '/api/admin/v2/services', 'POST', {'name': 'Тестова послуга', 'price': 500}, admin=True)
    assert status == 201, created
    sid = created['service']['id']

    status, updated = fetch_json(base, '/api/admin/v2/services', 'PUT', {'id': sid, 'price': 600}, admin=True)
    assert status == 200, updated

    status, deleted = fetch_json(base, '/api/admin/v2/services', 'DELETE', {'id': sid}, admin=True)
    assert status == 200, deleted

    names = _automation_event_names(portal)
    assert 'service_created' in names
    assert 'service_updated' in names
    assert 'service_disabled' in names

    with portal.db() as c:
        row = c.execute("SELECT payload_json FROM automation_events WHERE event_name='service_created'").fetchone()
    payload = _json.loads(row['payload_json'])
    assert payload['id'] == sid
    assert payload['name'] == 'Тестова послуга'


def test_employee_create_update_disable_emit_events(server):
    base, portal = server()
    status, created = fetch_json(base, '/api/admin/v2/employees', 'POST', {'name': 'Тестовий Спеціаліст'}, admin=True)
    assert status == 201, created
    eid = created['employee']['id']

    status, updated = fetch_json(base, '/api/admin/v2/employees', 'PUT', {'id': eid, 'bio': 'Оновлено'}, admin=True)
    assert status == 200, updated

    status, deleted = fetch_json(base, '/api/admin/v2/employees', 'DELETE', {'id': eid}, admin=True)
    assert status == 200, deleted

    names = _automation_event_names(portal)
    assert 'employee_created' in names
    assert 'employee_updated' in names
    assert 'employee_disabled' in names


def test_shift_create_update_delete_emit_events(server):
    base, portal = server()
    status, emp = fetch_json(base, '/api/admin/v2/employees', 'POST', {'name': 'Майстер Змін'}, admin=True)
    assert status == 201, emp
    eid = emp['employee']['id']

    status, created = fetch_json(base, '/api/admin/v2/shifts', 'POST', {
        'employee_id': eid, 'weekday': 1, 'start_time': '10:00', 'end_time': '16:00'
    }, admin=True)
    assert status == 201, created
    shift_id = created['shift']['id']

    status, updated = fetch_json(base, '/api/admin/v2/shifts', 'PUT', {'id': shift_id, 'start_time': '11:00'}, admin=True)
    assert status == 200, updated

    status, deleted = fetch_json(base, '/api/admin/v2/shifts', 'DELETE', {'id': shift_id}, admin=True)
    assert status == 200, deleted

    names = _automation_event_names(portal)
    assert 'shift_created' in names
    assert 'shift_updated' in names
    assert 'shift_deleted' in names


def test_booking_deletion_emits_event(server):
    base, portal = server()
    status, booking = fetch_json(base, '/api/bookings', 'POST', {
        'name': 'До Видалення', 'contact': 'delete@example.com', 'service': 'Класичний масаж'
    })
    assert status == 201, booking
    booking_id = booking['booking_id']

    status, deleted = fetch_json(base, '/api/admin/bookings', 'DELETE', {'id': booking_id}, admin=True)
    assert status == 200, deleted

    names = _automation_event_names(portal)
    assert names.count('booking_deleted') == 1
    with portal.db() as c:
        row = c.execute("SELECT payload_json FROM automation_events WHERE event_name='booking_deleted'").fetchone()
    payload = _json.loads(row['payload_json'])
    assert payload['booking_id'] == booking_id
