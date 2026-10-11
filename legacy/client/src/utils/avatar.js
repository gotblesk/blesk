// Единая утилита для аватарок — цвета, hue, fallback

// Вычислить hue аватара (единый алгоритм для всего приложения)
export function getAvatarHue(user) {
  if (user?.hue != null && user.hue !== 0) return user.hue;
  // Фоллбэк по первой букве username
  return ((user?.username?.charCodeAt(0) || 65) * 37) % 360;
}

// Градиент для фона аватара (красиво, для крупных аватаров)
export function getAvatarGradient(hue) {
  return `linear-gradient(135deg, hsl(${hue}, 70%, 50%), hsl(${hue + 40}, 70%, 60%))`;
}

// Solid цвет (для мелких аватаров)
export function getAvatarColor(hue) {
  return `hsl(${hue}, 70%, 50%)`;
}

// Первая буква ника (заглавная)
export function getInitial(user) {
  return (user?.username || 'U')[0].toUpperCase();
}
