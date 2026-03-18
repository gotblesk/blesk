import nacl from 'tweetnacl';
import { encodeBase64, decodeBase64, encodeUTF8, decodeUTF8 } from 'tweetnacl-util';
import API_URL from '../config';

// Кеш вычисленных shared-ключей (roomId → Uint8Array)
const sharedKeyCache = new Map();
// Кеш публичных ключей других пользователей (userId → base64)
const publicKeyCache = new Map();

// Генерация пары ключей (X25519) и загрузка публичного на сервер
export async function generateKeyPair() {
  const keyPair = nacl.box.keyPair();
  const publicKeyB64 = encodeBase64(keyPair.publicKey);
  const secretKeyB64 = encodeBase64(keyPair.secretKey);

  // Приватный ключ — в Electron safeStorage (DPAPI на Windows)
  if (window.blesk?.crypto?.saveSecretKey) {
    await window.blesk.crypto.saveSecretKey(secretKeyB64);
  } else {
    localStorage.setItem('blesk-secret-key', secretKeyB64);
  }

  localStorage.setItem('blesk-public-key', publicKeyB64);

  // Отправить публичный ключ на сервер
  const token = localStorage.getItem('token');
  await fetch(`${API_URL}/api/auth/keys`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ publicKey: publicKeyB64 }),
  });

  return publicKeyB64;
}

// Есть ли уже пара ключей
export async function hasKeyPair() {
  if (window.blesk?.crypto?.hasSecretKey) {
    return await window.blesk.crypto.hasSecretKey();
  }
  return !!localStorage.getItem('blesk-secret-key');
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
  const b64 = localStorage.getItem('blesk-secret-key');
  return b64 ? decodeBase64(b64) : null;
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

  if (roomId) sharedKeyCache.set(roomId, shared);
  return shared;
}

// Получить публичный ключ пользователя с сервера
export async function fetchPublicKey(userId) {
  if (publicKeyCache.has(userId)) return publicKeyCache.get(userId);

  const token = localStorage.getItem('token');
  const res = await fetch(`${API_URL}/api/users/${userId}`, {
    headers: { Authorization: `Bearer ${token}` },
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

    const [nonceB64, cipherB64] = payload.split('.');
    if (!nonceB64 || !cipherB64) return null;

    const nonce = decodeBase64(nonceB64);
    const ciphertext = decodeBase64(cipherB64);
    const decrypted = nacl.box.open.after(ciphertext, nonce, sharedKey);

    return decrypted ? encodeUTF8(decrypted) : null;
  } catch {
    return null;
  }
}

// Очистить кеши (при логауте)
export function clearCache() {
  sharedKeyCache.clear();
  publicKeyCache.clear();
}
