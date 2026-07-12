import json
import threading
import pytest


def test_non_loopback_webhook_configuration_is_rejected(load_portal_api):
    with pytest.raises(RuntimeError, match='loopback'):
        load_portal_api('https://example.invalid/webhook')


def test_oversized_payload_is_rejected_without_corrupt_row(load_portal_api):
    portal = load_portal_api()
    with portal.db() as c:
        with pytest.raises(ValueError, match='payload_too_large'):
            portal.enqueue_automation_event(c, 'automation_test', {'value': 'x' * 20000})
        assert c.execute('SELECT COUNT(*) FROM automation_events').fetchone()[0] == 0


def test_max_attempts_prevents_further_delivery(load_portal_api, fake_n8n):
    fake_n8n.fail_times = 10
    portal = load_portal_api(fake_n8n.url, {'HH_AUTOMATION_MAX_ATTEMPTS': '2', 'HH_AUTOMATION_BACKOFF_BASE': '0'})
    with portal.db() as c:
        event_id = portal.enqueue_automation_event(c, 'automation_test', {})
        assert portal.dispatch_automation_event(c, event_id) is False
        assert portal.dispatch_automation_event(c, event_id) is False
        assert portal.dispatch_automation_event(c, event_id) is False
        row = c.execute('SELECT * FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    assert row['attempts'] == 2
    assert len(fake_n8n.requests) == 2


def test_concurrent_dispatch_claims_event_once(load_portal_api, fake_n8n):
    portal = load_portal_api(fake_n8n.url)
    with portal.db() as c:
        event_id = portal.enqueue_automation_event(c, 'automation_test', {})
    results=[]
    def run():
        with portal.db() as c: results.append(portal.dispatch_automation_event(c,event_id))
    threads=[threading.Thread(target=run) for _ in range(2)]
    for t in threads:t.start()
    for t in threads:t.join()
    assert len(fake_n8n.requests) == 1
    assert results.count(True) == 1


def test_mismatched_ack_is_not_marked_delivered(load_portal_api, fake_n8n):
    portal = load_portal_api(fake_n8n.url)
    original = portal.urlopen
    class BadResponse:
        def __enter__(self): return self
        def __exit__(self,*a): return False
        def read(self): return json.dumps({'ok':True,'accepted':True,'event_id':'wrong'}).encode()
    portal.urlopen=lambda *a,**k: BadResponse()
    with portal.db() as c:
        event_id=portal.enqueue_automation_event(c,'automation_test',{})
        assert portal.dispatch_automation_event(c,event_id) is False
        row=c.execute('SELECT * FROM automation_events WHERE event_id=?',(event_id,)).fetchone()
    assert row['status']=='failed'
    assert row['last_error']=='invalid_ack'


def test_lost_lease_cannot_report_success(load_portal_api):
    portal = load_portal_api('http://127.0.0.1/unused')
    with portal.db() as c:
        event_id = portal.enqueue_automation_event(c, 'automation_test', {})
    class HijackResponse:
        def __enter__(self): return self
        def __exit__(self,*a): return False
        def read(self):
            with portal.db() as other:
                other.execute("UPDATE automation_events SET claim_token='new-owner' WHERE event_id=?", (event_id,))
            return json.dumps({'ok':True,'accepted':True,'event_id':event_id}).encode()
    portal.urlopen=lambda *a,**k: HijackResponse()
    with portal.db() as c:
        assert portal.dispatch_automation_event(c,event_id) is False
    with portal.db() as c:
        row=c.execute('SELECT status,claim_token FROM automation_events WHERE event_id=?',(event_id,)).fetchone()
    assert row['status']=='processing'
    assert row['claim_token']=='new-owner'
