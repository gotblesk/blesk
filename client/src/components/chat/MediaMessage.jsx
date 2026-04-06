import { useState, useRef, useEffect, useCallback } from 'react';
import { FileText, FilePdf, DownloadSimple, FilmStrip, MusicNote, Archive, ImageBroken, Play, Pause, PlayCircle, Lock } from '@phosphor-icons/react';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import { decryptFile, decryptFileMeta, fetchPublicKey } from '../../utils/cryptoService';
import './MediaMessage.css';

function getIcon(mime) {
  const m = mime || '';
  if (m.startsWith('video/')) return <FilmStrip size={20} />;
  if (m.startsWith('audio/')) return <MusicNote size={20} />;
  if (m.includes('zip')) return <Archive size={20} />;
  if (m === 'application/pdf') return <FilePdf size={20} />;
  return <FileText size={20} />;
}

function ImageWithFallback({ src, fullSrc, filename, onImageClick }) {
  const [broken, setBroken] = useState(false);
  if (broken) {
    return (
      <div className="media-msg__file media-msg__file--broken">
        <div className="media-msg__file-icon"><ImageBroken size={20} /></div>
        <div className="media-msg__file-info">
          <span className="media-msg__file-name">{filename}</span>
          <span className="media-msg__file-size">Не удалось загрузить</span>
        </div>
        <a href={fullSrc} download={filename} target="_blank" rel="noopener noreferrer" className="media-msg__download">
          <DownloadSimple size={16} />
        </a>
      </div>
    );
  }
  return (
    <img
      src={src}
      alt={filename}
      className="media-msg__image"
      onClick={() => onImageClick?.(fullSrc)}
      loading="lazy"
      onError={() => setBroken(true)}
    />
  );
}

