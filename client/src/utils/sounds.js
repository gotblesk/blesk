// blesk Sound Engine v2 — Premium синтезированные звуки (Apple-уровень)
// Реверб, обертоны, ADSR envelope, мягкие затухания

let audioCtx = null;
let reverbBuffer = null;
let ringtoneInterval = null;

function getCtx() {
  if (!audioCtx || audioCtx.state === 'closed') {
    audioCtx = new AudioContext();
    createReverbBuffer(); // подготовить реверб
  }
  if (audioCtx.state === 'suspended') audioCtx.resume();
  return audioCtx;
}

// Генерация импульса реверба (программный, без файлов)
function createReverbBuffer() {
  const ctx = getCtx();
  const rate = ctx.sampleRate;
  const length = rate * 1.2; // 1.2 секунды реверба
  const buffer = ctx.createBuffer(2, length, rate);
  for (let ch = 0; ch < 2; ch++) {
    const data = buffer.getChannelData(ch);
    for (let i = 0; i < length; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, 2.5);
    }
  }
  reverbBuffer = buffer;
}

// Громкость из настроек
function getVolume() {
  try {
    const s = JSON.parse(localStorage.getItem('blesk-settings') || '{}');
    return s.sounds === false ? 0 : 0.25;
  } catch { return 0.25; }
}

// ═══ Premium tone generator с обертонами + реверб ═══

function createNote(freq, duration, {
  type = 'sine',
  volume = null,
  attack = 0.01,
  decay = 0.1,
  sustain = 0.6,
  release = 0,
  reverb = 0.3,
  harmonics = [1],
  harmonicVolumes = [1],
  detune = 0,
} = {}) {
  const vol = volume ?? getVolume();
  if (vol === 0) return;
  const ctx = getCtx();
  const t = ctx.currentTime;
  const masterGain = ctx.createGain();

  // Реверб (convolver)
  let output = masterGain;
  if (reverb > 0 && reverbBuffer) {
    const dry = ctx.createGain();
    const wet = ctx.createGain();
    const convolver = ctx.createConvolver();
    convolver.buffer = reverbBuffer;
    dry.gain.value = 1 - reverb;
    wet.gain.value = reverb;
    masterGain.connect(dry);
    masterGain.connect(convolver);
    convolver.connect(wet);
    dry.connect(ctx.destination);
    wet.connect(ctx.destination);
  } else {
    masterGain.connect(ctx.destination);
  }

  // Обертоны — несколько осцилляторов на разных гармониках
  harmonics.forEach((h, i) => {
    const osc = ctx.createOscillator();
    const oscGain = ctx.createGain();
    osc.type = type;
    osc.frequency.value = freq * h;
    osc.detune.value = detune * (i % 2 === 0 ? 1 : -1);

    const hVol = (harmonicVolumes[i] ?? 0.3) * vol;

    // ADSR envelope
    oscGain.gain.setValueAtTime(0, t);
    oscGain.gain.linearRampToValueAtTime(hVol, t + attack); // Attack
    oscGain.gain.linearRampToValueAtTime(hVol * sustain, t + attack + decay); // Decay → Sustain
    const releaseStart = t + duration - release;
    if (release > 0) {
      oscGain.gain.setValueAtTime(hVol * sustain, releaseStart);
      oscGain.gain.exponentialRampToValueAtTime(0.001, t + duration); // Release
    } else {
      oscGain.gain.exponentialRampToValueAtTime(0.001, t + duration);
    }

    osc.connect(oscGain);
    oscGain.connect(masterGain);
    osc.start(t);
    osc.stop(t + duration + 0.05);
  });
}

// ═══ Звуки интерфейса — Apple-уровень ═══

// Уведомление — tri-tone как iOS (до-ми-соль с обертонами)
export function soundNotification(hue = 0) {
  const base = 784 + (hue / 360) * 150; // G5 range привязан к hue
  const opts = { harmonics: [1, 2, 3], harmonicVolumes: [1, 0.3, 0.1], reverb: 0.4, attack: 0.005, decay: 0.15, sustain: 0.4, detune: 3 };
  createNote(base, 0.35, opts);
  setTimeout(() => createNote(base * 1.25, 0.3, opts), 120);
  setTimeout(() => createNote(base * 1.5, 0.4, { ...opts, reverb: 0.5 }), 240);
}

