#!/usr/bin/env python3
"""Migrate existing Health Hand portal.db so bookings.user_id allows NULL.

Usage:
  python3 migrations/001_bookings_nullable_user.py /path/to/portal.db
"""
import sqlite3, sys, time
from pathlib import Path

DB = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'portal.db')


def table_exists(conn, table):
    return conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone() is not None


def cols(conn, table):
    conn.row_factory = sqlite3.Row
    return [dict(r) for r in conn.execute(f'PRAGMA table_info({table})')]


def create_bookings(conn):
    conn.execute('''CREATE TABLE IF NOT EXISTS bookings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
        external_id TEXT,
        lead_name TEXT DEFAULT '',
        lead_contact TEXT DEFAULT '',
        service TEXT NOT NULL,
        date TEXT DEFAULT '',
        time TEXT DEFAULT '',
        note TEXT DEFAULT '',
        channel TEXT DEFAULT '',
        status TEXT DEFAULT 'new',
        webhook_ok INTEGER DEFAULT 0,
        created_at INTEGER NOT NULL
    )''')


def main():
    if not DB.exists():
        raise SystemExit(f'DB not found: {DB}')
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA foreign_keys=OFF')
    try:
        if not table_exists(conn, 'bookings'):
            create_bookings(conn)
            print('created bookings table')
            return
        info = cols(conn, 'bookings')
        names = {c['name'] for c in info}
        user = next((c for c in info if c['name'] == 'user_id'), None)
        if user and not user['notnull'] and {'lead_name', 'lead_contact'} <= names:
            print('already migrated')
            return
        conn.execute('BEGIN')
        conn.execute('ALTER TABLE bookings RENAME TO bookings_old')
        create_bookings(conn)
        target = ['id','user_id','external_id','lead_name','lead_contact','service','date','time','note','channel','status','webhook_ok','created_at']
        old = {c['name'] for c in cols(conn, 'bookings_old')}
        select = []
        for col in target:
            if col in old:
                select.append(col)
            elif col in {'lead_name','lead_contact'}:
                select.append(f"'' AS {col}")
            elif col == 'status':
                select.append("'new' AS status")
            elif col == 'webhook_ok':
                select.append('0 AS webhook_ok')
            elif col == 'created_at':
                select.append(f'{int(time.time())} AS created_at')
            else:
                select.append(f"'' AS {col}")
        conn.execute(f"INSERT INTO bookings({','.join(target)}) SELECT {','.join(select)} FROM bookings_old")
        conn.execute('DROP TABLE bookings_old')
        conn.execute('CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings(user_id)')
        conn.commit()
        print('migrated bookings.user_id nullable')
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.execute('PRAGMA foreign_keys=ON')
        conn.close()


if __name__ == '__main__':
    main()
