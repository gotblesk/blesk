import API_URL from '../config';

// Централизованный хелпер авторизации для всех API-вызовов.
// Токены хранятся только в памяти (не в localStorage) — защита от XSS.
// httpOnly cookies — основной механизм авторизации (credentials: 'include').
// In-memory Bearer token — fallback для Socket.io auth и API-запросов.

// ═══ In-memory token store (НЕ localStorage) ═══
let _token = null;
let _refreshToken = null;
let _userId = null;

export function setTokens(token, refreshToken) {
  _token = token || null;
  _refreshToken = refreshToken || null;
}

export function getToken() { return _token; }
export function getRefreshToken() { return _refreshToken; }

export function setUserId(id) { _userId = id || null; }
export function getUserId() { return _userId; }

export function clearTokens() {
  _token = null;
  _refreshToken = null;
  _userId = null;
}

let csrfToken = null;
let csrfRefreshTimer = null;

async function fetchCsrfToken() {
  try {
    const headers = { 'Content-Type': 'application/json' };
    if (_token) headers['Authorization'] = `Bearer ${_token}`;

    const res = await fetch(`${API_URL}/api/auth/csrf`, {
      credentials: 'include',
      headers,
    });
    if (res.ok) {
      const data = await res.json();
      csrfToken = data.csrfToken;
    }
  } catch (err) {
    console.error('CSRF token fetch failed:', err?.message || err);
  }
}

// Инициализация CSRF — вызвать после логина или авто-логина
export async function initCsrf() {
  await fetchCsrfToken();

  // Обновлять CSRF-токен каждые 50 минут (TTL = 60 минут)
  if (csrfRefreshTimer) clearInterval(csrfRefreshTimer);
  csrfRefreshTimer = setInterval(fetchCsrfToken, 50 * 60 * 1000);
}

// Очистка при логауте
export function clearCsrf() {
  csrfToken = null;
  if (csrfRefreshTimer) {
    clearInterval(csrfRefreshTimer);
    csrfRefreshTimer = null;
  }
}

export function getAuthHeaders() {
  const headers = {};
  if (_token) headers['Authorization'] = `Bearer ${_token}`;
  if (csrfToken) headers['X-CSRF-Token'] = csrfToken;
  return headers;
}

export async function authFetch(path, options = {}) {
  const url = path.startsWith('http') ? path : `${API_URL}${path}`;
  const res = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: {
      ...getAuthHeaders(),
      ...options.headers,
    },
  });

  // CSRF token expired/invalid (например после PM2 restart) — обновить и повторить
  if (res.status === 403 && !options._csrfRetried) {
    const body = await res.clone().json().catch(() => null);
    if (body?.error?.includes?.('CSRF') || body?.error?.includes?.('csrf')) {
      await fetchCsrfToken();
      return authFetch(path, { ...options, _csrfRetried: true });
    }
  }

  return res;
}
