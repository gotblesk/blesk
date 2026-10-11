/**
 * blesk Shield — Double Ratchet state machine
 * Forward secrecy + break-in recovery
 */
import {
  generateKeyPair, dh,
  deriveRootAndChainKey, deriveMessageKey,
  symmetricEncrypt, symmetricDecrypt,
  encodeBase64, decodeBase64, decodeUTF8, encodeUTF8,
} from './shieldCrypto';

const MAX_SKIP = 1000; // Макс. пропущенных ключей сообщений
const SKIPPED_KEY_TTL = 7 * 24 * 60 * 60 * 1000; // 7 дней TTL для пропущенных ключей

// ═══════ Инициализация сессии ═══════

/**
 * Инициализировать сессию как инициатор (Alice)
 * После X3DH Alice знает sharedSecret и bobSPK (публичный)
 */
export function initSessionInitiator(sharedSecret, bobSPKPublic) {
  const bobSPK = typeof bobSPKPublic === 'string' ? decodeBase64(bobSPKPublic) : bobSPKPublic;
  const dhPair = generateKeyPair();

  // Первый DH ratchet step
  const dhOutput = dh(dhPair.secretKey, bobSPK);
  const { rootKey, chainKey } = deriveRootAndChainKey(sharedSecret, dhOutput);
  dhOutput.fill(0);

  return {
    DHs: { publicKey: dhPair.publicKey, secretKey: dhPair.secretKey },
    DHr: bobSPK,
    RK: rootKey,
    CKs: chainKey,
    CKr: null,
    Ns: 0,
    Nr: 0,
    PN: 0,
    MKSKIPPED: new Map(),
  };
}

/**
 * Инициализировать сессию как ответчик (Bob)
 * После X3DH Bob знает sharedSecret и имеет свой SPK pair
 */
export function initSessionResponder(sharedSecret, bobSPKPair) {
  const spkPair = {
    publicKey: typeof bobSPKPair.publicKey === 'string' ? decodeBase64(bobSPKPair.publicKey) : bobSPKPair.publicKey,
    secretKey: typeof bobSPKPair.secretKey === 'string' ? decodeBase64(bobSPKPair.secretKey) : bobSPKPair.secretKey,
  };

  return {
    DHs: spkPair,
    DHr: null, // Будет установлен при получении первого сообщения от Alice
    RK: sharedSecret,
    CKs: null,
    CKr: null,
    Ns: 0,
    Nr: 0,
    PN: 0,
    MKSKIPPED: new Map(),
  };
}

// ═══════ Шифрование (отправка) ═══════

/**
 * Зашифровать сообщение с Double Ratchet
 * @param {Object} session — текущее состояние сессии
 * @param {string} plaintext — текст сообщения
 * @returns {{ header: Object, ciphertext: Uint8Array, session: Object }}
 */
export function ratchetEncrypt(session, plaintext) {
  // Клонировать сессию для иммутабельности
  const s = cloneSession(session);

  if (!s.CKs) {
    throw new Error('Shield: sending chain not initialized');
  }

  // Вывести message key из sending chain
  const { chainKey, messageKey, nonce } = deriveMessageKey(s.CKs);
  s.CKs = chainKey;

  // Зашифровать
  const msgBytes = decodeUTF8(plaintext);
  const ciphertext = symmetricEncrypt(msgBytes, messageKey, nonce);

  // Обнулить message key
  messageKey.fill(0);

  // Заголовок
  const header = {
    dh: encodeBase64(s.DHs.publicKey),
    pn: s.PN,
    n: s.Ns,
  };

  s.Ns++;

  return { header, ciphertext, session: s };
}

// ═══════ Дешифрование (получение) ═══════

/**
 * Расшифровать сообщение с Double Ratchet
 * @param {Object} session — текущее состояние сессии
 * @param {Object} header — { dh, pn, n }
 * @param {Uint8Array} ciphertext
 * @returns {{ plaintext: string, session: Object }}
 */
export function ratchetDecrypt(session, header, ciphertext) {
  const s = cloneSession(session);
  const headerDH = decodeBase64(header.dh);

  // 1. Проверить пропущенные ключи
  const skippedKey = trySkippedMessageKey(s, header);
  if (skippedKey) {
    const decrypted = symmetricDecrypt(ciphertext, skippedKey.key, skippedKey.nonce);
    skippedKey.key.fill(0);
    if (!decrypted) throw new Error('Shield: не удалось расшифровать (пропущенный ключ)');
    return { plaintext: encodeUTF8(decrypted), session: s };
  }

  // 2. Если DH ключ отличается — выполнить DH ratchet step
  if (!s.DHr || !uint8ArrayEqual(headerDH, s.DHr)) {
    // Пропустить оставшиеся ключи в текущей receiving chain
    skipMessageKeys(s, header.pn);
    dhRatchetStep(s, headerDH);
  }

  // 3. Пропустить ключи до нужного номера
  skipMessageKeys(s, header.n);

  // 4. Вывести message key
  const { chainKey, messageKey, nonce } = deriveMessageKey(s.CKr);
  s.CKr = chainKey;
  s.Nr++;

  // 5. Расшифровать
  const decrypted = symmetricDecrypt(ciphertext, messageKey, nonce);
  messageKey.fill(0);

  if (!decrypted) throw new Error('Shield: не удалось расшифровать');

  return { plaintext: encodeUTF8(decrypted), session: s };
}

// ═══════ DH Ratchet Step ═══════

