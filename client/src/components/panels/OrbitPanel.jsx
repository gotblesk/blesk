import { useState, useRef, useEffect, useCallback } from 'react';
import { Planet, HandPointing } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import Glass from '../ui/Glass';
import './OrbitPanel.css';

// Вычислить позицию на орбите
function orbitPosition(index, total, radiusX, radiusY, rotation) {
  const angle = (index / total) * Math.PI * 2 + rotation;
  return {
    x: Math.cos(angle) * radiusX,
    y: Math.sin(angle) * radiusY * 0.6,
    z: Math.sin(angle),
  };
}

export default function OrbitPanel({ open, onClose, onOpenChat }) {
  const { chats, onlineUsers } = useChatStore();
  const [rotation, setRotation] = useState(0);
  const [nodesVisible, setNodesVisible] = useState(false);
  const [entranceDone, setEntranceDone] = useState(false);
  const [peekChat, setPeekChat] = useState(null);
  const [peekPos, setPeekPos] = useState({ x: 0, y: 0 });
  const longPressTimer = useRef(null);
  const animRef = useRef(null);
  const panelRef = useRef(null);

  // Анимация появления нод — задержка после открытия панели
  useEffect(() => {
    if (open) {
      // Сначала показать ноды (transition entrance)
      const t1 = setTimeout(() => setNodesVisible(true), 100);
      // Разрешить вращение только после завершения entrance-анимации
      const t2 = setTimeout(() => setEntranceDone(true), 800);
      return () => { clearTimeout(t1); clearTimeout(t2); };
    } else {
      setNodesVisible(false);
      setEntranceDone(false);
    }
  }, [open]);

  // Друзья из чатов (1-на-1)
  const friends = chats
    .filter((c) => c.otherUser)
    .map((c) => ({
      chatId: c.id,
      user: c.otherUser,
      lastMessage: c.lastMessage,
      unread: c.unreadCount || 0,
      isOnline: onlineUsers.includes(c.otherUser.id),
    }));

  // Медленное автовращение — начинается ПОСЛЕ entrance-анимации
  useEffect(() => {
    if (!entranceDone) return;
    let running = true;
    let last = performance.now();

    function tick(now) {
      if (!running) return;
      const dt = (now - last) / 1000;
      last = now;
      setRotation((r) => r + dt * 0.15); // 0.15 рад/сек
      animRef.current = requestAnimationFrame(tick);
    }
    animRef.current = requestAnimationFrame(tick);
    return () => {
      running = false;
      cancelAnimationFrame(animRef.current);
    };
  }, [entranceDone]);

  // Peek (зажатие)
  const handlePointerDown = useCallback((e, friend) => {
    const rect = e.currentTarget.getBoundingClientRect();
    longPressTimer.current = setTimeout(() => {
      setPeekChat(friend);
      setPeekPos({ x: rect.right + 12, y: rect.top });
    }, 400);
  }, []);

  const handlePointerUp = useCallback(() => {
    clearTimeout(longPressTimer.current);
  }, []);

  const handlePointerLeave = useCallback(() => {
    clearTimeout(longPressTimer.current);
    setPeekChat(null);
  }, []);

  const handleClick = useCallback((friend) => {
    clearTimeout(longPressTimer.current);
    setPeekChat(null);
    onOpenChat(friend.chatId);
  }, [onOpenChat]);

  // Получить hue для аватара
  const getHue = (user) => user.hue ?? (user.username?.charCodeAt(0) * 37) % 360;

  // Не удаляем из DOM — иначе CSS transition не работает при открытии

  const radiusX = 90;
  const radiusY = 90;

  return (
    <div className={`orbit-panel ${open ? 'orbit-panel--open' : ''}`} ref={panelRef}>
      <div className="orbit-panel__backdrop" onClick={onClose} />

      <Glass depth={3} radius={24} className="orbit-panel__body">
        <div className="orbit-panel__header">
          <span className="orbit-panel__title">Орбита</span>
          <span className="orbit-panel__count">{friends.filter((f) => f.isOnline).length} онлайн</span>
        </div>

        {friends.length > 0 ? (
          <div className="orbit-panel__stage">
            {/* Центр — "ты" */}
            <div className="orbit-panel__center">
              <div className="orbit-panel__center-glow" />
            </div>

            {/* Друзья на орбите */}
            {friends.map((friend, i) => {
              const pos = orbitPosition(i, friends.length, radiusX, radiusY, rotation);
              const scale = 0.65 + (pos.z + 1) * 0.25;
              const opacity = 0.45 + (pos.z + 1) * 0.3;
              const zIndex = Math.round((pos.z + 1) * 10);
              const hue = getHue(friend.user);
              const delay = i * 0.08;

              return (
                <div
                  key={friend.chatId}
                  className={`orbit-node ${friend.isOnline ? 'orbit-node--online' : ''} ${nodesVisible ? 'orbit-node--visible' : ''}`}
                  style={{
                    transform: nodesVisible
                      ? `translate(${pos.x}px, ${pos.y}px) scale(${scale})`
                      : `translate(0px, 0px) scale(0)`,
                    opacity: nodesVisible ? opacity : 0,
                    zIndex,
                    transitionDelay: nodesVisible && !entranceDone ? `${delay}s` : '0s',
                  }}
                  onPointerDown={(e) => handlePointerDown(e, friend)}
                  onPointerUp={handlePointerUp}
                  onPointerLeave={handlePointerLeave}
                  onClick={() => handleClick(friend)}
                >
                  <div
                    className="orbit-node__avatar"
                    style={{
                      background: `linear-gradient(135deg, hsl(${hue},70%,50%), hsl(${hue + 40},70%,60%))`,
                      boxShadow: friend.isOnline
                        ? `0 0 12px hsla(${hue},70%,50%,0.4)`
                        : 'none',
                    }}
                  >
                    {friend.user.username[0].toUpperCase()}
                  </div>
                  {friend.unread > 0 && (
                    <div className="orbit-node__badge">{friend.unread > 9 ? '9+' : friend.unread}</div>
                  )}
                  <div className="orbit-node__name">{friend.user.username}</div>
                  {friend.isOnline && <div className="orbit-node__pulse" style={{ borderColor: `hsl(${hue},70%,50%)` }} />}
                </div>
              );
            })}
          </div>
        ) : (
          <div className="orbit-panel__empty">
            <div className="orbit-panel__empty-icon"><Planet size={32} weight="regular" /></div>
            <div className="orbit-panel__empty-text">Орбита пуста</div>
            <div className="orbit-panel__empty-sub">Начните чат, чтобы добавить друзей</div>
          </div>
        )}

        {/* Подсказка снизу */}
        <div className="orbit-panel__hint">
          <span><HandPointing size={14} weight="regular" /> нажми</span> — открыть чат &nbsp;
          <span><HandPointing size={14} weight="fill" /> зажми</span> — предпросмотр
        </div>
      </Glass>

      {/* Peek preview */}
      {peekChat && (
        <div
          className="orbit-peek"
          style={{ top: peekPos.y, left: Math.min(peekPos.x, window.innerWidth - 260) }}
        >
          <Glass depth={3} radius={16} className="orbit-peek__card">
            <div className="orbit-peek__header">
              <div
                className="orbit-peek__avatar"
                style={{
                  background: `linear-gradient(135deg, hsl(${getHue(peekChat.user)},70%,50%), hsl(${getHue(peekChat.user) + 40},70%,60%))`,
                }}
              >
                {peekChat.user.username[0].toUpperCase()}
              </div>
              <div>
                <div className="orbit-peek__name">{peekChat.user.username}</div>
                <div className="orbit-peek__status">
                  {peekChat.isOnline ? <><span style={{width:8,height:8,borderRadius:'50%',background:'var(--online)',display:'inline-block',marginRight:4}} /> онлайн</> : <><span style={{width:8,height:8,borderRadius:'50%',background:'var(--offline)',display:'inline-block',marginRight:4}} /> офлайн</>}
                </div>
              </div>
            </div>
            {peekChat.lastMessage && (
              <div className="orbit-peek__msg">
                <span className="orbit-peek__msg-text">{peekChat.lastMessage.text}</span>
              </div>
            )}
          </Glass>
        </div>
      )}
    </div>
  );
}