// Отправка сообщения — мягкий восходящий "вуп"
export function soundSend() {
  const vol = getVolume();
  if (vol === 0) return;
  const ctx = getCtx();
  const t = ctx.currentTime;

  // Основной тон (sweep вверх)
  const osc1 = ctx.createOscillator();
  const osc2 = ctx.createOscillator(); // обертон
  const gain = ctx.createGain();
  const filter = ctx.createBiquadFilter();

  osc1.type = 'sine';
  osc1.frequency.setValueAtTime(500, t);
  osc1.frequency.exponentialRampToValueAtTime(1100, t + 0.1);

  osc2.type = 'sine';
  osc2.frequency.setValueAtTime(1000, t);
  osc2.frequency.exponentialRampToValueAtTime(2200, t + 0.1);

  filter.type = 'lowpass';
  filter.frequency.value = 3000;

  gain.gain.setValueAtTime(0, t);
  gain.gain.linearRampToValueAtTime(vol * 0.35, t + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.001, t + 0.18);

  const oscGain2 = ctx.createGain();
  oscGain2.gain.value = 0.15;

  osc1.connect(gain);
  osc2.connect(oscGain2);
  oscGain2.connect(gain);
  gain.connect(filter);
  filter.connect(ctx.destination);

  osc1.start(t);
  osc2.start(t);
  osc1.stop(t + 0.2);
  osc2.stop(t + 0.2);
}

// Входящее сообщение — мягкий "плинк" с реверберацией
export function soundReceive() {
  createNote(1175, 0.25, {
    harmonics: [1, 2, 4], harmonicVolumes: [1, 0.25, 0.08],
    reverb: 0.45, attack: 0.003, decay: 0.08, sustain: 0.3, detune: 2,
  });
  setTimeout(() => createNote(1397, 0.2, {
    harmonics: [1, 2], harmonicVolumes: [0.7, 0.15],
    reverb: 0.5, attack: 0.003, decay: 0.06, sustain: 0.2,
  }), 80);
}

// ═══ Входящий звонок — зацикленная мелодия (как iPhone) ═══
let ringtoneOscs = [];

function playRingtonePhrase() {
  const vol = getVolume();
  if (vol === 0) return;

  // Мелодия: мягкие колокольчики
  const melody = [
    { f: 1047, t: 0 },     // C6
    { f: 1175, t: 150 },   // D6
    { f: 1319, t: 300 },   // E6
    { f: 1175, t: 500 },   // D6
    { f: 1047, t: 650 },   // C6
    { f: 1175, t: 850 },   // D6
    { f: 1319, t: 1000 },  // E6
    { f: 1568, t: 1200 },  // G6
  ];

  const opts = {
    harmonics: [1, 2, 3, 5], harmonicVolumes: [1, 0.3, 0.12, 0.05],
    reverb: 0.5, attack: 0.003, decay: 0.12, sustain: 0.35, detune: 2,
    volume: vol * 0.6,
  };

  melody.forEach(({ f, t: delay }) => {
    setTimeout(() => createNote(f, 0.35, opts), delay);
  });
}

export function soundRingtoneStart() {
  soundRingtoneStop(); // остановить предыдущий если был
  playRingtonePhrase();
  ringtoneInterval = setInterval(playRingtonePhrase, 3000);
}

export function soundRingtoneStop() {
  if (ringtoneInterval) {
    clearInterval(ringtoneInterval);
    ringtoneInterval = null;
  }
}

// Обратная совместимость
export function soundRingtone() {
  playRingtonePhrase();
}

// Подключение к голосовой — восходящий аккорд с реверберацией
export function soundVoiceJoin() {
  const opts = { harmonics: [1, 2, 3], harmonicVolumes: [1, 0.2, 0.08], reverb: 0.5, attack: 0.01, decay: 0.2, sustain: 0.5, detune: 2 };
  createNote(523, 0.5, opts);
  setTimeout(() => createNote(659, 0.45, opts), 80);
  setTimeout(() => createNote(784, 0.5, { ...opts, reverb: 0.6 }), 160);
}

// Отключение от голосовой — нисходящий мягкий
export function soundVoiceLeave() {
  const opts = { harmonics: [1, 2], harmonicVolumes: [1, 0.2], reverb: 0.4, attack: 0.01, decay: 0.15, sustain: 0.4 };
  createNote(784, 0.35, opts);
  setTimeout(() => createNote(659, 0.3, opts), 80);
  setTimeout(() => createNote(523, 0.4, { ...opts, reverb: 0.5 }), 160);
}

// Кто-то зашёл в голосовую
export function soundUserJoined() {
  createNote(880, 0.25, {
    harmonics: [1, 2, 3], harmonicVolumes: [1, 0.2, 0.06],
    reverb: 0.35, attack: 0.005, decay: 0.1, sustain: 0.4,
  });
}

// Кто-то вышел
export function soundUserLeft() {
  createNote(523, 0.3, {
    harmonics: [1, 2], harmonicVolumes: [1, 0.15],
    reverb: 0.35, attack: 0.005, decay: 0.15, sustain: 0.3,
  });
}

// Мут
export function soundMute() {
  createNote(440, 0.12, { harmonics: [1, 3], harmonicVolumes: [1, 0.1], reverb: 0.2, attack: 0.003, decay: 0.05, sustain: 0.3 });
}

