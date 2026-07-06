#!/usr/bin/env python3
"""Generate a weekly Health Hand loop report from SQLite.

Usage:
  python3 scripts/health-hand-weekly-report.py --db portal.db --days 7
"""
from __future__ import annotations

import argparse
import os
import sqlite3
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def rows(conn, sql, params=()):
    conn.row_factory = sqlite3.Row
    return [dict(r) for r in conn.execute(sql, params)]


def count(conn, sql, params=()) -> int:
    return int(conn.execute(sql, params).fetchone()[0] or 0)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--db', default=str(ROOT / 'portal.db'))
    ap.add_argument('--days', type=int, default=7)
    args = ap.parse_args()
    since = int(time.time()) - max(1, args.days) * 86400
    db_path = Path(args.db)
    if not db_path.exists():
        raise SystemExit(f'DB not found: {db_path}')
    os.environ['HH_DB'] = str(db_path)
    sys.path.insert(0, str(ROOT))
    import portal_api
    portal_api.init_db()

    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        has_events = conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='events'").fetchone() is not None
        bookings_total = count(conn, 'SELECT COUNT(*) FROM bookings WHERE created_at>=?', (since,))
        confirmed = count(conn, "SELECT COUNT(*) FROM bookings WHERE created_at>=? AND status='confirmed'", (since,))
        cancelled = count(conn, "SELECT COUNT(*) FROM bookings WHERE created_at>=? AND status='cancelled'", (since,))
        no_show = count(conn, "SELECT COUNT(*) FROM bookings WHERE created_at>=? AND status='no_show'", (since,))
        completed = count(conn, "SELECT COUNT(*) FROM bookings WHERE created_at>=? AND status='completed'", (since,))
        services = rows(conn, '''SELECT service, COUNT(*) AS count FROM bookings
                                 WHERE created_at>=? GROUP BY service ORDER BY count DESC LIMIT 10''', (since,))
        feedback = rows(conn, '''SELECT id, service, status, feedback_note FROM bookings
                                 WHERE created_at>=? AND COALESCE(feedback_note,'')<>''
                                 ORDER BY id DESC LIMIT 10''', (since,))
        if has_events:
            event_counts = rows(conn, '''SELECT event_name, COUNT(*) AS count FROM events
                                        WHERE created_at>=? GROUP BY event_name ORDER BY count DESC''', (since,))
            event_map = {r['event_name']: r['count'] for r in event_counts}
        else:
            event_counts = []
            event_map = {}

    form_opened = event_map.get('form_opened', 0)
    submitted = event_map.get('booking_submitted', bookings_total)
    slot_selected = event_map.get('slot_selected', 0)
    booking_conversion = (submitted / form_opened * 100) if form_opened else None
    slot_conversion = (slot_selected / max(event_map.get('date_selected', 0), 1) * 100) if event_map.get('date_selected') else None

    print('# Health Hand weekly loop report')
    print(f'Period: last {args.days} days')
    print()
    print('## Funnel')
    print(f'- form_opened: {form_opened}')
    print(f'- service_selected: {event_map.get("service_selected", 0)}')
    print(f'- date_selected: {event_map.get("date_selected", 0)}')
    print(f'- slot_selected: {slot_selected}')
    print(f'- booking_submitted: {submitted}')
    if booking_conversion is not None:
        print(f'- booking conversion: {booking_conversion:.1f}%')
    if slot_conversion is not None:
        print(f'- slot selection conversion: {slot_conversion:.1f}%')
    print()
    print('## Bookings')
    print(f'- total: {bookings_total}')
    print(f'- confirmed: {confirmed}')
    print(f'- completed: {completed}')
    print(f'- cancelled: {cancelled}')
    print(f'- no_show: {no_show}')
    print()
    print('## Top services')
    if services:
        for row in services:
            print(f'- {row["service"]}: {row["count"]}')
    else:
        print('- no bookings yet')
    print()
    print('## Admin feedback notes')
    if feedback:
        for row in feedback:
            print(f'- #{row["id"]} · {row["service"]} · {row["status"]}: {row["feedback_note"]}')
    else:
        print('- no feedback notes yet')
    print()
    print('## Recommendations')
    if not form_opened:
        print('- No funnel events yet: first deploy event capture and wait for real traffic.')
    elif booking_conversion is not None and booking_conversion < 15:
        print('- Booking conversion is low: inspect form copy, trust signals, and number of required fields.')
    if submitted and confirmed / max(submitted, 1) < 0.5:
        print('- Confirmation rate is weak: review admin response time and feedback notes.')
    if no_show:
        print('- No-shows detected: add/verify reminders before visit.')
    if not feedback:
        print('- Ask admin to fill feedback_note on non-confirmed bookings; otherwise product loop has weak signal.')


if __name__ == '__main__':
    main()
