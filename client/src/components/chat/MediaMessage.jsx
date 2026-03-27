import { FileText, Download, Film, Music, Archive } from 'lucide-react';
import API_URL from '../../config';
import './MediaMessage.css';

function getIcon(mime) {
  const m = mime || '';
  if (m.startsWith('video/')) return <Film size={20} strokeWidth={1.5} />;
  if (m.startsWith('audio/')) return <Music size={20} strokeWidth={1.5} />;
  if (m.includes('zip')) return <Archive size={20} strokeWidth={1.5} />;
  return <FileText size={20} strokeWidth={1.5} />;
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
        const isImage = mime.startsWith('image/') ||
          (!mime && /\.(jpe?g|png|gif|webp|avif|bmp)$/i.test(a.filename || ''));
        const isVideo = mime.startsWith('video/');
        const isAudio = mime.startsWith('audio/');

        // Изображения
        if (isImage) {
          return (
            <img
              key={a.id}
              src={src}
              alt={a.filename}
              className="media-msg__image"
              onClick={() => onImageClick?.(fullSrc)}
              loading="lazy"
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

        // [MED-4] Аудио — инлайн-плеер (голосовые сообщения и аудиофайлы)
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
              <Download size={16} strokeWidth={1.5} />
            </a>
          </div>
        );
      })}
    </div>
  );
}
