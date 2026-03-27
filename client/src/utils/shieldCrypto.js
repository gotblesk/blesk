/**
 * blesk Shield — криптографические примитивы
 * HMAC-SHA-512, HKDF, X3DH, DH обёртки
 * Зависимости: ТОЛЬКО tweetnacl (nacl.box, nacl.sign, nacl.hash, nacl.randomBytes, nacl.secretbox)
 */
import nacl from 'tweetnacl';
import { encodeBase64, decodeBase64, encodeUTF8, decodeUTF8 } from 'tweetnacl-util';

// ═══════ HMAC-SHA-512 (из nacl.hash) ═══════

const HMAC_BLOCK_SIZE = 128; // SHA-512 block size
const HASH_OUTPUT_SIZE = 64; // SHA-512 output size

/**
 * HMAC-SHA-512 построенный на nacl.hash (SHA-512)
 * HMAC(K, m) = H((K' ⊕ opad) || H((K' ⊕ ipad) || m))
 */
export function hmacSha512(key, data) {
  // Если ключ длиннее блока — хешируем
  let keyBlock = key;
  if (keyBlock.length > HMAC_BLOCK_SIZE) {
    keyBlock = nacl.hash(keyBlock);
  }
  // Дополняем до размера блока нулями
  if (keyBlock.length < HMAC_BLOCK_SIZE) {
    const padded = new Uint8Array(HMAC_BLOCK_SIZE);
    padded.set(keyBlock);
    keyBlock = padded;
  }

  const ipad = new Uint8Array(HMAC_BLOCK_SIZE);
  const opad = new Uint8Array(HMAC_BLOCK_SIZE);
  for (let i = 0; i < HMAC_BLOCK_SIZE; i++) {
    ipad[i] = keyBlock[i] ^ 0x36;
    opad[i] = keyBlock[i] ^ 0x5c;
  }

  // H((K' ⊕ ipad) || m)
  const inner = new Uint8Array(HMAC_BLOCK_SIZE + data.length);
  inner.set(ipad);
  inner.set(data, HMAC_BLOCK_SIZE);
  const innerHash = nacl.hash(inner);

  // H((K' ⊕ opad) || innerHash)
  const outer = new Uint8Array(HMAC_BLOCK_SIZE + HASH_OUTPUT_SIZE);
  outer.set(opad);
  outer.set(innerHash, HMAC_BLOCK_SIZE);

  // [LOW-4] Обнулить ВСЕ промежуточные данные
  inner.fill(0);
  ipad.fill(0);
  opad.fill(0);

  const result = nacl.hash(outer);
  outer.fill(0);
  innerHash.fill(0);
  return result;
}

// ═══════ HKDF (RFC 5869) ═══════

/** HKDF-Extract: PRK = HMAC-Hash(salt, IKM) */
export function hkdfExtract(salt, ikm) {
  const s = salt.length > 0 ? salt : new Uint8Array(HASH_OUTPUT_SIZE);
  return hmacSha512(s, ikm);
}

/** HKDF-Expand: OKM = T(1) || T(2) || ... (усечённая до length) */
export function hkdfExpand(prk, info, length) {
  const n = Math.ceil(length / HASH_OUTPUT_SIZE);
  const okm = new Uint8Array(n * HASH_OUTPUT_SIZE);
  let prev = new Uint8Array(0);

  for (let i = 1; i <= n; i++) {
    const input = new Uint8Array(prev.length + info.length + 1);
    input.set(prev);
    input.set(info, prev.length);
    input[prev.length + info.length] = i;
    prev = hmacSha512(prk, input);
    okm.set(prev, (i - 1) * HASH_OUTPUT_SIZE);
  }

  return okm.slice(0, length);
}

/** HKDF полный: Extract + Expand */
export function hkdf(salt, ikm, info, length) {
  const prk = hkdfExtract(salt, ikm);
  const result = hkdfExpand(prk, info, length);
  // [LOW-3] Обнулить PRK после использования
  prk.fill(0);
  return result;
}

