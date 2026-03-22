/**
 * Генерирует hue (0-360) из строки (username/userId).
 * Используется для Hue Identity — каждый юзер = свой цвет.
 */
export function getHueFromString(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  return Math.abs(hash) % 360;
}

/**
 * Возвращает CSS custom properties для hue identity юзера.
 * Устанавливаются на msg-row через style prop.
 */
export function getHueStyles(hue) {
  const s = 60, l = 65;
  return {
    '--sender-hue-color': `hsl(${hue}, ${s}%, ${l}%)`,
    '--sender-bubble-bg': `hsla(${hue}, ${s}%, ${l}%, 0.03)`,
    '--sender-bubble-border': `hsla(${hue}, ${s}%, ${l}%, 0.06)`,
    '--sender-bubble-inner-bg': `hsla(${hue}, ${s}%, ${l}%, 0.06)`,
    '--sender-bubble-inner-border': `hsla(${hue}, ${s}%, ${l}%, 0.08)`,
    '--sender-bubble-bg-light': `hsla(${hue}, ${s}%, 40%, 0.06)`,
    '--sender-bubble-border-light': `hsla(${hue}, ${s}%, 40%, 0.1)`,
    '--sender-bubble-inner-bg-light': `hsla(${hue}, ${s}%, 40%, 0.1)`,
  };
}