// Анмут
export function soundUnmute() {
  createNote(660, 0.12, { harmonics: [1, 3], harmonicVolumes: [1, 0.1], reverb: 0.2, attack: 0.003, decay: 0.05, sustain: 0.3 });
}

// Ошибка — мягкий низкий дабл-тон
export function soundError() {
  const opts = { harmonics: [1, 2], harmonicVolumes: [1, 0.3], reverb: 0.3, attack: 0.005, decay: 0.15, sustain: 0.4 };
  createNote(280, 0.3, opts);
  setTimeout(() => createNote(220, 0.35, opts), 150);
}

// Успех — восходящая мелодия
export function soundSuccess() {
  const opts = { harmonics: [1, 2, 3], harmonicVolumes: [1, 0.2, 0.06], reverb: 0.45, attack: 0.005, decay: 0.1, sustain: 0.5 };
  createNote(523, 0.25, opts);
  setTimeout(() => createNote(659, 0.2, opts), 100);
  setTimeout(() => createNote(784, 0.2, opts), 200);
  setTimeout(() => createNote(1047, 0.4, { ...opts, reverb: 0.55 }), 300);
}

// Клик мыши — тактильный микро-тик с обертоном
export function soundClick() {
  createNote(2200, 0.04, {
    harmonics: [1, 2.5], harmonicVolumes: [0.3, 0.08],
    reverb: 0.1, attack: 0.001, decay: 0.02, sustain: 0.2,
  });
}

// Ховер мыши — ультра-мягкий
export function soundHover() {
  createNote(3200, 0.025, {
    harmonics: [1], harmonicVolumes: [0.1],
    reverb: 0.05, attack: 0.001, decay: 0.01, sustain: 0.1,
  });
}

// Переключение таба — мягкий свуп
export function soundTabSwitch() {
  const vol = getVolume();
  if (vol === 0) return;
  const ctx = getCtx();
  const t = ctx.currentTime;
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  const filter = ctx.createBiquadFilter();

  osc.type = 'sine';
  osc.frequency.setValueAtTime(700, t);
  osc.frequency.exponentialRampToValueAtTime(1000, t + 0.06);

  filter.type = 'lowpass';
  filter.frequency.value = 2500;

  gain.gain.setValueAtTime(0, t);
  gain.gain.linearRampToValueAtTime(vol * 0.12, t + 0.005);
  gain.gain.exponentialRampToValueAtTime(0.001, t + 0.1);

  osc.connect(filter);
  filter.connect(gain);
  gain.connect(ctx.destination);
  osc.start(t);
  osc.stop(t + 0.12);
}

// Открытие окна — мягкий "pop" с реверберацией
export function soundWindowOpen() {
  createNote(698, 0.2, {
    harmonics: [1, 2, 3], harmonicVolumes: [0.5, 0.15, 0.05],
    reverb: 0.35, attack: 0.003, decay: 0.08, sustain: 0.4,
  });
  setTimeout(() => createNote(880, 0.18, {
    harmonics: [1, 2], harmonicVolumes: [0.4, 0.1],
    reverb: 0.4, attack: 0.003, decay: 0.06, sustain: 0.3,
  }), 60);
}

// Закрытие окна — нисходящий мягкий "whomp"
export function soundWindowClose() {
  createNote(880, 0.15, {
    harmonics: [1, 2], harmonicVolumes: [0.4, 0.1],
    reverb: 0.3, attack: 0.003, decay: 0.06, sustain: 0.3,
  });
  setTimeout(() => createNote(600, 0.2, {
    harmonics: [1, 2], harmonicVolumes: [0.35, 0.08],
    reverb: 0.35, attack: 0.003, decay: 0.1, sustain: 0.2,
  }), 50);
}

// Звонок принят
export function soundCallAccepted() {
  const opts = { harmonics: [1, 2, 3], harmonicVolumes: [1, 0.2, 0.06], reverb: 0.4, attack: 0.005, decay: 0.1, sustain: 0.5 };
  createNote(659, 0.2, opts);
  setTimeout(() => createNote(784, 0.2, opts), 80);
  setTimeout(() => createNote(1047, 0.35, { ...opts, reverb: 0.5 }), 160);
}

// Звонок завершён
export function soundCallEnded() {
  const opts = { harmonics: [1, 2], harmonicVolumes: [1, 0.15], reverb: 0.35, attack: 0.005, decay: 0.12, sustain: 0.3 };
  createNote(784, 0.2, opts);
  setTimeout(() => createNote(523, 0.35, opts), 120);
}

// Звонок отклонён
export function soundCallDeclined() {
  createNote(440, 0.25, {
    harmonics: [1, 2], harmonicVolumes: [1, 0.2],
    reverb: 0.3, attack: 0.005, decay: 0.15, sustain: 0.3,
  });
}
