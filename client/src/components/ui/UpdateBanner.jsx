import { useState, useEffect } from 'react';
import { Rocket, X } from 'lucide-react';
import './UpdateBanner.css';

export default function UpdateBanner({ socketRef }) {
  const [updateInfo, setUpdateInfo] = useState(null); // { version, changelog, source }
  const [downloadProgress, setDownloadProgress] = useState(null); // 0-100 или null
  const [downloadSpeed, setDownloadSpeed] = useState(0);
  const [transferred, setTransferred] = useState(0);
  const [total, setTotal] = useState(0);
  const [downloaded, setDownloaded] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  // Слушать Electron autoUpdater (локальная проверка)
  useEffect(() => {
    if (!window.blesk?.update) return;

    window.blesk.update.onAvailable((version) => {
      setUpdateInfo({ version, source: 'electron' });
      setDismissed(false);
    });

    window.blesk.update.onProgress((data) => {
      if (typeof data === 'number') {
        setDownloadProgress(data);
      } else {
        setDownloadProgress(data.percent || 0);
        setDownloadSpeed(data.speed || 0);
        setTransferred(data.transferred || 0);
        setTotal(data.total || 0);
      }
    });

    window.blesk.update.onDownloaded(() => {
      setDownloaded(true);
      setDownloadProgress(null);
    });
  }, []);

  // Слушать серверный broadcast через сокет
  useEffect(() => {
    const socket = socketRef?.current;
    if (!socket) return;

    const handler = ({ version, changelog }) => {
      setUpdateInfo({ version, changelog, source: 'server' });
      setDismissed(false);
    };

    socket.on('app:update-available', handler);
    return () => socket.off('app:update-available', handler);
  }, [socketRef]);

  const handleInstall = () => {
    if (window.blesk?.update) {
      window.blesk.update.install();
    }
  };

  const handleDismiss = () => {
    setDismissed(true);
  };

  const formatSpeed = (bps) => {
    if (!bps || bps <= 0) return '';
    if (bps >= 1048576) return `${(bps / 1048576).toFixed(1)} МБ/с`;
    if (bps >= 1024) return `${(bps / 1024).toFixed(0)} КБ/с`;
    return `${bps} Б/с`;
  };

  if (!updateInfo || dismissed) return null;

  return (
    <div className="update-banner">
      <div className="update-banner__icon"><Rocket size={18} strokeWidth={1.5} /></div>
      <div className="update-banner__text">
        <span className="update-banner__title">
          Доступно обновление {updateInfo.version}
        </span>
        {updateInfo.changelog && (
          <span className="update-banner__changelog">{updateInfo.changelog}</span>
        )}
        {downloadProgress !== null && !downloaded && (
          <div className="update-banner__progress">
            <div
              className="update-banner__progress-bar"
              style={{ width: `${downloadProgress}%` }}
            />
          </div>
        )}
      </div>
      <div className="update-banner__actions">
        {downloaded ? (
          <button className="update-banner__btn update-banner__btn--install" onClick={handleInstall}>
            Установить
          </button>
        ) : downloadProgress !== null ? (
          <span className="update-banner__downloading">
            {downloadProgress}%
            {downloadSpeed > 0 && (
              <span className="update-banner__speed"> · {formatSpeed(downloadSpeed)}</span>
            )}
          </span>
        ) : null}
        <button className="update-banner__close" onClick={handleDismiss}><X size={16} strokeWidth={2} /></button>
      </div>
    </div>
  );
}
