const logger = require('../utils/logger');

let scanner = null;
let scannerAvailable = false;

async function initScanner() {
  if (process.env.CLAMAV_ENABLED !== 'true') {
    logger.info('ClamAV отключён (CLAMAV_ENABLED != true)');
    return;
  }

  try {
    const NodeClam = require('clamscan');
    scanner = await new NodeClam().init({
      clamdscan: {
        socket: process.env.CLAMAV_SOCKET || '/var/run/clamav/clamd.ctl',
        timeout: 30000,
        localFallback: true,
      },
      preference: 'clamdscan',
    });
    scannerAvailable = true;
    logger.info('ClamAV сканер инициализирован');
  } catch (err) {
    logger.warn({ err: err.message }, 'ClamAV недоступен');
    logger.warn('Загрузка файлов будет работать без антивирусной проверки');
    scannerAvailable = false;
  }
}

async function scanFile(filePath) {
  if (!scannerAvailable || !scanner) return { clean: true, skipped: true };

  try {
    const { isInfected, viruses } = await scanner.isInfected(filePath);
    if (isInfected) {
      logger.error({ filePath, viruses }, 'ВИРУС ОБНАРУЖЕН');
      return { clean: false, viruses };
    }
    return { clean: true, skipped: false };
  } catch (err) {
    logger.error({ err: err.message }, 'Ошибка сканирования');
    return { clean: true, skipped: true };
  }
}

module.exports = { initScanner, scanFile };
