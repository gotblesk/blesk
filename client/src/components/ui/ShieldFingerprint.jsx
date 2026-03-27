/**
 * blesk Shield — визуальный отпечаток ключей
 * 4x4 grid цветных точек с анимированными пульсациями
 * Одинаковый паттерн у обоих собеседников — для out-of-band верификации
 */
import { useState, useEffect, useMemo } from 'react';
import { getVisualFingerprint } from '../../utils/shieldService';

export default function ShieldFingerprint({ myPublicKey, peerPublicKey, size = 'normal' }) {
  const [time, setTime] = useState(0);

  // Анимация пульсации
  useEffect(() => {
    const iv = setInterval(() => setTime(t => t + 0.05), 50);
    return () => clearInterval(iv);
  }, []);

  // Генерировать паттерн из ключей
  const cells = useMemo(() => {
    if (!myPublicKey || !peerPublicKey) return null;
    return getVisualFingerprint(myPublicKey, peerPublicKey);
  }, [myPublicKey, peerPublicKey]);

  if (!cells) return null;

  const cellSize = size === 'compact' ? 10 : size === 'large' ? 20 : 14;
  const gap = size === 'compact' ? 3 : size === 'large' ? 6 : 4;
  const gridSize = cellSize * 4 + gap * 3;
  const dotRadius = cellSize / 2;

  return (
    <div
      className="shield-fp"
      style={{
        display: 'inline-grid',
        gridTemplateColumns: `repeat(4, ${cellSize}px)`,
        gap: `${gap}px`,
        padding: gap * 2,
        borderRadius: size === 'large' ? 16 : 10,
        background: 'rgba(255,255,255,0.03)',
        border: '1px solid rgba(255,255,255,0.06)',
      }}
      title="Shield отпечаток — сравните с собеседником"
    >
      {cells.map((cell, i) => {
        const pulse = 0.7 + 0.3 * Math.sin(time + cell.phase);
        const scale = 0.85 + 0.15 * Math.sin(time * 0.7 + cell.phase * 1.3);

        return (
          <div
            key={i}
            style={{
              width: cellSize,
              height: cellSize,
              borderRadius: '50%',
              background: `hsl(${cell.hue}, ${cell.saturation}%, ${cell.lightness}%)`,
              opacity: pulse,
              transform: `scale(${scale})`,
              transition: 'transform 0.15s ease',
              boxShadow: `0 0 ${cellSize * 0.6}px hsla(${cell.hue}, ${cell.saturation}%, ${cell.lightness}%, 0.3)`,
            }}
          />
        );
      })}
    </div>
  );
}

/**
 * Shield Badge — компактный индикатор в header чата
 * Показывает что чат защищён Shield (кликабельный для просмотра отпечатка)
 */
export function ShieldBadge({ active, onClick }) {
  return (
    <button
      className="shield-badge"
      onClick={onClick}
      title={active ? 'blesk Shield активен' : 'Шифрование отключено'}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        padding: '3px 8px',
        borderRadius: 100,
        border: 'none',
        background: active ? 'rgba(200,255,0,0.08)' : 'rgba(255,255,255,0.04)',
        color: active ? '#c8ff00' : 'rgba(255,255,255,0.3)',
        fontSize: 10,
        fontWeight: 700,
        cursor: 'pointer',
        transition: 'background 0.15s, color 0.15s',
        letterSpacing: '0.02em',
      }}
    >
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        {active && <polyline points="9 12 11 14 15 10" />}
      </svg>
      Shield
    </button>
  );
}
