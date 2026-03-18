import { X, FileText, Image as ImageIcon, Film, Music, Archive } from 'lucide-react';
import './AttachmentPreview.css';

function getFileIcon(type) {
  if (type.startsWith('image/')) return <ImageIcon size={20} strokeWidth={1.5} />;
  if (type.startsWith('video/')) return <Film size={20} strokeWidth={1.5} />;
  if (type.startsWith('audio/')) return <Music size={20} strokeWidth={1.5} />;
  if (type.includes('zip')) return <Archive size={20} strokeWidth={1.5} />;
  return <FileText size={20} strokeWidth={1.5} />;
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} Б`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`;
}

export default function AttachmentPreview({ files, onRemove, uploadProgress }) {
  if (!files.length) return null;
  return (
    <div className="attach-preview">
      {files.map((file, i) => (
        <div key={i} className="attach-preview__item">
          {file.type.startsWith('image/') ? (
            <img src={URL.createObjectURL(file)} alt="" className="attach-preview__thumb" />
          ) : (
            <div className="attach-preview__icon">{getFileIcon(file.type)}</div>
          )}
          <div className="attach-preview__info">
            <span className="attach-preview__name">{file.name.length > 20 ? file.name.slice(0, 17) + '...' : file.name}</span>
            <span className="attach-preview__size">{formatSize(file.size)}</span>
          </div>
          <button className="attach-preview__remove" onClick={() => onRemove(i)}>
            <X size={14} strokeWidth={2} />
          </button>
          {uploadProgress?.[i] !== undefined && uploadProgress[i] < 100 && (
            <div className="attach-preview__progress" style={{ width: `${uploadProgress[i]}%` }} />
          )}
        </div>
      ))}
    </div>
  );
}
