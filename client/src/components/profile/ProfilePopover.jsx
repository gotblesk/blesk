import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createPortal } from 'react-dom';
import ProfileCard from './ProfileCard';
import './ProfilePopover.css';

function calcPosition(anchorRect, cardWidth, cardHeight) {
  const pad = 16;
  const gap = 8;
  const vw = window.innerWidth;
  const vh = window.innerHeight;

  let x, y, originX;

  // Пробуем справа
  if (anchorRect.right + gap + cardWidth + pad < vw) {
    x = anchorRect.right + gap;
    originX = 'left';
  }
  // Пробуем слева
  else if (anchorRect.left - gap - cardWidth - pad > 0) {
    x = anchorRect.left - gap - cardWidth;
    originX = 'right';
  }
  // Fallback: центр
  else {
    x = Math.max(pad, (vw - cardWidth) / 2);
    originX = 'center';
  }

  // По вертикали: центрируем относительно anchor
  y = anchorRect.top + anchorRect.height / 2 - cardHeight / 2;

  // Clamp
  y = Math.max(pad, Math.min(y, vh - cardHeight - pad));
  x = Math.max(pad, Math.min(x, vw - cardWidth - pad));

  return { x, y, originX };
}

export default function ProfilePopover({
  anchorRef,
  userId,
  user,
  isOpen,
  onClose,
  onEdit,
  onOpenChat,
  onAddFriend,
}) {
  const [pos, setPos] = useState({ x: 0, y: 0, originX: 'left' });
  const cardRef = useRef(null);

  // Определяем mode
  const mode = useMemo(() => {
    if (!user || !userId) return 'other';
    return userId === user.id ? 'own' : 'other';
  }, [userId, user]);

  // Позиционирование при открытии
  useEffect(() => {
    if (!isOpen) return;
    if (!anchorRef?.current) {
      // Fallback: center on screen when no anchor
      setPos({ x: (window.innerWidth - 340) / 2, y: (window.innerHeight - 500) / 2, originX: 'center' });
      return;
    }
    const anchorRect = anchorRef.current.getBoundingClientRect();
    const cardWidth = 340;
    const cardHeight = 500;
    setPos(calcPosition(anchorRect, cardWidth, cardHeight));
  }, [isOpen, anchorRef, userId]);

  // Закрытие при scroll / resize
  useEffect(() => {
    if (!isOpen) return;
    const handleClose = () => onClose?.();
    window.addEventListener('resize', handleClose);
    window.addEventListener('scroll', handleClose, true);
    return () => {
      window.removeEventListener('resize', handleClose);
      window.removeEventListener('scroll', handleClose, true);
    };
  }, [isOpen, onClose]);

  // Закрытие при Escape
  useEffect(() => {
    if (!isOpen) return;
    const handleKey = (e) => { if (e.key === 'Escape') onClose?.(); };
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, [isOpen, onClose]);

  const handleBackdropClick = useCallback((e) => {
    if (e.target === e.currentTarget) onClose?.();
  }, [onClose]);

  const transformOrigin = pos.originX === 'left' ? 'left center'
    : pos.originX === 'right' ? 'right center'
    : 'center center';

  return createPortal(
    <AnimatePresence>
      {isOpen && userId && (
        <motion.div
          className="ppopover"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.12 }}
          onClick={handleBackdropClick}
          data-testid="profile-popover"
        >
          <div className="ppopover__backdrop" data-testid="profile-popover-backdrop" onClick={handleBackdropClick} />
          <motion.div
            className="ppopover__card"
            ref={cardRef}
            style={{ left: pos.x, top: pos.y, transformOrigin }}
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.96 }}
            transition={{ type: 'spring', stiffness: 400, damping: 28 }}
          >
            <ProfileCard
              mode={mode}
              userId={userId}
              user={user}
              onEdit={() => { onClose?.(); onEdit?.(); }}
              onOpenChat={onOpenChat}
              onClose={onClose}
              onAddFriend={onAddFriend}
            />
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  );
}
