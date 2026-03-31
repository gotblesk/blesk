// blesk Sound Engine v3 — Premium soft sounds (Apple Sonoma level)
// Warm reverb, gentle envelopes, crystalline but never harsh

let audioCtx = null;
let reverbBuffer = null;
let ringtoneInterval = null;

function getCtx() {
  if (!audioCtx || audioCtx.state === 'closed') {
    audioCtx = new AudioContext();
    createReverbBuffer();
  }
  if (audioCtx.state === 'suspended') audioCtx.resume();
  return audioCtx;
}

// Warm impulse reverb — longer tail, smoother decay
function createReverbBuffer() {
  if (!audioCtx) return;
  const ctx = audioCtx;
  const rate = ctx.sampleRate;
  const length = rate * 1.8;
  const buffer = ctx.createBuffer(2, length, rate);
  for (let ch = 0; ch < 2; ch++) {
    const data = buffer.getChannelData(ch);
    for (let i = 0; i < length; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, 3.0);
    }
  }
  reverbBuffer = buffer;
}

function getVolume() {
  try {
    const s = JSON.parse(localStorage.getItem('blesk-settings') || '{}');
    return s.sounds === false ? 0 : 0.18;
  } catch { return 0.18; }
}

// ═══ Premium tone generator ═══

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

  // Convolution reverb
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

  harmonics.forEach((h, i) => {
    const osc = ctx.createOscillator();
    const oscGain = ctx.createGain();
    osc.type = type;
    osc.frequency.value = freq * h;
    osc.detune.value = detune * (i % 2 === 0 ? 1 : -1);

    const hVol = (harmonicVolumes[i] ?? 0.3) * vol;

    // ADSR envelope
    oscGain.gain.setValueAtTime(0, t);
    oscGain.gain.linearRampToValueAtTime(hVol, t + attack);
    oscGain.gain.linearRampToValueAtTime(hVol * sustain, t + attack + decay);
    const releaseStart = t + duration - release;
    if (release > 0) {
      oscGain.gain.setValueAtTime(hVol * sustain, releaseStart);
      oscGain.gain.exponentialRampToValueAtTime(0.001, t + duration);
    } else {
      oscGain.gain.exponentialRampToValueAtTime(0.001, t + duration);
    }

    osc.connect(oscGain);
    oscGain.connect(masterGain);
    osc.start(t);
    osc.stop(t + duration + 0.05);
  });
}

// ═══ Interface sounds ═══

// Notification — warm tri-tone with extra harmonic for depth
export function soundNotification(hue = 0) {
  const base = 660 + (hue / 360) * 120;
  const opts = {
    harmonics: [1, 2, 3, 4], harmonicVolumes: [1, 0.25, 0.08, 0.03],
    reverb: 0.55, attack: 0.008, decay: 0.15, sustain: 0.4, detune: 3,
  };
  createNote(base, 0.35, opts);
  setTimeout(() => createNote(base * 1.25, 0.3, opts), 120);
  setTimeout(() => createNote(base * 1.5, 0.4, { ...opts, reverb: 0.6 }), 240);
}

// Send message — gentle ascending sweep
export function soundSend() {
  const vol = getVolume();
  if (vol === 0) return;
  const ctx = getCtx();
  const t = ctx.currentTime;

  const osc1 = ctx.createOscillator();
  const osc2 = ctx.createOscillator();
  const gain = ctx.createGain();
  const filter = ctx.createBiquadFilter();

  osc1.type = 'sine';
  osc1.frequency.setValueAtTime(420, t);
  osc1.frequency.exponentialRampToValueAtTime(900, t + 0.12);

  osc2.type = 'sine';
  osc2.frequency.setValueAtTime(840, t);
  osc2.frequency.exponentialRampToValueAtTime(1800, t + 0.12);

  filter.type = 'lowpass';
  filter.frequency.value = 2200;

  gain.gain.setValueAtTime(0, t);
  gain.gain.linearRampToValueAtTime(vol * 0.25, t + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.001, t + 0.22);

  const oscGain2 = ctx.createGain();
  oscGain2.gain.value = 0.12;

  osc1.connect(gain);
  osc2.connect(oscGain2);
  oscGain2.connect(gain);
  gain.connect(filter);
  filter.connect(ctx.destination);

  osc1.start(t);
  osc2.start(t);
  osc1.stop(t + 0.25);
  osc2.stop(t + 0.25);
}

// Receive message — soft plink with warm reverb
export function soundReceive() {
  createNote(980, 0.25, {
    harmonics: [1, 2, 4], harmonicVolumes: [1, 0.25, 0.08],
    reverb: 0.55, attack: 0.003, decay: 0.08, sustain: 0.3, detune: 2,
  });
  setTimeout(() => createNote(1175, 0.2, {
    harmonics: [1, 2], harmonicVolumes: [0.7, 0.15],
    reverb: 0.55, attack: 0.003, decay: 0.06, sustain: 0.2,
  }), 80);
}

// ═══ Ringtone — gentle bell melody (one octave lower) ═══
let ringtoneOscs = [];

