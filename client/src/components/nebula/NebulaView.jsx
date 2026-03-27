import { useRef, useEffect, useCallback, useState } from 'react';
import { Orbit, Activity } from 'lucide-react';
import { useChatStore } from '../../store/chatStore';
import { useSettingsStore } from '../../store/settingsStore';
import Avatar from '../ui/Avatar';
import WelcomeCard from './WelcomeCard';
import './NebulaView.css';

// ═══════ CONSTANTS ═══════
// Responsive: compute card size from vw-based clamp
function computeCardSize() {
  const vw = window.innerWidth;
  // Mirrors: clamp(280px, 20vw, 420px) and clamp(62px, 4.2vw, 90px)
  const w = Math.max(280, Math.min(420, vw * 0.20));
  const h = Math.max(62, Math.min(90, vw * 0.042));
  return { w, h };
}
const GAP = 8;

// DEBUG: тестовые чаты для проверки Nebula (удалить в продакшене)
const DEBUG_CHATS = [
  { id: 'debug-1', type: 'personal', otherUser: { id: 'u1', username: 'Егор', avatar: null }, lastMessage: { text: 'видел новый дизайн?', username: 'Егор', createdAt: new Date().toISOString() }, unreadCount: 2 },
  { id: 'debug-2', type: 'personal', otherUser: { id: 'u2', username: 'Дима', avatar: null }, lastMessage: { text: 'завтра в 15:00', username: 'Дима', createdAt: new Date().toISOString() }, unreadCount: 0 },
  { id: 'debug-3', type: 'personal', otherUser: { id: 'u3', username: 'Катя', avatar: null }, lastMessage: { text: 'скинь ссылку', username: 'Катя', createdAt: new Date().toISOString() }, unreadCount: 3 },
  { id: 'debug-4', type: 'group', name: 'CS Team', lastMessage: { text: 'Миха: го катку', username: 'Миха', createdAt: new Date().toISOString() }, unreadCount: 0 },
  { id: 'debug-5', type: 'personal', otherUser: { id: 'u5', username: 'Алексей', avatar: null }, lastMessage: { text: 'как дела?', username: 'Алексей', createdAt: new Date().toISOString() }, unreadCount: 0 },
  { id: 'debug-6', type: 'group', name: 'Студия', lastMessage: { text: '3 участника', username: '', createdAt: new Date().toISOString() }, unreadCount: 0 },
  { id: 'debug-7', type: 'personal', otherUser: { id: 'u7', username: 'Маша', avatar: null }, lastMessage: { text: 'до завтра!', username: 'Маша', createdAt: new Date().toISOString() }, unreadCount: 0 },
  { id: 'debug-8', type: 'group', name: 'GOTBLESK', lastMessage: { text: 'новый дроп...', username: '', createdAt: new Date().toISOString() }, unreadCount: 5 },
];
const USE_DEBUG = false; // Переключить на false для продакшена

