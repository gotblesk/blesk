/**
 * blesk Shield — service layer
 * Оркестрация X3DH, Double Ratchet, sessions, prekeys
 */
import {
  generateKeyPair, generateSigningKeyPair, generatePreKeyBundle,
  x3dhInitiator, x3dhResponder,
  encodeBase64, decodeBase64,
  generateVisualFingerprint,
} from './shieldCrypto';
import {
  initSessionInitiator, initSessionResponder,
  ratchetEncrypt, ratchetDecrypt,
  serializeSession, deserializeSession,
} from './shieldRatchet';
import API_URL from '../config';

// Кеш активных сессий в памяти (peerId → session)
const sessionCache = new Map();

// [HIGH-2] Per-peer lock для предотвращения concurrent session access
const sessionLocks = new Map();
async function withSessionLock(peerId, fn) {
  const prev = sessionLocks.get(peerId) ?? Promise.resolve();
  const next = prev.then(() => fn()).catch((err) => { throw err; });
  sessionLocks.set(peerId, next.catch(() => {}));
  return next;
}

// Флаг инициализации
let initialized = false;

// ═══════ Инициализация Shield ═══════

/**
 * Инициализировать Shield при запуске приложения
 * Генерирует signing key + SPK если отсутствуют, загружает bundle на сервер
 */
export async function initializeShield() {
  if (initialized) return;
  if (!window.blesk?.crypto) return;

  try {
    // 1. Проверить identity key (должен быть из legacy)
    const hasIK = await window.blesk.crypto.hasSecretKey();
    if (!hasIK) return; // E2E не настроено

    // 2. Signing key (Ed25519)
    const hasSigningKey = await window.blesk.crypto.hasSigningKey();
    if (!hasSigningKey) {
      const signingPair = generateSigningKeyPair();
      await window.blesk.crypto.saveSigningKey(encodeBase64(signingPair.secretKey));
      localStorage.setItem('blesk-signing-public-key', encodeBase64(signingPair.publicKey));
    }

    // 3. SPK (Signed PreKey)
    const spkJson = await window.blesk.crypto.getSPK();
    let spkData = spkJson ? JSON.parse(spkJson) : null;

    // Если нет SPK или он старше 7 дней — ротация
    const needsRotation = !spkData || (Date.now() - (spkData.createdAt || 0) > 7 * 24 * 60 * 60 * 1000);

    if (needsRotation) {
      await uploadBundle(50); // 50 OPK при первом запуске
    }

    initialized = true;
  } catch (err) {
    console.error('Shield: ошибка инициализации:', err);
  }
}

/**
 * Загрузить prekey bundle на сервер
 */
async function uploadBundle(opkCount = 50) {
  const ikSecretB64 = await window.blesk.crypto.getSecretKey();
  const signingSecretB64 = await window.blesk.crypto.getSigningKey();
  if (!ikSecretB64 || !signingSecretB64) return;

  const ikSecret = decodeBase64(ikSecretB64);
  const ikPublic = decodeBase64(localStorage.getItem('blesk-public-key') || '');
  const signingSecret = decodeBase64(signingSecretB64);
  const signingPublic = decodeBase64(localStorage.getItem('blesk-signing-public-key') || '');

  // Получить текущий SPK ID (инкрементальный)
  const spkJson = await window.blesk.crypto.getSPK();
  const prevSpk = spkJson ? JSON.parse(spkJson) : null;
  const spkId = (prevSpk?.spkId || 0) + 1;

  // Получить текущий OPK ID
  const opkJson = await window.blesk.crypto.getOPKs();
  const prevOpks = opkJson ? JSON.parse(opkJson) : { nextId: 0, keys: [] };
  const opkStartId = prevOpks.nextId || 0;

  // Генерировать bundle
  const { bundle, secrets } = generatePreKeyBundle(
    { publicKey: ikPublic, secretKey: ikSecret },
    { publicKey: signingPublic, secretKey: signingSecret },
    spkId, opkStartId, opkCount
  );

  // Сохранить секреты SPK локально
  await window.blesk.crypto.saveSPK(JSON.stringify({
    secretKey: secrets.spkSecretKey,
    publicKey: secrets.spkPublicKey,
    spkId: secrets.spkId,
    createdAt: Date.now(),
  }));

  // Сохранить секреты OPK локально
  const allOpks = [...(prevOpks.keys || []), ...secrets.oneTimePreKeyPairs];
  await window.blesk.crypto.saveOPKs(JSON.stringify({
    nextId: opkStartId + opkCount,
    keys: allOpks,
  }));

  // Загрузить bundle на сервер
  const token = localStorage.getItem('token');
  await fetch(`${API_URL}/api/shield/bundle`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(bundle),
  });

  // Обнулить секреты
  ikSecret.fill(0);
  signingSecret.fill(0);
}

