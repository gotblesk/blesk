import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Monitor, AppWindow, X } from '@phosphor-icons/react';
import './ScreenSharePicker.css';

export default function ScreenSharePicker({ onSelect, onCancel }) {
  const [sources, setSources] = useState([]);
  const [selected, setSelected] = useState(null);
  const [tab, setTab] = useState('screen');

  useEffect(() => {
    (async () => {
      if (window.blesk?.screen?.getSources) {
        const srcs = await window.blesk.screen.getSources();
        setSources(srcs);
      }
    })();
  }, []);

  // Esc закрывает пикер
  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'Escape') onCancel?.();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onCancel]);

  const filtered = sources.filter(s =>
    tab === 'screen' ? s.id.startsWith('screen:') : s.id.startsWith('window:')
  );

  return (
    <motion.div
      className="ssp__overlay"
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
    >
      <motion.div
        className="ssp__modal"
        initial={{ scale: 0.95, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', damping: 25, stiffness: 400 }}
      >
        <div className="ssp__header">
          <h3>Демонстрация экрана</h3>
          <button className="ssp__close" onClick={onCancel}><X size={18} /></button>
        </div>

        <div className="ssp__tabs">
          <button
            className={`ssp__tab ${tab === 'screen' ? 'ssp__tab--active' : ''}`}
            onClick={() => setTab('screen')}
          >
            <Monitor size={16} /> Экраны
          </button>
          <button
            className={`ssp__tab ${tab === 'window' ? 'ssp__tab--active' : ''}`}
            onClick={() => setTab('window')}
          >
            <AppWindow size={16} /> Окна
          </button>
        </div>

        <div className="ssp__grid">
          {filtered.map(src => (
            <div
              key={src.id}
              className={`ssp__source ${selected === src.id ? 'ssp__source--selected' : ''}`}
              onClick={() => setSelected(src.id)}
              onDoubleClick={() => onSelect(src.id)}
            >
              <img src={src.thumbnail} alt={src.name} className="ssp__thumb" />
              <div className="ssp__name">
                {src.appIcon && <img src={src.appIcon} className="ssp__icon" alt="" />}
                <span>{src.name}</span>
              </div>
            </div>
          ))}
          {filtered.length === 0 && (
            <div className="ssp__empty">Нет доступных источников</div>
          )}
        </div>

        <div className="ssp__footer">
          <button className="ssp__btn ssp__btn--cancel" onClick={onCancel}>Отмена</button>
          <button
            className="ssp__btn ssp__btn--share"
            disabled={!selected}
            onClick={() => selected && onSelect(selected)}
          >
            Демонстрировать
          </button>
        </div>
      </motion.div>
    </motion.div>
  );
}