function dhRatchetStep(session, headerDH) {
  session.PN = session.Ns;
  session.Ns = 0;
  session.Nr = 0;
  session.DHr = headerDH;

  // Вывести receiving chain key
  const dhRecv = dh(session.DHs.secretKey, session.DHr);
  const { rootKey: rk1, chainKey: ckr } = deriveRootAndChainKey(session.RK, dhRecv);
  session.RK = rk1;
  session.CKr = ckr;
  dhRecv.fill(0);

  // Генерировать новую DH пару
  const oldSecret = session.DHs.secretKey;
  session.DHs = generateKeyPair();

  // Вывести sending chain key
  const dhSend = dh(session.DHs.secretKey, session.DHr);
  const { rootKey: rk2, chainKey: cks } = deriveRootAndChainKey(session.RK, dhSend);
  session.RK = rk2;
  session.CKs = cks;
  dhSend.fill(0);

  // Обнулить старый секретный ключ
  oldSecret.fill(0);
}

// ═══════ Пропущенные ключи ═══════

function skipMessageKeys(session, until) {
  if (session.CKr === null) return;
  if (until - session.Nr > MAX_SKIP) {
    throw new Error('Shield: слишком много пропущенных сообщений');
  }

  while (session.Nr < until) {
    const { chainKey, messageKey, nonce } = deriveMessageKey(session.CKr);
    session.CKr = chainKey;

    const mapKey = (session.DHr ? encodeBase64(session.DHr) : 'init') + ':' + session.Nr;
    session.MKSKIPPED.set(mapKey, { key: messageKey, nonce, timestamp: Date.now() });
    session.Nr++;

    // Очистить старые пропущенные ключи
    evictOldSkippedKeys(session);
  }
}

function trySkippedMessageKey(session, header) {
  const mapKey = header.dh + ':' + header.n;
  if (session.MKSKIPPED.has(mapKey)) {
    const entry = session.MKSKIPPED.get(mapKey);
    session.MKSKIPPED.delete(mapKey);
    return entry;
  }
  return null;
}

function evictOldSkippedKeys(session) {
  const now = Date.now();
  for (const [key, entry] of session.MKSKIPPED) {
    if (now - entry.timestamp > SKIPPED_KEY_TTL) {
      entry.key.fill(0);
      session.MKSKIPPED.delete(key);
    }
  }
  // Жёсткий лимит
  if (session.MKSKIPPED.size > MAX_SKIP) {
    const oldest = [...session.MKSKIPPED.entries()]
      .sort((a, b) => a[1].timestamp - b[1].timestamp);
    while (session.MKSKIPPED.size > MAX_SKIP) {
      const [key, entry] = oldest.shift();
      entry.key.fill(0);
      session.MKSKIPPED.delete(key);
    }
  }
}

// ═══════ Сериализация / Десериализация ═══════

/** Сериализовать сессию в JSON-совместимый объект */
export function serializeSession(session) {
  const skipped = [];
  for (const [key, value] of session.MKSKIPPED) {
    skipped.push([key, {
      key: encodeBase64(value.key),
      nonce: encodeBase64(value.nonce),
      timestamp: value.timestamp,
    }]);
  }

  return {
    DHs: {
      publicKey: encodeBase64(session.DHs.publicKey),
      secretKey: encodeBase64(session.DHs.secretKey),
    },
    DHr: session.DHr ? encodeBase64(session.DHr) : null,
    RK: encodeBase64(session.RK),
    CKs: session.CKs ? encodeBase64(session.CKs) : null,
    CKr: session.CKr ? encodeBase64(session.CKr) : null,
    Ns: session.Ns,
    Nr: session.Nr,
    PN: session.PN,
    MKSKIPPED: skipped,
  };
}

/** Десериализовать сессию из JSON */
export function deserializeSession(json) {
  const skipped = new Map();
  if (json.MKSKIPPED) {
    for (const [key, value] of json.MKSKIPPED) {
      skipped.set(key, {
        key: decodeBase64(value.key),
        nonce: decodeBase64(value.nonce),
        timestamp: value.timestamp,
      });
    }
  }

  return {
    DHs: {
      publicKey: decodeBase64(json.DHs.publicKey),
      secretKey: decodeBase64(json.DHs.secretKey),
    },
    DHr: json.DHr ? decodeBase64(json.DHr) : null,
    RK: decodeBase64(json.RK),
    CKs: json.CKs ? decodeBase64(json.CKs) : null,
    CKr: json.CKr ? decodeBase64(json.CKr) : null,
    Ns: json.Ns || 0,
    Nr: json.Nr || 0,
    PN: json.PN || 0,
    MKSKIPPED: skipped,
  };
}

// ═══════ Утилиты ═══════

function cloneSession(session) {
  // Глубокое клонирование — новые Uint8Array для иммутабельности
  const skipped = new Map();
  for (const [key, value] of session.MKSKIPPED) {
    skipped.set(key, {
      key: new Uint8Array(value.key),
      nonce: new Uint8Array(value.nonce),
      timestamp: value.timestamp,
    });
  }

  return {
    DHs: {
      publicKey: new Uint8Array(session.DHs.publicKey),
      secretKey: new Uint8Array(session.DHs.secretKey),
    },
    DHr: session.DHr ? new Uint8Array(session.DHr) : null,
    RK: new Uint8Array(session.RK),
    CKs: session.CKs ? new Uint8Array(session.CKs) : null,
    CKr: session.CKr ? new Uint8Array(session.CKr) : null,
    Ns: session.Ns,
    Nr: session.Nr,
    PN: session.PN,
    MKSKIPPED: skipped,
  };
}

// [HIGH-5] Constant-time comparison — нет timing oracle
function uint8ArrayEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}
