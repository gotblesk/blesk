import { memo } from 'react';
import { ChatCircle, Microphone, Radio } from '@phosphor-icons/react';
import './Placeholder.css';

const shortcuts = [
  { icon: ChatCircle, label: 'Новый чат', action: 'new-chat' },
  { icon: Microphone, label: 'Голосовая', action: 'voice' },
  { icon: Radio, label: 'Каналы', action: 'channels' },
];

export default memo(function Placeholder({ onAction }) {
  const handleClick = (action) => {
    if (onAction) onAction(action);
  };

  return (
    <div className="placeholder">
      <span className="placeholder__wordmark">blesk</span>
      <span className="placeholder__greeting">Добро пожаловать</span>

      <div className="placeholder__shortcuts">
        {shortcuts.map((s) => (
          <button
            key={s.action}
            className="placeholder__pill"
            onClick={() => handleClick(s.action)}
          >
            <s.icon size={16} weight="regular" />
            <span>{s.label}</span>
          </button>
        ))}
      </div>

      <span className="placeholder__hint">
        <kbd className="placeholder__kbd">Ctrl</kbd>
        <span className="placeholder__hint-plus">+</span>
        <kbd className="placeholder__kbd">K</kbd>
        <span className="placeholder__hint-label">&mdash; поиск</span>
      </span>
    </div>
  );
});
