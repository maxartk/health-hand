#!/usr/bin/env python3
"""Health Hand portal + booking API.

Environment:
  HH_DB              SQLite DB path (default: portal.db)
  HH_PORT            API port (default: 8787)
  HH_HOST            API bind host (default: 127.0.0.1)
  HH_COOKIE_SECURE   1/0 (default: 1)
  HH_SESSION_TTL     seconds (default: 30 days)
  HH_ADMIN_TOKEN     token for admin endpoints (optional)
  HH_N8N_WEBHOOK     URL to notify on booking events (optional)
"""
import json, sqlite3, secrets, hashlib, hmac, time, re, os
from datetime import datetime, timedelta
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from http.cookies import SimpleCookie
from pathlib import Path
from urllib.parse import urlparse, parse_qs
from urllib.request import Request, urlopen
from urllib.error import URLError

BASE_DIR = Path(__file__).resolve().parent
DB = Path(os.environ.get('HH_DB', BASE_DIR / 'portal.db'))
DB.parent.mkdir(parents=True, exist_ok=True)
SESSION_TTL = int(os.environ.get('HH_SESSION_TTL', str(60 * 60 * 24 * 30)))
PBKDF2_ITERS = 210_000
COOKIE_SECURE = os.environ.get('HH_COOKIE_SECURE', '1').lower() not in {'0', 'false', 'no'}
LOGIN_WINDOW = 15 * 60
LOGIN_LIMIT = 8
CHANNELS = {'WhatsApp', 'Viber', 'Telegram', 'Дзвінок', 'Email'}
STATUS_BOOKING = {'new', 'contacted', 'confirmed', 'completed', 'cancelled', 'no_show', 'followup_sent'}
ADMIN_TOKEN = os.environ.get('HH_ADMIN_TOKEN') or os.environ.get('HH_ADMIN_PASSWORD', '')
N8N_WEBHOOK = os.environ.get('HH_N8N_WEBHOOK', '')
_login_failures = {}


def now_ts():
    return int(time.time())


def clean_channel(value):
    value = (value or '').strip()
    return value if value in CHANNELS else 'Дзвінок'


def clean_status(value, default='new'):
    value = (value or '').strip().lower()
    return value if value in STATUS_BOOKING else default


def db():
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA foreign_keys=ON')
    return conn


def column_info(conn, table):
    return [dict(row) for row in conn.execute(f'PRAGMA table_info({table})')]


def table_exists(conn, table):
    return conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone() is not None


def create_bookings(conn):
    conn.execute('''CREATE TABLE IF NOT EXISTS bookings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
        external_id TEXT,
        lead_name TEXT DEFAULT '',
        lead_contact TEXT DEFAULT '',
        service TEXT NOT NULL,
        service_id INTEGER REFERENCES services(id) ON DELETE SET NULL,
        employee_id INTEGER REFERENCES employees(id) ON DELETE SET NULL,
        start_at TEXT DEFAULT '',
        end_at TEXT DEFAULT '',
        date TEXT DEFAULT '',
        time TEXT DEFAULT '',
        note TEXT DEFAULT '',
        feedback_note TEXT DEFAULT '',
        channel TEXT DEFAULT '',
        status TEXT DEFAULT 'new',
        webhook_ok INTEGER DEFAULT 0,
        created_at INTEGER NOT NULL
    )''')


def migrate_bookings_nullable(conn):
    if not table_exists(conn, 'bookings'):
        return
    cols = column_info(conn, 'bookings')
    user_col = next((c for c in cols if c['name'] == 'user_id'), None)
    missing_lead_cols = {name for name in ['lead_name', 'lead_contact'] if name not in {c['name'] for c in cols}}
    if user_col and not user_col['notnull'] and not missing_lead_cols:
        return

    conn.execute('ALTER TABLE bookings RENAME TO bookings_old')
    create_bookings(conn)
    old_cols = {c['name'] for c in column_info(conn, 'bookings_old')}
    target = ['id', 'user_id', 'external_id', 'lead_name', 'lead_contact', 'service', 'service_id', 'employee_id', 'start_at', 'end_at', 'date', 'time', 'note', 'feedback_note', 'channel', 'status', 'webhook_ok', 'created_at']
    select_parts = []
    for col in target:
        if col in old_cols:
            select_parts.append(col)
        elif col == 'lead_name':
            select_parts.append("'' AS lead_name")
        elif col == 'lead_contact':
            select_parts.append("'' AS lead_contact")
        elif col == 'status':
            select_parts.append("'new' AS status")
        elif col == 'webhook_ok':
            select_parts.append('0 AS webhook_ok')
        elif col == 'created_at':
            select_parts.append(f'{now_ts()} AS created_at')
        else:
            select_parts.append("'' AS " + col)
    conn.execute(f"INSERT INTO bookings({','.join(target)}) SELECT {','.join(select_parts)} FROM bookings_old")
    conn.execute('DROP TABLE bookings_old')



def seed_v2_defaults(conn):
    if conn.execute('SELECT COUNT(*) FROM service_categories').fetchone()[0] == 0:
        conn.execute('INSERT INTO service_categories(name,description,sort_order,is_active,created_at) VALUES(?,?,?,?,?)',
            ('Масаж', 'Основні масажні процедури Health Hand', 10, 1, now_ts()))
    category_id = conn.execute('SELECT id FROM service_categories ORDER BY sort_order,id LIMIT 1').fetchone()['id']
    defaults = [
        ('Класичний масаж', 'Базова процедура для зняття напруги та відновлення тіла.', 60, 1200, category_id, 10),
        ('Релакс масаж', 'М’який антистресовий масаж для глибокого розслаблення.', 60, 1200, category_id, 20),
        ('Спортивний масаж', 'Інтенсивна робота з м’язами після навантажень або для профілактики.', 60, 1400, category_id, 30),
        ('Масаж спини', 'Фокус на спині, шиї та плечовому поясі.', 45, 900, category_id, 40),
        ('Антицелюлітний масаж', 'Курсова процедура для тонусу шкіри та моделюючого догляду.', 60, 1300, category_id, 50),
    ]
    for name, description, duration, price, cat, order in defaults:
        conn.execute("""INSERT INTO services(name,description,duration_minutes,price,category_id,sort_order,is_active,created_at)
                        SELECT ?,?,?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM services WHERE lower(name)=lower(?))""",
            (name, description, duration, price, cat, order, 1, now_ts(), name))
    if conn.execute('SELECT COUNT(*) FROM employees').fetchone()[0] == 0:
        conn.execute('INSERT INTO employees(name,role,bio,is_active,sort_order,created_at) VALUES(?,?,?,?,?,?)',
            ('Health Hand спеціаліст', 'massage_therapist', 'Основний спеціаліст Health Hand.', 1, 10, now_ts()))
    employee_id = conn.execute('SELECT id FROM employees WHERE is_active=1 ORDER BY sort_order,id LIMIT 1').fetchone()['id']
    for row in conn.execute('SELECT id FROM services WHERE is_active=1'):
        conn.execute("""INSERT INTO employee_services(employee_id,service_id,created_at)
                        SELECT ?,?,? WHERE NOT EXISTS (SELECT 1 FROM employee_services WHERE employee_id=? AND service_id=?)""",
            (employee_id, row['id'], now_ts(), employee_id, row['id']))
    for dow in range(0, 5):
        conn.execute("""INSERT INTO employee_shifts(employee_id,weekday,start_time,end_time,is_active,created_at)
                        SELECT ?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM employee_shifts WHERE employee_id=? AND weekday=? AND start_time=? AND end_time=?)""",
            (employee_id, dow, '09:00', '18:00', 1, now_ts(), employee_id, dow, '09:00', '18:00'))


