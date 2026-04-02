import { useState, useRef, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import './SegmentedCircle.css';

const STATUSES = [
  { key: 'online', label: 'В сети', color: '#4ade80' },
  { key: 'dnd', label: 'Не беспокоить', color: '#f59e0b' },
  { key: 'invisible', label: 'Невидимка', color: '#6b7280' },
];

function polarToCartesian(cx, cy, r, angleDeg) {
  const rad = ((angleDeg - 90) * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}

function describeArc(cx, cy, r, startAngle, endAngle) {
  const start = polarToCartesian(cx, cy, r, endAngle);
  const end = polarToCartesian(cx, cy, r, startAngle);
  const largeArc = endAngle - startAngle <= 180 ? '0' : '1';
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArc} 0 ${end.x} ${end.y}`;
}

const ARCS = [
  { startAngle: 0, endAngle: 100 },
  { startAngle: 120, endAngle: 220 },
  { startAngle: 240, endAngle: 340 },
];

export default function SegmentedCircle({ currentStatus, onStatusChange }) {
  const [expanded, setExpanded] = useState(false);
  const [hoveredKey, setHoveredKey] = useState(null);
  const containerRef = useRef(null);

  const currentColor = STATUSES.find(s => s.key === currentStatus)?.color || '#4ade80';

  const handleSelect = useCallback((key) => {
    onStatusChange?.(key);
    setExpanded(false);
    setHoveredKey(null);
  }, [onStatusChange]);

  const handleKeyDown = useCallback((e) => {
    if (!expanded) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setExpanded(true);
      }
      return;
    }
    const currentIdx = STATUSES.findIndex(s => s.key === (hoveredKey || currentStatus));
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault();
      setHoveredKey(STATUSES[(currentIdx + 1) % STATUSES.length].key);
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault();
      setHoveredKey(STATUSES[(currentIdx - 1 + STATUSES.length) % STATUSES.length].key);
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleSelect(hoveredKey || currentStatus);
    } else if (e.key === 'Escape') {
      setExpanded(false);
      setHoveredKey(null);
    }
  }, [expanded, hoveredKey, currentStatus, handleSelect]);

  useEffect(() => {
    if (!expanded) return;
    const handleClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setExpanded(false);
        setHoveredKey(null);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [expanded]);

  const svgSize = 60;
  const center = svgSize / 2;
  const radius = 22;

  return (
    <div
      className="seg-circle"
      ref={containerRef}
      role="radiogroup"
      aria-label="Статус"
      tabIndex={0}
      onKeyDown={handleKeyDown}
      data-testid="topnav-status-dot"
    >
      <motion.button
        className="seg-circle__dot"
        style={{ background: currentColor }}
        onClick={(e) => { e.stopPropagation(); setExpanded(prev => !prev); }}
        animate={{ scale: expanded ? 0 : 1, opacity: expanded ? 0 : 1 }}
        transition={{ type: 'spring', stiffness: 300, damping: 25 }}
        aria-label={`Статус: ${STATUSES.find(s => s.key === currentStatus)?.label}`}
      />

      <AnimatePresence>
        {expanded && (
          <motion.div
            className="seg-circle__ring"
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 300, damping: 25 }}
          >
            <svg
              width={svgSize}
              height={svgSize}
              viewBox={`0 0 ${svgSize} ${svgSize}`}
              className="seg-circle__svg"
            >
              {STATUSES.map((status, i) => {
                const arc = ARCS[i];
                const isActive = status.key === currentStatus;
                const isHovered = status.key === hoveredKey;
                return (
                  <motion.path
                    key={status.key}
                    d={describeArc(center, center, radius, arc.startAngle, arc.endAngle)}
                    fill="none"
                    stroke={status.color}
                    strokeWidth={isHovered || isActive ? 6 : 4}
                    strokeLinecap="round"
                    className="seg-circle__arc"
                    data-testid="topnav-status-segment"
                    data-status={status.key}
                    initial={{ pathLength: 0, opacity: 0 }}
                    animate={{ pathLength: 1, opacity: 1 }}
                    exit={{ pathLength: 0, opacity: 0 }}
                    transition={{
                      type: 'spring',
                      stiffness: 300,
                      damping: 25,
                      delay: i * 0.05,
                    }}
                    onMouseEnter={() => setHoveredKey(status.key)}
                    onMouseLeave={() => setHoveredKey(null)}
                    onClick={() => handleSelect(status.key)}
                    style={{ cursor: 'pointer' }}
                  />
                );
              })}
            </svg>

            <AnimatePresence>
              {hoveredKey && (
                <motion.div
                  className="seg-circle__tooltip"
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.1 }}
                >
                  {STATUSES.find(s => s.key === hoveredKey)?.label}
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
