let scanner = null;
let scannerAvailable = false;

async function initScanner() {
  if (process.env.CLAMAV_ENABLED !== 'true') {
    console.log('ClamAV отключён (CLAMAV_ENABLED != true)');
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
    console.log('ClamAV сканер инициализирован');
  } catch (err) {
    console.warn('ClamAV недоступен:', err.message);
    console.warn('Загрузка файлов будет работать без антивирусной проверки');
    scannerAvailable = false;
  }
}

async function scanFile(filePath) {
  if (!scannerAvailable || !scanner) return { clean: true, skipped: true };

  try {
    const { isInfected, viruses } = await scanner.isInfected(filePath);
    if (isInfected) {
      console.error(`ВИРУС ОБНАРУЖЕН в ${filePath}: ${viruses.join(', ')}`);
      return { clean: false, viruses };
    }
    return { clean: true, skipped: false };
  } catch (err) {
    console.error('Ошибка сканирования:', err.message);
    return { clean: true, skipped: true };
  }
}

module.exports = { initScanner, scanFile };
