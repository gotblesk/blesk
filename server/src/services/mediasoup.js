const mediasoup = require('mediasoup');
const os = require('os');

// Worker'ов = ядра CPU, но не больше 4 (для локальной разработки)
const numWorkers = Math.min(os.cpus().length, parseInt(process.env.MEDIASOUP_WORKERS || '4', 10));
const workers = [];
let nextWorkerIdx = 0;

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
    console.error('CRITICAL: MEDIASOUP_ANNOUNCED_IP not set. Voice will not work for remote clients.');
    console.error('Set MEDIASOUP_ANNOUNCED_IP=<your-public-ip> in .env');
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
    const rtcMin = 10000 + i * 1000;
    const rtcMax = rtcMin + 999;

    const worker = await mediasoup.createWorker({
      logLevel: 'warn',
      rtcMinPort: rtcMin,
      rtcMaxPort: rtcMax,
    });

    worker.on('died', () => {
      console.error(`mediasoup Worker ${i} умер, перезапуск через 2 сек...`);
      setTimeout(async () => {
        const newWorker = await mediasoup.createWorker({
          logLevel: 'warn',
          rtcMinPort: rtcMin,
          rtcMaxPort: rtcMax,
        });
        workers[i] = newWorker;
        console.log(`mediasoup Worker ${i} перезапущен`);
        console.warn(`Worker ${i} restarted. Rooms on this worker need reconnection.`);
      }, 2000);
    });

    workers.push(worker);
    console.log(`mediasoup Worker ${i} запущен (pid: ${worker.pid})`);
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
};
