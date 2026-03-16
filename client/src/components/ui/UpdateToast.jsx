import { useState, useEffect } from 'react';
import './UpdateToast.css';

export default function UpdateToast() {
  const [state, setState] = useState('idle'); // idle | downloading | ready
  const [version, setVersion] = useState('');
  const [progress, setProgress] = useState(0);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (!window.blesk?.update) return;

    window.blesk.update.onAvailable((v) => {
      setVersion(v);
      setState('downloading');
      setDismissed(false);
    });

    window.blesk.update.onProgress((percent) => {
      setProgress(percent);
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
              Загрузка обновления {version && `v${version}`}...
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
            Обновление {version && `v${version}`} готово к установке
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
