# Health Hand backend v2 notes

Це перший безпечний крок переносу DIANA → Health Hand без заміни існуючого сайту/порталу.

## Що додано в `portal_api.py`

Нові таблиці створюються idempotent через `init_db()`:

- `service_categories`
- `services`
- `employees`
- `employee_services`
- `employee_shifts`
- `employee_breaks`

Поточні таблиці (`users`, `bookings`, `appointments`, `care_plans`, `packages`, `intake_forms`) не ламаються.

## Seed за замовчуванням

Якщо каталог порожній, створюються:

- категорія `Масаж`
- 5 базових послуг:
  - Класичний масаж
  - Релакс масаж
  - Спортивний масаж
  - Масаж спини
  - Антицелюлітний масаж
- один активний спеціаліст `Health Hand спеціаліст`
- графік Пн–Пт 09:00–18:00

## Нові public endpoints

### `GET /api/v2/services`

Повертає активний каталог послуг.

### `GET /api/v2/employees`

Повертає активних спеціалістів.

### `GET /api/v2/availability?service_id=1&date=YYYY-MM-DD[&employee_id=1]`

Повертає доступні слоти з урахуванням:

- тривалості послуги
- майстра, який робить послугу
- графіка майстра
- перерв
- існуючих `appointments`
- буфера 15 хвилин

Поточна таблиця `appointments` ще не має `employee_id`, тому на цьому етапі існуючі записи блокують час для всіх майстрів. Це безпечніше, ніж подвійний запис.

## Нові admin endpoints

Потребують HTTP header з адмін-токеном у форматі Bearer.

### `GET /api/admin/v2/catalog`

Повертає categories/services/employees/shifts.

### `POST /api/admin/v2/services`

Створює послугу.

Payload:

```json
{
  "name": "Масаж шиї",
  "description": "...",
  "duration_minutes": 30,
  "price": 700,
  "category_id": 1,
  "sort_order": 60,
  "is_active": true
}
```

### `POST /api/admin/v2/employees`

Створює спеціаліста і може привʼязати послуги.

Payload:

```json
{
  "name": "Імʼя спеціаліста",
  "role": "massage_therapist",
  "bio": "...",
  "phone": "",
  "service_ids": [1, 2, 3]
}
```

### `POST /api/admin/v2/shifts`

Додає зміну спеціаліста.

Payload:

```json
{
  "employee_id": 1,
  "weekday": 0,
  "start_time": "09:00",
  "end_time": "18:00",
  "is_active": true
}
```

`weekday`: Monday=0 ... Sunday=6.

## Реально виконані перевірки

- `python3 -m py_compile portal_api.py` → OK
- `init_db()` на тимчасовій SQLite DB → OK
- Public API на тимчасовому сервері:
  - `/api/portal/health` → OK
  - `/api/v2/services` → OK, 5 послуг
  - `/api/v2/employees` → OK, 1 спеціаліст
  - `/api/v2/availability?service_id=1&date=2026-06-30` → OK, 17 слотів
- Admin API на тимчасовому сервері:
  - `/api/admin/v2/catalog` з тестовим `HH_ADMIN_TOKEN` → OK

## UI інтеграція виконана

Landing booking form тепер:

- завантажує послуги з `GET /api/v2/services`;
- показує послугу з тривалістю і ціною;
- після вибору послуги й дати запитує `GET /api/v2/availability`;
- замість ручного поля часу показує тільки доступні слоти;
- у payload заявки додає `service_id`, `employee_id`, `start_at`, `end_at`, але старий `/api/bookings` залишається сумісним і далі зберігає `service/date/time`.

Змінені файли:

- `index.html`
- `app.js`
- `styles.css`

## Наступний крок

Додати просту admin-сторінку для редагування каталогу послуг, майстрів і графіка, або підключити ці v2 endpoints до існуючого адмінського процесу.
