import useReducedMotion from '../../hooks/useReducedMotion';
import { GOO_FILTER } from './MetaballFilter';

// MetaballGroup — контейнер для metaball-эффекта
// ПРАВИЛО: children = ТОЛЬКО формы (круги, прямоугольники). Без текста!
// Текст/иконки передавать через overlay prop
//
// <MetaballGroup overlay={<span>Label</span>}>
//   <div className="blob" />
//   <div className="blob" />
// </MetaballGroup>

export default function MetaballGroup({
  children,
  overlay,
  disabled = false,
  className = '',
  style = {},
  blur = 10,
}) {
  const reducedMotion = useReducedMotion();
  const shouldFilter = !disabled && !reducedMotion;

  return (
    <div
      className={`metaball-group ${className}`}
      style={{ position: 'relative', ...style }}
    >
      {/* Filtered layer — shapes only */}
      <div
        className="metaball-group__shapes"
        style={shouldFilter ? { filter: GOO_FILTER } : undefined}
      >
        {children}
      </div>

      {/* Overlay — text, icons, labels (outside filter) */}
      {overlay && (
        <div
          className="metaball-group__overlay"
          style={{
            position: 'absolute',
            inset: 0,
            pointerEvents: 'none',
            zIndex: 1,
          }}
        >
          {overlay}
        </div>
      )}
    </div>
  );
}
