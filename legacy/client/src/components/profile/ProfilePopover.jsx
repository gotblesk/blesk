import { useEffect, useCallback, useRef, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createPortal } from 'react-dom';
import ProfileCard from './ProfileCard';
import './ProfilePopover.css';

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
  const cardRef = useRef(null);

  const mode = useMemo(() => {
    if (!user || !userId) return 'other';
    return userId === user.id ? 'own' : 'other';
  }, [userId, user]);

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

  return createPortal(
    <AnimatePresence>
      {isOpen && userId && (
        <motion.div
          className="ppopover"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15 }}
          onClick={handleBackdropClick}
          data-testid="profile-popover"
        >
          <motion.div
            className="ppopover__card"
            ref={cardRef}
            initial={{ opacity: 0, scale: 0.92, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
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
