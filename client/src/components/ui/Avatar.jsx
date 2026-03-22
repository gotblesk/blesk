import { useState } from 'react';
import API_URL from '../../config';
import { getAvatarHue, getAvatarGradient, getInitial } from '../../utils/avatar';
import './Avatar.css';

// Единый компонент аватарки для всего приложения
// Решает: broken image fallback, консистентные цвета, onError обработка
export default function Avatar({
  user,
  avatarUrl,           // fallback если user.avatar нет
  size = 'md',        // sm (24), md (36), lg (48), xl (80)
  showOnline = false,
  isOnline = false,
  userStatus,          // online | dnd | invisible
  className = '',
  onClick,
  children,            // для camera overlay и т.д.
}) {
  const [imgError, setImgError] = useState(false);

  const hue = getAvatarHue(user);
  const initial = getInitial(user);
  const avatarSrc = user?.avatar
    ? `${API_URL}/uploads/avatars/${user.avatar}`
    : avatarUrl
      ? `${API_URL}/uploads/avatars/${avatarUrl}`
      : null;
  const hasAvatar = avatarSrc && !imgError;

  const statusDot = showOnline && (isOnline || userStatus === 'dnd');
  const dotClass = userStatus === 'dnd' ? 'avatar__dot--dnd' : 'avatar__dot--online';

  // Поддержка числового size (inline) и строкового (CSS class)
  const isNumeric = typeof size === 'number';
  const sizeClass = isNumeric ? '' : `avatar--${size}`;
  const sizeStyle = isNumeric ? { width: size, height: size, fontSize: Math.round(size * 0.4) } : {};

  return (
    <div
      className={`avatar ${sizeClass} ${className}`}
      style={{ ...(hasAvatar ? {} : { background: getAvatarGradient(hue) }), ...sizeStyle }}
      onClick={onClick}
    >
      {hasAvatar ? (
        <img
          className="avatar__img"
          src={avatarSrc}
          alt=""
          onError={() => setImgError(true)}
        />
      ) : (
        <span className="avatar__letter">{initial}</span>
      )}

      {statusDot && <span className={`avatar__dot ${dotClass}`} />}

      {children}
    </div>
  );
}