def parse_date(value):
    return datetime.strptime(value, '%Y-%m-%d').date()


def parse_hhmm(day, value):
    hour, minute = [int(x) for x in value.split(':', 1)]
    return datetime(day.year, day.month, day.day, hour, minute)


def get_available_slots(conn, service_id, date_value, employee_id=None, buffer_minutes=15):
    try:
        day = parse_date(date_value)
    except Exception:
        return []
    service = conn.execute('SELECT * FROM services WHERE id=? AND is_active=1', (service_id,)).fetchone()
    if not service:
        return []
    duration = int(service['duration_minutes'] or 60)
    params = [service_id]
    emp_filter = ''
    if employee_id:
        emp_filter = ' AND e.id=?'
        params.append(employee_id)
    employees = conn.execute(f"""
        SELECT e.* FROM employees e
        JOIN employee_services es ON es.employee_id=e.id
        WHERE es.service_id=? AND e.is_active=1 {emp_filter}
        ORDER BY e.sort_order,e.id
    """, params).fetchall()
    slots = []
    for emp in employees:
        shifts = conn.execute("""SELECT * FROM employee_shifts
                                 WHERE employee_id=? AND weekday=? AND is_active=1
                                 ORDER BY start_time""", (emp['id'], day.weekday())).fetchall()
        busy_rows = conn.execute("""SELECT start_at,end_at FROM appointments
                                    WHERE status NOT IN ('cancelled','no_show')
                                      AND substr(start_at,1,10)=?
                                      AND (employee_id IS NULL OR employee_id=?)""", (date_value, emp['id'])).fetchall()
        busy = []
        for b in busy_rows:
            try:
                start = datetime.fromisoformat((b['start_at'] or '').replace('Z','+00:00')).replace(tzinfo=None)
                end_raw = b['end_at'] or ''
                end = datetime.fromisoformat(end_raw.replace('Z','+00:00')).replace(tzinfo=None) if end_raw else start + timedelta(minutes=duration)
                busy.append((start - timedelta(minutes=buffer_minutes), end + timedelta(minutes=buffer_minutes)))
            except Exception:
                continue
        breaks = conn.execute('SELECT * FROM employee_breaks WHERE employee_id=? AND weekday=? AND is_active=1', (emp['id'], day.weekday())).fetchall()
        for shift in shifts:
            cursor = parse_hhmm(day, shift['start_time'])
            shift_end = parse_hhmm(day, shift['end_time'])
            while cursor + timedelta(minutes=duration) <= shift_end:
                end = cursor + timedelta(minutes=duration)
                blocked = False
                for br in breaks:
                    bstart = parse_hhmm(day, br['start_time'])
                    bend = parse_hhmm(day, br['end_time'])
                    if cursor < bend and end > bstart:
                        blocked = True
                        break
                if not blocked:
                    for bstart, bend in busy:
                        if cursor < bend and end > bstart:
                            blocked = True
                            break
                if not blocked and cursor > datetime.now():
                    slots.append({
                        'employee_id': emp['id'],
                        'employee_name': emp['name'],
                        'service_id': service['id'],
                        'service_name': service['name'],
                        'date': date_value,
                        'time': cursor.strftime('%H:%M'),
                        'start_at': cursor.isoformat(timespec='minutes'),
                        'end_at': end.isoformat(timespec='minutes'),
                    })
                cursor += timedelta(minutes=30)
    return slots

def ensure_column(conn, table, column, definition):
    names = {c['name'] for c in column_info(conn, table)}
    if column not in names:
        conn.execute(f'ALTER TABLE {table} ADD COLUMN {column} {definition}')


