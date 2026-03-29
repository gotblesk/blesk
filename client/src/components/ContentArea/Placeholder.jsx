import { memo } from 'react';

export default memo(function Placeholder() {
  return (
    <div style={{
      flex: 1,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 12,
      opacity: 0.15,
      userSelect: 'none',
    }}>
      <span style={{
        fontFamily: 'var(--font-heading)',
        fontSize: 36,
        fontWeight: 800,
        letterSpacing: '-0.04em',
        color: 'var(--accent, #c8ff00)',
      }}>
        blesk
      </span>
      <span style={{ fontSize: 13, fontWeight: 500, color: 'rgba(255,255,255,0.5)' }}>
        Выберите чат
      </span>
      <span style={{ fontSize: 10, color: 'rgba(255,255,255,0.15)', display: 'flex', alignItems: 'center', gap: 4 }}>
        <kbd style={{ padding: '1px 5px', borderRadius: 3, background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.06)', fontFamily: 'var(--font-mono)', fontSize: 10 }}>Ctrl</kbd>
        +
        <kbd style={{ padding: '1px 5px', borderRadius: 3, background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.06)', fontFamily: 'var(--font-mono)', fontSize: 10 }}>K</kbd>
      </span>
    </div>
  );
});
