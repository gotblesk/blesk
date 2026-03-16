import { useState, useEffect, useRef } from 'react';
import { useVoiceStore } from '../../store/voiceStore';
import './VoiceSettings.css';

export default function VoiceSettings() {
  const {
    inputDeviceId,
    noiseSuppression,
    echoCancellation,
  } = useVoiceStore();

  const [devices, setDevices] = useState([]);
  const [selectedDevice, setSelectedDevice] = useState(inputDeviceId || '');
  const [noise, setNoise] = useState(noiseSuppression);
  const [echo, setEcho] = useState(echoCancellation);
  const [testing, setTesting] = useState(false);
  const [micLevel, setMicLevel] = useState(0);
  const streamRef = useRef(null);
  const animRef = useRef(null);

  // Загрузить список устройств
  useEffect(() => {
    async function loadDevices() {
      try {
        // Нужно запросить доступ чтобы получить имена устройств
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.getTracks().forEach((t) => t.stop());

        const allDevices = await navigator.mediaDevices.enumerateDevices();
        const audioInputs = allDevices.filter((d) => d.kind === 'audioinput');
        setDevices(audioInputs);
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
    } catch {
      // Ошибка доступа
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
      </div>
    </div>
  );
}
