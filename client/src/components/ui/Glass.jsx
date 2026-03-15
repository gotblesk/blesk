import { forwardRef } from 'react';
import './Glass.css';

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

  return (
    <div
      ref={ref}
      className={classes}
      style={{ borderRadius: radius, ...style }}
      onClick={onClick}
    >
      {children}
    </div>
  );
});

export default Glass;
