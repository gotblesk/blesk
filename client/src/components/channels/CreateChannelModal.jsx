import { useState, useRef, useCallback, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Megaphone } from '@phosphor-icons/react';
import { useChannelStore } from '../../store/channelStore';
import './CreateChannelModal.css';

const CATEGORIES = [
  { key: 'gaming', label: 'Игры', color: '#8b5cf6' },
  { key: 'music', label: 'Музыка', color: '#ec4899' },
  { key: 'art', label: 'Творчество', color: '#f59e0b' },
  { key: 'tech', label: 'Технологии', color: '#06b6d4' },
  { key: 'education', label: 'Образование', color: '#10b981' },
  { key: 'entertainment', label: 'Развлечения', color: '#f97316' },
  { key: 'news', label: 'Новости', color: '#3b82f6' },
  { key: 'sports', label: 'Спорт', color: '#eab308' },
  { key: 'science', label: 'Наука', color: '#14b8a6' },
  { key: 'other', label: 'Другое', color: '#6b7280' },
];

const STEP_SIZES = [
  { width: 420, height: 400 },
  { width: 420, height: 420 },
];

export default function CreateChannelModal({ onClose, onCreated }) {
  const [step, setStep] = useState(0);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('other');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const modalRef = useRef(null);
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const [glare, setGlare] = useState({ x: 50, y: 50 });

  useEffect(() => {
    const h = (e) => { if (e.key === 'Escape') onClose?.(); };
    document.addEventListener('keydown', h);
    return () => document.removeEventListener('keydown', h);
  }, [onClose]);

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

  const handleNext = () => {
    if (step === 0) {
      if (!name.trim() || name.trim().length < 2) {
        setError(name.trim().length < 2 ? 'Минимум 2 символа' : 'Введите название');
        return;
      }
      setError('');
    }
    setStep((s) => s + 1);
  };

  const handleCreate = async () => {
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

  const renderStepInfo = () => (
    <div className="ccw__step">
      <h2 className="ccw__title">Создать канал</h2>
      <p className="ccw__subtitle">Канал для публикации постов подписчикам</p>
      <div className="ccw__field">
        <label className="ccw__label">Название</label>
        <div className="ccw__field-wrap">
          <input
            className="ccw__input"
            placeholder="Мой канал"
            value={name}
            onChange={(e) => setName(e.target.value.slice(0, 50))}
            maxLength={50}
            autoFocus
          />
          <span className="ccw__counter">{name.length}/50</span>
        </div>
      </div>
      <div className="ccw__field">
        <label className="ccw__label">Описание</label>
        <div className="ccw__field-wrap">
          <textarea
            className="ccw__textarea"
            placeholder="О чём этот канал..."
            value={description}
            onChange={(e) => setDescription(e.target.value.slice(0, 300))}
            maxLength={300}
            rows={3}
          />
          <span className="ccw__counter">{description.length}/300</span>
        </div>
      </div>
      <AnimatePresence>
        {error && (
          <motion.div
            className="ccw__error"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
          >
            {error}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );

  const renderStepSettings = () => (
    <div className="ccw__step">
      <h2 className="ccw__title">Настройки канала</h2>
      <div className="ccw__field">
        <label className="ccw__label">Категория</label>
        <div className="ccw__cats">
          {CATEGORIES.map((cat) => (
            <button
              key={cat.key}
              className={`ccw__cat ${category === cat.key ? 'ccw__cat--active' : ''}`}
              style={{ '--cat-color': cat.color }}
              onClick={() => setCategory(cat.key)}
            >
              <span className="ccw__cat-dot" />
              {cat.label}
            </button>
          ))}
        </div>
      </div>
      <div className="ccw__preview">
        <div className="ccw__preview-icon">
          <Megaphone size={40} weight="fill" />
        </div>
        <span className="ccw__preview-name">{name || 'Без названия'}</span>
        {description && <span className="ccw__preview-desc">{description}</span>}
      </div>
      <AnimatePresence>
        {error && (
          <motion.div
            className="ccw__error"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
          >
            {error}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );

  return (
    <>
      <motion.div
        className="ccw__overlay"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        onClick={onClose}
      />
      <div className="ccw__wrap">
        <motion.div
          ref={modalRef}
          className="ccw"
          layout
          initial={{ opacity: 0, scale: 0.88, y: 30 }}
          animate={{
            opacity: 1,
            scale: 1,
            y: 0,
            width: STEP_SIZES[step].width,
            height: STEP_SIZES[step].height,
            rotateX: tilt.x,
            rotateY: tilt.y,
          }}
          exit={{ opacity: 0, scale: 0.92, y: 20 }}
          transition={{
            type: 'spring',
            damping: 28,
            stiffness: 350,
            rotateX: { duration: 0.1, ease: 'linear' },
            rotateY: { duration: 0.1, ease: 'linear' },
          }}
          onMouseMove={handleMouseMove}
          onMouseLeave={handleMouseLeave}
          onClick={(e) => e.stopPropagation()}
        >
          <div
            className="ccw__glare"
            style={{
              background: `radial-gradient(ellipse at ${glare.x}% ${glare.y}%, var(--glare-color) 0%, transparent 60%)`,
            }}
          />
          <div className="ccw__inner">
            <div className="ccw__head">
              <div className="ccw__progress">
                {[0, 1].map((i) => (
                  <span key={i} className={`ccw__dot ${step === i ? 'ccw__dot--active' : step > i ? 'ccw__dot--done' : ''}`} />
                ))}
              </div>
              <motion.button
                className="ccw__close"
                onClick={onClose}
                whileHover={{ rotate: 90 }}
                whileTap={{ scale: 0.8 }}
                transition={{ duration: 0.2 }}
              >
                <X size={14} weight="regular" />
              </motion.button>
            </div>

            <div className="ccw__body">
              <AnimatePresence mode="wait">
                <motion.div
                  key={step}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.15 }}
                  className="ccw__content"
                >
                  {step === 0 && renderStepInfo()}
                  {step === 1 && renderStepSettings()}
                </motion.div>
              </AnimatePresence>
            </div>

            <div className="ccw__nav">
              {step > 0 ? (
                <button className="ccw__btn ccw__btn--back" onClick={() => { setError(''); setStep((s) => s - 1); }}>
                  Назад
                </button>
              ) : (
                <span />
              )}
              {step < 1 ? (
                <button
                  className="ccw__btn ccw__btn--next"
                  onClick={handleNext}
                >
                  Далее
                </button>
              ) : (
                <button
                  className="ccw__btn ccw__btn--create"
                  onClick={handleCreate}
                  disabled={loading || !name.trim()}
                >
                  <span className="ccw__btn-shimmer" />
                  {loading ? 'Создание...' : 'Создать'}
                </button>
              )}
            </div>
          </div>
        </motion.div>
      </div>
    </>
  );
}
