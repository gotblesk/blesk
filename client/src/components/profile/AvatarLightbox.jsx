import { useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createPortal } from 'react-dom';
import API_URL from '../../config';
import './AvatarLightbox.css';

export default function AvatarLightbox({ avatarFilename, userId, isOpen, onClose }) {
  const handleKeyDown = useCallback((e) => {
    if (e.key === 'Escape') onClose();
  }, [onClose]);

  useEffect(() => {
    if (isOpen) {
      document.addEventListener('keydown', handleKeyDown);
      return () => document.removeEventListener('keydown', handleKeyDown);
    }
  }, [isOpen, handleKeyDown]);

  if (!avatarFilename) return null;

  const src = `${API_URL}/uploads/avatars/${avatarFilename}`;

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="avatar-lightbox"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          onClick={onClose}
          data-testid="avatar-lightbox"
        >
          <div className="avatar-lightbox__backdrop" data-testid="avatar-lightbox-backdrop" />
          <motion.img
            className="avatar-lightbox__image"
            src={src}
            alt="Avatar"
            layoutId={`avatar-${userId}`}
            data-testid="avatar-lightbox-image"
            onClick={(e) => e.stopPropagation()}
            initial={{ scale: 0.8 }}
            animate={{ scale: 1 }}
            exit={{ scale: 0.8 }}
            transition={{ type: 'spring', stiffness: 300, damping: 28 }}
          />
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  );
}
