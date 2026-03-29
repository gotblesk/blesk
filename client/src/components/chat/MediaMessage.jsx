import { useState, useRef, useEffect, useCallback } from 'react';
import { FileText, DownloadSimple, FilmStrip, MusicNote, Archive, ImageBroken, Play, Pause } from '@phosphor-icons/react';
import API_URL from '../../config';
import './MediaMessage.css';

function getIcon(mime) {
  const m = mime || '';
  if (m.startsWith('video/')) return <FilmStrip size={20} />;
  if (m.startsWith('audio/')) return <MusicNote size={20} />;
  if (m.includes('zip')) return <Archive size={20} />;
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

function VoiceMessage({ src }) {
  const [playing, setPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [waveform, setWaveform] = useState([]);
  const audioRef = useRef(null);
  const rafRef = useRef(null);

  // Генерируем визуальную волну (30 столбцов с псевдо-случайной высотой)
  useEffect(() => {
    const bars = Array.from({ length: 30 }, () => 0.2 + Math.random() * 0.8);
    setWaveform(bars);
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
    if (playing) { el.pause(); }
    else { el.play(); }
    setPlaying(!playing);
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

  return (
    <div className={`media-msg__voice${playing ? ' media-msg__voice--playing' : ''}`}>
      <audio
        ref={audioRef}
        src={src}
        preload="metadata"
        onEnded={handleEnded}
        onLoadedMetadata={handleLoaded}
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
    </div>
  );
}

function formatSize(bytes) {
  const b = Number(bytes); // [HIGH-7] BigInt → Number
  if (b < 1024) return `${b} Б`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} КБ`;
  return `${(b / (1024 * 1024)).toFixed(1)} МБ`;
}

export default function MediaMessage({ attachments, onImageClick }) {
  return (
    <div className="media-msg">
      {attachments.map((a) => {
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

        // [MED-4] Видео — инлайн-плеер
        if (isVideo) {
          return (
            <div key={a.id} className="media-msg__video-wrap">
              <video
                src={fullSrc}
                controls
                preload="metadata"
                className="media-msg__video"
              />
              <div className="media-msg__video-info">
                <span className="media-msg__file-name">{a.filename}</span>
                <span className="media-msg__file-size">{formatSize(a.size)}</span>
              </div>
            </div>
          );
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
        return (
          <div key={a.id} className="media-msg__file">
            <div className="media-msg__file-icon">{getIcon(a.mimeType)}</div>
            <div className="media-msg__file-info">
              <span className="media-msg__file-name">{a.filename}</span>
              <span className="media-msg__file-size">{formatSize(a.size)}</span>
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
