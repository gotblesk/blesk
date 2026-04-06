import { useMemo, useRef } from 'react';
import { Fire, Lightning, ChatCircle, Cloud, Drop } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import Glass from '../ui/Glass';
import './VibeMeter.css';

// Рассчитать "энергию" общения (0-100) по количеству сообщений
function calcEnergy(chat) {
  // Простая формула: непрочитанные + активность
  const unread = chat.unreadCount || 0;
  const hasRecent = chat.lastMessage?.createdAt
    ? (Date.now() - new Date(chat.lastMessage.createdAt).getTime()) < 3600000
    : false;
  let energy = Math.min(unread * 15 + (hasRecent ? 40 : 0), 100);
  return energy;
}

// Общий вайб: эмодзи + текст
function getVibeData(avgEnergy) {
  if (avgEnergy >= 70) return { icon: <Fire size={20} weight="fill" />, label: 'Горит!', color: '#ef4444' };
  if (avgEnergy >= 45) return { icon: <Lightning size={20} weight="fill" />, label: 'Активно', color: '#c8ff00' };
  if (avgEnergy >= 20) return { icon: <ChatCircle size={20} weight="fill" />, label: 'Общаетесь', color: '#818cf8' };
  return { icon: <Cloud size={20} weight="regular" />, label: 'Спокойно', color: 'rgba(255,255,255,0.3)' };
}

export default function VibeMeter({ open, onClose, onOpenChat }) {
  const chats = useChatStore((s) => s.chats);
  const onlineUsers = useChatStore((s) => s.onlineUsers);

  // Друзья из DM-чатов, отсортированные по энергии
  const friends = useMemo(() => {
    return chats
      .filter((c) => c.otherUser)
      .map((c) => ({
        chatId: c.id,
        user: c.otherUser,
        lastMessage: c.lastMessage,
        unread: c.unreadCount || 0,
        isOnline: onlineUsers.includes(c.otherUser.id),
        energy: calcEnergy(c),
      }))
      .sort((a, b) => b.energy - a.energy);
  }, [chats, onlineUsers]);

  const avgEnergy = friends.length > 0
    ? Math.round(friends.reduce((sum, f) => sum + f.energy, 0) / friends.length)
    : 0;

  const vibe = getVibeData(avgEnergy);

  const getHue = (user) => user.hue ?? (user.username?.charCodeAt(0) * 37) % 360;

  // Стабильные высоты баров (не меняются при ре-рендере)
  const barHeightsRef = useRef({});
  const getBarHeight = (chatId, index) => {
    const key = `${chatId}-${index}`;
    if (!barHeightsRef.current[key]) {
      barHeightsRef.current[key] = 6 + Math.random() * 12;
    }
    return barHeightsRef.current[key];
  };

  // Количество баров в энерго-индикаторе
  const energyBars = (energy) => {
    const count = Math.max(1, Math.round(energy / 20));
    return Array.from({ length: count }, (_, i) => i);
  };

  // Цвет по энергии
  const energyColor = (energy) => {
    if (energy >= 70) return '#c8ff00';
    if (energy >= 40) return '#818cf8';
    return 'rgba(255,255,255,0.15)';
  };

  // Описание активности
  const activityText = (friend) => {
    if (friend.energy >= 70) return 'общаетесь часто';
    if (friend.energy >= 40) return 'активный чат';
    if (friend.energy >= 20) return 'иногда пишете';
    return 'давно не общались';
  };

  return (
    <div className={`vibe-panel ${open ? 'vibe-panel--open' : ''}`}>
      <div className="vibe-panel__backdrop" onClick={onClose} />

      <Glass depth={3} radius={24} className="vibe-panel__body">
        <div className="vibe-panel__header">
          <span className="vibe-panel__title">Вайб</span>
          <span className="vibe-panel__online">{friends.filter((f) => f.isOnline).length} онлайн</span>
        </div>

        {/* Общий вайб-метр */}
        <div className="vibe-panel__meter">
          <div className="vibe-panel__meter-emoji" style={{ color: vibe.color }}>{vibe.icon}</div>
          <div className="vibe-panel__meter-info">
            <div className="vibe-panel__meter-label">{vibe.label}</div>
            <div className="vibe-panel__meter-bar-wrap">
              <div
                className="vibe-panel__meter-bar-fill"
                style={{
                  width: `${avgEnergy}%`,
                  background: `linear-gradient(90deg, ${vibe.color}, rgba(200,255,0,0.6))`,
                }}
              />
            </div>
          </div>
          <div className="vibe-panel__meter-pct" style={{ color: vibe.color }}>
            {avgEnergy}%
          </div>
        </div>

        {/* Список друзей */}
        <div className="vibe-panel__list">
          {friends.length > 0 ? (
            friends.map((friend) => {
              const hue = getHue(friend.user);
              return (
                <div
                  key={friend.chatId}
                  className="vibe-card"
                  style={{ borderLeftColor: `hsl(${hue},70%,55%)` }}
                  onClick={() => { onOpenChat(friend.chatId); onClose(); }}
                >
                  <div
                    className="vibe-card__avatar"
                    style={{
                      background: `linear-gradient(135deg, hsl(${hue},70%,50%), hsl(${hue + 40},70%,60%))`,
                      boxShadow: friend.energy >= 50
                        ? `0 0 12px hsla(${hue},70%,50%,0.3)`
                        : 'none',
                    }}
                  >
                    {friend.user.username[0].toUpperCase()}
                    {friend.isOnline && <div className="vibe-card__online-dot" />}
                  </div>
                  <div className="vibe-card__info">
                    <div className="vibe-card__name">{friend.user.username}</div>
                    <div className="vibe-card__activity">{activityText(friend)}</div>
                  </div>
                  <div className="vibe-card__energy">
                    {energyBars(friend.energy).map((_, i) => (
                      <div
                        key={i}
                        className="vibe-card__energy-bar"
                        style={{
                          background: energyColor(friend.energy),
                          animationDelay: `${i * 0.15}s`,
                          height: `${getBarHeight(friend.chatId, i)}px`,
                        }}
                      />
                    ))}
                  </div>
                </div>
              );
            })
          ) : (
            <div className="vibe-panel__empty">
              <div className="vibe-panel__empty-icon"><Drop size={28} weight="regular" /></div>
              <div className="vibe-panel__empty-text">Нет активности</div>
            </div>
          )}
        </div>

        <div className="vibe-panel__hint">
          Чем чаще общаетесь — тем ярче карточка
        </div>
      </Glass>
    </div>
  );
}
