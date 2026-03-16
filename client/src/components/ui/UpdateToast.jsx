import { useState, useEffect } from 'react';
import './UpdateToast.css';

// Форматирование размера файла
function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} Б`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} КБ`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} ГБ`;
}

// Форматирование скорости
function formatSpeed(bytesPerSecond) {
  if (bytesPerSecond < 1024) return `${bytesPerSecond} Б/с`;
  if (bytesPerSecond < 1024 * 1024) return `${(bytesPerSecond / 1024).toFixed(0)} КБ/с`;
  return `${(bytesPerSecond / (1024 * 1024)).toFixed(1)} МБ/с`;
}

export default function UpdateToast() {
  const [state, setState] = useState('idle'); // idle | downloading | ready
  const [version, setVersion] = useState('');
  const [progress, setProgress] = useState(0);
  const [speed, setSpeed] = useState(0);
  const [transferred, setTransferred] = useState(0);
  const [total, setTotal] = useState(0);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (!window.blesk?.update) return;

    window.blesk.update.onAvailable((v) => {
      setVersion(v);
      setState('downloading');
      setDismissed(false);
    });

    window.blesk.update.onProgress((data) => {
      // Поддержка старого формата (число) и нового (объект)
      if (typeof data === 'number') {
        setProgress(data);
      } else {
        setProgress(data.percent || 0);
        setSpeed(data.speed || 0);
        setTransferred(data.transferred || 0);
        setTotal(data.total || 0);
      }
    });

    window.blesk.update.onDownloaded(() => {
      setState('ready');
    });
  }, []);

  if (state === 'idle' || dismissed) return null;

  const handleInstall = () => {
    window.blesk?.update.install();
  };

  return (
    <div className={`update-toast update-toast--${state}`}>
      <div className="update-toast__icon">
        {state === 'downloading' ? '⬇' : '✨'}
      </div>

      <div className="update-toast__content">
        {state === 'downloading' && (
          <>
            <div className="update-toast__text">
              Загрузка {version && `v${version}`}
              {total > 0 && (
                <span className="update-toast__size">
                  {' '}— {formatBytes(transferred)} / {formatBytes(total)}
                </span>
              )}
            </div>
            <div className="update-toast__meta">
              {speed > 0 && (
                <span className="update-toast__speed">{formatSpeed(speed)}</span>
              )}
              <span className="update-toast__percent">{progress}%</span>
            </div>
            <div className="update-toast__bar">
              <div
                className="update-toast__fill"
                style={{ width: `${progress}%` }}
              />
            </div>
          </>
        )}

        {state === 'ready' && (
          <div className="update-toast__text">
            Обновление {version && `v${version}`} готово
          </div>
        )}
      </div>

      {state === 'ready' && (
        <button className="update-toast__btn" onClick={handleInstall}>
          Перезапустить
        </button>
      )}

      <button
        className="update-toast__close"
        onClick={() => setDismissed(true)}
        title="Скрыть"
      >
        ×
      </button>
    </div>
  );
}
