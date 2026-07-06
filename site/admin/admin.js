const $ = (selector, root = document) => root.querySelector(selector);
const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));

const state = {
  token: sessionStorage.getItem('hh_admin_token') || '',
  bookings: [],
  summary: null,
  catalog: null
};

function status(text, type = '') {
  const el = $('#adminStatus');
  if (!el) return;
  el.textContent = text || '';
  el.style.color = type === 'err' ? '#a83232' : type === 'ok' ? '#14784f' : '';
}

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.ok === false) throw new Error(data.error || `HTTP ${response.status}`);
  return data;
}

function metric(label, value, hint = '') {
  return `<article class="metric"><span>${esc(label)}</span><strong>${esc(value)}</strong>${hint ? `<small>${esc(hint)}</small>` : ''}</article>`;
}

function renderSummary() {
  const box = $('#funnelMetrics');
  if (!box) return;
  const events = state.summary?.events || {};
  const formOpened = events.form_opened || 0;
  const submitted = events.booking_submitted || 0;
  const conversion = formOpened ? `${Math.round((submitted / formOpened) * 100)}%` : '—';
  box.innerHTML = [
    metric('Form opened', formOpened),
    metric('Service selected', events.service_selected || 0),
    metric('Slot selected', events.slot_selected || 0),
    metric('Bookings', submitted, `conversion ${conversion}`)
  ].join('');
}

function renderCatalog() {
  const box = $('#catalogMetrics');
  if (!box) return;
  box.innerHTML = [
    metric('Послуги', state.catalog?.services?.length || 0),
    metric('Майстри', state.catalog?.employees?.length || 0),
    metric('Зміни', state.catalog?.shifts?.length || 0),
    metric('Категорії', state.catalog?.categories?.length || 0)
  ].join('');
}

function renderBookings() {
  const box = $('#bookingRows');
  if (!box) return;
  if (!state.bookings.length) {
    box.innerHTML = '<p class="empty-state">Заявок поки немає.</p>';
    return;
  }
  box.innerHTML = state.bookings.map((booking) => `
    <article class="booking-row" data-booking-id="${esc(booking.id)}">
      <div>
        <strong>#${esc(booking.id)} · ${esc(booking.lead_name || 'Клієнт')}</strong>
        <small>${esc(booking.lead_contact || '')}</small>
        <small>${esc(booking.channel || '')}</small>
      </div>
      <div>
        <strong>${esc(booking.service || 'Послуга')}</strong>
        <small>${esc([booking.date, booking.time].filter(Boolean).join(' ') || booking.start_at || 'час не вказано')}</small>
        <small>${esc(booking.note || '')}</small>
      </div>
      <label>Статус
        <select data-status>
          ${['new','contacted','confirmed','completed','cancelled','no_show','followup_sent'].map((item) => `<option value="${item}" ${item === booking.status ? 'selected' : ''}>${item}</option>`).join('')}
        </select>
      </label>
      <label>Feedback note
        <textarea data-feedback placeholder="Чому підтвердився / не підтвердився?">${esc(booking.feedback_note || '')}</textarea>
        <button class="btn btn-soft" type="button" data-save-status>Зберегти</button>
      </label>
    </article>
  `).join('');
}

async function loadAdmin() {
  if (!state.token) {
    status('Введіть admin token.', 'err');
    return;
  }
  status('Завантажую…');
  const [bookings, summary, catalog] = await Promise.all([
    api('/api/admin/bookings'),
    api('/api/admin/v2/events/summary?days=7'),
    api('/api/admin/v2/catalog')
  ]);
  state.bookings = bookings.bookings || [];
  state.summary = summary;
  state.catalog = catalog;
  renderSummary();
  renderCatalog();
  renderBookings();
  status('Оновлено.', 'ok');
}

async function saveBookingStatus(row) {
  const bookingId = row.dataset.bookingId;
  const payload = {
    booking_id: bookingId,
    status: $('[data-status]', row).value,
    feedback_note: $('[data-feedback]', row).value.trim()
  };
  await api('/api/admin/bookings/status', { method: 'POST', body: JSON.stringify(payload) });
  status(`Заявку #${bookingId} оновлено.`, 'ok');
  await loadAdmin();
}

$('#saveToken')?.addEventListener('click', () => {
  state.token = $('#adminToken').value.trim();
  sessionStorage.setItem('hh_admin_token', state.token);
  loadAdmin().catch((error) => status(error.message, 'err'));
});

$('#refreshAdmin')?.addEventListener('click', () => loadAdmin().catch((error) => status(error.message, 'err')));
$('#bookingRows')?.addEventListener('click', (event) => {
  const button = event.target.closest('[data-save-status]');
  if (!button) return;
  const row = button.closest('[data-booking-id]');
  saveBookingStatus(row).catch((error) => status(error.message, 'err'));
});

if ($('#adminToken')) $('#adminToken').value = state.token;
loadAdmin().catch((error) => status(error.message, 'err'));
