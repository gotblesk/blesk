import { Planet } from '@phosphor-icons/react';
import './SpacesPlaceholder.css';

export default function SpacesPlaceholder() {
  return (
    <div className="spaces-ph">
      <div className="spaces-ph__skeleton">
        <div className="spaces-ph__bar" />
        <div className="spaces-ph__bar spaces-ph__bar--short" />
        <div className="spaces-ph__grid">
          <div className="spaces-ph__card" />
          <div className="spaces-ph__card" />
          <div className="spaces-ph__card" />
          <div className="spaces-ph__card" />
        </div>
      </div>
      <div className="spaces-ph__content">
        <Planet size={48} weight="duotone" className="spaces-ph__icon" />
        <h2 className="spaces-ph__title">Пространства</h2>
        <p className="spaces-ph__text">Скоро</p>
        <p className="spaces-ph__hint">Создавай сообщества с текстовыми и голосовыми комнатами</p>
      </div>
    </div>
  );
}
