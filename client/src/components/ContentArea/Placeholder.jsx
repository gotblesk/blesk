import { memo } from 'react';
import './Placeholder.css';

export default memo(function Placeholder() {
  return (
    <div className="placeholder">
      <span className="placeholder__logo">
        blesk
      </span>
      <span className="placeholder__title">
        Выберите чат
      </span>
      <span className="placeholder__hint">
        <kbd className="placeholder__kbd">Ctrl</kbd>
        +
        <kbd className="placeholder__kbd">K</kbd>
      </span>
    </div>
  );
});