// ═══════ Session Management ═══════

/**
 * Получить или создать Shield-сессию для собеседника
 */
async function getSession(peerId) {
  // Из кеша в памяти
  if (sessionCache.has(peerId)) return sessionCache.get(peerId);

  // Из safeStorage
  const json = await window.blesk.crypto.getSession(peerId);
  if (json) {
    const session = deserializeSession(JSON.parse(json));
    sessionCache.set(peerId, session);
    return session;
  }

  return null;
}

/**
 * Сохранить сессию
 */
async function saveSession(peerId, session) {
  sessionCache.set(peerId, session);
  const json = JSON.stringify(serializeSession(session));
  await window.blesk.crypto.saveSession(peerId, json);
}

/**
 * Создать новую сессию через X3DH (инициатор)
 */
async function createSession(peerId) {
  // Получить bundle собеседника
  const token = localStorage.getItem('token');
  const res = await fetch(`${API_URL}/api/shield/bundle/${peerId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!res.ok) return null;
  const bundle = await res.json();

  // Получить наш identity key
  const ikSecretB64 = await window.blesk.crypto.getSecretKey();
  if (!ikSecretB64) return null;

  const ikSecret = decodeBase64(ikSecretB64);
  const ikPublicB64 = localStorage.getItem('blesk-public-key');

  // X3DH
  const x3dhResult = x3dhInitiator(ikSecret, decodeBase64(ikPublicB64), bundle);
  ikSecret.fill(0);

  // Инициализировать сессию
  const session = initSessionInitiator(x3dhResult.sharedSecret, bundle.signedPreKey);
  x3dhResult.sharedSecret.fill(0);

  // Сохранить
  await saveSession(peerId, session);

  return {
    session,
    header: {
      identityKey: ikPublicB64,
      ephemeralKey: x3dhResult.ephemeralKey,
      opkId: x3dhResult.opkId,
      spkId: x3dhResult.spkId,
    },
  };
}

// ═══════ Encrypt / Decrypt ═══════

/**
 * [HIGH-2] Зашифровать сообщение для собеседника (Shield) — с session lock
 * @returns {{ text: string, shieldHeader?: Object }} | null
 */
export async function shieldEncrypt(peerId, plaintext) {
  if (!window.blesk?.crypto) return null;

  return withSessionLock(peerId, async () => {
    try {
      let session = await getSession(peerId);
      let shieldHeader = null;

      // Если нет сессии — создать через X3DH
      if (!session) {
        // [HIGH-1] Проверить — должны ли мы быть инициатором
        // Детерминированное правило: инициатор = тот, чей userId < peerId
        const myId = localStorage.getItem('blesk-user-id') || '';
        if (myId > peerId) {
          // Мы НЕ инициатор — подождать входящее сообщение от собеседника
          return null;
        }
        const result = await createSession(peerId);
        if (!result) return null;
        session = result.session;
        shieldHeader = result.header;
      }

      // Double Ratchet encrypt
      const { header, ciphertext, session: updatedSession } = ratchetEncrypt(session, plaintext);

      // Сохранить обновлённую сессию
      await saveSession(peerId, updatedSession);

      // Wire format: SHIELD:1:<header_b64>.<ciphertext_b64>
      const headerB64 = btoa(JSON.stringify(header));
      const cipherB64 = encodeBase64(ciphertext);
      const wireText = `SHIELD:1:${headerB64}.${cipherB64}`;

      return { text: wireText, shieldHeader };
    } catch (err) {
      console.error('Shield encrypt error:', err);
      return null;
    }
  });
}

/**
 * [HIGH-2] Расшифровать сообщение от собеседника (Shield) — с session lock
 */
export async function shieldDecrypt(senderId, wireText, shieldHeader = null) {
  if (!window.blesk?.crypto) return null;

  return withSessionLock(senderId, async () => {
    try {
      // Парсить wire format
      if (!wireText.startsWith('SHIELD:1:')) return null;
      const payload = wireText.substring(9);
      const dotIndex = payload.indexOf('.');
      if (dotIndex === -1) return null;

      const headerB64 = payload.substring(0, dotIndex);
      const cipherB64 = payload.substring(dotIndex + 1);

      const header = JSON.parse(atob(headerB64));
      const ciphertext = decodeBase64(cipherB64);

      let session = await getSession(senderId);

      // Если нет сессии и есть X3DH header — создать как ответчик
      if (!session && shieldHeader) {
        session = await createSessionResponder(senderId, shieldHeader);
        if (!session) return null;
      }

      if (!session) return null;

      // Double Ratchet decrypt
      const { plaintext, session: updatedSession } = ratchetDecrypt(session, header, ciphertext);

      // Сохранить обновлённую сессию
      await saveSession(senderId, updatedSession);

      return plaintext;
    } catch (err) {
      console.error('Shield decrypt error:', err);
      return null;
    }
  });
}

/**
 * Создать сессию как ответчик (Bob получает первое сообщение от Alice)
 */
async function createSessionResponder(senderId, header) {
  const ikSecretB64 = await window.blesk.crypto.getSecretKey();
  const spkJson = await window.blesk.crypto.getSPK();
  if (!ikSecretB64 || !spkJson) return null;

  const ikSecret = decodeBase64(ikSecretB64);
  const ikPublicB64 = localStorage.getItem('blesk-public-key');
  const spkData = JSON.parse(spkJson);
  const spkSecret = decodeBase64(spkData.secretKey);

  // Найти OPK secret если использовался
  let opkSecret = null;
  if (header.opkId !== null && header.opkId !== undefined) {
    const opkJson = await window.blesk.crypto.getOPKs();
    if (opkJson) {
      const opks = JSON.parse(opkJson);
      const opkEntry = opks.keys?.find(k => k.id === header.opkId);
      if (opkEntry) {
        opkSecret = decodeBase64(opkEntry.secretKey);
        // [HIGH-3] Удалить использованный OPK из локального хранилища
        opks.keys = opks.keys.filter(k => k.id !== header.opkId);
        await window.blesk.crypto.saveOPKs(JSON.stringify(opks));
      }
    }
  }

  // X3DH как ответчик
  const sharedSecret = x3dhResponder(
    ikSecret, decodeBase64(ikPublicB64),
    spkSecret, opkSecret,
    header
  );

  // Обнулить секреты
  ikSecret.fill(0);
  spkSecret.fill(0);
  if (opkSecret) opkSecret.fill(0);

  // Инициализировать сессию как ответчик
  const session = initSessionResponder(sharedSecret, {
    publicKey: spkData.publicKey,
    secretKey: spkData.secretKey,
  });
  sharedSecret.fill(0);

  await saveSession(senderId, session);
  return session;
}

// ═══════ OPK Replenishment ═══════

/**
 * Пополнить OPK когда сервер сообщает что их мало
 */
export async function replenishOPKs(count = 50) {
  try {
    const opkJson = await window.blesk.crypto.getOPKs();
    const prevOpks = opkJson ? JSON.parse(opkJson) : { nextId: 0, keys: [] };
    const startId = prevOpks.nextId || 0;

    const newOpks = [];
    const newOpkPairs = [];
    for (let i = 0; i < count; i++) {
      const pair = generateKeyPair();
      newOpks.push({ id: startId + i, key: encodeBase64(pair.publicKey) });
      newOpkPairs.push({
        id: startId + i,
        secretKey: encodeBase64(pair.secretKey),
        publicKey: encodeBase64(pair.publicKey),
      });
    }

    // Загрузить на сервер
    const token = localStorage.getItem('token');
    await fetch(`${API_URL}/api/shield/replenish`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ oneTimePreKeys: newOpks }),
    });

    // Сохранить секреты локально
    const allKeys = [...prevOpks.keys, ...newOpkPairs];
    await window.blesk.crypto.saveOPKs(JSON.stringify({
      nextId: startId + count,
      keys: allKeys,
    }));
  } catch (err) {
    console.error('Shield: ошибка пополнения OPK:', err);
  }
}

// ═══════ Visual Fingerprint ═══════

/**
 * Получить визуальный fingerprint для пары ключей
 * @param {string} myPublicKey — base64
 * @param {string} peerPublicKey — base64
 * @returns {Array<{hue, saturation, lightness, phase}>} — 16 ячеек
 */
export function getVisualFingerprint(myPublicKey, peerPublicKey) {
  return generateVisualFingerprint(myPublicKey, peerPublicKey);
}

/**
 * Проверить поддерживает ли собеседник Shield
 */
export async function peerSupportsShield(peerId) {
  try {
    const token = localStorage.getItem('token');
    const res = await fetch(`${API_URL}/api/shield/bundle/${peerId}`, {
      method: 'HEAD',
      headers: { Authorization: `Bearer ${token}` },
    });
    return res.ok;
  } catch {
    return false;
  }
}

/**
 * Проверить инициализирован ли Shield
 */
export function isShieldReady() {
  return initialized;
}

/**
 * [LOW-1] Сбросить Shield при логауте (очистить кеши и флаг)
 */
export function resetShield() {
  initialized = false;
  sessionCache.clear();
  sessionLocks.clear();
}
