import { useState, useEffect, useCallback } from 'react';
import API_URL from '../../config';
import './FeedbackScreen.css';

const TYPES = [
  { id: 'bug', emoji: '🐛', label: 'Баг' },
  { id: 'suggestion', emoji: '💡', label: 'Предложение' },
  { id: 'question', emoji: '❓', label: 'Вопрос' },
];

const VERSION = 'v0.1.0-alpha';

export default function FeedbackScreen({ open, onClose }) {
  const [type, setType] = useState('bug');
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  // Escape закрывает
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  // Сброс при открытии
  useEffect(() => {
    if (open) {
      setType('bug');
      setText('');
      setSent(false);
      setError('');
    }
  }, [open]);

  const handleSubmit = useCallback(async () => {
    if (!text.trim() || sending) return;

    setSending(true);
    setError('');

    try {
      const token = localStorage.getItem('token');
      const osInfo = navigator.userAgent;

      const res = await fetch(`${API_URL}/api/feedback`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          type,
          text: text.trim(),
          appVersion: VERSION,
          osInfo,
        }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || 'Ошибка отправки');
      }

      setSent(true);
    } catch (err) {
      setError(err.message || 'Не удалось отправить');
    } finally {
      setSending(false);
    }
  }, [text, type, sending]);

  if (!open) return null;

  return (
    <div className="feedback-overlay" onClick={onClose}>
      <div
        className="feedback-card"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="feedback-card__shine" />

        <button className="feedback-close" onClick={onClose}>
          <span>✕</span>
        </button>

        {!sent ? (
          <>
            <div className="feedback-header">
              <div className="feedback-header__icon">📝</div>
              <div className="feedback-header__title">Обратная связь</div>
              <div className="feedback-header__sub">
                Помоги нам стать лучше
              </div>
            </div>

            {/* Тип */}
            <div className="feedback-types">
              {TYPES.map((t) => (
                <button
                  key={t.id}
                  className={`feedback-type ${
                    type === t.id ? 'feedback-type--active' : ''
                  }`}
                  onClick={() => setType(t.id)}
                >
                  <span className="feedback-type__emoji">{t.emoji}</span>
                  <span className="feedback-type__label">{t.label}</span>
                </button>
              ))}
            </div>

            {/* Textarea */}
            <div className="feedback-field">
              <textarea
                className="feedback-textarea"
                placeholder={
                  type === 'bug'
                    ? 'Опиши что произошло и как это воспроизвести...'
                    : type === 'suggestion'
                    ? 'Расскажи свою идею...'
                    : 'Задай свой вопрос...'
                }
                value={text}
                onChange={(e) => setText(e.target.value)}
                maxLength={2000}
                rows={5}
              />
              <div className="feedback-field__counter">
                {text.length} / 2000
              </div>
            </div>

            {/* Мета */}
            <div className="feedback-meta">
              <div className="feedback-meta__item">
                <span className="feedback-meta__label">Версия</span>
                <span className="feedback-meta__value">{VERSION}</span>
              </div>
              <div className="feedback-meta__item">
                <span className="feedback-meta__label">OS</span>
                <span className="feedback-meta__value">
                  {navigator.platform || 'N/A'}
                </span>
              </div>
            </div>

            {error && <div className="feedback-error">{error}</div>}

            {/* Кнопка */}
            <button
              className={`feedback-submit ${
                !text.trim() ? 'feedback-submit--disabled' : ''
              }`}
              onClick={handleSubmit}
              disabled={!text.trim() || sending}
            >
              {sending ? (
                <span className="feedback-submit__spinner" />
              ) : (
                'Отправить'
              )}
            </button>
          </>
        ) : (
          <div className="feedback-success">
            <div className="feedback-success__icon">✅</div>
            <div className="feedback-success__title">Отправлено!</div>
            <div className="feedback-success__text">
              Спасибо за обратную связь. Мы обязательно прочитаем.
            </div>
            <button className="feedback-success__btn" onClick={onClose}>
              Закрыть
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