// ═══════ NEBULA VIEW — Physics chat cards ═══════
export default function NebulaView({ onOpenChat, onNavigate, onOpenProfile, onOpenOrbit, onOpenVibe, user }) {
  const realChats = useChatStore(s => s.chats);
  const onlineUsers = useChatStore(s => s.onlineUsers);
  const loadingChatList = useChatStore(s => s.loadingChatList);
  const chatsInitialized = useChatStore(s => s.chatsInitialized);
  const loadChats = useChatStore(s => s.loadChats);
  const chats = USE_DEBUG && realChats.length === 0 ? DEBUG_CHATS : realChats;

  // Загрузить чаты при монтировании (если ещё не загружены)
  useEffect(() => {
    if (realChats.length === 0 && !loadingChatList) {
      loadChats();
    }
  }, []); // eslint-disable-line

  // Inject debug chats into store so ChatView can find them
  useEffect(() => {
    if (USE_DEBUG && realChats.length === 0) {
      const store = useChatStore.getState();
      DEBUG_CHATS.forEach(chat => {
        if (!store.chats.find(c => c.id === chat.id)) {
          store.addChat(chat);
        }
      });
      // Add fake messages for each debug chat
      const fakeMessages = {};
      DEBUG_CHATS.forEach(chat => {
        const otherName = chat.otherUser?.username || chat.name || 'Чат';
        fakeMessages[chat.id] = [
          { id: `m1-${chat.id}`, chatId: chat.id, userId: 'u-other', username: otherName, text: 'Привет! Как тебе новый дизайн blesk?', type: 'text', createdAt: new Date(Date.now() - 300000).toISOString() },
          { id: `m2-${chat.id}`, chatId: chat.id, userId: 'me', username: user?.username || 'Я', text: 'Выглядит невероятно!', type: 'text', createdAt: new Date(Date.now() - 240000).toISOString() },
          { id: `m3-${chat.id}`, chatId: chat.id, userId: 'u-other', username: otherName, text: chat.lastMessage?.text || 'Согласен', type: 'text', createdAt: new Date(Date.now() - 60000).toISOString() },
        ];
      });
      useChatStore.setState(state => ({ messages: { ...state.messages, ...fakeMessages } }));
    }
  }, [realChats.length, user?.username]);
  const sizeRef = useRef(computeCardSize());
  const containerRef = useRef(null);
  const bodiesRef = useRef([]);
  const animRef = useRef(false);
  const frameRef = useRef(null);

  // ═══════ GRID POSITIONS ═══════
  const getGrid = useCallback(() => {
    const W = window.innerWidth;
    const H = window.innerHeight;
    const cx = W / 2, cy = H / 2;
    const { w: CW, h: CH } = sizeRef.current;
    const gx = CW + 8, gy = CH + 8;
    const cols = Math.max(2, Math.floor((W - 100) / gx));
    const positions = [];
    const startX = cx - (cols - 1) * gx / 2;
    const startY = cy - 2 * gy;

    for (let i = 0; i < 20; i++) {
      const col = i % cols;
      const row = Math.floor(i / cols);
      positions.push({
        x: startX + col * gx,
        y: startY + row * gy + 60,
      });
    }
    return positions;
  }, []);

  // ═══════ LOAD/SAVE POSITIONS ═══════
  const loadPositions = useCallback(() => {
    try {
      // Сбросить позиции при смене версии (карточки могли измениться)
      const ver = localStorage.getItem('blesk_nebula_ver');
      if (ver !== '0.6.2') {
        localStorage.removeItem('blesk_nebula');
        localStorage.setItem('blesk_nebula_ver', '0.6.2');
        return null;
      }
      const saved = JSON.parse(localStorage.getItem('blesk_nebula'));
      if (saved) {
        // Clamp to current window bounds
        const hw = sizeRef.current.w / 2, hh = sizeRef.current.h / 2;
        for (const key in saved) {
          saved[key].x = Math.max(hw, Math.min(window.innerWidth - hw, saved[key].x));
          saved[key].y = Math.max(55 + hh, Math.min(window.innerHeight - hh, saved[key].y));
        }
        return saved;
      }
    } catch (e) {}
    return null;
  }, []);

  // [IMP-7] Debounced save — не блокировать main thread из RAF
  const saveTimerRef = useRef(null);
  const savePositions = useCallback(() => {
    clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      const pos = {};
      bodiesRef.current.forEach(b => { pos[b.chatId] = { x: b.x, y: b.y }; });
      localStorage.setItem('blesk_nebula', JSON.stringify(pos));
    }, 500);
  }, []);

  // ═══════ PHYSICS TICK ═══════
  const tick = useCallback(() => {
    const bodies = bodiesRef.current;
    let motion = 0;

    bodies.forEach(b => {
      if (b.drag) { motion += 1; return; }
      if (Math.abs(b.vx) < 0.05 && Math.abs(b.vy) < 0.05) { b.vx = 0; b.vy = 0; return; }

      b.vx *= 0.992;
      b.vy *= 0.992;
      b.x += b.vx;
      b.y += b.vy;

      // Bouncy walls
      const hw = sizeRef.current.w / 2, hh = sizeRef.current.h / 2;
      const W = window.innerWidth, H = window.innerHeight;
      if (b.x < hw) { b.x = hw; b.vx = Math.abs(b.vx) * 0.55; }
      if (b.x > W - hw) { b.x = W - hw; b.vx = -Math.abs(b.vx) * 0.55; }
      if (b.y < 55 + hh) { b.y = 55 + hh; b.vy = Math.abs(b.vy) * 0.55; }
      if (b.y > H - hh) { b.y = H - hh; b.vy = -Math.abs(b.vy) * 0.55; }

      motion += Math.abs(b.vx) + Math.abs(b.vy);
    });

    // Card-card collision (only moving cards)
    for (let i = 0; i < bodies.length; i++) {
      const a = bodies[i];
      if (a.drag || (a.vx === 0 && a.vy === 0)) continue;

      for (let j = 0; j < bodies.length; j++) {
        if (j === i || bodies[j].drag) continue;
        const b = bodies[j];
        const dx = b.x - a.x, dy = b.y - a.y;
        const ox = sizeRef.current.w - Math.abs(dx), oy = sizeRef.current.h - Math.abs(dy);
        if (ox <= 0 || oy <= 0) continue;

        if (ox < oy) {
          const s = dx > 0 ? 1 : -1;
          a.x -= s * ox;
          if (b.vx === 0 && b.vy === 0) {
            b.vx = a.vx * 0.3; b.vy = a.vy * 0.15;
            a.vx *= -0.4;
          } else {
            const t = a.vx; a.vx = b.vx * 0.5; b.vx = t * 0.5;
          }
        } else {
          const s = dy > 0 ? 1 : -1;
          a.y -= s * oy;
          if (b.vx === 0 && b.vy === 0) {
            b.vy = a.vy * 0.3; b.vx = a.vx * 0.15;
            a.vy *= -0.4;
          } else {
            const t = a.vy; a.vy = b.vy * 0.5; b.vy = t * 0.5;
          }
        }
      }
    }

    // Render
    const { w: rCW, h: rCH } = sizeRef.current;
    bodies.forEach(b => {
      if (b.el) {
        b.el.style.transform = `translate(${b.x - rCW / 2}px, ${b.y - rCH / 2}px)`;
      }
      if (b.blob) {
        b.blob.style.transform = `translate(${b.x - rCW / 2}px, ${b.y - rCH / 2}px)`;
      }
    });

    if (motion < 0.05) {
      animRef.current = false;
      frameRef.current = null;
      savePositions();
      return;
    }
    frameRef.current = requestAnimationFrame(tick);
  }, [savePositions]);

  const startPhysics = useCallback(() => {
    if (animRef.current) return;
    animRef.current = true;
    frameRef.current = requestAnimationFrame(tick);
  }, [tick]);

  // ═══════ POINTER HANDLERS ═══════
  const handlePointerDown = useCallback((e, bodyIndex) => {
    const b = bodiesRef.current[bodyIndex];
    if (!b) return;
    e.preventDefault();
    // Захватить указатель — события идут на этот элемент даже когда курсор за его пределами
    e.target.setPointerCapture(e.pointerId);
    b.drag = true;
    b.t0 = Date.now();
    b.mx0 = e.clientX;
    b.my0 = e.clientY;
    b.ox = e.clientX - b.x;
    b.oy = e.clientY - b.y;
    b.vx = 0; b.vy = 0;
    b.vh = [];
    startPhysics();
  }, [startPhysics]);

  const handlePointerMove = useCallback((e, bodyIndex) => {
    const b = bodiesRef.current[bodyIndex];
    if (!b || !b.drag) return;
    const px = b.x, py = b.y;
    const hw = sizeRef.current.w / 2, hh = sizeRef.current.h / 2;
    b.x = Math.max(hw, Math.min(window.innerWidth - hw, e.clientX - b.ox));
    b.y = Math.max(hh, Math.min(window.innerHeight - hh, e.clientY - b.oy));
    b.vh.push({ dx: b.x - px, dy: b.y - py, t: Date.now() });
    if (b.vh.length > 6) b.vh.shift();

    // Push overlapping cards
    bodiesRef.current.forEach((o, j) => {
      if (j === bodyIndex || o.drag) return;
      const dx = o.x - b.x, dy = o.y - b.y;
      const ox = sizeRef.current.w - Math.abs(dx), oy = sizeRef.current.h - Math.abs(dy);
      if (ox <= 0 || oy <= 0) return;
      if (ox < oy) o.x += (dx > 0 ? 1 : -1) * ox;
      else o.y += (dy > 0 ? 1 : -1) * oy;
    });
  }, []);

  const handlePointerUp = useCallback((e, bodyIndex) => {
    const b = bodiesRef.current[bodyIndex];
    if (!b || !b.drag) return;
    // Отпустить захват указателя
    try { e.target.releasePointerCapture(e.pointerId); } catch {}
    b.drag = false;
    const dt = Date.now() - b.t0;
    const dd = Math.hypot(e.clientX - b.mx0, e.clientY - b.my0);

    if (dt < 250 && dd < 8) {
      // CLICK — open chat
      b.vx = 0; b.vy = 0;
      onOpenChat(b.chatId);
      savePositions();
      return;
    }

    const rc = b.vh.filter(v => Date.now() - v.t < 80);
    let sx = 0, sy = 0;
    if (rc.length) { rc.forEach(v => { sx += v.dx; sy += v.dy; }); sx /= rc.length; sy /= rc.length; }

    if (Math.hypot(sx, sy) > 3) {
      // FLING
      b.vx = sx * 0.3;
      b.vy = sy * 0.3;
    } else {
      // DROP — snap to nearest slot
      b.vx = 0; b.vy = 0;
      let bestD = 80, bestX = b.x, bestY = b.y;
      bodiesRef.current.forEach((o, j) => {
        if (j === bodyIndex) return;
        const { w: sCW, h: sCH } = sizeRef.current;
        const slots = [
          { x: o.x + sCW + GAP, y: o.y },
          { x: o.x - sCW - GAP, y: o.y },
          { x: o.x, y: o.y + sCH + GAP },
          { x: o.x, y: o.y - sCH - GAP },
        ];
        for (const s of slots) {
          if (s.x < sCW / 2 || s.x > window.innerWidth - sCW / 2) continue;
          if (s.y < 60 || s.y > window.innerHeight - sCH / 2) continue;
          let free = true;
          bodiesRef.current.forEach((k, ki) => {
            if (ki === bodyIndex) return;
            if (Math.abs(k.x - s.x) < sCW - 1 && Math.abs(k.y - s.y) < sCH - 1) free = false;
          });
          if (!free) continue;
          const d = Math.hypot(b.x - s.x, b.y - s.y);
          if (d < bestD) { bestD = d; bestX = s.x; bestY = s.y; }
        }
      });
      b.x = bestX; b.y = bestY;
      savePositions();
    }
  }, [onOpenChat, savePositions]);

  // ═══════ RESET ═══════
  const handleReset = useCallback(() => {
    localStorage.removeItem('blesk_nebula');
    const grid = getGrid();
    bodiesRef.current.forEach((b, i) => {
      const pos = grid[i] || { x: window.innerWidth / 2, y: window.innerHeight / 2 };
      b.x = pos.x; b.y = pos.y; b.vx = 0; b.vy = 0;
    });
    // Force re-render
    const { w: rCW, h: rCH } = sizeRef.current;
    bodiesRef.current.forEach(b => {
      if (b.el) b.el.style.transform = `translate(${b.x - rCW / 2}px, ${b.y - rCH / 2}px)`;
      if (b.blob) b.blob.style.transform = `translate(${b.x - rCW / 2}px, ${b.y - rCH / 2}px)`;
    });
    savePositions();
  }, [getGrid, savePositions]);

  // ═══════ INIT BODIES ═══════
  // Инициализация только при изменении ID чатов (не при каждом обновлении lastMessage)
  const chatIdsRef = useRef('');
  useEffect(() => {
    if (!chats.length) return;
    const newIds = chats.slice(0, 12).map(c => c.id).join(',');
    if (newIds === chatIdsRef.current && bodiesRef.current.length > 0) return;
    chatIdsRef.current = newIds;

    const saved = loadPositions();
    const grid = getGrid();

    bodiesRef.current = chats.slice(0, 12).map((chat, i) => {
      // Сохранить текущую позицию если body уже существует
      const existing = bodiesRef.current.find(b => b.chatId === chat.id);
      const pos = existing
        ? { x: existing.x, y: existing.y }
        : (saved?.[chat.id] || grid[i] || { x: window.innerWidth / 2, y: window.innerHeight / 2 });
      return {
        chatId: chat.id,
        x: Math.max(sizeRef.current.w / 2, Math.min(window.innerWidth - sizeRef.current.w / 2, pos.x)),
        y: Math.max(60, Math.min(window.innerHeight - sizeRef.current.h / 2, pos.y)),
        vx: 0, vy: 0,
        drag: false, t0: 0, mx0: 0, my0: 0, ox: 0, oy: 0, vh: [],
        el: null, blob: null,
      };
    });
  }, [chats, loadPositions, getGrid]);

  // Cleanup
  useEffect(() => {
    // Update card sizes on resize
    const onResize = () => {
      sizeRef.current = computeCardSize();
      const { w: CW, h: CH } = sizeRef.current;
      // Clamp bodies to new bounds
      bodiesRef.current.forEach(b => {
        b.x = Math.max(CW / 2, Math.min(window.innerWidth - CW / 2, b.x));
        b.y = Math.max(55 + CH / 2, Math.min(window.innerHeight - CH / 2, b.y));
        b.vx = 0; b.vy = 0;
      });
    };
    window.addEventListener('resize', onResize);
    return () => {
      window.removeEventListener('resize', onResize);
      if (frameRef.current) cancelAnimationFrame(frameRef.current);
      bodiesRef.current.forEach(b => { b.el = null; b.blob = null; });
      bodiesRef.current = [];
    };
  }, []);

  // ═══════ GET HUE FOR CHAT ═══════
  const getChatHue = (chat) => {
    if (chat.type === 'chat' && chat.otherUser) {
      // Hash username to get consistent hue
      let hash = 0;
      const name = chat.otherUser.username || '';
      for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
      return Math.abs(hash) % 360;
    }
    return 200; // groups default to cyan
  };

  const getChatColor = (chat) => {
    const hue = getChatHue(chat);
    return `hsla(${hue}, 60%, 50%, 0.12)`;
  };

  const getChatName = (chat) => {
    if (chat.otherUser?.username) return chat.otherUser.username;
    return chat.name || 'Чат';
  };

  const getChatLetter = (chat) => {
    const name = getChatName(chat);
    return name.charAt(0).toUpperCase();
  };

  const isOnline = (chat) => {
    if (chat.otherUser) {
      return onlineUsers.includes(chat.otherUser.id);
    }
    return false;
  };

  const getPreview = (chat) => {
    if (!chat.lastMessage) return '';
    const text = chat.lastMessage.text || '';
    if (chat.type === 'group' && chat.lastMessage.username) {
      return `${chat.lastMessage.username}: ${text}`;
    }
    return text;
  };

  const getTime = (chat) => {
    if (!chat.lastMessage?.createdAt) return '';
    const d = new Date(chat.lastMessage.createdAt);
    const now = new Date();
    const isToday = d.toDateString() === now.toDateString();
    if (isToday) {
      return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    }
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (d.toDateString() === yesterday.toDateString()) {
      return 'вчера';
    }
    return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });
  };

  // Welcome Card для новых пользователей
  const handleWelcomeAction = useCallback((action) => {
    if (action === 'profile' && onOpenProfile) onOpenProfile();
    else if (onNavigate) onNavigate(action);
  }, [onNavigate, onOpenProfile]);

  if (!chatsInitialized || loadingChatList) {
    return <div className="nebula" />;
  }

  if (!chats.length) {
    return (
      <div className="nebula">
        <WelcomeCard
          username={user?.username || 'друг'}
          onAction={handleWelcomeAction}
        />
      </div>
    );
  }

  return (
    <div className="nebula" ref={containerRef}>
      {/* SVG metaball filter */}
      <svg className="nebula-svg">
        <defs>
          <filter id="nebulaGoo">
            <feGaussianBlur in="SourceGraphic" stdDeviation="5" result="blur" />
            <feColorMatrix in="blur" type="matrix"
              values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 18 -7" />
          </filter>
        </defs>
      </svg>

      {/* Metaball layer */}
      <div className="nebula-metaball">
        {chats.slice(0, 12).map((chat, i) => (
          <div
            key={`blob-${chat.id}`}
            className="nebula-blob"
            style={{
              width: sizeRef.current.w, height: sizeRef.current.h,
              background: getChatColor(chat),
            }}
            ref={el => {
            if (bodiesRef.current[i]) {
              bodiesRef.current[i].blob = el;
              if (el) {
                const b = bodiesRef.current[i];
                el.style.transform = `translate(${b.x - sizeRef.current.w / 2}px, ${b.y - sizeRef.current.h / 2}px)`;
              }
            } else if (el) {
              const grid = getGrid();
              const pos = grid[i] || { x: window.innerWidth / 2, y: window.innerHeight / 2 };
              el.style.transform = `translate(${pos.x - sizeRef.current.w / 2}px, ${pos.y - sizeRef.current.h / 2}px)`;
            }
          }}
          />
        ))}
      </div>

      {/* Chat cards */}
      {chats.slice(0, 12).map((chat, i) => (
        <div
          key={chat.id}
          className="nebula-card"
          ref={el => {
            if (bodiesRef.current[i]) {
              bodiesRef.current[i].el = el;
              if (el) {
                const b = bodiesRef.current[i];
                el.style.transform = `translate(${b.x - sizeRef.current.w / 2}px, ${b.y - sizeRef.current.h / 2}px)`;
              }
            } else if (el) {
              // Bodies not ready yet — use grid fallback to avoid (0,0) bunching
              const grid = getGrid();
              const pos = grid[i] || { x: window.innerWidth / 2, y: window.innerHeight / 2 };
              el.style.transform = `translate(${pos.x - sizeRef.current.w / 2}px, ${pos.y - sizeRef.current.h / 2}px)`;
            }
          }}
          onPointerDown={e => handlePointerDown(e, i)}
          onPointerMove={e => handlePointerMove(e, i)}
          onPointerUp={e => handlePointerUp(e, i)}
          onPointerCancel={e => handlePointerUp(e, i)}
          onLostPointerCapture={e => {
            const b = bodiesRef.current[i];
            if (b?.drag) { b.drag = false; b.vx = 0; b.vy = 0; }
          }}
          style={{ touchAction: 'none' }}
        >
          <div className="nebula-card__inner">
            <Avatar
              username={getChatName(chat)}
              avatarUrl={chat.otherUser?.avatar}
              size={34}
              showOnline={isOnline(chat)}
            />
            <div className="nebula-card__info">
              <div className="nebula-card__name">{getChatName(chat)}</div>
              <div className="nebula-card__preview">{getPreview(chat)}</div>
            </div>
            <div className="nebula-card__meta">
              {/* [IMP-3] Один вызов вместо двух */}
              {(() => { const t = getTime(chat); return t ? <span className="nebula-card__time">{t}</span> : null; })()}
              {chat.unreadCount > 0 && (
                <span className="nebula-card__badge">{chat.unreadCount}</span>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
