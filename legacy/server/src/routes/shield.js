/**
 * blesk Shield — серверные endpoints для prekey bundles и key transparency
 * Сервер НЕ расшифровывает ничего — только хранит и раздаёт публичные ключи
 */
const express = require('express');
const router = express.Router();
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');
const crypto = require('crypto');
const { emitToUser } = require('../utils/socketUtils');
const logger = require('../utils/logger');

// POST /api/shield/bundle — загрузить prekey bundle
router.post('/bundle', authenticate, async (req, res) => {
  try {
    const userId = req.userId;
    const { signingKey, signedPreKey, signedPreKeySig, signedPreKeyId, oneTimePreKeys } = req.body;

    // Валидация
    if (!signingKey || !signedPreKey || !signedPreKeySig || signedPreKeyId === undefined) {
      return res.status(400).json({ error: 'Неполные данные bundle' });
    }
    // [LOW-2] Ed25519 public key = 32 bytes → base64 = 44 chars
    if (typeof signingKey !== 'string' || signingKey.length !== 44) {
      return res.status(400).json({ error: 'Некорректный signing key' });
    }
    if (typeof signedPreKey !== 'string' || signedPreKey.length !== 44) {
      return res.status(400).json({ error: 'Некорректный signed prekey' });
    }

    // Обновить signing key в профиле
    await prisma.user.update({
      where: { id: userId },
      data: { signingKey },
    });

    // Upsert PreKeyBundle
    const bundle = await prisma.preKeyBundle.upsert({
      where: { userId },
      create: {
        userId,
        signingKey,
        signedPreKey,
        signedPreKeySig,
        signedPreKeyId,
      },
      update: {
        signingKey,
        signedPreKey,
        signedPreKeySig,
        signedPreKeyId,
      },
    });

    // Загрузить OPK если есть
    if (Array.isArray(oneTimePreKeys) && oneTimePreKeys.length > 0) {
      const opkData = oneTimePreKeys
        .filter(opk => opk && opk.id !== undefined && opk.key)
        .slice(0, 100) // Лимит 100 OPK за раз
        .map(opk => ({
          bundleId: bundle.id,
          keyId: opk.id,
          publicKey: opk.key,
        }));

      if (opkData.length > 0) {
        await prisma.oneTimePreKey.createMany({
          data: opkData,
          skipDuplicates: true,
        });
      }
    }

    // [CRIT-1] Key transparency log — подпись от клиента (если передана)
    const { keyLogSignature } = req.body;
    await appendKeyLog(userId, 'spk', signedPreKey, keyLogSignature || '');

    res.json({ ok: true, bundleId: bundle.id });
  } catch (err) {
    logger.error({ err }, 'shield:bundle error');
    res.status(500).json({ error: 'Ошибка загрузки bundle' });
  }
});

// GET /api/shield/bundle/:userId — получить bundle собеседника
router.get('/bundle/:userId', authenticate, async (req, res) => {
  try {
    const targetUserId = req.params.userId;

    const bundle = await prisma.preKeyBundle.findUnique({
      where: { userId: targetUserId },
    });

    if (!bundle) {
      return res.status(404).json({ error: 'Shield bundle не найден' });
    }

    // Получить identity key из профиля
    const user = await prisma.user.findUnique({
      where: { id: targetUserId },
      select: { publicKey: true, signingKey: true },
    });

    // [CRIT-3] Атомарно забрать один OPK через транзакцию (предотвращение reuse)
    let oneTimePreKey = null;
    try {
      const result = await prisma.$transaction(async (tx) => {
        const opk = await tx.oneTimePreKey.findFirst({
          where: { bundleId: bundle.id, used: false },
          orderBy: { keyId: 'asc' },
        });
        if (!opk) return null;

        // [S11] Пометить OPK как "в доставке" с временной меткой (deliveredAt)
        // used: true ставится сразу — клиент должен подтвердить получение через /confirm-opk
        await tx.oneTimePreKey.update({
          where: { id: opk.id },
          data: { used: true, deliveredAt: new Date() },
        });

        const remaining = await tx.oneTimePreKey.count({
          where: { bundleId: bundle.id, used: false },
        });

        return { opk, remaining };
      }, { isolationLevel: 'Serializable' });

      if (result) {
        oneTimePreKey = { id: result.opk.keyId, key: result.opk.publicKey };

        // Уведомить владельца если OPK мало
        if (result.remaining < 10) {
          emitToUser(targetUserId, 'shield:opk-low', { remaining: result.remaining });
        }
      }
    } catch (txErr) {
      // Transaction conflict — OPK уже занят другим запросом, продолжить без OPK
      logger.warn({ err: txErr.message }, 'OPK transaction conflict');
    }

    res.json({
      identityKey: user.publicKey,
      signingKey: bundle.signingKey,
      signedPreKey: bundle.signedPreKey,
      signedPreKeySig: bundle.signedPreKeySig,
      signedPreKeyId: bundle.signedPreKeyId,
      oneTimePreKey,
    });
  } catch (err) {
    logger.error({ err }, 'shield:getBundle error');
    res.status(500).json({ error: 'Ошибка получения bundle' });
  }
});

