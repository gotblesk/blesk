import nacl from 'tweetnacl';
import { encodeBase64, decodeBase64, encodeUTF8, decodeUTF8 } from 'tweetnacl-util';
import API_URL from '../config';
import { getAuthHeaders } from './authFetch';

// Кеш вычисленных shared-ключей (roomId → Uint8Array)
const sharedKeyCache = new Map();
// Кеш публичных ключей других пользователей (userId → base64)
const publicKeyCache = new Map();

// Генерация пары ключей (X25519) и загрузка публичного на сервер
export async function generateKeyPair() {
  const keyPair = nacl.box.keyPair();
  const publicKeyB64 = encodeBase64(keyPair.publicKey);
  const secretKeyB64 = encodeBase64(keyPair.secretKey);

  // Приватный ключ — ТОЛЬКО в Electron safeStorage (DPAPI на Windows)
  if (window.blesk?.crypto?.saveSecretKey) {
    await window.blesk.crypto.saveSecretKey(secretKeyB64);
  } else {
    // НЕ сохраняем в localStorage — небезопасно (XSS = компрометация E2E)
    console.warn('[E2E] safeStorage недоступен — ключи не сохранены, E2E отключено');
    return null;
  }

  localStorage.setItem('blesk-public-key', publicKeyB64);

  // Отправить публичный ключ на сервер
  await fetch(`${API_URL}/api/auth/keys`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    credentials: 'include',
    body: JSON.stringify({ publicKey: publicKeyB64 }),
  });

  return publicKeyB64;
}

// Есть ли уже пара ключей
export async function hasKeyPair() {
  if (window.blesk?.crypto?.hasSecretKey) {
    return await window.blesk.crypto.hasSecretKey();
  }
  // Без safeStorage E2E недоступно
  return false;
}

// Создать пару если её нет
export async function ensureKeyPair() {
  const has = await hasKeyPair();
  if (!has) await generateKeyPair();
}

// Получить приватный ключ из хранилища
async function getSecretKey() {
  if (window.blesk?.crypto?.getSecretKey) {
    const b64 = await window.blesk.crypto.getSecretKey();
    return b64 ? decodeBase64(b64) : null;
  }
  // Без safeStorage приватный ключ недоступен
  return null;
}

// Публичный ключ текущего пользователя (base64)
export function getPublicKeyB64() {
  return localStorage.getItem('blesk-public-key');
}

// Вычислить shared key (Diffie-Hellman) между нами и собеседником
async function getSharedKey(otherPublicKeyB64, roomId) {
  if (roomId && sharedKeyCache.has(roomId)) return sharedKeyCache.get(roomId);

  const secretKey = await getSecretKey();
  if (!secretKey) return null;

  const otherPublicKey = decodeBase64(otherPublicKeyB64);
  const shared = nacl.box.before(otherPublicKey, secretKey);

  // Обнулить приватный ключ из памяти после использования
  secretKey.fill(0);

  if (roomId) sharedKeyCache.set(roomId, shared);
  return shared;
}

// Получить публичный ключ пользователя с сервера
export async function fetchPublicKey(userId) {
  if (publicKeyCache.has(userId)) return publicKeyCache.get(userId);

  const res = await fetch(`${API_URL}/api/users/${userId}`, {
    headers: { ...getAuthHeaders() }, credentials: 'include',
  });
  if (!res.ok) return null;
  const data = await res.json();
  if (data.publicKey) publicKeyCache.set(userId, data.publicKey);
  return data.publicKey || null;
}

// Зашифровать текст для собеседника
// Возвращает строку: base64(nonce).base64(ciphertext)
export async function encryptMessage(text, otherPublicKeyB64, roomId) {
  const sharedKey = await getSharedKey(otherPublicKeyB64, roomId);
  if (!sharedKey) return null;

  const nonce = nacl.randomBytes(24);
  const msgBytes = decodeUTF8(text);
  const encrypted = nacl.box.after(msgBytes, nonce, sharedKey);

  return encodeBase64(nonce) + '.' + encodeBase64(encrypted);
}

// Расшифровать сообщение от собеседника
export async function decryptMessage(payload, senderPublicKeyB64, roomId) {
  try {
    const sharedKey = await getSharedKey(senderPublicKeyB64, roomId);
    if (!sharedKey) return null;

    // Разделить payload на nonce и ciphertext по первой точке
    const dotIndex = payload.indexOf('.');
    if (dotIndex === -1) return null;
    const nonceB64 = payload.substring(0, dotIndex);
    const cipherB64 = payload.substring(dotIndex + 1);
    if (!nonceB64 || !cipherB64) return null;

    const nonce = decodeBase64(nonceB64);
    // Nonce для nacl.box должен быть ровно 24 байта
    if (nonce.length !== 24) return null;

    const ciphertext = decodeBase64(cipherB64);
    const decrypted = nacl.box.open.after(ciphertext, nonce, sharedKey);

    return decrypted ? encodeUTF8(decrypted) : null;
  } catch {
    return null;
  }
}

