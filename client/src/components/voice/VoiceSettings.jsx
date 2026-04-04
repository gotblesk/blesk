import { useState, useEffect, useRef } from 'react';
import { useVoiceStore } from '../../store/voiceStore';
import './VoiceSettings.css';

export default function VoiceSettings() {
  const {
    inputDeviceId,
    outputDeviceId,
    noiseSuppression,
    echoCancellation,
    vadThreshold,
    setOutputDevice,
    setVadThreshold,
  } = useVoiceStore();

  const [devices, setDevices] = useState([]);
  const [outputDevices, setOutputDevices] = useState([]);
  const [selectedDevice, setSelectedDevice] = useState(inputDeviceId || '');
  const [selectedOutput, setSelectedOutput] = useState(outputDeviceId || 'default');
  const aiNoiseSuppression = useVoiceStore(s => s.aiNoiseSuppression);
  const setAiNoiseSuppression = useVoiceStore(s => s.setAiNoiseSuppression);
  const [noise, setNoise] = useState(noiseSuppression);
  const [echo, setEcho] = useState(echoCancellation);
  const [aiNoise, setAiNoise] = useState(aiNoiseSuppression);
  const [localVadThreshold, setLocalVadThreshold] = useState(vadThreshold);
  const [testing, setTesting] = useState(false);
  const [micLevel, setMicLevel] = useState(0);
  const streamRef = useRef(null);
  const animRef = useRef(null);
  const ctxRef = useRef(null);

  // Загрузить список устройств
  useEffect(() => {
    async function loadDevices() {
      try {
        // Нужно запросить доступ чтобы получить имена устройств
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.getTracks().forEach((t) => t.stop());

        const allDevices = await navigator.mediaDevices.enumerateDevices();
        const audioInputs = allDevices.filter((d) => d.kind === 'audioinput');
        const audioOutputs = allDevices.filter((d) => d.kind === 'audiooutput');
        setDevices(audioInputs);
        setOutputDevices(audioOutputs);
      } catch {
        // Нет доступа к микрофону
      }
    }
    loadDevices();
  }, []);

  // Тест микрофона
  const toggleTest = async () => {
    if (testing) {
      // Остановить тест
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((t) => t.stop());
        streamRef.current = null;
      }
      if (animRef.current) {
        cancelAnimationFrame(animRef.current);
        animRef.current = null;
      }
      ctxRef.current?.close();
      ctxRef.current = null;
      setTesting(false);
      setMicLevel(0);
      return;
    }

    try {
      const constraints = {
        audio: {
          echoCancellation: echo,
          noiseSuppression: noise,
          ...(selectedDevice ? { deviceId: { exact: selectedDevice } } : {}),
        },
      };

      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      streamRef.current = stream;
      setTesting(true);

      // Визуализация уровня
      const ctx = new AudioContext();
      ctxRef.current = ctx;
      const source = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 256;
      source.connect(analyser);

      const data = new Uint8Array(analyser.frequencyBinCount);

      function updateLevel() {
        analyser.getByteFrequencyData(data);
        const avg = data.reduce((a, b) => a + b, 0) / data.length;
        setMicLevel(Math.min(100, Math.round(avg * 2)));
        animRef.current = requestAnimationFrame(updateLevel);
      }
      updateLevel();
    } catch (err) {
      // [Баг #8] Показать ошибку доступа к микрофону
      if (err.name === 'NotAllowedError') {
        useVoiceStore.getState().setMediaError?.('Нет доступа к микрофону. Проверьте разрешения ОС.');
      }
    }
  };

  // Cleanup при размонтировании
  useEffect(() => {
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((t) => t.stop());
      }
      if (animRef.current) {
        cancelAnimationFrame(animRef.current);
      }
      ctxRef.current?.close();
      ctxRef.current = null;
    };
  }, []);

  // Сохранить настройки в store + localStorage
  const handleDeviceChange = (deviceId) => {
    setSelectedDevice(deviceId);
    useVoiceStore.setState({ inputDeviceId: deviceId || null });
    localStorage.setItem('blesk-input-device', deviceId || '');
  };

  const handleNoiseChange = (val) => {
    setNoise(val);
    useVoiceStore.setState({ noiseSuppression: val });
    localStorage.setItem('blesk-noise-suppression', String(val));
  };

  const handleEchoChange = (val) => {
    setEcho(val);
    useVoiceStore.setState({ echoCancellation: val });
    localStorage.setItem('blesk-echo-cancellation', String(val));
  };

  const handleAiNoiseChange = (val) => {
    setAiNoise(val);
    setAiNoiseSuppression(val);
  };

  const handleOutputChange = (deviceId) => {
    setSelectedOutput(deviceId);
    setOutputDevice(deviceId);
  };

  const handleVadChange = (val) => {
    const num = Number(val);
    setLocalVadThreshold(num);
    setVadThreshold(num);
  };

  return (
    <div className="voice-settings">
      {/* Выбор микрофона */}
      <div className="voice-settings__group">
        <label className="voice-settings__label">Микрофон</label>
        <select
          className="voice-settings__select"
          value={selectedDevice}
          onChange={(e) => handleDeviceChange(e.target.value)}
        >
          <option value="">По умолчанию</option>
          {devices.map((d) => (
            <option key={d.deviceId} value={d.deviceId}>
              {d.label || `Микрофон ${d.deviceId.slice(0, 8)}`}
            </option>
          ))}
        </select>
      </div>

      {/* Выбор устройства вывода */}
      <div className="voice-settings__group">
        <label className="voice-settings__label">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 6, verticalAlign: -2 }}>
            <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
            <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
            <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
          </svg>
          Устройство вывода
        </label>
        <select
          className="voice-settings__select"
          value={selectedOutput}
          onChange={(e) => handleOutputChange(e.target.value)}
        >
          <option value="default">По умолчанию</option>
          {outputDevices.map((d) => (
            <option key={d.deviceId} value={d.deviceId}>
              {d.label || `Динамик ${d.deviceId.slice(0, 8)}`}
            </option>
          ))}
        </select>
      </div>

      {/* Тест микрофона */}
      <div className="voice-settings__group">
        <label className="voice-settings__label">Тест микрофона</label>
        <div className="voice-settings__test">
          <button
            className={`voice-settings__test-btn ${testing ? 'voice-settings__test-btn--active' : ''}`}
            onClick={toggleTest}
          >
            {testing ? 'Остановить' : 'Проверить'}
          </button>
          <div className="voice-settings__level-bar">
            <div
              className="voice-settings__level-fill"
              style={{ width: `${micLevel}%` }}
            />
          </div>
        </div>
      </div>

      {/* Чувствительность микрофона (VAD) */}
      <div className="voice-settings__group">
        <label className="voice-settings__label">Чувствительность микрофона</label>
        <div className="voice-settings__vad">
          <div className="voice-settings__vad-slider-row">
            <input
              type="range"
              className="voice-settings__slider"
              min="0"
              max="100"
              value={localVadThreshold}
              onChange={(e) => handleVadChange(e.target.value)}
            />
            <span className="voice-settings__vad-value">{localVadThreshold}</span>
          </div>
          <div className="voice-settings__vad-meter">
            <div
              className="voice-settings__vad-meter-fill"
              style={{ width: `${micLevel}%` }}
            />
            <div
              className="voice-settings__vad-threshold-line"
              style={{ left: `${localVadThreshold}%` }}
            />
          </div>
          <span className="voice-settings__hint">
            Выше = микрофон активируется при более тихом голосе
          </span>
        </div>
      </div>

      {/* Переключатели */}
      <div className="voice-settings__group">
        <label className="voice-settings__label">Обработка звука</label>

        <div className="voice-settings__toggle-row">
          <span className="voice-settings__toggle-text">Шумоподавление</span>
          <button
            className={`voice-settings__toggle ${noise ? 'voice-settings__toggle--on' : ''}`}
            onClick={() => handleNoiseChange(!noise)}
          >
            <div className="voice-settings__toggle-knob" />
          </button>
        </div>

        <div className="voice-settings__toggle-row">
          <span className="voice-settings__toggle-text">Эхоподавление</span>
          <button
            className={`voice-settings__toggle ${echo ? 'voice-settings__toggle--on' : ''}`}
            onClick={() => handleEchoChange(!echo)}
          >
            <div className="voice-settings__toggle-knob" />
          </button>
        </div>

        <div className="voice-settings__toggle-row">
          <div>
            <span className="voice-settings__toggle-text">AI шумоподавление</span>
            <span className="voice-settings__hint">Подавляет фоновый шум, гул, шипение</span>
          </div>
          <button
            className={`voice-settings__toggle ${aiNoise ? 'voice-settings__toggle--on' : ''}`}
            onClick={() => handleAiNoiseChange(!aiNoise)}
          >
            <div className="voice-settings__toggle-knob" />
          </button>
        </div>
      </div>
    </div>
  );
}