// ═══════ KDF для Double Ratchet ═══════

const INFO_RK = decodeUTF8('blesk-shield-rk');
const INFO_X3DH = decodeUTF8('blesk-shield-x3dh');

/**
 * KDF_RK: Вывести новый root key + chain key из DH output
 * @returns {{ rootKey: Uint8Array(32), chainKey: Uint8Array(32) }}
 */
export function deriveRootAndChainKey(rootKey, dhOutput) {
  const derived = hkdf(rootKey, dhOutput, INFO_RK, 64);
  return {
    rootKey: derived.slice(0, 32),
    chainKey: derived.slice(32, 64),
  };
}

/**
 * KDF_CK: Вывести message key + новый chain key + nonce из chain key
 * @returns {{ chainKey: Uint8Array(32), messageKey: Uint8Array(32), nonce: Uint8Array(24) }}
 */
export function deriveMessageKey(chainKey) {
  const ckNext = hmacSha512(chainKey, new Uint8Array([0x01])).slice(0, 32);
  const mk = hmacSha512(chainKey, new Uint8Array([0x02])).slice(0, 32);
  const iv = hmacSha512(chainKey, new Uint8Array([0x03])).slice(0, 24);
  return { chainKey: ckNext, messageKey: mk, nonce: iv };
}

// ═══════ Симметричное шифрование (nacl.secretbox) ═══════

/** Зашифровать plaintext с message key + nonce */
export function symmetricEncrypt(plaintext, messageKey, nonce) {
  return nacl.secretbox(plaintext, nonce, messageKey);
}

/** Расшифровать ciphertext с message key + nonce */
export function symmetricDecrypt(ciphertext, messageKey, nonce) {
  return nacl.secretbox.open(ciphertext, nonce, messageKey);
}

// ═══════ DH операции ═══════

/** Генерировать эфемерную пару ключей X25519 */
export function generateKeyPair() {
  return nacl.box.keyPair();
}

/** X25519 Diffie-Hellman: вычислить shared secret */
export function dh(ourSecret, theirPublic) {
  return nacl.box.before(theirPublic, ourSecret);
}

/** Генерировать пару ключей Ed25519 (для подписей) */
export function generateSigningKeyPair() {
  return nacl.sign.keyPair();
}

/** Подписать данные Ed25519 (detached) */
export function sign(signingSecretKey, data) {
  return nacl.sign.detached(data, signingSecretKey);
}

/** Проверить подпись Ed25519 (detached) */
export function verify(signingPublicKey, data, signature) {
  return nacl.sign.detached.verify(data, signature, signingPublicKey);
}

// ═══════ X3DH (Extended Triple Diffie-Hellman) ═══════

/**
 * Подписать Signed PreKey
 * @param {Uint8Array} signingSecretKey — Ed25519 secret key (64 bytes)
 * @param {Uint8Array} preKeyPublic — X25519 public key (32 bytes)
 * @returns {Uint8Array} signature (64 bytes)
 */
export function signPreKey(signingSecretKey, preKeyPublic) {
  return sign(signingSecretKey, preKeyPublic);
}

/** Проверить подпись Signed PreKey */
export function verifyPreKeySignature(signingPublicKey, preKeyPublic, signature) {
  return verify(signingPublicKey, preKeyPublic, signature);
}

/**
 * Генерировать prekey bundle для загрузки на сервер
 * @param {Object} identityKeyPair — {publicKey, secretKey} X25519
 * @param {Object} signingKeyPair — {publicKey, secretKey} Ed25519
 * @param {number} spkId — ID signed prekey
 * @param {number} opkStartId — начальный ID для one-time prekeys
 * @param {number} opkCount — количество OPK
 */
