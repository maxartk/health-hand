import json
import os
import subprocess
import sqlite3
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_automation_worker_cli_dispatches_due_events_once_and_exits(tmp_path):
    db_path = tmp_path / 'portal.db'
    env = dict(os.environ)
    env['HH_DB'] = str(db_path)
    env['HH_ADMIN_TOKEN'] = 'worker-test-token'
    env['HH_COOKIE_SECURE'] = '0'
    env['HH_N8N_WEBHOOK'] = ''  # unreachable/unconfigured -> event will be marked failed, not hang

    # Seed the DB with a pending automation event via the module's own init/enqueue helpers.
    seed = subprocess.run(
        [sys.executable, '-c',
         "import os,sys; sys.path.insert(0, %r); import portal_api as p; p.init_db();"
         "c=p.db(); eid=p.enqueue_automation_event(c, 'automation_test', {'x':1}); c.commit(); c.close(); print(eid)"
         % str(ROOT)],
        cwd=ROOT, env=env, text=True, capture_output=True, timeout=15,
    )
    assert seed.returncode == 0, seed.stderr
    event_id = seed.stdout.strip().splitlines()[-1]

    result = subprocess.run(
        [sys.executable, str(ROOT / 'portal_api.py'), 'automation-worker', '--once'],
        cwd=ROOT, env=env, text=True, capture_output=True, timeout=15,
    )
    assert result.returncode == 0, result.stderr
    assert 'checked=1' in result.stdout

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    row = conn.execute('SELECT status FROM automation_events WHERE event_id=?', (event_id,)).fetchone()
    conn.close()
    assert row['status'] == 'failed'  # not_configured webhook -> terminal failed, but the sweep ran