def init_db():
    with db() as c:
        c.execute('''CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            contact TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            birthday TEXT DEFAULT '',
            channel TEXT DEFAULT 'WhatsApp',
            notes TEXT DEFAULT '',
            reminders INTEGER DEFAULT 1,
            created_at INTEGER NOT NULL
        )''')
        c.execute('''CREATE TABLE IF NOT EXISTS sessions (
            token TEXT PRIMARY KEY,
            user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            expires_at INTEGER NOT NULL
        )''')
        create_bookings(c)
        migrate_bookings_nullable(c)
        ensure_column(c, 'bookings', 'service_id', 'INTEGER REFERENCES services(id) ON DELETE SET NULL')
        ensure_column(c, 'bookings', 'employee_id', 'INTEGER REFERENCES employees(id) ON DELETE SET NULL')
        ensure_column(c, 'bookings', 'start_at', "TEXT DEFAULT ''")
        ensure_column(c, 'bookings', 'end_at', "TEXT DEFAULT ''")
        ensure_column(c, 'bookings', 'feedback_note', "TEXT DEFAULT ''")
        c.execute('''CREATE TABLE IF NOT EXISTS intake_forms (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
            booking_id INTEGER REFERENCES bookings(id) ON DELETE SET NULL,
            contraindications TEXT DEFAULT '',
            medications TEXT DEFAULT '',
            pain_scale INTEGER,
            previous_massage TEXT DEFAULT '',
            goals TEXT DEFAULT '',
            risk_flags TEXT DEFAULT '',
            created_at INTEGER NOT NULL
        )''')
        c.execute('''CREATE TABLE IF NOT EXISTS care_plans (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            booking_id INTEGER REFERENCES bookings(id) ON DELETE SET NULL,
            recommendation TEXT DEFAULT '',
            next_service TEXT DEFAULT '',
            next_date TEXT DEFAULT '',
            exercises TEXT DEFAULT '',
            notes TEXT DEFAULT '',
            created_at INTEGER NOT NULL
        )''')
        c.execute('''CREATE TABLE IF NOT EXISTS packages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            name TEXT NOT NULL,
            total_sessions INTEGER NOT NULL DEFAULT 0,
            remaining_sessions INTEGER NOT NULL DEFAULT 0,
            expires_at INTEGER,
            created_at INTEGER NOT NULL
        )''')
        c.execute('''CREATE TABLE IF NOT EXISTS appointments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            booking_id INTEGER NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
            user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
            employee_id INTEGER REFERENCES employees(id) ON DELETE SET NULL,
            start_at TEXT,
            end_at TEXT,
            calendar_event_id TEXT DEFAULT '',
            status TEXT DEFAULT 'scheduled',
            created_at INTEGER NOT NULL
        )''')
        ensure_column(c, 'appointments', 'employee_id', 'INTEGER REFERENCES employees(id) ON DELETE SET NULL')
        c.execute('''CREATE TABLE IF NOT EXISTS password_resets (
            token_hash TEXT PRIMARY KEY,
            user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            expires_at INTEGER NOT NULL
        )''')
        c.execute("""CREATE TABLE IF NOT EXISTS service_categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            description TEXT DEFAULT '',
            sort_order INTEGER DEFAULT 0,
            is_active INTEGER DEFAULT 1,
            created_at INTEGER NOT NULL
        )""")
        c.execute("""CREATE TABLE IF NOT EXISTS services (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            description TEXT DEFAULT '',
            duration_minutes INTEGER NOT NULL DEFAULT 60,
            price INTEGER DEFAULT 0,
            category_id INTEGER REFERENCES service_categories(id) ON DELETE SET NULL,
            sort_order INTEGER DEFAULT 0,
            is_active INTEGER DEFAULT 1,
            created_at INTEGER NOT NULL
        )""")
        c.execute("""CREATE TABLE IF NOT EXISTS employees (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            role TEXT DEFAULT 'massage_therapist',
            bio TEXT DEFAULT '',
            phone TEXT DEFAULT '',
            is_active INTEGER DEFAULT 1,
            sort_order INTEGER DEFAULT 0,
            show_on_site INTEGER DEFAULT 1,
            created_at INTEGER NOT NULL
        )""")
        ensure_column(c, 'employees', 'show_on_site', 'INTEGER DEFAULT 1')
        c.execute("""CREATE TABLE IF NOT EXISTS employee_services (
            employee_id INTEGER NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
            service_id INTEGER NOT NULL REFERENCES services(id) ON DELETE CASCADE,
            created_at INTEGER NOT NULL,
            PRIMARY KEY(employee_id, service_id)
        )""")
        c.execute("""CREATE TABLE IF NOT EXISTS employee_shifts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            employee_id INTEGER NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
            weekday INTEGER NOT NULL,
            start_time TEXT NOT NULL,
            end_time TEXT NOT NULL,
            is_active INTEGER DEFAULT 1,
            created_at INTEGER NOT NULL
        )""")
        c.execute("""CREATE TABLE IF NOT EXISTS employee_breaks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            employee_id INTEGER NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
            weekday INTEGER NOT NULL,
            start_time TEXT NOT NULL,
            end_time TEXT NOT NULL,
            label TEXT DEFAULT '',
            is_active INTEGER DEFAULT 1,
            created_at INTEGER NOT NULL
        )""")
        c.execute("""CREATE TABLE IF NOT EXISTS events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            event_name TEXT NOT NULL,
            session_id TEXT DEFAULT '',
            user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
            booking_id INTEGER REFERENCES bookings(id) ON DELETE SET NULL,
            service_id INTEGER REFERENCES services(id) ON DELETE SET NULL,
            employee_id INTEGER REFERENCES employees(id) ON DELETE SET NULL,
            page TEXT DEFAULT '',
            source TEXT DEFAULT '',
            meta_json TEXT DEFAULT '{}',
            created_at INTEGER NOT NULL
        )""")
        c.execute('CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings(user_id)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_bookings_contact ON bookings(lead_contact)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_intake_user_id ON intake_forms(user_id)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_care_user_id ON care_plans(user_id)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_packages_user_id ON packages(user_id)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_services_active ON services(is_active,sort_order)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_employees_active ON employees(is_active,sort_order)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_employee_shifts_lookup ON employee_shifts(employee_id,weekday,is_active)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_events_created ON events(created_at)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_events_name_created ON events(event_name,created_at)')
        c.execute('CREATE INDEX IF NOT EXISTS idx_events_session ON events(session_id,created_at)')
        seed_v2_defaults(c)
        c.execute('DELETE FROM sessions WHERE expires_at <= ?', (now_ts(),))
        c.execute('DELETE FROM password_resets WHERE expires_at <= ?', (now_ts(),))


def norm_contact(v):
    return re.sub(r'\s+', '', (v or '').strip().lower())


def hash_password(password, salt=None):
    salt = salt or secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac('sha256', password.encode(), salt.encode(), PBKDF2_ITERS).hex()
    return f'pbkdf2_sha256${PBKDF2_ITERS}${salt}${digest}'


def verify_password(password, stored):
    try:
        alg, iters, salt, digest = stored.split('$', 3)
        calc = hashlib.pbkdf2_hmac('sha256', password.encode(), salt.encode(), int(iters)).hex()
        return hmac.compare_digest(calc, digest)
    except Exception:
        return False


def public_user(row):
    return {
        'id': row['id'], 'name': row['name'], 'contact': row['contact'],
        'birthday': row['birthday'] or '', 'channel': clean_channel(row['channel']),
        'notes': row['notes'] or '', 'reminders': bool(row['reminders'])
    }


def booking_public(row):
    return {k: row[k] for k in row.keys()}


def clean_event_name(value):
    value = (value or '').strip().lower()
    return value if re.match(r'^[a-z0-9_:-]{2,64}$', value) else ''


def event_summary(conn, since_ts):
    totals = {row['event_name']: row['count'] for row in conn.execute(
        'SELECT event_name, COUNT(*) AS count FROM events WHERE created_at>=? GROUP BY event_name',
        (since_ts,)
    )}
    services = [dict(row) for row in conn.execute("""
        SELECT COALESCE(s.name, e.meta_json) AS service, COUNT(*) AS count
        FROM events e LEFT JOIN services s ON s.id=e.service_id
        WHERE e.created_at>=? AND e.event_name IN ('service_selected','booking_submitted')
        GROUP BY service ORDER BY count DESC LIMIT 10
    """, (since_ts,))]
    return {'events': totals, 'top_services': services}


def remote_ip(handler):
    xfwd = handler.headers.get('X-Forwarded-For', '')
    return (xfwd.split(',')[0].strip() or handler.client_address[0] or 'unknown')


def login_key(handler, contact):
    return f'{remote_ip(handler)}:{contact}'


def login_blocked(handler, contact):
    key = login_key(handler, contact)
    cutoff = time.time() - LOGIN_WINDOW
    attempts = [t for t in _login_failures.get(key, []) if t > cutoff]
    _login_failures[key] = attempts
    return len(attempts) >= LOGIN_LIMIT


def record_login_failure(handler, contact):
    key = login_key(handler, contact)
    _login_failures.setdefault(key, []).append(time.time())


def clear_login_failures(handler, contact):
    _login_failures.pop(login_key(handler, contact), None)