function formatTime(sec) {
  const s = Math.floor(sec || 0);
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${r < 10 ? '0' : ''}${r}`;
}

const VOICE_BAR_COUNT = 30;

function VoiceMessage({ src }) {
  const [playing, setPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [waveform, setWaveform] = useState(() => Array.from({ length: VOICE_BAR_COUNT }, () => 0.2 + Math.random() * 0.8));
  // [V6] Сохранять скорость воспроизведения между сообщениями
  const [speed, setSpeed] = useState(() => parseFloat(localStorage.getItem('voiceSpeed')) || 1);
  const [audioError, setAudioError] = useState(false);
  const audioRef = useRef(null);
  const rafRef = useRef(null);

  // Декодируем аудио и строим реальную форму волны через OfflineAudioContext
  useEffect(() => {
    let cancelled = false;

    async function buildWaveform() {
      try {
        const response = await fetch(src);
        if (!response.ok) { if (!cancelled) setAudioError(true); return; }
        const arrayBuffer = await response.arrayBuffer();

        // OfflineAudioContext — длительность достаточная для декодирования
        const offlineCtx = new OfflineAudioContext(1, 44100, 44100);
        const audioBuffer = await offlineCtx.decodeAudioData(arrayBuffer);

        const data = audioBuffer.getChannelData(0);
        const step = Math.floor(data.length / VOICE_BAR_COUNT);
        const bars = Array.from({ length: VOICE_BAR_COUNT }, (_, i) => {
          let sum = 0;
          const start = i * step;
          const end = Math.min(start + step, data.length);
          for (let j = start; j < end; j++) {
            sum += Math.abs(data[j]);
          }
          const avg = sum / (end - start);
          return Math.max(0.08, Math.min(1, avg * 3.5));
        });

        if (!cancelled) setWaveform(bars);
      } catch {
        // Если декодирование не удалось — оставляем псевдо-случайные бары
      }
    }

    buildWaveform();
    return () => { cancelled = true; };
  }, [src]);

  // Singleton playback — останавливаем этот плеер если запустился другой
  useEffect(() => {
    const handleStopOthers = (e) => {
      if (e.detail !== audioRef.current && audioRef.current && !audioRef.current.paused) {
        audioRef.current.pause();
        setPlaying(false);
      }
    };
    window.addEventListener('blesk-voice-play', handleStopOthers);
    return () => window.removeEventListener('blesk-voice-play', handleStopOthers);
  }, []);

  const updateProgress = useCallback(() => {
    const el = audioRef.current;
    if (el && el.duration) {
      setProgress((el.currentTime / el.duration) * 100);
    }
    if (playing) rafRef.current = requestAnimationFrame(updateProgress);
  }, [playing]);

  useEffect(() => {
    if (playing) {
      rafRef.current = requestAnimationFrame(updateProgress);
    }
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current); };
  }, [playing, updateProgress]);

  const toggle = () => {
    const el = audioRef.current;
    if (!el) return;
    if (playing) {
      el.pause();
      setPlaying(false);
    } else {
      // Остановить все другие голосовые сообщения
      window.dispatchEvent(new CustomEvent('blesk-voice-play', { detail: el }));
      el.play();
      setPlaying(true);
    }
  };

  const cycleSpeed = (e) => {
    e.stopPropagation();
    const speeds = [1, 1.5, 2];
    const nextIdx = (speeds.indexOf(speed) + 1) % speeds.length;
    const newSpeed = speeds[nextIdx];
    setSpeed(newSpeed);
    localStorage.setItem('voiceSpeed', String(newSpeed)); // [V6] persist
    if (audioRef.current) audioRef.current.playbackRate = newSpeed;
  };

  const handleEnded = () => {
    setPlaying(false);
    setProgress(0);
  };

  const handleLoaded = () => {
    const el = audioRef.current;
    if (el && el.duration && isFinite(el.duration)) setDuration(el.duration);
  };

  const handleBarClick = (e) => {
    const el = audioRef.current;
    if (!el || !el.duration) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    el.currentTime = ratio * el.duration;
    setProgress(ratio * 100);
  };

  if (audioError) {
    return (
      <div className="media-msg__voice media-msg__voice--error">
        <span className="media-msg__voice-error-text">Не удалось загрузить аудио</span>
      </div>
    );
  }

  return (
    <div className={`media-msg__voice${playing ? ' media-msg__voice--playing' : ''}`}>
      <audio
        ref={audioRef}
        src={src}
        preload="metadata"
        onEnded={handleEnded}
        onLoadedMetadata={handleLoaded}
        onError={() => setAudioError(true)}
      />
      <button className="media-msg__voice-play" onClick={toggle}>
        {playing ? <Pause size={14} weight="fill" /> : <Play size={14} weight="fill" />}
      </button>
      <div className="media-msg__voice-bars" onClick={handleBarClick}>
        {waveform.map((h, i) => (
          <div
            key={i}
            className="media-msg__voice-bar"
            style={{
              height: `${h * 100}%`,
              background: (i / waveform.length * 100) <= progress
                ? 'var(--accent, #c8ff00)'
                : 'var(--voice-bar-inactive)',
            }}
          />
        ))}
      </div>
      <span className="media-msg__voice-time">
        {formatTime(playing ? (audioRef.current?.currentTime || 0) : duration)}
      </span>
      <button className="voice-msg__speed" onClick={cycleSpeed}>
        {speed}×
      </button>
    </div>
  );
}

function formatSize(bytes) {
  const b = Number(bytes); // [HIGH-7] BigInt → Number
  if (b < 1024) return `${b} Б`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} КБ`;
  return `${(b / (1024 * 1024)).toFixed(1)} МБ`;
}

// [5.3.1] Видеоплеер с play overlay — показывает кнопку поверх видео до начала воспроизведения
function VideoPlayer({ src, filename, size }) {
  const [isPlaying, setIsPlaying] = useState(false);
  const videoRef = useRef(null);

  const handlePlay = () => {
    const v = videoRef.current;
    if (!v) return;
    v.play();
    setIsPlaying(true);
  };

  const handlePause = () => setIsPlaying(false);
  const handleEnded = () => setIsPlaying(false);

  return (
    <div className="media-msg__video-wrap">
      <div className="media-msg__video-container">
        <video
          ref={videoRef}
          src={src}
          controls
          preload="metadata"
          className="media-msg__video"
          onPlay={() => setIsPlaying(true)}
          onPause={handlePause}
          onEnded={handleEnded}
        />
        {!isPlaying && (
          <div className="media-msg__play-overlay" onClick={handlePlay}>
            <PlayCircle size={48} weight="fill" />
          </div>
        )}
      </div>
      <div className="media-msg__video-info">
        <span className="media-msg__file-name">{filename}</span>
        <span className="media-msg__file-size">{formatSize(size)}</span>
      </div>
    </div>
  );
}

// ─── E2E: обёртка для зашифрованных вложений ───
// Скачивает зашифрованный блоб, расшифровывает, создаёт object URL и рендерит через обычные компоненты
// Кеш расшифрованных URL чтобы не расшифровывать повторно при ре-рендере
const decryptedUrlCache = new Map();

