import { useRef } from 'react';
import { motion, useMotionValue, useSpring, useTransform } from 'framer-motion';
import { WarningCircle } from '@phosphor-icons/react';
import useReducedMotion from '../../hooks/useReducedMotion';
import './GravityCard.css';

export default function GravityCard({
  tilt = 0,
  icon,
  title,
  subtitle,
  error,
  index = 0,
  dimmed = false,
  children,
}) {
  const reduced = useReducedMotion();
  const cardRef = useRef(null);

  // Parallax tilt from mouse
  const mouseX = useMotionValue(0);
  const mouseY = useMotionValue(0);
  const springX = useSpring(mouseX, { stiffness: 150, damping: 25 });
  const springY = useSpring(mouseY, { stiffness: 150, damping: 25 });
  const rotateX = useTransform(springY, [-0.5, 0.5], [4, -4]);
  const rotateY = useTransform(springX, [-0.5, 0.5], [-4, 4]);

  function handleMouseMove(e) {
    if (reduced) return;
    // Не тильтить при зажатой кнопке мыши или над интерактивными элементами
    if (e.buttons > 0) {
      mouseX.set(0);
      mouseY.set(0);
      return;
    }
    const tag = e.target.tagName?.toLowerCase();
    if (tag === 'input' || tag === 'button' || tag === 'textarea') {
      mouseX.set(0);
      mouseY.set(0);
      return;
    }
    const rect = cardRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = (e.clientX - rect.left) / rect.width - 0.5;
    const y = (e.clientY - rect.top) / rect.height - 0.5;
    mouseX.set(x);
    mouseY.set(y);
  }

  function handleMouseLeave() {
    mouseX.set(0);
    mouseY.set(0);
  }

  const className = [
    'g-card',
    error && 'g-card--error',
    dimmed && 'g-card--dimmed',
  ].filter(Boolean).join(' ');

  return (
    <motion.div
      ref={cardRef}
      className={className}
      initial={reduced ? {} : { opacity: 0, y: 30, rotate: 0 }}
      animate={{
        opacity: dimmed ? 0.7 : 1,
        y: 0,
        rotate: tilt,
        scale: dimmed ? 0.98 : 1,
        x: error && !reduced ? [0, -8, 8, -4, 4, 0] : 0,
      }}
      exit={{ opacity: 0, y: -20 }}
      transition={{
        type: 'spring',
        stiffness: 120,
        damping: 14,
        delay: index * 0.12,
        x: { duration: 0.5 },
      }}
      style={reduced ? {} : { rotateX, rotateY }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
    >
      {/* Header */}
      <div className="g-header">
        <div className="g-icon">
          {icon}
        </div>
        <div>
          <div className="g-title">{title}</div>
          {subtitle && <div className="g-subtitle">{subtitle}</div>}
        </div>
      </div>

      {/* Content (inputs, etc.) */}
      {children}

      {/* Error message */}
      {error && (
        <div className="g-error-msg">
          <WarningCircle size={12} weight="regular" />
          {error}
        </div>
      )}
    </motion.div>
  );
}