def session_cookie(token):
    secure = '; Secure' if COOKIE_SECURE else ''
    return f'hh_session={token}; Path=/; Max-Age={SESSION_TTL}; HttpOnly; SameSite=Lax{secure}'


def clear_cookie():
    secure = '; Secure' if COOKIE_SECURE else ''
    return f'hh_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax{secure}'


def notify_n8n(event, payload):
    if not N8N_WEBHOOK:
        return
    try:
        body = json.dumps({'event': event, 'data': payload, 'ts': now_ts()}, ensure_ascii=False).encode()
        req = Request(N8N_WEBHOOK, data=body, headers={'Content-Type': 'application/json'}, method='POST')
        with urlopen(req, timeout=10) as resp:
            resp.read()
    except URLError as e:
        print(f'n8n notify error: {e}', flush=True)
    except Exception as e:
        print(f'n8n notify error: {e}', flush=True)


def require_admin(handler):
    auth = handler.headers.get('Authorization', '')
    token = auth.split(' ')[-1] if auth.startswith('Bearer ') else ''
    if not ADMIN_TOKEN or not secrets.compare_digest(token, ADMIN_TOKEN):
        handler.send_json(403, {'ok': False, 'error': 'admin_required'})
        return False
    return True


class Handler(BaseHTTPRequestHandler):
    server_version = 'HealthHandPortal/1.2'

    def log_message(self, fmt, *args):
        return

    def send_json(self, code, data, cookie=None, extra_headers=None):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Access-Control-Allow-Origin', self.headers.get('Origin') or '*')
        self.send_header('Access-Control-Allow-Credentials', 'true')
        if cookie:
            self.send_header('Set-Cookie', cookie)
        if extra_headers:
            for k, v in extra_headers.items():
                self.send_header(k, v)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_cors(self):
        origin = self.headers.get('Origin') or '*'
        self.send_response(204)
        self.send_header('Access-Control-Allow-Origin', origin)
        self.send_header('Access-Control-Allow-Credentials', 'true')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.send_header('Access-Control-Max-Age', '86400')
        self.end_headers()

    def read_json(self):
        n = int(self.headers.get('Content-Length') or 0)
        if n > 100_000:
            raise ValueError('payload_too_large')
        return json.loads(self.rfile.read(n).decode() or '{}')

    def session_user(self):
        cookie = SimpleCookie(self.headers.get('Cookie') or '')
        token = cookie.get('hh_session')
        if not token:
            return None
        with db() as c:
            row = c.execute('''SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id
                               WHERE s.token=? AND s.expires_at>?''', (token.value, now_ts())).fetchone()
        return row

    def require_user(self):
        user = self.session_user()
        if not user:
            self.send_json(401, {'ok': False, 'error': 'auth_required'})
            return None
        return user

    def do_OPTIONS(self):
        self.send_cors()

    def do_GET(self):
        path = urlparse(self.path).path
        if path == '/api/portal/health':
            return self.send_json(200, {'ok': True})
        if path == '/api/portal/me':
            user = self.session_user()
            if not user:
                return self.send_json(200, {'ok': True, 'user': None, 'bookings': []})
            with db() as c:
                bookings = [dict(x) for x in c.execute('SELECT * FROM bookings WHERE user_id=? ORDER BY id DESC LIMIT 50', (user['id'],))]
            return self.send_json(200, {'ok': True, 'user': public_user(user), 'bookings': bookings})
        if path == '/api/portal/care-plan':
            user = self.require_user()
            if not user:
                return
            with db() as c:
                plan = c.execute('SELECT * FROM care_plans WHERE user_id=? ORDER BY id DESC LIMIT 1', (user['id'],)).fetchone()
            return self.send_json(200, {'ok': True, 'care_plan': dict(plan) if plan else None})
        if path == '/api/portal/packages':
            user = self.require_user()
            if not user:
                return
            with db() as c:
                packages = [dict(x) for x in c.execute('SELECT * FROM packages WHERE user_id=? ORDER BY id DESC', (user['id'],))]
            return self.send_json(200, {'ok': True, 'packages': packages})
        if path == '/api/portal/intake':
            user = self.require_user()
            if not user:
                return
            with db() as c:
                intake = c.execute('SELECT * FROM intake_forms WHERE user_id=? ORDER BY id DESC LIMIT 1', (user['id'],)).fetchone()
            return self.send_json(200, {'ok': True, 'intake': dict(intake) if intake else None})
        if path == '/api/admin/bookings':
            if not require_admin(self):
                return
            status = urlparse(self.path).query.replace('status=', '') if 'status=' in urlparse(self.path).query else ''
            with db() as c:
                if status:
                    rows = [dict(x) for x in c.execute('SELECT * FROM bookings WHERE status=? ORDER BY id DESC LIMIT 200', (status,))]
                else:
                    rows = [dict(x) for x in c.execute('SELECT * FROM bookings ORDER BY id DESC LIMIT 200')]
            return self.send_json(200, {'ok': True, 'bookings': rows})
        if path == '/api/v2/services':
            with db() as c:
                rows = [dict(x) for x in c.execute("""SELECT s.*, c.name AS category_name
                                                        FROM services s LEFT JOIN service_categories c ON c.id=s.category_id
                                                        WHERE s.is_active=1 ORDER BY c.sort_order,s.sort_order,s.id""")]
            return self.send_json(200, {'ok': True, 'services': rows})
        if path == '/api/v2/employees':
            with db() as c:
                rows = [dict(x) for x in c.execute('SELECT * FROM employees WHERE is_active=1 AND show_on_site=1 ORDER BY sort_order,id')]
            return self.send_json(200, {'ok': True, 'employees': rows})
        if path == '/api/v2/availability':
            q = parse_qs(urlparse(self.path).query)
            try:
                service_id = int((q.get('service_id') or ['0'])[0])
            except Exception:
                service_id = 0
            date_value = (q.get('date') or [''])[0]
            employee_raw = (q.get('employee_id') or [''])[0]
            employee_id = int(employee_raw) if employee_raw.isdigit() else None
            if not service_id or not date_value:
                return self.send_json(400, {'ok': False, 'error': 'service_id and date required'})
            with db() as c:
                slots = get_available_slots(c, service_id, date_value, employee_id)
            return self.send_json(200, {'ok': True, 'slots': slots})
        if path == '/api/admin/v2/catalog':
            if not require_admin(self):
                return
            with db() as c:
                categories = [dict(x) for x in c.execute('SELECT * FROM service_categories ORDER BY sort_order,id')]
                services = [dict(x) for x in c.execute('SELECT * FROM services ORDER BY sort_order,id')]
                employees = [dict(x) for x in c.execute('SELECT * FROM employees ORDER BY sort_order,id')]
                shifts = [dict(x) for x in c.execute('SELECT * FROM employee_shifts ORDER BY employee_id,weekday,start_time')]
                employee_services = [dict(x) for x in c.execute('SELECT employee_id,service_id FROM employee_services')]
            return self.send_json(200, {'ok': True, 'categories': categories, 'services': services, 'employees': employees, 'shifts': shifts, 'employee_services': employee_services})
        if path == '/api/admin/v2/events/summary':
            if not require_admin(self):
                return
            q = parse_qs(urlparse(self.path).query)
            days_raw = (q.get('days') or ['7'])[0]
            days = int(days_raw) if days_raw.isdigit() else 7
            days = max(1, min(days, 90))
            since = now_ts() - days * 86400
            with db() as c:
                summary = event_summary(c, since)
            return self.send_json(200, {'ok': True, 'days': days, **summary})
        return self.send_json(404, {'ok': False, 'error': 'not_found'})

    def do_PUT(self):
        path = urlparse(self.path).path
        try:
            data = self.read_json()
        except Exception:
            return self.send_json(400, {'ok': False, 'error': 'bad_json'})

        if path == '/api/admin/v2/services':
            if not require_admin(self):
                return
            sid = int(data.get('id') or 0)
            if not sid:
                return self.send_json(400, {'ok': False, 'error': 'id required'})
            with db() as c:
                row = c.execute('SELECT * FROM services WHERE id=?', (sid,)).fetchone()
                if not row:
                    return self.send_json(404, {'ok': False, 'error': 'not found'})
                c.execute('''UPDATE services SET name=?,description=?,duration_minutes=?,price=?,category_id=?,sort_order=?,is_active=? WHERE id=?''',
                    (data.get('name', row['name']).strip() if data.get('name') else row['name'],
                     data.get('description', row['description']),
                     int(data['duration_minutes']) if 'duration_minutes' in data else row['duration_minutes'],
                     int(data['price']) if 'price' in data else row['price'],
                     data.get('category_id', row['category_id']),
                     int(data['sort_order']) if 'sort_order' in data else row['sort_order'],
                     1 if data.get('is_active', bool(row['is_active'])) else 0,
                     sid))
                row = c.execute('SELECT * FROM services WHERE id=?', (sid,)).fetchone()
            return self.send_json(200, {'ok': True, 'service': dict(row)})

        if path == '/api/admin/v2/employees':
            if not require_admin(self):
                return
            emp_id = int(data.get('id') or 0)
            if not emp_id:
                return self.send_json(400, {'ok': False, 'error': 'id required'})
            with db() as c:
                row = c.execute('SELECT * FROM employees WHERE id=?', (emp_id,)).fetchone()
                if not row:
                    return self.send_json(404, {'ok': False, 'error': 'not found'})
                c.execute('''UPDATE employees SET name=?,role=?,bio=?,phone=?,is_active=?,sort_order=?,show_on_site=? WHERE id=?''',
                    (data.get('name', row['name']).strip() if data.get('name') else row['name'],
                     data.get('role', row['role']),
                     data.get('bio', row['bio']),
                     data.get('phone', row['phone']),
                     1 if data.get('is_active', bool(row['is_active'])) else 0,
                     int(data['sort_order']) if 'sort_order' in data else row['sort_order'],
                     1 if data.get('show_on_site', bool(row['show_on_site']) if 'show_on_site' in dict(row) else True) else 0,
                     emp_id))
                if data.get('service_ids') is not None:
                    c.execute('DELETE FROM employee_services WHERE employee_id=?', (emp_id,))
                    for sid in data.get('service_ids') or []:
                        c.execute('INSERT OR IGNORE INTO employee_services(employee_id,service_id,created_at) VALUES(?,?,?)', (emp_id, int(sid), now_ts()))
                row = c.execute('SELECT * FROM employees WHERE id=?', (emp_id,)).fetchone()
            return self.send_json(200, {'ok': True, 'employee': dict(row)})

        if path == '/api/admin/v2/shifts':
            if not require_admin(self):
                return
            shift_id = int(data.get('id') or 0)
            if not shift_id:
                return self.send_json(400, {'ok': False, 'error': 'id required'})
            with db() as c:
                row = c.execute('SELECT * FROM employee_shifts WHERE id=?', (shift_id,)).fetchone()
                if not row:
                    return self.send_json(404, {'ok': False, 'error': 'not found'})
                weekday = int(data.get('weekday', row['weekday']))
                start = data.get('start_time', row['start_time'])
                end = data.get('end_time', row['end_time'])
                if weekday < 0 or weekday > 6 or not re.match(r'^\d{2}:\d{2}$', start) or not re.match(r'^\d{2}:\d{2}$', end):
                    return self.send_json(400, {'ok': False, 'error': 'invalid weekday or time'})
                c.execute('''UPDATE employee_shifts SET employee_id=?,weekday=?,start_time=?,end_time=?,is_active=? WHERE id=?''',
                    (int(data.get('employee_id') or row['employee_id']), weekday, start, end,
                     1 if data.get('is_active', bool(row['is_active'])) else 0, shift_id))
                row = c.execute('SELECT * FROM employee_shifts WHERE id=?', (shift_id,)).fetchone()
            return self.send_json(200, {'ok': True, 'shift': dict(row)})

        return self.send_json(404, {'ok': False, 'error': 'not_found'})

    def do_DELETE(self):
        path = urlparse(self.path).path
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)

        # DELETE /api/admin/bookings?id=X  or  body {id}
        if path == '/api/admin/bookings':
            if not require_admin(self):
                return
            try:
                data = self.read_json() if self.headers.get('Content-Length') else {}
            except Exception:
                data = {}
            booking_id = int(data.get('id') or query.get('id', [0])[0])
            if not booking_id:
                return self.send_json(400, {'ok': False, 'error': 'id required'})
            with db() as c:
                row = c.execute('SELECT * FROM bookings WHERE id=?', (booking_id,)).fetchone()
                if not row:
                    return self.send_json(404, {'ok': False, 'error': 'not found'})
                c.execute('DELETE FROM appointments WHERE booking_id=?', (booking_id,))
                c.execute('DELETE FROM bookings WHERE id=?', (booking_id,))
            return self.send_json(200, {'ok': True, 'deleted': booking_id})

        if path == '/api/admin/v2/services':
            if not require_admin(self):
                return
            try:
                data = self.read_json() if self.headers.get('Content-Length') else {}
            except Exception:
                data = {}
            sid = int(data.get('id') or query.get('id', [0])[0])
            hard = data.get('hard', False)
            if not sid:
                return self.send_json(400, {'ok': False, 'error': 'id required'})
            with db() as c:
                row = c.execute('SELECT * FROM services WHERE id=?', (sid,)).fetchone()
                if not row:
                    return self.send_json(404, {'ok': False, 'error': 'not found'})
                if hard:
                    c.execute('DELETE FROM employee_services WHERE service_id=?', (sid,))
                    c.execute('DELETE FROM services WHERE id=?', (sid,))
                else:
                    c.execute('UPDATE services SET is_active=0 WHERE id=?', (sid,))
            return self.send_json(200, {'ok': True, 'deleted': sid, 'hard': bool(hard)})

        if path == '/api/admin/v2/employees':
            if not require_admin(self):
                return
            try:
                data = self.read_json() if self.headers.get('Content-Length') else {}
            except Exception:
                data = {}
            emp_id = int(data.get('id') or query.get('id', [0])[0])
            hard = data.get('hard', False)
            if not emp_id:
                return self.send_json(400, {'ok': False, 'error': 'id required'})
            with db() as c:
                row = c.execute('SELECT * FROM employees WHERE id=?', (emp_id,)).fetchone()
                if not row:
                    return self.send_json(404, {'ok': False, 'error': 'not found'})
                if hard:
                    c.execute('DELETE FROM employee_services WHERE employee_id=?', (emp_id,))
                    c.execute('DELETE FROM employee_shifts WHERE employee_id=?', (emp_id,))
                    c.execute('DELETE FROM employees WHERE id=?', (emp_id,))
                else:
                    c.execute('UPDATE employees SET is_active=0 WHERE id=?', (emp_id,))
            return self.send_json(200, {'ok': True, 'deleted': emp_id, 'hard': bool(hard)})

        if path == '/api/admin/v2/shifts':
            if not require_admin(self):
                return
            try:
                data = self.read_json() if self.headers.get('Content-Length') else {}
            except Exception:
                data = {}
            shift_id = int(data.get('id') or query.get('id', [0])[0])
            if not shift_id:
                return self.send_json(400, {'ok': False, 'error': 'id required'})
            with db() as c:
                row = c.execute('SELECT * FROM employee_shifts WHERE id=?', (shift_id,)).fetchone()
                if not row:
                    return self.send_json(404, {'ok': False, 'error': 'not found'})
                c.execute('DELETE FROM employee_shifts WHERE id=?', (shift_id,))
            return self.send_json(200, {'ok': True, 'deleted': shift_id})

        return self.send_json(404, {'ok': False, 'error': 'not_found'})

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            data = self.read_json()
        except Exception:
            return self.send_json(400, {'ok': False, 'error': 'bad_json'})

        if path == '/api/events':
            event_name = clean_event_name(data.get('event_name') or data.get('event'))
            if not event_name:
                return self.send_json(400, {'ok': False, 'error': 'bad_event_name'})
            user = self.session_user()
            def as_int_or_none(value):
                try:
                    return int(value) if value not in (None, '') else None
                except Exception:
                    return None
            meta = data.get('meta') if isinstance(data.get('meta'), dict) else {}
            meta_json = json.dumps(meta, ensure_ascii=False)[:4000]
            with db() as c:
                cur = c.execute('''INSERT INTO events(event_name,session_id,user_id,booking_id,service_id,employee_id,page,source,meta_json,created_at)
                                   VALUES(?,?,?,?,?,?,?,?,?,?)''',
                    (event_name, (data.get('session_id') or '')[:128], user['id'] if user else None,
                     as_int_or_none(data.get('booking_id')), as_int_or_none(data.get('service_id')),
                     as_int_or_none(data.get('employee_id')), (data.get('page') or '')[:500],
                     (data.get('source') or 'site')[:80], meta_json, now_ts()))
                event_id = cur.lastrowid
            return self.send_json(201, {'ok': True, 'event_id': event_id})

        if path == '/api/portal/register':
            name = (data.get('name') or '').strip()
            contact = norm_contact(data.get('contact'))
            password = data.get('password') or ''
            channel = clean_channel(data.get('channel'))
            if len(name) < 2 or len(contact) < 5 or len(password) < 8:
                return self.send_json(400, {'ok': False, 'error': 'Заповніть ім’я, контакт і пароль мінімум 8 символів.'})
            try:
                with db() as c:
                    cur = c.execute('INSERT INTO users(name,contact,password_hash,birthday,channel,notes,reminders,created_at) VALUES(?,?,?,?,?,?,?,?)',
                        (name, contact, hash_password(password), data.get('birthday',''), channel, data.get('notes',''), 1 if data.get('reminders', True) else 0, now_ts()))
                    uid = cur.lastrowid
                    token = secrets.token_urlsafe(32)
                    c.execute('INSERT INTO sessions(token,user_id,expires_at) VALUES(?,?,?)', (token, uid, now_ts() + SESSION_TTL))
                    c.execute('UPDATE bookings SET user_id=? WHERE user_id IS NULL AND lead_contact=?', (uid, contact))
                    user = c.execute('SELECT * FROM users WHERE id=?', (uid,)).fetchone()
                    bookings = [dict(x) for x in c.execute('SELECT * FROM bookings WHERE user_id=? ORDER BY id DESC LIMIT 50', (uid,))]
                notify_n8n('user_registered', {'user_id': uid, 'contact': contact, 'name': name})
                return self.send_json(201, {'ok': True, 'user': public_user(user), 'bookings': bookings}, session_cookie(token))
            except sqlite3.IntegrityError:
                return self.send_json(409, {'ok': False, 'error': 'Такий контакт уже зареєстрований. Увійдіть у кабінет.'})

        if path == '/api/portal/login':
            contact = norm_contact(data.get('contact'))
            password = data.get('password') or ''
            if login_blocked(self, contact):
                return self.send_json(429, {'ok': False, 'error': 'Забагато невдалих спроб. Спробуйте пізніше.'})
            with db() as c:
                user = c.execute('SELECT * FROM users WHERE contact=?', (contact,)).fetchone()
                if not user or not verify_password(password, user['password_hash']):
                    record_login_failure(self, contact)
                    return self.send_json(401, {'ok': False, 'error': 'Невірний контакт або пароль.'})
                clear_login_failures(self, contact)
                token = secrets.token_urlsafe(32)
                c.execute('INSERT INTO sessions(token,user_id,expires_at) VALUES(?,?,?)', (token, user['id'], now_ts() + SESSION_TTL))
            return self.send_json(200, {'ok': True, 'user': public_user(user)}, session_cookie(token))

        if path == '/api/portal/logout':
            cookie = SimpleCookie(self.headers.get('Cookie') or '')
            token = cookie.get('hh_session')
            if token:
                with db() as c:
                    c.execute('DELETE FROM sessions WHERE token=?', (token.value,))
            return self.send_json(200, {'ok': True}, clear_cookie())

        if path == '/api/portal/profile':
            user = self.require_user()
            if not user:
                return
            channel = clean_channel(data.get('channel'))
            with db() as c:
                c.execute('UPDATE users SET name=?, birthday=?, channel=?, notes=?, reminders=? WHERE id=?',
                    ((data.get('name') or user['name']).strip(), data.get('birthday',''), channel, data.get('notes',''), 1 if data.get('reminders', True) else 0, user['id']))
                row = c.execute('SELECT * FROM users WHERE id=?', (user['id'],)).fetchone()
            return self.send_json(200, {'ok': True, 'user': public_user(row)})

        if path == '/api/portal/bookings':
            user = self.require_user()
            if not user:
                return
            channel = clean_channel(data.get('channel') or user['channel'])
            with db() as c:
                c.execute('''INSERT INTO bookings(user_id,external_id,lead_name,lead_contact,service,service_id,employee_id,start_at,end_at,date,time,note,channel,status,webhook_ok,created_at)
                             VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)''',
                    (user['id'], data.get('id',''), user['name'], user['contact'], data.get('service','Масаж'),
                     int(data.get('service_id')) if str(data.get('service_id','')).isdigit() else None,
                     int(data.get('employee_id')) if str(data.get('employee_id','')).isdigit() else None,
                     data.get('start_at',''), data.get('end_at',''), data.get('date',''), data.get('time',''), data.get('note',''), channel, clean_status(data.get('status')), 1 if data.get('webhookOk') else 0, now_ts()))
                booking_id = c.lastrowid
                bookings = [dict(x) for x in c.execute('SELECT * FROM bookings WHERE user_id=? ORDER BY id DESC LIMIT 50', (user['id'],))]
            notify_n8n('booking_created', {'booking_id': booking_id, 'user_id': user['id'], 'contact': user['contact']})
            return self.send_json(201, {'ok': True, 'booking_id': booking_id, 'bookings': bookings})

        if path == '/api/portal/intake':
            user = self.require_user()
            if not user:
                return
            booking_id = data.get('booking_id')
            with db() as c:
                c.execute('''INSERT INTO intake_forms(user_id,booking_id,contraindications,medications,pain_scale,previous_massage,goals,risk_flags,created_at)
                             VALUES(?,?,?,?,?,?,?,?,?)''',
                    (user['id'], booking_id, data.get('contraindications',''), data.get('medications',''), data.get('pain_scale'), data.get('previous_massage',''), data.get('goals',''), data.get('risk_flags',''), now_ts()))
                intake = c.execute('SELECT * FROM intake_forms WHERE user_id=? ORDER BY id DESC LIMIT 1', (user['id'],)).fetchone()
            return self.send_json(201, {'ok': True, 'intake': dict(intake)})

        if path == '/api/portal/care-plan':
            user = self.require_user()
            if not user:
                return
            with db() as c:
                c.execute('''INSERT INTO care_plans(user_id,booking_id,recommendation,next_service,next_date,exercises,notes,created_at)
                             VALUES(?,?,?,?,?,?,?,?)''',
                    (user['id'], data.get('booking_id'), data.get('recommendation',''), data.get('next_service',''), data.get('next_date',''), data.get('exercises',''), data.get('notes',''), now_ts()))
                plan = c.execute('SELECT * FROM care_plans WHERE user_id=? ORDER BY id DESC LIMIT 1', (user['id'],)).fetchone()
            return self.send_json(201, {'ok': True, 'care_plan': dict(plan)})

        if path == '/api/portal/packages':
            user = self.require_user()
            if not user:
                return
            name = (data.get('name') or '').strip()
            total = int(data.get('total_sessions') or 0)
            remaining = int(data.get('remaining_sessions') or total)
            if not name or total <= 0:
                return self.send_json(400, {'ok': False, 'error': 'Вкажіть назву та кількість сеансів.'})
            with db() as c:
                c.execute('''INSERT INTO packages(user_id,name,total_sessions,remaining_sessions,expires_at,created_at)
                             VALUES(?,?,?,?,?,?)''',
                    (user['id'], name, total, remaining, data.get('expires_at'), now_ts()))
                packages = [dict(x) for x in c.execute('SELECT * FROM packages WHERE user_id=? ORDER BY id DESC', (user['id'],))]
            return self.send_json(201, {'ok': True, 'packages': packages})

        if path == '/api/admin/v2/services':
            if not require_admin(self):
                return
            name = (data.get('name') or '').strip()
            if len(name) < 2:
                return self.send_json(400, {'ok': False, 'error': 'name required'})
            with db() as c:
                cur = c.execute("""INSERT INTO services(name,description,duration_minutes,price,category_id,sort_order,is_active,created_at)
                                   VALUES(?,?,?,?,?,?,?,?)""",
                    (name, data.get('description',''), int(data.get('duration_minutes') or 60), int(data.get('price') or 0), data.get('category_id'), int(data.get('sort_order') or 0), 1 if data.get('is_active', True) else 0, now_ts()))
                row = c.execute('SELECT * FROM services WHERE id=?', (cur.lastrowid,)).fetchone()
            return self.send_json(201, {'ok': True, 'service': dict(row)})

        if path == '/api/admin/v2/employees':
            if not require_admin(self):
                return
            name = (data.get('name') or '').strip()
            if len(name) < 2:
                return self.send_json(400, {'ok': False, 'error': 'name required'})
            with db() as c:
                cur = c.execute("""INSERT INTO employees(name,role,bio,phone,is_active,sort_order,show_on_site,created_at)
                                   VALUES(?,?,?,?,?,?,?,?)""",
                    (name, data.get('role','massage_therapist'), data.get('bio',''), data.get('phone',''), 1 if data.get('is_active', True) else 0, int(data.get('sort_order') or 0), 1 if data.get('show_on_site', True) else 0, now_ts()))
                employee_id = cur.lastrowid
                for sid in data.get('service_ids') or []:
                    c.execute('INSERT OR IGNORE INTO employee_services(employee_id,service_id,created_at) VALUES(?,?,?)', (employee_id, int(sid), now_ts()))
                row = c.execute('SELECT * FROM employees WHERE id=?', (employee_id,)).fetchone()
            return self.send_json(201, {'ok': True, 'employee': dict(row)})

        if path == '/api/admin/v2/shifts':
            if not require_admin(self):
                return
            employee_id = int(data.get('employee_id') or 0)
            weekday = int(data.get('weekday') or -1)
            start = (data.get('start_time') or '').strip()
            end = (data.get('end_time') or '').strip()
            if not employee_id or weekday < 0 or weekday > 6 or not re.match(r'^\d{2}:\d{2}$', start) or not re.match(r'^\d{2}:\d{2}$', end):
                return self.send_json(400, {'ok': False, 'error': 'employee_id, weekday, start_time, end_time required'})
            with db() as c:
                cur = c.execute("""INSERT INTO employee_shifts(employee_id,weekday,start_time,end_time,is_active,created_at)
                                   VALUES(?,?,?,?,?,?)""",
                    (employee_id, weekday, start, end, 1 if data.get('is_active', True) else 0, now_ts()))
                row = c.execute('SELECT * FROM employee_shifts WHERE id=?', (cur.lastrowid,)).fetchone()
            return self.send_json(201, {'ok': True, 'shift': dict(row)})

        if path == '/api/bookings':
            name = (data.get('name') or '').strip()
            contact = norm_contact(data.get('contact'))
            service = (data.get('service') or 'Масаж').strip()
            channel = clean_channel(data.get('channel'))
            if len(name) < 2 or len(contact) < 5 or not service:
                return self.send_json(400, {'ok': False, 'error': 'Заповніть ім’я, контакт і послугу.'})
            user_id = None
            user = self.session_user()
            if user:
                user_id = user['id']
            else:
                with db() as c:
                    u = c.execute('SELECT id FROM users WHERE contact=?', (contact,)).fetchone()
                    if u:
                        user_id = u['id']
            with db() as c:
                service_id = int(data.get('service_id')) if str(data.get('service_id','')).isdigit() else None
                employee_id = int(data.get('employee_id')) if str(data.get('employee_id','')).isdigit() else None
                start_at = data.get('start_at','')
                end_at = data.get('end_at','')
                cur = c.execute('''INSERT INTO bookings(user_id,external_id,lead_name,lead_contact,service,service_id,employee_id,start_at,end_at,date,time,note,channel,status,webhook_ok,created_at)
                                   VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)''',
                    (user_id, data.get('id',''), name, contact, service, service_id, employee_id, start_at, end_at, data.get('date',''), data.get('time',''), data.get('note',''), channel, 'new', 1 if data.get('webhookOk') else 0, now_ts()))
                booking_id = cur.lastrowid
                if start_at:
                    c.execute('''INSERT INTO appointments(booking_id,user_id,employee_id,start_at,end_at,calendar_event_id,status,created_at)
                                 VALUES(?,?,?,?,?,?,?,?)
                                 ON CONFLICT(booking_id) DO UPDATE SET employee_id=excluded.employee_id, start_at=excluded.start_at, end_at=excluded.end_at, status=excluded.status''',
                        (booking_id, user_id, employee_id, start_at, end_at, '', 'requested', now_ts()))
                booking_row = c.execute('SELECT * FROM bookings WHERE id=?', (booking_id,)).fetchone()
                c.execute('''INSERT INTO events(event_name,session_id,user_id,booking_id,service_id,employee_id,page,source,meta_json,created_at)
                             VALUES(?,?,?,?,?,?,?,?,?,?)''',
                    ('booking_submitted', data.get('session_id',''), user_id, booking_id, service_id, employee_id,
                     data.get('page',''), data.get('source','site'), json.dumps({'channel': channel, 'service': service}, ensure_ascii=False), now_ts()))
            notify_n8n('booking_created', dict(booking_row))
            return self.send_json(201, {'ok': True, 'booking_id': booking_id, 'linked': bool(user_id)})

        if path == '/api/admin/bookings/status':
            if not require_admin(self):
                return
            booking_id = data.get('booking_id')
            status = clean_status(data.get('status'))
            if not booking_id:
                return self.send_json(400, {'ok': False, 'error': 'booking_id required'})
            feedback_note = (data.get('feedback_note') or data.get('note') or '').strip()
            with db() as c:
                if feedback_note:
                    c.execute('UPDATE bookings SET status=?, feedback_note=? WHERE id=?', (status, feedback_note, booking_id))
                else:
                    c.execute('UPDATE bookings SET status=? WHERE id=?', (status, booking_id))
                if status in ('cancelled', 'no_show'):
                    c.execute('UPDATE appointments SET status=? WHERE booking_id=?', (status, booking_id))
                row = c.execute('SELECT * FROM bookings WHERE id=?', (booking_id,)).fetchone()
            notify_n8n('booking_status_changed', dict(row))
            return self.send_json(200, {'ok': True, 'booking': dict(row)})

        if path == '/api/admin/appointments':
            if not require_admin(self):
                return
            booking_id = data.get('booking_id')
            start = data.get('start_at', '')
            end = data.get('end_at', '')
            if not booking_id or not start:
                return self.send_json(400, {'ok': False, 'error': 'booking_id and start_at required'})
            try:
                start_dt = datetime.fromisoformat(start.replace('Z', '+00:00'))
                end_dt = datetime.fromisoformat(end.replace('Z', '+00:00')) if end else None
                if end_dt is not None and end_dt <= start_dt:
                    raise ValueError('end must be after start')
            except (TypeError, ValueError):
                return self.send_json(400, {'ok': False, 'error': 'invalid appointment date range'})
            with db() as c:
                row = c.execute('SELECT user_id FROM bookings WHERE id=?', (booking_id,)).fetchone()
                if not row:
                    return self.send_json(404, {'ok': False, 'error': 'booking not found'})
                employee_id = int(data.get('employee_id')) if str(data.get('employee_id','')).isdigit() else None
                c.execute('''INSERT INTO appointments(booking_id,user_id,employee_id,start_at,end_at,calendar_event_id,status,created_at)
                             VALUES(?,?,?,?,?,?,?,?)
                             ON CONFLICT(booking_id) DO UPDATE SET employee_id=excluded.employee_id, start_at=excluded.start_at, end_at=excluded.end_at, status=excluded.status''',
                    (booking_id, row['user_id'], employee_id, start, end, data.get('calendar_event_id',''), data.get('status','scheduled'), now_ts()))
                c.execute('UPDATE bookings SET employee_id=COALESCE(?, employee_id), start_at=?, end_at=? WHERE id=?', (employee_id, start, end, booking_id))
                appt = c.execute('SELECT * FROM appointments WHERE booking_id=?', (booking_id,)).fetchone()
            notify_n8n('appointment_set', dict(appt))
            return self.send_json(201, {'ok': True, 'appointment': dict(appt)})

        if path == '/api/admin/care-plan':
            if not require_admin(self):
                return
            user_id = data.get('user_id')
            if not user_id:
                return self.send_json(400, {'ok': False, 'error': 'user_id required'})
            with db() as c:
                c.execute('''INSERT INTO care_plans(user_id,booking_id,recommendation,next_service,next_date,exercises,notes,created_at)
                             VALUES(?,?,?,?,?,?,?,?)''',
                    (user_id, data.get('booking_id'), data.get('recommendation',''), data.get('next_service',''), data.get('next_date',''), data.get('exercises',''), data.get('notes',''), now_ts()))
                plan = c.execute('SELECT * FROM care_plans WHERE id=?', (c.lastrowid,)).fetchone()
            notify_n8n('care_plan_created', dict(plan))
            return self.send_json(201, {'ok': True, 'care_plan': dict(plan)})

        return self.send_json(404, {'ok': False, 'error': 'not_found'})


if __name__ == '__main__':
    init_db()
    host = os.environ.get('HH_HOST', '127.0.0.1')
    port = int(os.environ.get('HH_PORT', '8787'))
    print(f'Health Hand API listening on http://{host}:{port}', flush=True)
    ThreadingHTTPServer((host, port), Handler).serve_forever()
