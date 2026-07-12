import json


def test_enqueue_creates_pending_row_with_unique_event_id(load_portal_api):
    portal = load_portal_api()
    with portal.db() as c:
        event_id = portal.enqueue_automation_event(c, 'booking_created', {'booking_id': 1})
    assert event_id
    with portal.db() as c:
        row = c.execute('SELECT * FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    assert row is not None
    assert row['status'] == 'pending'
    assert row['attempts'] == 0
    assert row['event_name'] == 'booking_created'
    assert row['last_error'] == ''
    assert row['next_attempt_at'] <= portal.now_ts()
    assert json.loads(row['payload_json']) == {'booking_id': 1}

    with portal.db() as c:
        second_id = portal.enqueue_automation_event(c, 'booking_created', {'booking_id': 2})
    assert second_id != event_id


def test_dispatch_delivers_successfully_and_marks_delivered(load_portal_api, fake_n8n):
    portal = load_portal_api(fake_n8n.url)
    with portal.db() as c:
        event_id = portal.enqueue_automation_event(c, 'booking_created', {'booking_id': 42})
        ok = portal.dispatch_automation_event(c, event_id)
    assert ok is True
    assert len(fake_n8n.requests) == 1
    delivered = fake_n8n.requests[0]
    assert delivered['headers'].get('X-Event-Id') == event_id
    assert delivered['body']['event_id'] == event_id
    assert delivered['body']['event'] == 'booking_created'
    assert delivered['body']['data'] == {'booking_id': 42}

    with portal.db() as c:
        row = c.execute('SELECT * FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    assert row['status'] == 'delivered'
    assert row['attempts'] == 1
    assert row['last_error'] == ''
    assert row['delivered_at'] is not None


def test_dispatch_failure_marks_failed_with_sanitized_error_and_backoff(load_portal_api, fake_n8n):
    fake_n8n.fail_times = 2
    portal = load_portal_api(fake_n8n.url)
    before = portal.now_ts()
    with portal.db() as c:
        event_id = portal.enqueue_automation_event(c, 'booking_created', {'booking_id': 7})
        ok = portal.dispatch_automation_event(c, event_id)
    assert ok is False
    with portal.db() as c:
        row = c.execute('SELECT * FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    assert row['status'] == 'failed'
    assert row['attempts'] == 1
    assert row['last_error'] == 'http_500'
    assert 'http' not in row['last_error'] or fake_n8n.url not in row['last_error']
    assert portal.N8N_WEBHOOK not in row['last_error']
    first_backoff = row['next_attempt_at'] - before
    assert first_backoff >= portal.AUTOMATION_BACKOFF_BASE

    # second attempt still fails (fail_times had 2, one consumed) -> backoff should grow
    with portal.db() as c:
        portal.dispatch_automation_event(c, event_id)
        row2 = c.execute('SELECT * FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    assert row2['event_id'] == event_id
    assert row2['status'] == 'failed'
    assert row2['attempts'] == 2
    second_backoff = row2['next_attempt_at'] - row['updated_at']
    assert second_backoff > first_backoff

    # third attempt succeeds (fail_times exhausted) using the SAME event_id/idempotency key
    with portal.db() as c:
        ok3 = portal.dispatch_automation_event(c, event_id)
        row3 = c.execute('SELECT * FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    assert ok3 is True
    assert row3['status'] == 'delivered'
    assert row3['attempts'] == 3
    assert len(fake_n8n.requests) == 3
    assert all(r['body']['event_id'] == event_id for r in fake_n8n.requests)


def test_dispatch_without_configured_webhook_marks_failed_not_configured(load_portal_api):
    portal = load_portal_api('')
    with portal.db() as c:
        event_id = portal.enqueue_automation_event(c, 'user_registered', {'user_id': 1})
        ok = portal.dispatch_automation_event(c, event_id)
    assert ok is False
    with portal.db() as c:
        row = c.execute('SELECT * FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    assert row['status'] == 'failed'
    assert row['last_error'] == 'not_configured'


def test_dispatch_pending_events_only_processes_due_events(load_portal_api, fake_n8n):
    portal = load_portal_api(fake_n8n.url)
    with portal.db() as c:
        due_id = portal.enqueue_automation_event(c, 'booking_created', {'booking_id': 1})
        future_id = portal.enqueue_automation_event(c, 'booking_created', {'booking_id': 2})
        c.execute("UPDATE automation_events SET next_attempt_at=? WHERE event_id=?", (portal.now_ts() + 3600, future_id))
    result = portal.dispatch_pending_events()
    assert result == {'checked': 1, 'delivered': 1, 'failed': 0}
    with portal.db() as c:
        due_row = c.execute('SELECT status FROM automation_events WHERE event_id=?', (due_id,)).fetchone()
        future_row = c.execute('SELECT status FROM automation_events WHERE event_id=?', (future_id,)).fetchone()
    assert due_row['status'] == 'delivered'
    assert future_row['status'] == 'pending'


def test_business_mutation_via_emit_helper_succeeds_even_when_n8n_unreachable(load_portal_api):
    portal = load_portal_api('http://127.0.0.1:1')  # nothing listens here -> connection refused
    event_id = portal.emit_automation_event('booking_created', {'booking_id': 99})
    assert event_id
    import time as _time
    for _ in range(50):
        with portal.db() as c:
            row = c.execute('SELECT * FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
        if row['status'] == 'failed':
            break
        _time.sleep(0.05)
    assert row['status'] == 'failed'
    assert row['last_error'] == 'network_error'