export function generatePreKeyBundle(identityKeyPair, signingKeyPair, spkId, opkStartId, opkCount) {
  // Signed PreKey
  const spkPair = generateKeyPair();
  const spkSig = signPreKey(signingKeyPair.secretKey, spkPair.publicKey);

  // One-Time PreKeys
  const oneTimePreKeys = [];
  const oneTimePreKeyPairs = []; // секретные ключи хранить локально
  for (let i = 0; i < opkCount; i++) {
    const opkPair = generateKeyPair();
    oneTimePreKeys.push({
      id: opkStartId + i,
      key: encodeBase64(opkPair.publicKey),
    });
    oneTimePreKeyPairs.push({
      id: opkStartId + i,
      secretKey: encodeBase64(opkPair.secretKey),
      publicKey: encodeBase64(opkPair.publicKey),
    });
  }

  return {
    bundle: {
      identityKey: encodeBase64(identityKeyPair.publicKey),
      signingKey: encodeBase64(signingKeyPair.publicKey),
      signedPreKey: encodeBase64(spkPair.publicKey),
      signedPreKeySig: encodeBase64(spkSig),
      signedPreKeyId: spkId,
      oneTimePreKeys,
    },
    // Секретные данные для локального хранения
    secrets: {
      spkSecretKey: encodeBase64(spkPair.secretKey),
      spkPublicKey: encodeBase64(spkPair.publicKey),
      spkId,
      oneTimePreKeyPairs,
    },
  };
}

/**
 * X3DH — сторона инициатора (Alice)
 * Alice хочет отправить первое сообщение Bob'у
 * @param {Uint8Array} aliceIdentitySecret — Alice's X25519 secret key
 * @param {Object} bobBundle — { identityKey, signingKey, signedPreKey, signedPreKeySig, signedPreKeyId, oneTimePreKey? }
 * @returns {{ sharedSecret: Uint8Array(32), ephemeralKey: string, opkId: number|null, spkId: number }}
 */
export function x3dhInitiator(aliceIdentitySecret, aliceIdentityPublic, bobBundle) {
  const bobIK = decodeBase64(bobBundle.identityKey);
  const bobSPK = decodeBase64(bobBundle.signedPreKey);
  const bobSigningKey = decodeBase64(bobBundle.signingKey);
  const bobSPKSig = decodeBase64(bobBundle.signedPreKeySig);

  // Проверить подпись SPK
  if (!verifyPreKeySignature(bobSigningKey, bobSPK, bobSPKSig)) {
    throw new Error('Shield: недействительная подпись signed prekey');
  }

  // Генерировать эфемерный ключ
  const ephemeral = generateKeyPair();

  // 3 обязательных DH
  const dh1 = dh(aliceIdentitySecret, bobSPK);    // IK_a × SPK_b
  const dh2 = dh(ephemeral.secretKey, bobIK);       // EK_a × IK_b
  const dh3 = dh(ephemeral.secretKey, bobSPK);      // EK_a × SPK_b

  // 4-й DH с one-time prekey (если есть)
  let dh4 = null;
  let opkId = null;
  if (bobBundle.oneTimePreKey) {
    const bobOPK = decodeBase64(bobBundle.oneTimePreKey.key);
    dh4 = dh(ephemeral.secretKey, bobOPK);
    opkId = bobBundle.oneTimePreKey.id;
  }

  // Конкатенировать DH результаты
  const dhConcat = new Uint8Array(32 * (dh4 ? 4 : 3));
  dhConcat.set(dh1, 0);
  dhConcat.set(dh2, 32);
  dhConcat.set(dh3, 64);
  if (dh4) dhConcat.set(dh4, 96);

  // Вывести shared secret через HKDF
  const salt = new Uint8Array(64); // zeros
  const sharedSecret = hkdf(salt, dhConcat, INFO_X3DH, 32);

  // Обнулить промежуточные ключи
  dh1.fill(0);
  dh2.fill(0);
  dh3.fill(0);
  if (dh4) dh4.fill(0);
  dhConcat.fill(0);
  ephemeral.secretKey.fill(0);

  return {
    sharedSecret,
    ephemeralKey: encodeBase64(ephemeral.publicKey),
    opkId,
    spkId: bobBundle.signedPreKeyId,
  };
}