function EncryptedAttachment({ attachment, senderUserId, roomId, onImageClick }) {
  const [state, setState] = useState('loading'); // loading | ready | error
  const [decryptedUrl, setDecryptedUrl] = useState(null);
  const [meta, setMeta] = useState(null); // { filename, mimeType }
  const objectUrlRef = useRef(null);

  useEffect(() => {
    let cancelled = false;

    async function decrypt() {
      // Проверить кеш
      const cacheKey = attachment.id;
      if (decryptedUrlCache.has(cacheKey)) {
        const cached = decryptedUrlCache.get(cacheKey);
        if (!cancelled) {
          setDecryptedUrl(cached.url);
          setMeta(cached.meta);
          setState('ready');
        }
        return;
      }

      try {
        // 1. Получить публичный ключ отправителя
        const senderPubKey = await fetchPublicKey(senderUserId);
        if (!senderPubKey) {
          if (!cancelled) setState('error');
          return;
        }

        // 2. Расшифровать метаданные (filename + mimeType)
        let fileMeta = { filename: 'file', mimeType: 'application/octet-stream' };
        if (attachment.encryptedMeta) {
          const decMeta = await decryptFileMeta(attachment.encryptedMeta, senderPubKey, roomId);
          if (decMeta) fileMeta = decMeta;
        }

        // 3. Скачать зашифрованный файл
        const fullUrl = `${API_URL}${attachment.url}`;
        const headers = getAuthHeaders();
        const response = await fetch(fullUrl, { headers, credentials: 'include' });
        if (!response.ok) {
          if (!cancelled) setState('error');
          return;
        }
        const encryptedBuffer = await response.arrayBuffer();

        // 4. Расшифровать содержимое файла
        const decryptedBuffer = await decryptFile(encryptedBuffer, senderPubKey, roomId);
        if (!decryptedBuffer) {
          if (!cancelled) setState('error');
          return;
        }

        // 5. Создать object URL из расшифрованных данных
        const blob = new Blob([decryptedBuffer], { type: fileMeta.mimeType });
        const url = URL.createObjectURL(blob);

        // Кешировать
        decryptedUrlCache.set(cacheKey, { url, meta: fileMeta });

        if (!cancelled) {
          objectUrlRef.current = url;
          setDecryptedUrl(url);
          setMeta(fileMeta);
          setState('ready');
        } else {
          // Если компонент размонтирован до завершения — не отзываем URL (он в кеше)
        }
      } catch (err) {
        console.error('E2E file decrypt error:', err);
        if (!cancelled) setState('error');
      }
    }

    decrypt();
    return () => { cancelled = true; };
  }, [attachment.id, attachment.url, attachment.encryptedMeta, senderUserId, roomId]);

  // Загрузка
  if (state === 'loading') {
    return (
      <div className="media-msg__file media-msg__file--encrypted-loading">
        <div className="media-msg__file-icon"><Lock size={20} /></div>
        <div className="media-msg__file-info">
          <span className="media-msg__file-name">Расшифровка...</span>
          <span className="media-msg__file-size">{formatSize(attachment.size)}</span>
        </div>
      </div>
    );
  }

  // Ошибка расшифровки
  if (state === 'error') {
    return (
      <div className="media-msg__file media-msg__file--encrypted-error">
        <div className="media-msg__file-icon"><Lock size={20} /></div>
        <div className="media-msg__file-info">
          <span className="media-msg__file-name">Не удалось расшифровать</span>
          <span className="media-msg__file-size">E2E зашифрованный файл</span>
        </div>
      </div>
    );
  }

  // Расшифровано — рендерим как обычное вложение
  const mime = meta?.mimeType || '';
  const filename = meta?.filename || 'file';
  const isVoice = filename.startsWith('voice-') && /\.(webm|ogg)$/i.test(filename);
  const isImage = mime.startsWith('image/');
  const isVideo = mime.startsWith('video/') && !isVoice;
  const isAudio = mime.startsWith('audio/');

  if (isVoice) {
    return <VoiceMessage src={decryptedUrl} />;
  }

  if (isImage) {
    return (
      <ImageWithFallback
        src={decryptedUrl}
        fullSrc={decryptedUrl}
        filename={filename}
        onImageClick={onImageClick}
      />
    );
  }

  if (isVideo) {
    return <VideoPlayer src={decryptedUrl} filename={filename} size={attachment.size} />;
  }

  if (isAudio) {
    return (
      <div className="media-msg__audio-wrap">
        <audio src={decryptedUrl} controls preload="metadata" className="media-msg__audio" />
        <div className="media-msg__audio-info">
          <span className="media-msg__file-name">{filename}</span>
          <span className="media-msg__file-size">{formatSize(attachment.size)}</span>
        </div>
      </div>
    );
  }

  // Скачивание расшифрованного файла
  return (
    <div className="media-msg__file">
      <div className="media-msg__file-icon">
        <Lock size={14} style={{ marginRight: 2, opacity: 0.5 }} />
        {getIcon(mime)}
      </div>
      <div className="media-msg__file-info">
        <span className="media-msg__file-name">{filename}</span>
        <span className="media-msg__file-size">{formatSize(attachment.size)}</span>
      </div>
      <a
        href={decryptedUrl}
        download={filename}
        className="media-msg__download"
      >
        <DownloadSimple size={16} />
      </a>
    </div>
  );
}

