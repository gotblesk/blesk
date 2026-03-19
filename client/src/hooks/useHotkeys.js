import { useEffect } from 'react';
import { useSettingsStore } from '../store/settingsStore';

// Парсит строку "Ctrl+Shift+K" в объект для сравнения с KeyboardEvent
function parseCombo(combo) {
  const parts = combo.split('+').map((p) => p.trim().toLowerCase());
  return {
    ctrl: parts.includes('ctrl'),
    shift: parts.includes('shift'),
    alt: parts.includes('alt'),
    key: parts.filter((p) => !['ctrl', 'shift', 'alt'].includes(p))[0] || '',
  };
}

function matchesEvent(e, parsed) {
  if (e.ctrlKey !== parsed.ctrl) return false;
  if (e.shiftKey !== parsed.shift) return false;
  if (e.altKey !== parsed.alt) return false;

  const key = e.key.toLowerCase();
  // Цифры: e.key = "1" при Ctrl+1
  if (parsed.key === key) return true;
  // Запятая
  if (parsed.key === ',' && key === ',') return true;
  // Буквы
  if (parsed.key.length === 1 && key === parsed.key) return true;
  return false;
}

// actions: { actionName: callback }
export function useHotkeys(actions) {
  const hotkeys = useSettingsStore((s) => s.hotkeys);

  useEffect(() => {
    function onKeyDown(e) {
      // Не перехватывать если фокус в текстовом поле
      const tag = e.target.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || e.target.isContentEditable) {
        // Исключение: Escape и Ctrl+K работают даже в полях
        if (e.key !== 'Escape' && !(e.ctrlKey && e.key.toLowerCase() === 'k')) return;
      }

      for (const [action, callback] of Object.entries(actions)) {
        const combo = hotkeys[action];
        if (!combo) continue;
        if (matchesEvent(e, parseCombo(combo))) {
          e.preventDefault();
          callback();
          return;
        }
      }
    }

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [hotkeys, actions]);
}
