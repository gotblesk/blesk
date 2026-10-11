import { useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Warning, X } from '@phosphor-icons/react';
import './ConfirmDialog.css';

const backdropVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.2 } },
  exit: { opacity: 0, transition: { duration: 0.15 } },
};

const dialogVariants = {
  hidden: { opacity: 0, scale: 0.85, y: 12 },
  visible: {
    opacity: 1,
    scale: 1,
    y: 0,
    transition: { type: 'spring', stiffness: 500, damping: 30 },
  },
  exit: {
    opacity: 0,
    scale: 0.9,
    y: 8,
    transition: { duration: 0.15 },
  },
};

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmText = 'Подтвердить',
  cancelText = 'Отмена',
  danger = false,
  onConfirm,
  onCancel,
}) {
  const dialogRef = useRef(null);
  const triggerRef = useRef(null);
  const cancelBtnRef = useRef(null);

  // [CRIT-4] Сохранить элемент-триггер для возврата фокуса
  useEffect(() => {
    if (open) {
      triggerRef.current = document.activeElement;
    }
  }, [open]);

  // [CRIT-4] Focus trap + Escape + фокус на первой кнопке
  useEffect(() => {
    if (!open) return;

    // Фокус на кнопку "Отмена" при открытии
    requestAnimationFrame(() => {
      cancelBtnRef.current?.focus();
    });

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation(); // [IMP-13] Не пробрасывать Escape в родительскую модалку
        onCancel?.();
        return;
      }

      // Focus trap: Tab циклирует внутри диалога
      if (e.key === 'Tab' && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll('button, [tabindex]:not([tabindex="-1"])');
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];

        if (e.shiftKey) {
          if (document.activeElement === first) {
            e.preventDefault();
            last.focus();
          }
        } else {
          if (document.activeElement === last) {
            e.preventDefault();
            first.focus();
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown, true); // capture для приоритета
    return () => {
      window.removeEventListener('keydown', handleKeyDown, true);
      // Вернуть фокус на триггер при закрытии
      if (triggerRef.current && typeof triggerRef.current.focus === 'function') {
        triggerRef.current.focus();
      }
    };
  }, [open, onCancel]);

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="confirm-dialog-backdrop"
          variants={backdropVariants}
          initial="hidden"
          animate="visible"
          exit="exit"
          onClick={onCancel}
        >
          <motion.div
            className={`confirm-dialog ${danger ? 'confirm-dialog--danger' : ''}`}
            variants={dialogVariants}
            initial="hidden"
            animate="visible"
            exit="exit"
            onClick={(e) => e.stopPropagation()}
            ref={dialogRef}
            role="alertdialog"
            aria-modal="true"
            aria-label={title}
          >
            {/* [IMP-7] Close button aria-label */}
            <button className="confirm-dialog__close" onClick={onCancel} aria-label="Закрыть">
              <X size={16} weight="regular" />
            </button>

            {danger && (
              <div className="confirm-dialog__icon">
                <Warning size={28} weight="regular" />
              </div>
            )}

            <h3 className="confirm-dialog__title">{title}</h3>
            {message && <p className="confirm-dialog__message">{message}</p>}

            <div className="confirm-dialog__actions">
              <button
                ref={cancelBtnRef}
                className="confirm-dialog__btn confirm-dialog__btn--cancel"
                onClick={onCancel}
              >
                {cancelText}
              </button>
              <button
                className={`confirm-dialog__btn confirm-dialog__btn--confirm ${danger ? 'confirm-dialog__btn--danger' : ''}`}
                onClick={onConfirm}
              >
                {confirmText}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