// Очистить кеши и ключи (при логауте)
export function clearCache() {
  // Обнулить shared keys перед очисткой
  for (const key of sharedKeyCache.values()) {
    if (key instanceof Uint8Array) key.fill(0);
  }
  sharedKeyCache.clear();
  publicKeyCache.clear();
  // Удалить ключи из localStorage (на случай если остались от старых версий)
  localStorage.removeItem('blesk-secret-key');
  localStorage.removeItem('blesk-public-key');
}

// Инвалидировать кеш ключей для конкретного пользователя (при смене ключа)
export function invalidateKeyCache(userId, roomId) {
  publicKeyCache.delete(userId);
  if (roomId) {
    const old = sharedKeyCache.get(roomId);
    if (old instanceof Uint8Array) old.fill(0);
    sharedKeyCache.delete(roomId);
  }
}

// Инвалидировать все shared keys для пользователя (перебор по roomId)
export function invalidateUserKeys(userId) {
  publicKeyCache.delete(userId);
  // Shared key кеш — по roomId, нужно знать roomId
  // При смене ключа безопаснее очистить весь кеш
  for (const key of sharedKeyCache.values()) {
    if (key instanceof Uint8Array) key.fill(0);
  }
  sharedKeyCache.clear();
}

// ─── E2E шифрование файлов (только для 1-on-1 DM чатов) ───
// Для групповых чатов E2E файлов пока не реализовано —
// нужно шифровать для каждого участника отдельно (multi-recipient encryption)

// Зашифровать файл (ArrayBuffer) для собеседника
// Возвращает Uint8Array: [24 байта nonce][ciphertext]
export async function encryptFile(fileArrayBuffer, otherPublicKeyB64, roomId) {
  const sharedKey = await getSharedKey(otherPublicKeyB64, roomId);
  if (!sharedKey) return null;

  const nonce = nacl.randomBytes(24);
  const plaintext = new Uint8Array(fileArrayBuffer);
  const encrypted = nacl.box.after(plaintext, nonce, sharedKey);

  // nonce (24) + ciphertext
  const combined = new Uint8Array(nonce.length + encrypted.length);
  combined.set(nonce);
  combined.set(encrypted, nonce.length);
  return combined;
}

// Расшифровать файл от собеседника
// Принимает ArrayBuffer (nonce + ciphertext), возвращает ArrayBuffer расшифрованных данных
export async function decryptFile(encryptedArrayBuffer, senderPublicKeyB64, roomId) {
  try {
    const sharedKey = await getSharedKey(senderPublicKeyB64, roomId);
    if (!sharedKey) return null;

    const data = new Uint8Array(encryptedArrayBuffer);
    if (data.length <= 24) return null; // Слишком короткий — нет данных после nonce

    const nonce = data.slice(0, 24);
    const ciphertext = data.slice(24);
    const decrypted = nacl.box.open.after(ciphertext, nonce, sharedKey);

    if (!decrypted) return null;
    return decrypted.buffer;
  } catch {
    return null;
  }
}

// Зашифровать метаданные файла (имя, MIME-тип) как JSON-строку
// Возвращает строку в формате base64(nonce).base64(ciphertext)
export async function encryptFileMeta(meta, otherPublicKeyB64, roomId) {
  const json = JSON.stringify(meta);
  return encryptMessage(json, otherPublicKeyB64, roomId);
}

// Расшифровать метаданные файла
// Возвращает объект { filename, mimeType } или null
export async function decryptFileMeta(payload, senderPublicKeyB64, roomId) {
  const json = await decryptMessage(payload, senderPublicKeyB64, roomId);
  if (!json) return null;
  try {
    return JSON.parse(json);
  } catch {
    return null;
  }
}

// Получить fingerprint ключа (SHA-256, первые 16 символов hex)
export async function getKeyFingerprint(publicKeyB64) {
  if (!publicKeyB64) return null;
  try {
    const keyBytes = decodeBase64(publicKeyB64);
    const hashBuffer = await crypto.subtle.digest('SHA-256', keyBytes);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    // Формат: XXXX XXXX XXXX XXXX (16 hex символов = 8 байт)
    return hex.slice(0, 16).replace(/(.{4})/g, '$1 ').trim();
  } catch {
    return null;
  }
}

// Получить fingerprint своего ключа
export async function getMyFingerprint() {
  const pubKey = getPublicKeyB64();
  return getKeyFingerprint(pubKey);
}

// Получить fingerprint ключа собеседника
export async function getPeerFingerprint(userId) {
  const pubKey = await fetchPublicKey(userId);
  return getKeyFingerprint(pubKey);
}
