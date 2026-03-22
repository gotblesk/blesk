import { forwardRef } from 'react';
import './Glass.css';

// Double Layer Glass — два вложенных backdrop-filter слоя
// API не изменён: depth(0-3), radius, hover, className, style, onClick

const depthStyles = {
  0: 'glass--depth-0',
  1: 'glass--depth-1',
  2: 'glass--depth-2',
  3: 'glass--depth-3',
};

const Glass = forwardRef(function Glass({
  children,
  depth = 1,
  radius = 16,
  hover = false,
  className = '',
  style = {},
  onClick,
}, ref) {
  const classes = [
    'glass',
    depthStyles[depth] || depthStyles[1],
    hover ? 'glass--hover' : '',
    className,
  ].filter(Boolean).join(' ');

  const innerRadius = Math.max(radius - 8, 6);

  return (
    <div
      ref={ref}
      className={classes}
      style={{ borderRadius: radius, ...style }}
      onClick={onClick}
    >
      {/* Outer layer — light blur */}
      <div className="glass__outer" aria-hidden="true" />
      {/* Inner layer — heavier blur */}
      <div
        className="glass__inner"
        style={{ borderRadius: innerRadius }}
        aria-hidden="true"
      />
      {/* Edge border */}
      <div className="glass__edge" aria-hidden="true" />
      {/* Content */}
      <div className="glass__content">
        {children}
      </div>
    </div>
  );
});

export default Glass;
