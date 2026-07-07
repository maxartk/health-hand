// Health Hand app
const N8N_WEBHOOK_URL = '/webhook/health-hand-booking';
const API = '/api/portal';
const API_V2 = '/api/v2';
const BOOKING_API = '/api/bookings';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
let portalState = { user: null, bookings: [] };
let bookingCatalog = { services: [], slots: [] };
let bookingFormOpenedTracked = false;
const EVENT_API = '/api/events';
const HH_SESSION_ID = (() => {
  try {
    const key = 'hh_session_id';
    let value = localStorage.getItem(key);
    if (!value) {
      value = (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`);
      localStorage.setItem(key, value);
    }
    return value;
  } catch {
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
})();

async function apiFetch(url, options = {}) {
  const response = await fetch(url, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.ok === false) throw new Error(data.error || `HTTP ${response.status}`);
  return data;
}

function trackEvent(eventName, meta = {}) {
  try {
    const payload = {
      event_name: eventName,
      session_id: HH_SESSION_ID,
      page: location.href,
      source: 'health-hand-site',
      service_id: meta.service_id || '',
      employee_id: meta.employee_id || '',
      booking_id: meta.booking_id || '',
      meta
    };
    const body = JSON.stringify(payload);
    if (navigator.sendBeacon) {
      const blob = new Blob([body], { type: 'application/json' });
      navigator.sendBeacon(EVENT_API, blob);
      return;
    }
    fetch(EVENT_API, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, credentials: 'same-origin', keepalive: true }).catch(() => {});
  } catch {
    // Analytics must never block booking.
  }
}

function trackBookingFormOpened() {
  if (bookingFormOpenedTracked) return;
  bookingFormOpenedTracked = true;
  trackEvent('form_opened');
}

async function sendToWebhook(payload) {
  const response = await fetch(N8N_WEBHOOK_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!response.ok) throw new Error(`Webhook повернув ${response.status}`);
  return { ok: true };
}

function setMinDate() {
  const date = $('input[name="date"]');
  if (!date) return;
  const today = new Date();
  today.setMinutes(today.getMinutes() - today.getTimezoneOffset());
  date.min = today.toISOString().slice(0, 10);
}

function formatPrice(value) {
  const price = Number(value || 0);
  return price > 0 ? `${price.toLocaleString('uk-UA')} грн` : 'ціну уточнимо';
}

function setSelectLoading(select, text) {
  if (!select) return;
  select.innerHTML = `<option value="">${esc(text)}</option>`;
  select.disabled = true;
}

function renderServiceOptions(form) {
  const select = form?.elements.service;
  if (!select) return;
  const previous = select.value;
  if (!bookingCatalog.services.length) {
    select.disabled = false;
    return;
  }
  select.innerHTML = '<option value="">Оберіть послугу</option>' + bookingCatalog.services.map((service) => {
    const meta = `${service.duration_minutes || 60} хв · ${formatPrice(service.price)}`;
    return `<option value="${esc(service.name)}" data-service-id="${esc(service.id)}" data-duration="${esc(service.duration_minutes || 60)}">${esc(service.name)} — ${esc(meta)}</option>`;
  }).join('');
  select.disabled = false;
  if (previous) {
    const same = [...select.options].find((option) => option.value === previous || option.textContent.includes(previous));
    if (same) select.value = same.value;
  }
}

function renderSiteServices() {
  const grid = $('#siteServiceGrid');
  if (!grid || !bookingCatalog.services.length) return;
  grid.innerHTML = bookingCatalog.services.map((service, index) => {
    const delay = index % 3 === 1 ? ' delay-1' : index % 3 === 2 ? ' delay-2' : '';
    const duration = service.duration_minutes ? `${service.duration_minutes} хв` : 'тривалість уточнимо';
    const price = formatPrice(service.price);
    return `<article class="service reveal${delay}">
      <span>${String(index + 1).padStart(2, '0')}</span>
      <h3>${esc(service.name)}</h3>
      <p>${esc(service.description || 'Опис послуги можна змінити в Android-додатку власниці.')}</p>
      <strong>${esc(price)} · ${esc(duration)}</strong>
    </article>`;
  }).join('');
}

function selectedServiceId(form) {
  const option = form?.elements.service?.selectedOptions?.[0];
  return option?.dataset?.serviceId || '';
}

async function loadBookingServices() {
  const form = $('#bookingForm');
  if (!form) return;
  const serviceSelect = form.elements.service;
  try {
    setSelectLoading(serviceSelect, 'Завантажую послуги…');
    const data = await apiFetch(`${API_V2}/services`);
    bookingCatalog.services = data.services || [];
    renderSiteServices();
    renderServiceOptions(form);
  } catch (error) {
    console.warn('Services API failed', error);
    if (serviceSelect) serviceSelect.disabled = false;
  }
}

function renderSlotOptions(form, slots, message) {
  const timeSelect = form?.elements.time;
  if (!timeSelect) return;
  bookingCatalog.slots = slots || [];
  if (!bookingCatalog.slots.length) {
    timeSelect.innerHTML = `<option value="">${esc(message || 'На цю дату вільних слотів немає')}</option>`;
    timeSelect.disabled = true;
    return;
  }
  timeSelect.innerHTML = '<option value="">Оберіть доступний час</option>' + bookingCatalog.slots.map((slot) => {
    const label = `${slot.time} · ${slot.employee_name || 'Health Hand'}`;
    return `<option value="${esc(slot.time)}" data-start-at="${esc(slot.start_at)}" data-end-at="${esc(slot.end_at)}" data-employee-id="${esc(slot.employee_id)}">${esc(label)}</option>`;
  }).join('');
  timeSelect.disabled = false;
}

async function loadAvailabilitySlots() {
  const form = $('#bookingForm');
  if (!form) return;
  const serviceId = selectedServiceId(form);
  const date = form.elements.date?.value;
  const status = $('#formStatus');
  if (!serviceId || !date) {
    renderSlotOptions(form, [], 'Спочатку оберіть послугу і дату');
    return;
  }
  try {
    renderSlotOptions(form, [], 'Шукаю вільні слоти…');
    const data = await apiFetch(`${API_V2}/availability?service_id=${encodeURIComponent(serviceId)}&date=${encodeURIComponent(date)}`);
    renderSlotOptions(form, data.slots || [], 'На цю дату немає вільних слотів. Оберіть іншу дату.');
    if ((data.slots || []).length) setStatus(status, '', `Доступно ${data.slots.length} слотів на обрану дату.`);
    else setStatus(status, 'err', 'На цю дату немає вільних слотів. Спробуйте іншу дату.');
  } catch (error) {
    console.warn('Availability API failed', error);
    renderSlotOptions(form, [], 'Не вдалося завантажити слоти');
    setStatus(status, 'err', 'Не вдалося завантажити доступні слоти. Спробуйте оновити сторінку.');
  }
}

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[ch]));
}

function formatBookingDate(booking) {
  const date = [booking.date, booking.time].filter(Boolean).join(' ');
  return date || (booking.created_at ? new Date(booking.created_at * 1000).toLocaleString('uk-UA') : 'Дата уточнюється');
}

function statusLabel(status) {
  const map = {
    new: 'Нова заявка',
    contacted: 'Зв’язались',
    confirmed: 'Підтверджено',
    completed: 'Відбувся',
    cancelled: 'Скасовано',
    no_show: 'Не прийшов',
    followup_sent: 'Follow-up'
  };
  return map[status] || status;
}

function statusClass(status) {
  const map = {
    new: 'status-new',
    contacted: 'status-new',
    confirmed: 'status-confirmed',
    completed: 'status-completed',
    cancelled: 'status-cancelled',
    no_show: 'status-cancelled',
    followup_sent: 'status-completed'
  };
  return map[status] || 'status-new';
}

function setStatus(el, type, text) {
  if (!el) return;
  el.className = `form-status ${type || ''}`.trim();
  el.textContent = text || '';
}

function fillBookingFromUser() {
  const form = $('#bookingForm');
  const user = portalState.user;
  if (!form || !user) return;
  if (!form.elements.name.value) form.elements.name.value = user.name || '';
  if (!form.elements.contact.value) form.elements.contact.value = user.contact || '';
  if (form.elements.channel && !form.elements.channel.value) form.elements.channel.value = user.channel || '';
}

async function refreshPortal() {
  try {
    const data = await apiFetch(`${API}/me`);
    portalState.user = data.user;
    portalState.bookings = data.bookings || [];
  } catch {
    portalState = { user: null, bookings: [] };
  }
  renderPortal();
}

function renderPortal() {
  const auth = $('#portalAuth');
  const account = $('#portalAccount');
  const user = portalState.user;
  if (auth) auth.hidden = Boolean(user);
  if (account) account.hidden = !user;
  if (!user) return;

  if ($('#accountName')) $('#accountName').textContent = `${user.name} · ${user.contact}`;
  const profileForm = $('#clientProfileForm');
  if (profileForm && !profileForm.dataset.dirty) {
    profileForm.elements.name.value = user.name || '';
    profileForm.elements.birthday.value = user.birthday || '';
    profileForm.elements.channel.value = user.channel || 'WhatsApp';
    profileForm.elements.notes.value = user.notes || '';
    profileForm.elements.reminders.checked = Boolean(user.reminders);
  }

  const summary = $('#clientSummary');
  if (summary) {
    summary.innerHTML = `
      <div><strong>${esc(user.name)}</strong><span>${esc(user.contact)}</span></div>
      <div><strong>${esc(user.channel || 'Канал не обрано')}</strong><span>${portalState.bookings.length} записів у кабінеті</span></div>
    `;
  }

  renderDashboard();
  renderBookingList();
  fillBookingFromUser();
}

function renderDashboard() {
  const welcome = $('#dashboardWelcome');
  const nextVisit = $('#nextVisit');
  const user = portalState.user;
  if (!user) return;

  if (welcome) {
    welcome.innerHTML = `
      <h3>Вітаємо, ${esc(user.name)}</h3>
      <p>Ваш особистий кабінет Health Hand. Тут зберігаються записи, рекомендації та історія візитів.</p>
    `;
  }

  if (nextVisit) {
    const upcoming = portalState.bookings
      .filter((b) => ['new', 'contacted', 'confirmed'].includes(b.status))
      .sort((a, b) => (a.date || '').localeCompare(b.date || '') || (a.time || '').localeCompare(b.time || ''))[0];
    if (upcoming) {
      nextVisit.innerHTML = `
        <strong>Наступний візит</strong>
        <span>${esc(upcoming.service)} · ${esc(formatBookingDate(upcoming))}</span>
      `;
    } else {
      nextVisit.innerHTML = '';
    }
  }
}

function renderBookingList() {
  const list = $('#bookingList');
  const user = portalState.user;
  if (!list || !user) return;
  if (!portalState.bookings.length) {
    list.innerHTML = '<p class="empty-state">Поки немає записів. Після заявки вона зʼявиться тут автоматично.</p>';
  } else {
    list.innerHTML = portalState.bookings.map((booking) => `
      <article class="booking-item">
        <div><strong>${esc(booking.service || 'Масаж')}</strong><span class="status-badge ${statusClass(booking.status)}">${esc(statusLabel(booking.status))}</span></div>
        <p>${esc(booking.note || 'Без додаткового коментаря')}</p>
        <small>${esc(booking.channel || user.channel || 'канал не вказано')} · ${esc(formatBookingDate(booking))}</small>
      </article>
    `).join('');
  }
}

async function saveBookingToBackend(payload, webhookOk) {
  try {
    await apiFetch(BOOKING_API, { method: 'POST', body: JSON.stringify({ ...payload, webhookOk }) });
    await refreshPortal();
    return true;
  } catch (error) {
    console.warn('Booking API failed', error);
    return false;
  }
}

function initBookingForm() {
  const form = $('#bookingForm');
  if (!form) return;
  const status = $('#formStatus');
  loadBookingServices().then(loadAvailabilitySlots);
  form.addEventListener('focusin', trackBookingFormOpened);
  form.elements.service?.addEventListener('change', () => {
    const serviceId = selectedServiceId(form);
    trackEvent('service_selected', { service_id: serviceId, service: form.elements.service.value });
    loadAvailabilitySlots();
  });
  form.elements.date?.addEventListener('change', () => {
    trackEvent('date_selected', { service_id: selectedServiceId(form), date: form.elements.date.value });
    loadAvailabilitySlots();
  });
  form.elements.time?.addEventListener('change', () => {
    const option = form.elements.time.selectedOptions?.[0];
    trackEvent('slot_selected', {
      service_id: selectedServiceId(form),
      employee_id: option?.dataset?.employeeId || '',
      time: form.elements.time.value,
      start_at: option?.dataset?.startAt || '',
      end_at: option?.dataset?.endAt || ''
    });
  });
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }
    const data = Object.fromEntries(new FormData(form).entries());
    const serviceOption = form.elements.service?.selectedOptions?.[0];
    const timeOption = form.elements.time?.selectedOptions?.[0];
    data.service_id = serviceOption?.dataset?.serviceId || '';
    data.service = serviceOption?.value || data.service;
    data.employee_id = timeOption?.dataset?.employeeId || '';
    data.start_at = timeOption?.dataset?.startAt || '';
    data.end_at = timeOption?.dataset?.endAt || '';
    data.consent = data.consent === 'on' || data.consent === true;
    const payload = { ...data, session_id: HH_SESSION_ID, id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()), createdAt: new Date().toISOString(), page: location.href, userAgent: navigator.userAgent };
    setStatus(status, '', 'Надсилаю заявку…');
    let webhookOk = false;
    try {
      await sendToWebhook(payload);
      webhookOk = true;
      const saved = await saveBookingToBackend(payload, true);
      trackEvent('booking_submitted', { service_id: data.service_id, employee_id: data.employee_id, date: data.date, time: data.time, saved });
      setStatus(status, 'ok', portalState.user ? 'Заявку надіслано й додано в особистий кабінет.' : 'Заявку надіслано. Створіть кабінет нижче, щоб бачити історію записів.');
      form.reset();
      setMinDate();
      fillBookingFromUser();
      if (location.pathname.startsWith('/portal')) location.hash = '#cabinet';
      else location.href = '/portal/';
    } catch (error) {
      await saveBookingToBackend(payload, webhookOk);
      setStatus(status, 'err', `Заявку збережено, але автоматична відправка дала помилку: ${error.message}.`);
    }
  });
}

function updateTabAria(tabs, panels) {
  tabs.forEach((tab, i) => {
    const isActive = tab.classList.contains('active');
    tab.setAttribute('aria-selected', String(isActive));
    tab.setAttribute('tabindex', isActive ? '0' : '-1');
    if (panels[i]) panels[i].hidden = !isActive;
  });
}

function initClientPortal() {
  const authStatus = $('#authStatus');
  const profileStatus = $('#profileStatus');

  const authTabs = $$('[data-auth-tab]');
  const authPanels = $$('[data-auth-panel]');
  authTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      const name = tab.dataset.authTab;
      authTabs.forEach((item) => item.classList.toggle('active', item === tab));
      authPanels.forEach((panel) => panel.classList.toggle('active', panel.dataset.authPanel === name));
      updateTabAria(authTabs, authPanels);
      setStatus(authStatus, '', '');
    });
  });

  const portalTabs = $$('.portal-account [data-portal-tab]');
  const portalPanels = $$('.portal-panel');
  portalTabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      const name = tab.dataset.portalTab;
      portalTabs.forEach((item) => item.classList.toggle('active', item === tab));
      portalPanels.forEach((panel) => panel.classList.toggle('active', panel.dataset.portalPanel === name));
      updateTabAria(portalTabs, portalPanels);
      renderPortal();
    });
  });

  $('#registerForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.checkValidity()) { form.reportValidity(); return; }
    const data = Object.fromEntries(new FormData(form).entries());
    data.reminders = data.reminders === 'on';
    try {
      setStatus(authStatus, '', 'Створюю кабінет…');
      const result = await apiFetch(`${API}/register`, { method: 'POST', body: JSON.stringify(data) });
      portalState.user = result.user;
      portalState.bookings = result.bookings || [];
      form.reset();
      setStatus(authStatus, 'ok', 'Кабінет створено.');
      await refreshPortal();
    } catch (error) {
      setStatus(authStatus, 'err', error.message);
    }
  });

  $('#loginForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.checkValidity()) { form.reportValidity(); return; }
    const data = Object.fromEntries(new FormData(form).entries());
    try {
      setStatus(authStatus, '', 'Входжу…');
      await apiFetch(`${API}/login`, { method: 'POST', body: JSON.stringify(data) });
      form.reset();
      setStatus(authStatus, 'ok', 'Вхід виконано.');
      await refreshPortal();
    } catch (error) {
      setStatus(authStatus, 'err', error.message);
    }
  });

  $('#logoutBtn')?.addEventListener('click', async () => {
    await apiFetch(`${API}/logout`, { method: 'POST', body: '{}' }).catch(() => null);
    portalState = { user: null, bookings: [] };
    renderPortal();
  });

  $('#clientProfileForm')?.addEventListener('input', (event) => { event.currentTarget.dataset.dirty = 'true'; });
  $('#clientProfileForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.checkValidity()) { form.reportValidity(); return; }
    const data = Object.fromEntries(new FormData(form).entries());
    data.reminders = data.reminders === 'on';
    try {
      const result = await apiFetch(`${API}/profile`, { method: 'POST', body: JSON.stringify(data) });
      portalState.user = result.user;
      form.dataset.dirty = '';
      setStatus(profileStatus, 'ok', 'Профіль оновлено.');
      renderPortal();
    } catch (error) {
      setStatus(profileStatus, 'err', error.message);
    }
  });

  $('#repeatLastBooking')?.addEventListener('click', () => {
    const last = portalState.bookings[0];
    const user = portalState.user;
    const form = $('#bookingForm');
    if (!form) return;
    form.elements.name.value = user?.name || '';
    form.elements.contact.value = user?.contact || '';
    if (form.elements.channel) form.elements.channel.value = last?.channel || user?.channel || 'WhatsApp';
    if (last?.service) form.elements.service.value = last.service;
    if (last?.note) form.elements.note.value = last.note;
    location.hash = '#rezervace';
    setTimeout(() => form.elements.date?.focus(), 350);
  });

  updateTabAria(authTabs, authPanels);
  updateTabAria(portalTabs, portalPanels);
  refreshPortal();
}

function initPackageButtons() {
  $$('[data-fill]').forEach((button) => {
    button.addEventListener('click', () => {
      const service = button.dataset.fill;
      const select = $('select[name="service"]');
      if (select) {
        const option = [...select.options].find((opt) => opt.textContent === service || opt.textContent.startsWith(`${service} —`));
        if (option) select.value = option.value;
      }
      location.hash = '#rezervace';
      setTimeout(() => $('input[name="name"]')?.focus(), 450);
    });
  });
}

function initNav() {
  const header = $('.site-header');
  const toggle = $('.nav-toggle');
  const nav = $('.site-nav');
  const onScroll = () => header?.classList.toggle('scrolled', scrollY > 16);
  onScroll();
  addEventListener('scroll', onScroll, { passive: true });
  toggle?.addEventListener('click', () => {
    const open = nav.classList.toggle('open');
    document.body.classList.toggle('menu-open', open);
    toggle.setAttribute('aria-expanded', String(open));
  });
  $$('.site-nav a').forEach((link) => link.addEventListener('click', () => {
    nav?.classList.remove('open');
    document.body.classList.remove('menu-open');
    toggle?.setAttribute('aria-expanded', 'false');
  }));
}

function initReveal() {
  const elements = $$('.reveal');
  const observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    }
  }, { threshold: 0.15 });
  elements.forEach((element) => observer.observe(element));
}

// --- Premium features: quiz, sticky CTA, urgency banner, FAB ---

const QUIZ_QUESTIONS = [
  {
    question: 'Що вас турбує найбільше?',
    options: [
      { label: 'Біль / скутість у спині', value: 'back' },
      { label: 'Потрібен базовий масаж тіла', value: 'classic' },
      { label: 'Потрібен моделюючий догляд', value: 'anti_cellulite' },
      { label: 'Стрес, втома, безсоння', value: 'stress' },
      { label: 'Після спорту або навантажень', value: 'sport' }
    ]
  },
  {
    question: 'Як довго вас це турбує?',
    options: [
      { label: 'Кілька днів', value: 'short' },
      { label: 'Тиждень і більше', value: 'medium' },
      { label: 'Постійно / хронічно', value: 'long' }
    ]
  },
  {
    question: 'Чи є протипоказання або гострі болі?',
    options: [
      { label: 'Ні, загальне напруження', value: 'none' },
      { label: 'Температура / запалення', value: 'contra' },
      { label: 'Травма / операція нещодавно', value: 'injury' }
    ]
  },
  {
    question: 'Який формат вам підходить?',
    options: [
      { label: 'Один сеанс — спробувати', value: 'single' },
      { label: 'Курс зі знижкою', value: 'course' },
      { label: 'Подарунковий сертифікат', value: 'gift' }
    ]
  },
  {
    question: 'Зручний канал зв’язку?',
    options: [
      { label: 'Telegram', value: 'Telegram' },
      { label: 'WhatsApp', value: 'WhatsApp' },
      { label: 'Viber', value: 'Viber' },
      { label: 'Телефон', value: 'Дзвінок' }
    ]
  },
  {
    question: 'Коли хотіли б прийти?',
    options: [
      { label: 'Якнайшвидше', value: 'soon' },
      { label: 'Цього тижня', value: 'this_week' },
      { label: 'У вихідні', value: 'weekend' }
    ]
  }
];

const QUIZ_RESULTS = {
  back: { service: 'Масаж спини', duration: '45 хв', note: 'Масаж спини' },
  classic: { service: 'Класичний масаж', duration: '60 хв', note: 'Класичний масаж' },
  anti_cellulite: { service: 'Антицелюлітний масаж', duration: '60 хв', note: 'Антицелюлітний масаж' },
  stress: { service: 'Релакс масаж', duration: '60 хв', note: 'Релакс масаж' },
  sport: { service: 'Спортивний масаж', duration: '60 хв', note: 'Спортивний масаж' }
};

function initQuiz() {
  const modal = $('#quizModal');
  const body = $('#quizBody');
  const nextBtn = $('#quizNext');
  const prevBtn = $('#quizPrev');
  const progress = $('#quizProgress');
  if (!modal || !body || !nextBtn || !prevBtn || !progress) return;

  let step = 0;
  const answers = {};

  const close = () => { modal.hidden = true; document.body.style.overflow = ''; };
  const open = () => {
    step = 0;
    Object.keys(answers).forEach((k) => delete answers[k]);
    modal.hidden = false;
    document.body.style.overflow = 'hidden';
    renderStep();
  };

  $$('[data-open-quiz]').forEach((el) => el.addEventListener('click', open));
  $$('[data-close-quiz]').forEach((el) => el.addEventListener('click', close));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !modal.hidden) close(); });

  function renderStep() {
    const q = QUIZ_QUESTIONS[step];
    progress.firstElementChild.style.width = `${((step + 1) / QUIZ_QUESTIONS.length) * 100}%`;
    prevBtn.hidden = step === 0;
    nextBtn.textContent = step === QUIZ_QUESTIONS.length - 1 ? 'Отримати рекомендацію' : 'Далі';
    nextBtn.disabled = !answers[step];

    if (step < QUIZ_QUESTIONS.length) {
      body.innerHTML = `
        <p class="quiz-question">${esc(q.question)}</p>
        <div class="quiz-options" role="radiogroup" aria-label="${esc(q.question)}">
          ${q.options.map((opt) => `
            <button class="quiz-option ${answers[step] === opt.value ? 'selected' : ''}" type="button" data-value="${esc(opt.value)}" role="radio" aria-checked="${answers[step] === opt.value}">
              <span class="check" aria-hidden="true"></span>
              <span>${esc(opt.label)}</span>
            </button>
          `).join('')}
        </div>
      `;
      $$('.quiz-option', body).forEach((btn) => {
        btn.addEventListener('click', () => {
          answers[step] = btn.dataset.value;
          renderStep();
        });
      });
    }
  }

  nextBtn.addEventListener('click', () => {
    if (step < QUIZ_QUESTIONS.length - 1) {
      step++;
      renderStep();
    } else {
      showResult();
    }
  });

  prevBtn.addEventListener('click', () => { if (step > 0) { step--; renderStep(); } });

  function showResult() {
    const main = answers[0];
    const contra = answers[2];
    const format = answers[3];
    const channel = answers[4];
    const rec = QUIZ_RESULTS[main] || QUIZ_RESULTS.back;
    const service = format === 'gift' ? 'Подарунковий сертифікат' : rec.service;
    const warn = contra === 'contra' || contra === 'injury'
      ? '<p style="color:#a83232"><strong>Увага:</strong> при гострих станах, температурі або нещодавній травмі спочатку проконсультуйтесь із лікарем.</p>'
      : '';
    body.innerHTML = `
      <div class="quiz-result">
        <h3>Рекомендація: ${esc(service)}</h3>
        <p>${esc(rec.duration)} · ${esc(rec.note)}</p>
        ${warn}
        <button class="btn btn-primary" type="button" data-book-result>Записатися на ${esc(service)}</button>
      </div>
    `;
    nextBtn.hidden = true;
    prevBtn.hidden = true;
    progress.firstElementChild.style.width = '100%';
    $('[data-book-result]', body)?.addEventListener('click', () => {
      const select = $('select[name="service"]');
      if (select) {
        const option = [...select.options].find((opt) => opt.textContent === service || opt.textContent.startsWith(`${service} —`));
        if (option) select.value = option.value;
      }
      const channelSelect = $('select[name="channel"]');
      if (channelSelect && channel) channelSelect.value = channel;
      close();
      location.hash = '#rezervace';
      setTimeout(() => $('input[name="name"]')?.focus(), 450);
    });
  }
}

function initStickyCta() {
  const el = $('#stickyCta');
  if (!el) return;
  const show = () => {
    const heroBottom = $('.hero')?.getBoundingClientRect().bottom || 0;
    el.classList.toggle('visible', heroBottom < 0);
  };
  addEventListener('scroll', show, { passive: true });
  show();
}

function initTopBanner() {
  const banner = $('#topBanner');
  const text = $('#bannerText');
  const close = $('.banner-close');
  if (!banner || !text) return;

  const setVisible = (visible) => {
    banner.classList.toggle('visible', visible);
    document.body.classList.toggle('banner-visible', visible);
  };

  const messages = [
    '🔥 Цього тижня залишилось 4 вільних місця на лікувальний масаж',
    '⏰ Записуйтесь заздалегідь — вечірні слоти найпопулярніші',
    '🎁 Подарунковий сертифікат — практична турбота про близьких'
  ];
  let idx = 0;
  setInterval(() => {
    idx = (idx + 1) % messages.length;
    text.style.opacity = '0';
    setTimeout(() => { text.textContent = messages[idx]; text.style.opacity = '1'; }, 300);
  }, 6000);

  close?.addEventListener('click', () => {
    banner.style.display = 'none';
    setVisible(false);
    sessionStorage.setItem('hh_banner_closed', '1');
  });

  if (sessionStorage.getItem('hh_banner_closed') === '1') {
    banner.style.display = 'none';
    setVisible(false);
  } else {
    setVisible(true);
  }
}

function initFab() {
  const fab = $('.fab');
  if (!fab) return;
  const phone = fab.getAttribute('href')?.replace(/\D/g, '') || '380000000000';
  if (phone === '380000000000') fab.style.display = 'none';
}

setMinDate();
initNav();
initReveal();
initPackageButtons();
initBookingForm();
initClientPortal();
initQuiz();
initStickyCta();
initTopBanner();
initFab();
