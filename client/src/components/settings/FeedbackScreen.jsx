import { useState, useEffect, useCallback } from 'react';
import { Bug, Lightbulb, HelpCircle, FileText, CheckCircle, X } from 'lucide-react';
import useAppVersion from '../../hooks/useAppVersion';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import './FeedbackScreen.css';

const TYPES = [
  { id: 'bug', emoji: <Bug size={16} strokeWidth={1.5} />, label: 'Баг' },
  { id: 'suggestion', emoji: <Lightbulb size={16} strokeWidth={1.5} />, label: 'Предложение' },
  { id: 'question', emoji: <HelpCircle size={16} strokeWidth={1.5} />, label: 'Вопрос' },
];

export default function FeedbackScreen({ open, onClose }) {
  const VERSION = 'v' + useAppVersion();
  const [type, setType] = useState('bug');
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const [myFeedback, setMyFeedback] = useState([]);
  const [showHistory, setShowHistory] = useState(false);

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

  const loadMyFeedback = async () => {
    try {
      const res = await fetch(`${API_URL}/api/feedback`, {
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      if (res.ok) {
        const data = await res.json();
        setMyFeedback(data.feedbacks || []);
      }
    } catch (err) { console.error('FeedbackScreen loadMyFeedback:', err?.message || err); }
  };

  const handleToggleHistory = () => {
    if (!showHistory) {
      loadMyFeedback();
    }
    setShowHistory((prev) => !prev);
  };

  const handleSubmit = useCallback(async () => {
    if (!text.trim() || sending) return;

    setSending(true);
    setError('');

    try {
      const osInfo = navigator.userAgent;

      const res = await fetch(`${API_URL}/api/feedback`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeaders(),
        },
        credentials: 'include',
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
          <X size={18} strokeWidth={2} />
        </button>

        {!sent ? (
          <>
            <div className="feedback-header">
              <div className="feedback-header__icon"><FileText size={18} strokeWidth={1.5} /></div>
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

            <button
              className="feedback-history__toggle"
              onClick={handleToggleHistory}
            >
              {showHistory ? 'Скрыть обращения' : 'Мои обращения'}
            </button>

            {showHistory && (
              <div className="feedback-history">
                {myFeedback.length === 0 ? (
                  <div className="feedback-history__item">
                    <div className="feedback-history__text">Нет обращений</div>
                  </div>
                ) : (
                  myFeedback.map((fb) => {
                    const typeInfo = TYPES.find((t) => t.id === fb.type);
                    return (
                      <div key={fb.id} className="feedback-history__item">
                        <div className="feedback-history__type">
                          {typeInfo ? typeInfo.emoji : <FileText size={16} strokeWidth={1.5} />}{' '}
                          {typeInfo ? typeInfo.label : fb.type}
                        </div>
                        <div className="feedback-history__text">{fb.text}</div>
                        <div className="feedback-history__meta">
                          {new Date(fb.createdAt).toLocaleDateString('ru-RU')}{' '}
                          {fb.status && `· ${fb.status}`}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            )}
          </>
        ) : (
          <div className="feedback-success">
            <div className="feedback-success__icon"><CheckCircle size={32} strokeWidth={1.5} /></div>
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