function playRingtonePhrase() {
  const vol = getVolume();
  if (vol === 0) return;

  const melody = [
    { f: 523, t: 0 },     // C5
    { f: 587, t: 150 },   // D5
    { f: 659, t: 300 },   // E5
    { f: 587, t: 500 },   // D5
    { f: 523, t: 650 },   // C5
    { f: 587, t: 850 },   // D5
    { f: 659, t: 1000 },  // E5
    { f: 784, t: 1200 },  // G5
  ];

  const opts = {
    harmonics: [1, 2, 3, 5], harmonicVolumes: [1, 0.3, 0.12, 0.05],
    reverb: 0.6, attack: 0.003, decay: 0.12, sustain: 0.35, detune: 2,
    volume: vol * 0.45,
  };

  melody.forEach(({ f, t: delay }) => {
    setTimeout(() => createNote(f, 0.35, opts), delay);
  });
}

export function soundRingtoneStart() {
  soundRingtoneStop();
  playRingtonePhrase();
  ringtoneInterval = setInterval(playRingtonePhrase, 3000);
}

export function soundRingtoneStop() {
  if (ringtoneInterval) {
    clearInterval(ringtoneInterval);
    ringtoneInterval = null;
  }
}

export function soundRingtone() {
  playRingtonePhrase();
}

// Voice join — warm ascending chord
export function soundVoiceJoin() {
  const opts = {
    harmonics: [1, 2, 3], harmonicVolumes: [1, 0.2, 0.08],
    reverb: 0.5, attack: 0.01, decay: 0.2, sustain: 0.5, detune: 2,
    volume: getVolume() * 0.6,
  };
  createNote(523, 0.5, opts);
  setTimeout(() => createNote(659, 0.45, opts), 80);
  setTimeout(() => createNote(784, 0.5, { ...opts, reverb: 0.6 }), 160);
}

// Voice leave — soft descending
export function soundVoiceLeave() {
  const opts = {
    harmonics: [1, 2], harmonicVolumes: [1, 0.2],
    reverb: 0.4, attack: 0.01, decay: 0.15, sustain: 0.4,
    volume: getVolume() * 0.6,
  };
  createNote(784, 0.35, opts);
  setTimeout(() => createNote(659, 0.3, opts), 80);
  setTimeout(() => createNote(523, 0.4, { ...opts, reverb: 0.5 }), 160);
}

// User joined voice room
export function soundUserJoined() {
  createNote(880, 0.25, {
    harmonics: [1, 2, 3], harmonicVolumes: [1, 0.2, 0.06],
    reverb: 0.35, attack: 0.005, decay: 0.1, sustain: 0.4,
  });
}

// User left voice room
export function soundUserLeft() {
  createNote(523, 0.3, {
    harmonics: [1, 2], harmonicVolumes: [1, 0.15],
    reverb: 0.35, attack: 0.005, decay: 0.15, sustain: 0.3,
  });
}

// Mute
export function soundMute() {
  createNote(440, 0.12, {
    harmonics: [1, 3], harmonicVolumes: [1, 0.1],
    reverb: 0.2, attack: 0.003, decay: 0.05, sustain: 0.3,
  });
}

// Unmute
export function soundUnmute() {
  createNote(660, 0.12, {
    harmonics: [1, 3], harmonicVolumes: [1, 0.1],
    reverb: 0.2, attack: 0.003, decay: 0.05, sustain: 0.3,
  });
}

// Error — warm, not alarming
export function soundError() {
  const opts = {
    harmonics: [1, 2], harmonicVolumes: [1, 0.25],
    reverb: 0.4, attack: 0.005, decay: 0.15, sustain: 0.5,
  };
  createNote(240, 0.3, opts);
  setTimeout(() => createNote(190, 0.35, opts), 150);
}

// Success — rising melody, softened
export function soundSuccess() {
  const opts = {
    harmonics: [1, 2, 3], harmonicVolumes: [0.8, 0.15, 0.05],
    reverb: 0.55, attack: 0.005, decay: 0.1, sustain: 0.5,
  };
  createNote(523, 0.25, opts);
  setTimeout(() => createNote(659, 0.2, opts), 100);
  setTimeout(() => createNote(784, 0.2, opts), 200);
  setTimeout(() => createNote(1047, 0.4, { ...opts, reverb: 0.6 }), 300);
}

// Click — ultra-subtle tactile tick
export function soundClick() {
  createNote(1800, 0.035, {
    harmonics: [1, 2.5], harmonicVolumes: [0.2, 0.05],
    reverb: 0.1, attack: 0.001, decay: 0.02, sustain: 0.2,
  });
}

// Hover — barely perceptible
export function soundHover() {
  createNote(2800, 0.025, {
    harmonics: [1], harmonicVolumes: [0.06],
    reverb: 0.05, attack: 0.001, decay: 0.01, sustain: 0.1,
  });
}

