# Health Hand — салон лікувального масажу

Веб-портал і система управління заявками для салону лікувального масажу **Health Hand** (Умань, Україна).

Проєкт включає: публічний лендінг, клієнтський портал з реєстрацією та історією записів, адмін-панель, бекенд API на Python, та n8n-автоматизацію для AI-сортування заявок, нагадувань та Telegram-сповіщень.

## 🏗 Архітектура

```
┌─────────────────────────────────────────────────┐
│  nginx (443)  ← Let's Encrypt SSL               │
│  /var/www/health-hand/site/  → статика           │
│  /api/*  → portal_api.py (127.0.0.1:8787)       │
│  /webhook/*  → n8n (127.0.0.1:5678)             │
│  /n8n/  → n8n UI                                │
└─────────────────────────────────────────────────┘

┌──────────────────────┐    ┌──────────────────────┐
│  portal_api.py       │    │  n8n workflow        │
│  (systemd service)   │    │  (Docker container)  │
│                      │    │                      │
│  • SQLite (portal.db)│    │  • AI triage         │
│  • User auth         │◄──►│  • Telegram notify   │
│  • Bookings CRUD    │    │  • Reminders (cron)   │
│  • Availability     │    │  • Site chat AI      │
│  • Admin API        │    │  • WhatsApp AI agent │
│  • Events tracking  │    │                      │
└──────────────────────┘    └──────────────────────┘
```

## 📁 Структура

```
health-hand/
├── site/                          # Веб-фронтенд (статичні файли)
│   ├── index.html                 # Лендінг сайлону
│   ├── app.js                     # JS: форми, портал, бронювання
│   ├── styles.css
│   ├── portal/index.html         # Клієнтський портал
│   ├── admin/                    # Адмін-панель
│   │   ├── index.html
│   │   └── admin.js
│   └── assets/                   # Фотографії
├── portal_api.py                  # Python HTTP API (940 рядків)
├── migrations/                    # SQL-міграції SQLite
├── scripts/
│   ├── health-hand-smoke.py       # Smoke-тести
│   └── health-hand-weekly-report.py
├── n8n/
│   └── health-hand-unified-ai-automation.json   # n8n workflow export
├── deploy/
│   ├── nginx-health-hand.conf     # nginx конфіг
│   └── health-hand-portal.service # systemd unit
├── docs/
│   ├── health-hand-v2-backend-notes.md
│   └── health-hand-loop-engineering.md
└── .gitignore
```

## ⚙️ Технології

| Компонент | Стек |
|---|---|
| Фронтенд | HTML, CSS, Vanilla JS (без фреймворків) |
| Бекенд | Python 3 stdlib (`http.server`), SQLite |
| Автоматизація | n8n (Docker), OpenRouter AI, Telegram Bot API |
| Веб-сервер | nginx + Let's Encrypt |
| Процес-менеджер | systemd |

## 🔑 Функціонал

### Клієнтський портал
- Реєстрація та вхід за паролем (PBKDF2-SHA256, 210k iterations)
- Персональний кабінет з історією записів
- Бронювання послуг з вибором майстра та часу
- Вибір каналу зв'язку: Telegram, Дзвінок, Email
- Управління нагадуваннями

### Бекенд API (`portal_api.py`)
- `POST /api/bookings` — створення заявки
- `GET/POST /api/portal/*` — реєстрація, логін, профіль
- `GET /api/v2/services` — каталог послуг
- `GET /api/v2/availability` — доступні слоти
- `GET /api/admin/v2/*` — адмін-API (Bearer token)
- `GET /api/events` — серверний SSE
- `GET /api/reminders/due` — нагадування (за ключем)

### Адмін-панель
- CRUD послуг, категорій, спеціалістів
- Управління графіком майстрів
- Перегляд і зміна статусу заявок
- Статистика та аналітика

### n8n автоматизація
- **AI Triage** — аналіз заявки, оцінка терміновості, червоні прапори (протипоказання)
- **Telegram-сповіщення** — адмін отримує заявки та нагадування в Telegram
- **Follow-up** — повідомлення клієнту після візиту + запит відгуку
- **Щоденні нагадування** — cron 08:00, перевірка завтрашніх візитів
- **Site Chat** — AI-консультант на сайті (тільки масаж/Health Hand)
- **WhatsApp AI** — парсинг повідомлень, створення бронювання

### Безпека
- PBKDF2 хешування паролів
- Session cookies (HttpOnly, SameSite)
- Rate limiting на логін (8 спроб / 15 хв)
- Admin API behind Bearer token
- nginx блокує `.py`, `.db`, `.env`, `__pycache__/`

## 🚀 Розгортання

Детально — у [docs/health-hand-v2-backend-notes.md](docs/health-hand-v2-backend-notes.md) та [deploy/](deploy/).

### Швидкий старт

```bash
# 1. Клонувати
git clone https://github.com/maxartk/health-hand.git
cd health-hand

# 2. Налаштувати env
sudo cp /etc/health-hand-portal.env /etc/health-hand-portal.env
# Заповнити: HH_ADMIN_PASSWORD, HH_REMINDER_KEY, HH_PUBLIC_BASE_URL

# 3. Запустити бекенд
sudo cp deploy/health-hand-portal.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now health-hand-portal

# 4. Nginx
sudo cp deploy/nginx-health-hand.conf /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/health-hand.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 5. n8n (опціонально)
docker run -d --name n8n -p 127.0.0.1:5678:5678 -v n8n_data:/root/.n8n n8nio/n8n
# Імпортувати n8n/health-hand-unified-ai-automation.json через UI
```

### Змінні середовища

| Змінна | Опис |
|---|---|
| `HH_DB` | Шлях до SQLite (за замовч.: `portal.db`) |
| `HH_PORT` | Порт API (за замовч.: `8787`) |
| `HH_ADMIN_PASSWORD` | Пароль/токен адміна |
| `HH_REMINDER_KEY` | Секрет для `/api/reminders/due` |
| `HH_PUBLIC_BASE_URL` | Публічна URL (305 редиректи, CORS) |
| `HH_COOKIE_SECURE` | `1` для HTTPS, `0` для HTTP |
| `HH_N8N_WEBHOOK` | URL n8n webhook (опціонально) |

## 📝 Ліцензія

Private project for Health Hand salon. All rights reserved.