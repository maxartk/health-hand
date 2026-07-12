from conftest import fetch_json


def test_automation_summary_requires_admin(server):
    base, portal = server()
    status, resp = fetch_json(base, '/api/admin/v2/automation/summary')
    assert status == 403
    assert resp['ok'] is False


def test_automation_summary_reports_configured_counts_and_last_outcomes(server, fake_n8n):
    base, portal = server(fake_n8n.url)
    fetch_json(base, '/api/bookings', 'POST', {'name': 'Клієнт A', 'contact': 'a@example.com', 'service': 'Класичний масаж'}, admin=False)
    portal.dispatch_pending_events()

    import time
    for _ in range(50):
        status, summary = fetch_json(base, '/api/admin/v2/automation/summary', admin=True)
        if summary.get('counts', {}).get('delivered', 0) >= 1:
            break
        time.sleep(0.05)

    assert status == 200
    assert summary['ok'] is True
    assert summary['configured'] is True
    assert summary['counts']['delivered'] == 1
    assert summary['counts']['pending'] == 0
    assert summary['counts']['failed'] == 0
    assert summary['last_success_at'] is not None
    assert summary['last_failure_at'] is None
    assert summary['last_error'] is None


def test_automation_events_lists_recent_events_without_raw_payload(server):
    base, portal = server()
    fetch_json(base, '/api/bookings', 'POST', {'name': 'Клієнт B', 'contact': 'b@example.com', 'service': 'Класичний масаж'})

    status, resp = fetch_json(base, '/api/admin/v2/automation/events', admin=True)
    assert status == 200
    assert resp['ok'] is True
    assert len(resp['events']) >= 1
    event = resp['events'][0]
    assert set(['event_id', 'event_name', 'status', 'attempts', 'last_error', 'created_at', 'updated_at']).issubset(event.keys())
    assert 'payload_json' not in event
    assert 'payload' not in event


def test_automation_retry_redelivers_a_failed_event(server, fake_n8n):
    fake_n8n.fail_times = 1
    base, portal = server(fake_n8n.url)
    status, resp = fetch_json(base, '/api/bookings', 'POST', {'name': 'Клієнт C', 'contact': 'c@example.com', 'service': 'Класичний масаж'})
    assert status == 201
    portal.dispatch_pending_events()

    import time
    event_id = None
    for _ in range(50):
        with portal.db() as c:
            row = c.execute("SELECT event_id, status FROM automation_events WHERE event_name='booking_created'").fetchone()
        if row and row['status'] == 'failed':
            event_id = row['event_id']
            break
        time.sleep(0.05)
    assert event_id, 'expected the first delivery attempt to fail'

    status, retry_resp = fetch_json(base, '/api/admin/v2/automation/retry', 'POST', {'event_id': event_id}, admin=True)
    assert status == 200, retry_resp
    assert retry_resp['ok'] is True
    assert retry_resp['event']['status'] == 'delivered'

    with portal.db() as c:
        row = c.execute('SELECT status FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    assert row['status'] == 'delivered'


def test_automation_retry_rejects_unknown_or_non_failed_event(server):
    base, portal = server()
    status, resp = fetch_json(base, '/api/admin/v2/automation/retry', 'POST', {'event_id': 'does-not-exist'}, admin=True)
    assert status == 404
    assert resp['ok'] is False

    with portal.db() as c:
        event_id = portal.enqueue_automation_event(c, 'booking_created', {'booking_id': 1})
    status2, resp2 = fetch_json(base, '/api/admin/v2/automation/retry', 'POST', {'event_id': event_id}, admin=True)
    assert status2 == 400
    assert resp2['ok'] is False


def test_automation_test_endpoint_enqueues_and_attempts_delivery(server, fake_n8n):
    base, portal = server(fake_n8n.url)
    status, resp = fetch_json(base, '/api/admin/v2/automation/test', 'POST', {}, admin=True)
    assert status == 201, resp
    assert resp['ok'] is True
    assert resp['event']['event_name'] == 'automation_test'
    assert resp['event']['status'] == 'delivered'
    assert len(fake_n8n.requests) == 1
    assert fake_n8n.requests[0]['body']['event'] == 'automation_test'
