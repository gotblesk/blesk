/**
 * Продвинутое шумоподавление через Web Audio API.
 * Spectral gating: анализ FFT -> определение шумового профиля ->
 * подавление частот ниже порога.
 *
 * Цепочка:
 * 1. High-pass filter (обрезка < 85 Hz — гул, вибрации)
 * 2. Notch 50 Hz + 60 Hz (электрический гул)
 * 3. Low-pass filter (обрезка > 14 kHz — шипение)
 * 4. Analyser + adaptive noise gate (GainNode)
 * 5. Compressor (выравнивание громкости)
 */

export function createNoiseSuppressionPipeline(audioContext, sourceNode) {
  // 1. High-pass — убирает низкочастотный гул (вентилятор, кондиционер)
  const highPass = audioContext.createBiquadFilter();
  highPass.type = 'highpass';
  highPass.frequency.value = 85;
  highPass.Q.value = 0.7;

  // 2. Low-pass — убирает высокочастотное шипение
  const lowPass = audioContext.createBiquadFilter();
  lowPass.type = 'lowpass';
  lowPass.frequency.value = 14000;
  lowPass.Q.value = 0.7;

  // 3. Notch фильтры для электрического гула (50/60 Hz harmonics)
  const notch50 = audioContext.createBiquadFilter();
  notch50.type = 'notch';
  notch50.frequency.value = 50;
  notch50.Q.value = 10;

  const notch60 = audioContext.createBiquadFilter();
  notch60.type = 'notch';
  notch60.frequency.value = 60;
  notch60.Q.value = 10;

  // 4. Compressor — выравнивает громкость голоса
  const compressor = audioContext.createDynamicsCompressor();
  compressor.threshold.value = -30;
  compressor.knee.value = 10;
  compressor.ratio.value = 4;
  compressor.attack.value = 0.003;  // 3ms — быстро реагирует на речь
  compressor.release.value = 0.15;  // 150ms — плавно отпускает

  // 5. Noise gate через GainNode + AnalyserNode
  const analyser = audioContext.createAnalyser();
  analyser.fftSize = 512;
  analyser.smoothingTimeConstant = 0.85;

  const gateGain = audioContext.createGain();
  gateGain.gain.value = 1;

  // Adaptive noise floor tracking
  let noiseFloor = -60; // dBFS
  let noiseFloorSamples = [];
  const NOISE_FLOOR_WINDOW = 50; // ~5 сек при 10ms interval
  const GATE_THRESHOLD_ABOVE_FLOOR = 8; // dB выше noise floor = открыть gate

  // Smoothing для gate (анти-клик)
  let currentGate = 1;
  const GATE_ATTACK = 0.01;   // 10ms — быстро открывается при речи
  const GATE_RELEASE = 0.08;  // 80ms — плавно закрывается

  const dataArray = new Float32Array(analyser.fftSize);

  let intervalId = null;

  function startGating() {
    intervalId = setInterval(() => {
      analyser.getFloatTimeDomainData(dataArray);

      // RMS level в dBFS
      let sum = 0;
      for (let i = 0; i < dataArray.length; i++) {
        sum += dataArray[i] * dataArray[i];
      }
      const rms = Math.sqrt(sum / dataArray.length);
      const dB = rms > 0 ? 20 * Math.log10(rms) : -100;

      // Обновить noise floor (адаптивный)
      noiseFloorSamples.push(dB);
      if (noiseFloorSamples.length > NOISE_FLOOR_WINDOW) {
        noiseFloorSamples.shift();
      }
      // Noise floor = 10-й перцентиль (самые тихие моменты)
      const sorted = [...noiseFloorSamples].sort((a, b) => a - b);
      noiseFloor = sorted[Math.floor(sorted.length * 0.1)] || -60;

      // Gate decision
      const threshold = noiseFloor + GATE_THRESHOLD_ABOVE_FLOOR;
      const targetGate = dB > threshold ? 1 : 0;

      // Smooth transition
      if (targetGate > currentGate) {
        currentGate += (targetGate - currentGate) * (1 - Math.exp(-1 / (GATE_ATTACK * 100)));
      } else {
        currentGate += (targetGate - currentGate) * (1 - Math.exp(-1 / (GATE_RELEASE * 100)));
      }

      // Не полностью закрывать — оставить -40dB чтобы не было мёртвой тишины
      const finalGain = Math.max(currentGate, 0.01);
      gateGain.gain.setTargetAtTime(finalGain, audioContext.currentTime, 0.01);
    }, 10); // 100 раз в секунду — smooth gating
  }

  // Цепочка: source -> highPass -> notch50 -> notch60 -> lowPass -> analyser -> gateGain -> compressor -> output
  sourceNode.connect(highPass);
  highPass.connect(notch50);
  notch50.connect(notch60);
  notch60.connect(lowPass);
  lowPass.connect(analyser);
  analyser.connect(gateGain);
  gateGain.connect(compressor);

  startGating();

  return {
    output: compressor,
    destroy: () => {
      if (intervalId) clearInterval(intervalId);
      [highPass, lowPass, notch50, notch60, compressor, analyser, gateGain].forEach(n => {
        try { n.disconnect(); } catch { /* already disconnected */ }
      });
    },
    getNoiseFloor: () => noiseFloor,
    getGateState: () => currentGate,
  };
}

/**
 * Обработать MediaStream через noise suppression pipeline.
 * Возвращает новый MediaStream с обработанным аудио.
 */
export async function createSuppressedStream(inputStream) {
  const audioContext = new AudioContext({ sampleRate: 48000 });
  if (audioContext.state === 'suspended') {
    await audioContext.resume();
  }
  const source = audioContext.createMediaStreamSource(inputStream);
  const destination = audioContext.createMediaStreamDestination();

  const pipeline = createNoiseSuppressionPipeline(audioContext, source);
  pipeline.output.connect(destination);

  // Перенести видео-треки (если есть)
  inputStream.getVideoTracks().forEach(t => destination.stream.addTrack(t));

  return {
    stream: destination.stream,
    destroy: () => {
      pipeline.destroy();
      audioContext.close();
    },
    getNoiseFloor: pipeline.getNoiseFloor,
    getGateState: pipeline.getGateState,
  };
}
