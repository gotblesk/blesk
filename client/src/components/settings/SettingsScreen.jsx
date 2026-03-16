import { useState } from 'react';
import Glass from '../ui/Glass';
import VoiceSettings from '../voice/VoiceSettings';
import { useSettingsStore } from '../../store/settingsStore';
import './SettingsScreen.css';

const SECTIONS = [
  { id: 'general', icon: '⚙️', label: 'Общие' },
  { id: 'voice', icon: '🎙', label: 'Голос и видео' },
  { id: 'notifications', icon: '🔔', label: 'Уведомления' },
  { id: 'privacy', icon: '🔒', label: 'Приватность' },
  { id: 'appearance', icon: '🎨', label: 'Оформление' },
];

export default function SettingsScreen({ onBack }) {
  const [section, setSection] = useState('general');

  const settings = useSettingsStore();
  const { toggle, setValue } = settings;

  // Переключатель темы
  const handleThemeChange = (theme) => {
    setValue('theme', theme);
    // Применить тему к document
    document.documentElement.setAttribute('data-theme', theme);
  };

  return (
    <div className="settings-screen section-enter">
      <div className="settings-screen__header">
        <button className="settings-screen__back" onClick={onBack}>
          ← Назад
        </button>
        <div className="settings-screen__title">Настройки</div>
      </div>

      <div className="settings-screen__layout">
        {/* Боковое меню */}
        <div className="settings-screen__sidebar">
          {SECTIONS.map((s) => (
            <button
              key={s.id}
              className={`settings-nav-item ${section === s.id ? 'settings-nav-item--active' : ''}`}
              onClick={() => setSection(s.id)}
            >
              <span className="settings-nav-item__icon">{s.icon}</span>
              <span className="settings-nav-item__label">{s.label}</span>
            </button>
          ))}
        </div>

        {/* Контент */}
        <div className="settings-screen__content">
          {section === 'general' && (
            <div className="settings-section">
              <div className="settings-section__title">Общие</div>

              <Glass depth={1} radius={14} className="settings-group">
                <SettingToggle
                  label="Звуки"
                  hint="Звуки уведомлений и сообщений"
                  value={settings.sounds}
                  onChange={() => toggle('sounds')}
                />
                <SettingToggle
                  label="Анимированный фон"
                  hint="Цветные шары на фоне"
                  value={settings.animatedBg}
                  onChange={() => toggle('animatedBg')}
                />
              </Glass>
            </div>
          )}

          {section === 'voice' && (
            <div className="settings-section">
              <div className="settings-section__title">Голос и видео</div>
              <Glass depth={1} radius={14} className="settings-group">
                <VoiceSettings />
              </Glass>
            </div>
          )}

          {section === 'notifications' && (
            <div className="settings-section">
              <div className="settings-section__title">Уведомления</div>

              <Glass depth={1} radius={14} className="settings-group">
                <SettingToggle
                  label="Уведомления"
                  hint="Показывать уведомления"
                  value={settings.notifications}
                  onChange={() => toggle('notifications')}
                />
                <SettingToggle
                  label="Сообщения"
                  hint="Уведомления о новых сообщениях"
                  value={settings.notifMessages}
                  onChange={() => toggle('notifMessages')}
                />
                <SettingToggle
                  label="Друзья"
                  hint="Заявки в друзья"
                  value={settings.notifFriends}
                  onChange={() => toggle('notifFriends')}
                />
                <SettingToggle
                  label="Упоминания"
                  hint="Когда вас @упоминают"
                  value={settings.notifMentions}
                  onChange={() => toggle('notifMentions')}
                />
              </Glass>
            </div>
          )}

          {section === 'privacy' && (
            <div className="settings-section">
              <div className="settings-section__title">Приватность</div>

              <Glass depth={1} radius={14} className="settings-group">
                <SettingToggle
                  label="Показывать онлайн"
                  hint="Другие видят что вы в сети"
                  value={settings.showOnline}
                  onChange={() => toggle('showOnline')}
                />
                <SettingToggle
                  label="Индикатор набора"
                  hint="Другие видят когда вы печатаете"
                  value={settings.showTyping}
                  onChange={() => toggle('showTyping')}
                />
              </Glass>
            </div>
          )}

          {section === 'appearance' && (
            <div className="settings-section">
              <div className="settings-section__title">Оформление</div>

              <Glass depth={1} radius={14} className="settings-group">
                <div className="setting-row">
                  <div className="setting-row__info">
                    <div className="setting-row__label">Тема</div>
                    <div className="setting-row__hint">Внешний вид приложения</div>
                  </div>
                  <div className="setting-theme-switcher">
                    <button
                      className={`setting-theme-btn ${settings.theme === 'dark' ? 'setting-theme-btn--active' : ''}`}
                      onClick={() => handleThemeChange('dark')}
                    >
                      Тёмная
                    </button>
                    <button
                      className={`setting-theme-btn ${settings.theme === 'light' ? 'setting-theme-btn--active' : ''}`}
                      onClick={() => handleThemeChange('light')}
                    >
                      Светлая
                    </button>
                  </div>
                </div>
                <SettingToggle
                  label="Компактные сообщения"
                  hint="Меньше отступов между сообщениями"
                  value={settings.compactMessages}
                  onChange={() => toggle('compactMessages')}
                />
              </Glass>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function SettingToggle({ label, hint, value, onChange }) {
  return (
    <div className="setting-row" onClick={onChange}>
      <div className="setting-row__info">
        <div className="setting-row__label">{label}</div>
        {hint && <div className="setting-row__hint">{hint}</div>}
      </div>
      <div className={`setting-toggle ${value ? 'setting-toggle--on' : ''}`}>
        <div className="setting-toggle__thumb" />
      </div>
    </div>
  );
}