export default function MediaMessage({ attachments, onImageClick, senderUserId, roomId }) {
  return (
    <div className="media-msg">
      {attachments.map((a) => {
        // E2E зашифрованные вложения — расшифровка на клиенте
        if (a.encrypted) {
          return (
            <EncryptedAttachment
              key={a.id}
              attachment={a}
              senderUserId={senderUserId}
              roomId={roomId}
              onImageClick={onImageClick}
            />
          );
        }

        // Обычные (не зашифрованные) вложения — как раньше
        const src = `${API_URL}${a.thumbnailUrl || a.url}`;
        const fullSrc = `${API_URL}${a.url}`;
        // [BUG 3] Guard against null/undefined mimeType + fallback по расширению
        const mime = a.mimeType || '';
        // Голосовые сообщения — проверяем по имени файла ДО проверки MIME,
        // потому что file-type может вернуть video/webm вместо audio/webm
        const isVoice = a.filename?.startsWith('voice-') && /\.(webm|ogg)$/i.test(a.filename || '');
        const isImage = mime.startsWith('image/') ||
          (!mime && /\.(jpe?g|png|gif|webp|avif|bmp)$/i.test(a.filename || ''));
        const isVideo = mime.startsWith('video/') && !isVoice;
        const isAudio = mime.startsWith('audio/');

        // Голосовые сообщения — компактный плеер (до проверки video/image)
        if (isVoice) {
          return <VoiceMessage key={a.id} src={fullSrc} />;
        }

        // Изображения
        if (isImage) {
          return (
            <ImageWithFallback
              key={a.id}
              src={src}
              fullSrc={fullSrc}
              filename={a.filename}
              onImageClick={onImageClick}
            />
          );
        }

        // [MED-4] Видео — инлайн-плеер с play overlay [5.3.1]
        if (isVideo) {
          return <VideoPlayer key={a.id} src={fullSrc} filename={a.filename} size={a.size} />;
        }

        // Аудиофайлы (музыка и т.д.) — стандартный плеер
        if (isAudio) {
          return (
            <div key={a.id} className="media-msg__audio-wrap">
              <audio
                src={fullSrc}
                controls
                preload="metadata"
                className="media-msg__audio"
              />
              <div className="media-msg__audio-info">
                <span className="media-msg__file-name">{a.filename}</span>
                <span className="media-msg__file-size">{formatSize(a.size)}</span>
              </div>
            </div>
          );
        }

        // Остальные файлы — карточка скачивания
        // [HIGH-4] download={a.filename} сохраняет оригинальное имя
        const isPdf = mime === 'application/pdf' || /\.pdf$/i.test(a.filename || '');
        return (
          <div key={a.id} className="media-msg__file">
            <div className="media-msg__file-icon">{isPdf ? <FilePdf size={20} /> : getIcon(a.mimeType)}</div>
            <div className="media-msg__file-info">
              <span className="media-msg__file-name">{a.filename}</span>
              <span className="media-msg__file-size">{isPdf ? 'PDF документ · ' : ''}{formatSize(a.size)}</span>
            </div>
            <a
              href={fullSrc}
              download={a.filename}
              target="_blank"
              rel="noopener noreferrer"
              className="media-msg__download"
            >
              <DownloadSimple size={16} />
            </a>
          </div>
        );
      })}
    </div>
  );
}
