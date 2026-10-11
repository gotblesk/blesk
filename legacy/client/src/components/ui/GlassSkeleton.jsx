import './GlassSkeleton.css';

// Базовый примитив
export function Skeleton({ width, height, borderRadius, variant = '', circle, pill, className = '' }) {
  const shape = circle
    ? 'glass-skeleton--circle'
    : pill
    ? 'glass-skeleton--pill'
    : variant === 'line'
    ? 'glass-skeleton--line'
    : '';

  const style = {};
  if (width !== undefined) style.width = typeof width === 'number' ? `${width}px` : width;
  if (height !== undefined) style.height = typeof height === 'number' ? `${height}px` : height;
  if (borderRadius !== undefined) style.borderRadius = typeof borderRadius === 'number' ? `${borderRadius}px` : borderRadius;

  return (
    <div
      className={`glass-skeleton ${shape} ${className}`}
      style={style}
    />
  );
}

// Текстовая строка — высота 14px, ширина случайная 60-90%
Skeleton.Line = function SkeletonLine({ width, className = '' }) {
  const w = width ?? `${60 + Math.floor(Math.random() * 30)}%`;
  return (
    <Skeleton
      variant="line"
      width={w}
      height={14}
      className={className}
    />
  );
};

// Круг — для аватаров
Skeleton.Circle = function SkeletonCircle({ size = 36, className = '' }) {
  return (
    <Skeleton
      circle
      width={size}
      height={size}
      className={className}
    />
  );
};

// Блок — для карточек
Skeleton.Block = function SkeletonBlock({ height = 80, width, borderRadius, className = '' }) {
  return (
    <Skeleton
      variant="block"
      height={height}
      width={width ?? '100%'}
      borderRadius={borderRadius}
      className={className}
    />
  );
};

// ═══ Составные скелетоны для конкретных экранов ═══

export function ChannelCardSkeleton() {
  return (
    <div className="skeleton-channel-card glass-skeleton" style={{ minHeight: 120 }}>
      <Skeleton.Circle size={40} className="skeleton-channel-card__avatar" />
      <Skeleton className="skeleton-channel-card__title glass-skeleton--text" />
      <Skeleton className="skeleton-channel-card__desc glass-skeleton--text-sm" />
      <Skeleton className="skeleton-channel-card__desc2 glass-skeleton--text-sm" />
    </div>
  );
}

export function FriendRowSkeleton() {
  return (
    <div className="skeleton-friend-row">
      <Skeleton.Circle size={36} className="skeleton-friend-row__avatar" />
      <div className="skeleton-friend-row__info">
        <Skeleton className="skeleton-friend-row__name glass-skeleton--text" />
        <Skeleton className="skeleton-friend-row__status glass-skeleton--text-sm" />
      </div>
    </div>
  );
}

export function ChatRowSkeleton() {
  return (
    <div className="skeleton-chat-row">
      <Skeleton.Circle size={40} className="skeleton-chat-row__avatar" />
      <div className="skeleton-chat-row__info">
        <Skeleton className="skeleton-chat-row__name glass-skeleton--text" />
        <Skeleton className="skeleton-chat-row__preview glass-skeleton--text-sm" />
      </div>
    </div>
  );
}

export function ChannelGridSkeleton({ count = 6 }) {
  return (
    <div className="mo__grid">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="mo__grid-item">
          <ChannelCardSkeleton />
        </div>
      ))}
    </div>
  );
}

export function FriendListSkeleton({ count = 5 }) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <FriendRowSkeleton key={i} />
      ))}
    </>
  );
}
