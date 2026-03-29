import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { X } from '@phosphor-icons/react';
import './ImageLightbox.css';

export default function ImageLightbox({ src, onClose }) {
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  return createPortal(
    <div className="lightbox" onClick={onClose}>
      <button className="lightbox__close" onClick={onClose}>
        <X size={24} weight="bold" />
      </button>
      <img
        src={src}
        alt=""
        className="lightbox__image"
        onClick={(e) => e.stopPropagation()}
      />
    </div>,
    document.body
  );
}