/**
 * X3DH — сторона ответчика (Bob)
 * Bob получает первое сообщение от Alice
 * @param {Uint8Array} bobIdentitySecret — Bob's X25519 secret key
 * @param {Uint8Array} bobSPKSecret — Bob's signed prekey secret
 * @param {Uint8Array|null} bobOPKSecret — Bob's one-time prekey secret (если использовался)
 * @param {Object} header — { identityKey, ephemeralKey, opkId, spkId }
 * @returns {Uint8Array(32)} sharedSecret
 */
export function x3dhResponder(bobIdentitySecret, bobIdentityPublic, bobSPKSecret, bobOPKSecret, header) {
  const aliceIK = decodeBase64(header.identityKey);
  const aliceEK = decodeBase64(header.ephemeralKey);

  // 3 обязательных DH (зеркально от инициатора)
  const dh1 = dh(bobSPKSecret, aliceIK);     // SPK_b × IK_a
  const dh2 = dh(bobIdentitySecret, aliceEK); // IK_b × EK_a
  const dh3 = dh(bobSPKSecret, aliceEK);      // SPK_b × EK_a

  let dh4 = null;
  if (bobOPKSecret) {
    dh4 = dh(bobOPKSecret, aliceEK);            // OPK_b × EK_a
  }

  const dhConcat = new Uint8Array(32 * (dh4 ? 4 : 3));
  dhConcat.set(dh1, 0);
  dhConcat.set(dh2, 32);
  dhConcat.set(dh3, 64);
  if (dh4) dhConcat.set(dh4, 96);

  const salt = new Uint8Array(64);
  const sharedSecret = hkdf(salt, dhConcat, INFO_X3DH, 32);

  // Обнулить
  dh1.fill(0);
  dh2.fill(0);
  dh3.fill(0);
  if (dh4) dh4.fill(0);
  dhConcat.fill(0);

  return sharedSecret;
}

// ═══════ Утилиты ═══════

/** Конкатенировать Uint8Array */
export function concat(...arrays) {
  const total = arrays.reduce((s, a) => s + a.length, 0);
  const result = new Uint8Array(total);
  let offset = 0;
  for (const arr of arrays) {
    result.set(arr, offset);
    offset += arr.length;
  }
  return result;
}

/** Генерировать визуальный fingerprint (48 байт → 16 цветов для 4x4 grid) */
export function generateVisualFingerprint(ikA, ikB) {
  // Сортировать ключи лексикографически для одинакового результата у обоих
  const keyA = typeof ikA === 'string' ? decodeBase64(ikA) : ikA;
  const keyB = typeof ikB === 'string' ? decodeBase64(ikB) : ikB;

  let first, second;
  for (let i = 0; i < 32; i++) {
    if (keyA[i] < keyB[i]) { first = keyA; second = keyB; break; }
    if (keyA[i] > keyB[i]) { first = keyB; second = keyA; break; }
  }
  if (!first) { first = keyA; second = keyB; }

  const combined = concat(first, second);
  const fpInfo = decodeUTF8('blesk-shield-visual-fp');
  // [MED-1] 64 байта → 16 ячеек x 4 байта (H, S, L, phase) — независимые
  const fpBytes = hkdf(new Uint8Array(64), combined, fpInfo, 64);

  const cells = [];
  for (let i = 0; i < 16; i++) {
    cells.push({
      hue: Math.round((fpBytes[i * 4] / 255) * 360),
      saturation: 50 + Math.round((fpBytes[i * 4 + 1] / 255) * 40),   // 50-90%
      lightness: 40 + Math.round((fpBytes[i * 4 + 2] / 255) * 20),     // 40-60%
      phase: (fpBytes[i * 4 + 3] / 255) * Math.PI * 2,
    });
  }

  return cells;
}

// Re-export для удобства
export { encodeBase64, decodeBase64, encodeUTF8, decodeUTF8 };
export { nacl };
