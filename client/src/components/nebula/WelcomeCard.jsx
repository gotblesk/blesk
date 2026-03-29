import { UsersThree, Compass, UserCircle, HandWaving } from '@phosphor-icons/react';
import './WelcomeCard.css';

export default function WelcomeCard({ username, onAction }) {
  return (
    <div className="welcome-card">
      <div className="welcome-card__inner">
        <div className="welcome-card__header">
          <span className="welcome-card__wave"><HandWaving size={24} weight="regular" /></span>
          <h2 className="welcome-card__title">
            Привет, <span className="welcome-card__name">{username}</span>!
          </h2>
        </div>

        <p className="welcome-card__desc">
          Добро пожаловать в <strong>blesk</strong> — приватный мессенджер нового поколения.
          Общайся, создавай, делись — на своих правилах.
        </p>

        <div className="welcome-card__actions">
          <button
            className="welcome-card__btn"
            onClick={() => onAction('friends')}
          >
            <div className="welcome-card__btn-icon">
              <UsersThree size={14} weight="regular" />
            </div>
            <div className="welcome-card__btn-text">
              <span className="welcome-card__btn-label">Добавить друзей</span>
              <span className="welcome-card__btn-hint">Найди знакомых</span>
            </div>
          </button>

          <button
            className="welcome-card__btn"
            onClick={() => onAction('channels')}
          >
            <div className="welcome-card__btn-icon welcome-card__btn-icon--channels">
              <Compass size={14} />
            </div>
            <div className="welcome-card__btn-text">
              <span className="welcome-card__btn-label">Открыть каналы</span>
              <span className="welcome-card__btn-hint">Подпишись на интересное</span>
            </div>
          </button>

          <button
            className="welcome-card__btn"
            onClick={() => onAction('profile')}
          >
            <div className="welcome-card__btn-icon welcome-card__btn-icon--profile">
              <UserCircle size={14} />
            </div>
            <div className="welcome-card__btn-text">
              <span className="welcome-card__btn-label">Настроить профиль</span>
              <span className="welcome-card__btn-hint">Аватар, статус, bio</span>
            </div>
          </button>
        </div>

        <div className="welcome-card__footer">
          blesk — твой блеск. Твои правила.
        </div>
      </div>
    </div>
  );
}
