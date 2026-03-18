import { FileText, Download, Film, Music, Archive } from 'lucide-react';
import API_URL from '../../config';
import './MediaMessage.css';

function getIcon(mime) {
  if (mime.startsWith('video/')) return <Film size={20} strokeWidth={1.5} />;
  if (mime.startsWith('audio/')) return <Music size={20} strokeWidth={1.5} />;
  if (mime.includes('zip')) return <Archive size={20} strokeWidth={1.5} />;
  return <FileText size={20} strokeWidth={1.5} />;
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} Б`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
}

export default function MediaMessage({ attachments, onImageClick }) {
  return (
    <div className="media-msg">
      {attachments.map((a) => {
        if (a.mimeType.startsWith('image/')) {
          const src = `${API_URL}${a.thumbnailUrl || a.url}`;
          const fullSrc = `${API_URL}${a.url}`;
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
        return (
          <div key={a.id} className="media-msg__file">
            <div className="media-msg__file-icon">{getIcon(a.mimeType)}</div>
            <div className="media-msg__file-info">
              <span className="media-msg__file-name">{a.filename}</span>
              <span className="media-msg__file-size">{formatSize(a.size)}</span>
            </div>
            <a
              href={`${API_URL}${a.url}`}
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
