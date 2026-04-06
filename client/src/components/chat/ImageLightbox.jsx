import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { X, DownloadSimple, MagnifyingGlassPlus, MagnifyingGlassMinus, ArrowLeft, ArrowRight } from '@phosphor-icons/react';
import './ImageLightbox.css';

export default function ImageLightbox({ src, images = [], currentIndex = 0, onClose, onNavigate }) {
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const hasNav = images.length > 1;

  const resetView = useCallback(() => { setZoom(1); setPan({ x: 0, y: 0 }); }, []);

  const handlePrev = useCallback(() => {
    if (!hasNav) return;
    resetView();
    const prev = (currentIndex - 1 + images.length) % images.length;
    onNavigate?.(prev);
  }, [hasNav, currentIndex, images.length, onNavigate, resetView]);

  const handleNext = useCallback(() => {
    if (!hasNav) return;
    resetView();
    const next = (currentIndex + 1) % images.length;
    onNavigate?.(next);
  }, [hasNav, currentIndex, images.length, onNavigate, resetView]);

  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowLeft') handlePrev();
      if (e.key === 'ArrowRight') handleNext();
      if (e.key === '+' || e.key === '=') setZoom(z => Math.min(z + 0.25, 5));
      if (e.key === '-') setZoom(z => Math.max(z - 0.25, 0.5));
      if (e.key === '0') resetView();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose, handlePrev, handleNext, resetView]);

  const handleWheel = (e) => {
    e.preventDefault();
    setZoom(z => Math.min(Math.max(z + (e.deltaY > 0 ? -0.15 : 0.15), 0.5), 5));
  };

  const handleDownload = () => {
    const link = document.createElement('a');
    link.href = src;
    link.download = src.split('/').pop() || 'image';
    link.click();
  };

  const activeSrc = hasNav ? images[currentIndex] : src;

  return createPortal(
    <div className="lightbox" onClick={onClose}>
      {/* Toolbar */}
      <div className="lightbox__toolbar" onClick={e => e.stopPropagation()}>
        <button className="lightbox__tool-btn" onClick={() => setZoom(z => Math.min(z + 0.25, 5))} title="Увеличить">
          <MagnifyingGlassPlus size={20} />
        </button>
        <button className="lightbox__tool-btn" onClick={() => setZoom(z => Math.max(z - 0.25, 0.5))} title="Уменьшить">
          <MagnifyingGlassMinus size={20} />
        </button>
        <span className="lightbox__zoom-label">{Math.round(zoom * 100)}%</span>
        <button className="lightbox__tool-btn" onClick={handleDownload} title="Скачать">
          <DownloadSimple size={20} />
        </button>
        {hasNav && (
          <span className="lightbox__counter">{currentIndex + 1} / {images.length}</span>
        )}
      </div>

      <button className="lightbox__close" onClick={onClose}>
        <X size={24} weight="bold" />
      </button>

      {/* Navigation arrows */}
      {hasNav && (
        <>
          <button className="lightbox__nav lightbox__nav--prev" onClick={e => { e.stopPropagation(); handlePrev(); }}>
            <ArrowLeft size={28} weight="bold" />
          </button>
          <button className="lightbox__nav lightbox__nav--next" onClick={e => { e.stopPropagation(); handleNext(); }}>
            <ArrowRight size={28} weight="bold" />
          </button>
        </>
      )}

      <img
        src={activeSrc}
        alt=""
        className="lightbox__image"
        style={{ transform: `scale(${zoom}) translate(${pan.x}px, ${pan.y}px)` }}
        onClick={e => e.stopPropagation()}
        onWheel={handleWheel}
        draggable={false}
      />
    </div>,
    document.body
  );
}
