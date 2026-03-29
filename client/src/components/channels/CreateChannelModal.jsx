import { useState, useRef, useCallback, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X } from '@phosphor-icons/react';
import { useChannelStore } from '../../store/channelStore';
import './CreateChannelModal.css';

const CATEGORIES = [
  { key: 'news', label: 'Новости', color: '#3b82f6' },
  { key: 'gaming', label: 'Игры', color: '#8b5cf6' },
  { key: 'music', label: 'Музыка', color: '#ec4899' },
  { key: 'art', label: 'Арт', color: '#f59e0b' },
  { key: 'tech', label: 'Технологии', color: '#06b6d4' },
  { key: 'other', label: 'Другое', color: '#6b7280' },
];

const childV = {
  hidden: { opacity: 0, y: 12 },
  visible: (i) => ({
    opacity: 1, y: 0,
    transition: { delay: 0.15 + i * 0.06, duration: 0.4, ease: [0.16, 1, 0.3, 1] },
  }),
};

export default function CreateChannelModal({ onClose, onCreated }) {
  // [IMP-13] Escape для закрытия модалки
  useEffect(() => {
    const h = (e) => { if (e.key === 'Escape') onClose?.(); };
    document.addEventListener('keydown', h);
    return () => document.removeEventListener('keydown', h);
  }, [onClose]);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('other');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const modalRef = useRef(null);
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const [glare, setGlare] = useState({ x: 50, y: 50 });

  const handleMouseMove = useCallback((e) => {
    const el = modalRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const px = (e.clientX - cx) / (rect.width / 2);
    const py = (e.clientY - cy) / (rect.height / 2);
    setTilt({ x: py * -6, y: px * 6 });
    setGlare({ x: 50 + px * 30, y: 50 + py * 30 });
  }, []);

  const handleMouseLeave = useCallback(() => {
    setTilt({ x: 0, y: 0 });
    setGlare({ x: 50, y: 50 });
  }, []);

  const handleCreate = async () => {
    // [IMP-6] Предотвращение double-submit + [IMP-12] мин длина
    if (loading) return;
    if (!name.trim() || name.trim().length < 2) {
      setError(name.trim().length < 2 ? 'Минимум 2 символа' : 'Введите название');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const channel = await useChannelStore.getState().createChannel({
        name: name.trim(),
        description: description.trim(),
        category,
      });
      onCreated?.(channel);
    } catch (err) {
      setError(err.message || 'Ошибка создания');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <motion.div
        className="ccm__overlay"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        onClick={onClose}
      />
      <div className="ccm__wrap">
        <motion.div
          ref={modalRef}
          className="ccm"
          initial={{ opacity: 0, scale: 0.88, y: 30, rotateX: 0, rotateY: 0 }}
          animate={{ opacity: 1, scale: 1, y: 0, rotateX: tilt.x, rotateY: tilt.y }}
          exit={{ opacity: 0, scale: 0.92, y: 20 }}
          transition={{
            type: 'spring', damping: 22, stiffness: 300,
            rotateX: { duration: 0.1, ease: 'linear' },
            rotateY: { duration: 0.1, ease: 'linear' },
          }}
          onMouseMove={handleMouseMove}
          onMouseLeave={handleMouseLeave}
          onClick={(e) => e.stopPropagation()}
        >
          <div
            className="ccm__glare"
            style={{
              background: `radial-gradient(ellipse at ${glare.x}% ${glare.y}%, var(--glare-color) 0%, transparent 60%)`,
            }}
          />
          <div className="ccm__inner">
            <motion.div className="ccm__head" custom={0} variants={childV} initial="hidden" animate="visible">
              <span className="ccm__title">Создать канал</span>
              <motion.button
                className="ccm__close"
                onClick={onClose}
                whileHover={{ rotate: 90 }}
                whileTap={{ scale: 0.8 }}
                transition={{ duration: 0.2 }}
              >
                <X size={14} weight="regular" />
              </motion.button>
            </motion.div>

            <div className="ccm__body">
              <motion.div className="ccm__field" custom={1} variants={childV} initial="hidden" animate="visible">
                <label className="ccm__label">Название</label>
                <div className="ccm__field-wrap">
                  <input
                    className="ccm__input"
                    placeholder="Мой канал"
                    value={name}
                    onChange={(e) => setName(e.target.value.slice(0, 50))}
                    onKeyDown={(e) => { if (e.key === 'Enter') handleCreate(); if (e.key === 'Escape') onClose(); }}
                    maxLength={50}
                    autoFocus
                  />
                  <span className="ccm__counter">{name.length}/50</span>
                </div>
              </motion.div>

              <motion.div className="ccm__field" custom={2} variants={childV} initial="hidden" animate="visible">
                <label className="ccm__label">Описание</label>
                <div className="ccm__field-wrap">
                  <textarea
                    className="ccm__textarea"
                    placeholder="О чём этот канал..."
                    value={description}
                    onChange={(e) => setDescription(e.target.value.slice(0, 300))}
                    maxLength={300}
                    rows={3}
                  />
                  <span className="ccm__counter">{description.length}/300</span>
                </div>
              </motion.div>

              <motion.div className="ccm__field" custom={3} variants={childV} initial="hidden" animate="visible">
                <label className="ccm__label">Категория</label>
                <div className="ccm__cats">
                  {CATEGORIES.map((cat) => (
                    <button
                      key={cat.key}
                      className={`ccm__cat ${category === cat.key ? 'ccm__cat--active' : ''}`}
                      style={{ '--cat-color': cat.color }}
                      onClick={() => setCategory(cat.key)}
                    >
                      <span className="ccm__cat-dot" />
                      {cat.label}
                    </button>
                  ))}
                </div>
              </motion.div>

              <AnimatePresence>
                {error && (
                  <motion.div
                    className="ccm__error"
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                  >
                    {error}
                  </motion.div>
                )}
              </AnimatePresence>

              <motion.div custom={4} variants={childV} initial="hidden" animate="visible">
                <motion.button
                  className="ccm__submit"
                  onClick={handleCreate}
                  disabled={loading || !name.trim()}
                  whileHover={name.trim() && !loading ? { scale: 1.02, boxShadow: '0 0 24px rgba(200,255,0,0.25)' } : {}}
                  whileTap={name.trim() && !loading ? { scale: 0.97 } : {}}
                >
                  <span className="ccm__submit-shimmer" />
                  {loading ? 'Создание...' : 'Создать'}
                </motion.button>
              </motion.div>
            </div>
          </div>
        </motion.div>
      </div>
    </>
  );
}