// POST /api/shield/replenish — пополнить OPK
router.post('/replenish', authenticate, async (req, res) => {
  try {
    const userId = req.userId;
    const { oneTimePreKeys } = req.body;

    if (!Array.isArray(oneTimePreKeys) || oneTimePreKeys.length === 0) {
      return res.status(400).json({ error: 'Пустой список OPK' });
    }

    const bundle = await prisma.preKeyBundle.findUnique({ where: { userId } });
    if (!bundle) {
      return res.status(404).json({ error: 'Bundle не найден — сначала загрузите bundle' });
    }

    const opkData = oneTimePreKeys
      .filter(opk => opk && opk.id !== undefined && opk.key)
      .slice(0, 100)
      .map(opk => ({
        bundleId: bundle.id,
        keyId: opk.id,
        publicKey: opk.key,
      }));

    if (opkData.length > 0) {
      await prisma.oneTimePreKey.createMany({
        data: opkData,
        skipDuplicates: true,
      });
    }

    res.json({ ok: true, added: opkData.length });
  } catch (err) {
    logger.error({ err }, 'shield:replenish error');
    res.status(500).json({ error: 'Ошибка пополнения OPK' });
  }
});

// GET /api/shield/opk-count — количество оставшихся OPK
router.get('/opk-count', authenticate, async (req, res) => {
  try {
    const bundle = await prisma.preKeyBundle.findUnique({ where: { userId: req.userId } });
    if (!bundle) return res.json({ count: 0 });

    const count = await prisma.oneTimePreKey.count({
      where: { bundleId: bundle.id, used: false },
    });
    res.json({ count });
  } catch (err) {
    logger.error({ err }, 'shield:opk-count error');
    res.status(500).json({ error: 'Ошибка подсчёта OPK' });
  }
});

// GET /api/shield/key-log/:userId — key transparency log
router.get('/key-log/:userId', authenticate, async (req, res) => {
  try {
    const logs = await prisma.keyTransparencyLog.findMany({
      where: { userId: req.params.userId },
      orderBy: { createdAt: 'asc' },
      take: 100,
    });
    res.json({ logs });
  } catch (err) {
    logger.error({ err }, 'shield:key-log error');
    res.status(500).json({ error: 'Ошибка загрузки key log' });
  }
});

// [CRIT-1] Утилита: добавить запись в key transparency log
// Подпись ДОЛЖНА приходить от клиента (Ed25519) — сервер не может подделать
async function appendKeyLog(userId, keyType, publicKey, clientSignature) {
  try {
    const lastEntry = await prisma.keyTransparencyLog.findFirst({
      where: { userId },
      orderBy: { createdAt: 'desc' },
    });

    const prevHash = lastEntry
      ? crypto.createHash('sha256').update(lastEntry.id + lastEntry.publicKey).digest('hex')
      : '0'.repeat(64);

    // Подпись передаётся клиентом — Ed25519 detached signature
    // Если клиент не предоставил подпись — сохранить без (для обратной совместимости)
    const signature = clientSignature || '';

    await prisma.keyTransparencyLog.create({
      data: {
        userId,
        keyType,
        publicKey,
        prevHash,
        signature,
      },
    });
  } catch (err) {
    logger.error({ err }, 'appendKeyLog error');
  }
}

// POST /api/shield/key-log — клиент загружает подписанную запись
router.post('/key-log', authenticate, async (req, res) => {
  try {
    const { keyType, publicKey, signature } = req.body;
    if (!keyType || !publicKey || !signature) {
      return res.status(400).json({ error: 'Неполные данные' });
    }
    await appendKeyLog(req.userId, keyType, publicKey, signature);
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'shield:key-log POST error');
    res.status(500).json({ error: 'Ошибка записи в key log' });
  }
});

module.exports = router;
