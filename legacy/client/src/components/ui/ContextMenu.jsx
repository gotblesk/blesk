import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import './ContextMenu.css';

export default function ContextMenu({ x, y, items, onClose }) {
  const menuRef = useRef(null);

  // Индексы интерактивных элементов (без divider)
  const actionableIndices = useMemo(
    () => items.reduce((acc, item, i) => { if (!item.divider) acc.push(i); return acc; }, []),
    [items],
  );

  const [focusedIndex, setFocusedIndex] = useState(actionableIndices[0] ?? 0);

  // Авто-фокус на меню при открытии
  useEffect(() => {
    menuRef.current?.focus();
  }, []);

  // Закрытие по клику вне
  useEffect(() => {
    function handleClick(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) onClose();
    }
    window.addEventListener('mousedown', handleClick);
    return () => window.removeEventListener('mousedown', handleClick);
  }, [onClose]);

  // Клавиатурная навигация
  function handleKeyDown(e) {
    const pos = actionableIndices.indexOf(focusedIndex);

    switch (e.key) {
      case 'ArrowDown': {
        e.preventDefault();
        const next = pos + 1 >= actionableIndices.length ? 0 : pos + 1;
        setFocusedIndex(actionableIndices[next]);
        break;
      }
      case 'ArrowUp': {
        e.preventDefault();
        const prev = pos - 1 < 0 ? actionableIndices.length - 1 : pos - 1;
        setFocusedIndex(actionableIndices[prev]);
        break;
      }
      case 'Enter':
      case ' ': {
        e.preventDefault();
        const item = items[focusedIndex];
        if (item && !item.divider && item.onClick) {
          item.onClick();
          onClose();
        }
        break;
      }
      case 'Escape':
        e.preventDefault();
        onClose();
        break;
      case 'Tab':
        // Ловушка фокуса — не даём уйти из меню
        e.preventDefault();
        break;
      default:
        break;
    }
  }

  // Позиционирование (useLayoutEffect — синхронно до paint, убирает визуальный флэш)
  useLayoutEffect(() => {
    if (!menuRef.current) return;
    const rect = menuRef.current.getBoundingClientRect();
    if (rect.right > window.innerWidth) {
      menuRef.current.style.left = `${window.innerWidth - rect.width - 8}px`;
    }
    if (rect.bottom > window.innerHeight) {
      menuRef.current.style.top = `${window.innerHeight - rect.height - 8}px`;
    }
  }, [x, y]);

  return (
    <motion.div
      ref={menuRef}
      className="ctx-menu"
      style={{ left: x, top: y }}
      role="menu"
      tabIndex={-1}
      onKeyDown={handleKeyDown}
      initial={{ opacity: 0, scale: 0.92, y: -4 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ type: 'spring', damping: 22, stiffness: 500, mass: 0.5 }}
    >
      {items.map((item, i) =>
        item.divider ? (
          <div key={i} className="ctx-menu__divider" role="separator" />
        ) : (
          <button
            key={i}
            className={`ctx-menu__item${item.danger ? ' ctx-menu__item--danger' : ''}${i === focusedIndex ? ' ctx-menu__item--focused' : ''}`}
            role="menuitem"
            tabIndex={-1}
            data-index={i}
            onClick={() => { item.onClick(); onClose(); }}
            onMouseEnter={() => setFocusedIndex(i)}
          >
            {item.icon && <span className="ctx-menu__icon">{item.icon}</span>}
            <span>{item.label}</span>
            {item.shortcut && <span className="ctx-menu__shortcut">{item.shortcut}</span>}
          </button>
        )
      )}
    </motion.div>
  );
}
