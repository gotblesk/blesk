const mediasoup = require('mediasoup');
const os = require('os');
const logger = require('../utils/logger');

// Worker'ов = ядра CPU, но не больше 4 (для локальной разработки)
const numWorkers = Math.min(os.cpus().length, parseInt(process.env.MEDIASOUP_WORKERS || '4', 10));
const workers = [];
let nextWorkerIdx = 0;
let onWorkerDied = null;

// Кодеки для голоса и видео
const mediaCodecs = [
  {
    kind: 'audio',
    mimeType: 'audio/opus',
    clockRate: 48000,
    channels: 2,
    parameters: {
      usedtx: 1, // Discontinuous Transmission — экономит трафик во время тишины
    },
  },
  {
    kind: 'video',
    mimeType: 'video/H264',
    clockRate: 90000,
    parameters: {
      'packetization-mode': 1,
      'profile-level-id': '42e01f',
      'level-asymmetry-allowed': 1,
    },
  },
  {
    kind: 'video',
    mimeType: 'video/VP8',
    clockRate: 90000,
    parameters: {},
  },
];

// Настройки WebRTC транспорта
function getTransportOptions() {
  const listenIp = process.env.MEDIASOUP_LISTEN_IP || '0.0.0.0';
  const announcedIp = process.env.MEDIASOUP_ANNOUNCED_IP || process.env.SERVER_IP || null;

  if (!announcedIp && process.env.NODE_ENV === 'production') {
    throw new Error('MEDIASOUP_ANNOUNCED_IP или SERVER_IP обязателен в production. Голос не будет работать без публичного IP.');
  }

  return {
    listenIps: [
      {
        ip: listenIp,
        announcedIp: announcedIp,
      },
    ],
    enableUdp: true,
    enableTcp: true,
    preferUdp: true,
    initialAvailableOutgoingBitrate: 2500000,
  };
}

// Создать Worker'ы при старте сервера
async function createWorkers() {
  for (let i = 0; i < numWorkers; i++) {
    const portsPerWorker = 5000;
    const rtcMin = 10000 + i * portsPerWorker;
    const rtcMax = rtcMin + portsPerWorker - 1;

    const worker = await mediasoup.createWorker({
      logLevel: 'warn',
      rtcMinPort: rtcMin,
      rtcMaxPort: rtcMax,
    });

    let restartAttempts = 0;
    worker.on('died', () => {
      restartAttempts++;
      const delay = Math.min(2000 * Math.pow(2, restartAttempts - 1), 30000);
      logger.error({ workerIdx: i, attempt: restartAttempts, nextRetryMs: delay }, 'mediasoup Worker умер');

      if (typeof onWorkerDied === 'function') {
        onWorkerDied(i, worker);
      }

      if (restartAttempts > 5) {
        logger.error({ workerIdx: i }, 'mediasoup Worker не удалось перезапустить после 5 попыток');
        return;
      }

      setTimeout(async () => {
        try {
          const newWorker = await mediasoup.createWorker({
            logLevel: 'warn',
            rtcMinPort: rtcMin,
            rtcMaxPort: rtcMax,
          });
          workers[i] = newWorker;
          restartAttempts = 0;
          logger.info({ workerIdx: i, pid: newWorker.pid }, 'mediasoup Worker перезапущен');
        } catch (err) {
          logger.error({ workerIdx: i, err }, 'Ошибка перезапуска Worker');
        }
      }, delay);
    });

    workers.push(worker);
    logger.info({ workerIdx: i, pid: worker.pid }, 'mediasoup Worker запущен');
  }
}

// Round-robin выбор Worker'а
function getNextWorker() {
  const worker = workers[nextWorkerIdx];
  nextWorkerIdx = (nextWorkerIdx + 1) % workers.length;
  return worker;
}

// Создать Router для голосовой комнаты
async function createRouter() {
  const worker = getNextWorker();
  return worker.createRouter({ mediaCodecs });
}

// Создать WebRTC транспорт
async function createWebRtcTransport(router) {
  const transport = await router.createWebRtcTransport(getTransportOptions());

  // Максимум 30 сек на подключение
  transport.on('dtlsstatechange', (state) => {
    if (state === 'failed' || state === 'closed') {
      transport.close();
    }
  });

  return {
    transport,
    params: {
      id: transport.id,
      iceParameters: transport.iceParameters,
      iceCandidates: transport.iceCandidates,
      dtlsParameters: transport.dtlsParameters,
    },
  };
}

module.exports = {
  createWorkers,
  createRouter,
  createWebRtcTransport,
  mediaCodecs,
  setOnWorkerDied: (fn) => { onWorkerDied = fn; },
};