// Tab switch — soft swoosh
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
  filter.frequency.value = 2000;

  gain.gain.setValueAtTime(0, t);
  gain.gain.linearRampToValueAtTime(vol * 0.08, t + 0.005);
  gain.gain.exponentialRampToValueAtTime(0.001, t + 0.1);

  osc.connect(filter);
  filter.connect(gain);
  gain.connect(ctx.destination);
  osc.start(t);
  osc.stop(t + 0.12);
}

// Window open — soft pop
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

// Window close — descending whomp
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

// Call accepted
export function soundCallAccepted() {
  const opts = {
    harmonics: [1, 2, 3], harmonicVolumes: [1, 0.2, 0.06],
    reverb: 0.4, attack: 0.005, decay: 0.1, sustain: 0.5,
  };
  createNote(659, 0.2, opts);
  setTimeout(() => createNote(784, 0.2, opts), 80);
  setTimeout(() => createNote(1047, 0.35, { ...opts, reverb: 0.5 }), 160);
}

// Call ended
export function soundCallEnded() {
  const opts = {
    harmonics: [1, 2], harmonicVolumes: [1, 0.15],
    reverb: 0.35, attack: 0.005, decay: 0.12, sustain: 0.3,
  };
  createNote(784, 0.2, opts);
  setTimeout(() => createNote(523, 0.35, opts), 120);
}

// Call declined
export function soundCallDeclined() {
  createNote(440, 0.25, {
    harmonics: [1, 2], harmonicVolumes: [1, 0.2],
    reverb: 0.3, attack: 0.005, decay: 0.15, sustain: 0.3,
  });
}

// ═══ New sounds ═══

// Toggle switch (on/off)
export function soundToggle(isOn = true) {
  createNote(isOn ? 720 : 540, 0.1, {
    harmonics: [1, 2], harmonicVolumes: [0.15, 0.04],
    reverb: 0.2, attack: 0.003, decay: 0.04, sustain: 0.2,
  });
}

// Keystroke feedback — ultra-subtle
export function soundType() {
  const freq = 1400 + Math.random() * 400;
  createNote(freq, 0.02, {
    harmonics: [1], harmonicVolumes: [0.04],
    reverb: 0.05, attack: 0.001, decay: 0.008, sustain: 0.1,
  });
}

// Emoji reaction — happy pop
export function soundReaction() {
  createNote(880, 0.15, {
    harmonics: [1, 2, 3], harmonicVolumes: [0.2, 0.08, 0.03],
    reverb: 0.35, attack: 0.003, decay: 0.06, sustain: 0.3,
  });
  setTimeout(() => createNote(1175, 0.12, {
    harmonics: [1, 2], harmonicVolumes: [0.15, 0.05],
    reverb: 0.4, attack: 0.003, decay: 0.04, sustain: 0.2,
  }), 60);
}

// Delete — soft descending poof
export function soundDelete() {
  const vol = getVolume();
  if (vol === 0) return;
  const ctx = getCtx();
  const t = ctx.currentTime;
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  const filter = ctx.createBiquadFilter();

  osc.type = 'sine';
  osc.frequency.setValueAtTime(600, t);
  osc.frequency.exponentialRampToValueAtTime(200, t + 0.2);

  filter.type = 'lowpass';
  filter.frequency.setValueAtTime(2000, t);
  filter.frequency.exponentialRampToValueAtTime(400, t + 0.2);

  gain.gain.setValueAtTime(0, t);
  gain.gain.linearRampToValueAtTime(vol * 0.15, t + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.001, t + 0.25);

  osc.connect(filter);
  filter.connect(gain);
  gain.connect(ctx.destination);
  osc.start(t);
  osc.stop(t + 0.3);
}

// Copy to clipboard — quick confirmation
export function soundCopy() {
  createNote(1047, 0.08, {
    harmonics: [1, 2], harmonicVolumes: [0.15, 0.04],
    reverb: 0.15, attack: 0.002, decay: 0.03, sustain: 0.2,
  });
}

// Focus mode — gentle chime
export function soundFocus() {
  const opts = {
    harmonics: [1, 2, 3], harmonicVolumes: [0.18, 0.06, 0.02],
    reverb: 0.6, attack: 0.01, decay: 0.2, sustain: 0.5, detune: 1,
  };
  createNote(523, 0.5, opts);
  setTimeout(() => createNote(784, 0.6, { ...opts, reverb: 0.7 }), 200);
}

// Screenshot — shutter click
export function soundScreenshot() {
  const vol = getVolume();
  if (vol === 0) return;
  const ctx = getCtx();
  const t = ctx.currentTime;
  const bufferSize = ctx.sampleRate * 0.06;
  const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < bufferSize; i++) {
    data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / bufferSize, 4);
  }
  const source = ctx.createBufferSource();
  source.buffer = buffer;
  const gain = ctx.createGain();
  const filter = ctx.createBiquadFilter();
  filter.type = 'bandpass';
  filter.frequency.value = 3000;
  filter.Q.value = 2;
  gain.gain.value = vol * 0.2;
  source.connect(filter);
  filter.connect(gain);
  gain.connect(ctx.destination);
  source.start(t);
}
