import { useState, useCallback, useRef, useEffect } from 'react';
import AnimatedBackground from '../ui/AnimatedBackground';
import NavShelf from '../ui/NavShelf';
import NotificationBell from '../ui/NotificationBell';
import SpotlightProfile from '../ui/SpotlightProfile';
import ChatHub from '../chat/ChatHub';
import ChatView from '../chat/ChatView';
import OrbitPanel from '../panels/OrbitPanel';
import VibeMeter from '../panels/VibeMeter';
import AboutScreen from '../settings/AboutScreen';
import FeedbackScreen from '../settings/FeedbackScreen';
import { useSocket } from '../../hooks/useSocket';
import { useChatStore } from '../../store/chatStore';
import useWindowManager from '../../hooks/useWindowManager';
import './MainScreen.css';

// Зоны свайпа (px от края)
const EDGE_ZONE = 40;
const SWIPE_THRESHOLD = 60;

export default function MainScreen({ user, onLogout }) {
  const [activeTab, setActiveTab] = useState('chats');
  const [orbitOpen, setOrbitOpen] = useState(false);
  const [vibeOpen, setVibeOpen] = useState(false);
  const [aboutOpen, setAboutOpen] = useState(false);
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const socketRef = useSocket();
  const { chats } = useChatStore();

  // Менеджер окон чатов
  const {
    windows,
    maxZ,
    openWindow,
    closeWindow,
    focusWindow,
    moveWindow,
    resizeWindow,
    clearMorph,
  } = useWindowManager();

  const openChatIds = Object.keys(windows);

  // Свайп-жесты
  const swipeRef = useRef({ startX: 0, startY: 0, edge: null });

  useEffect(() => {
    function onPointerDown(e) {
      const x = e.clientX;
      const w = window.innerWidth;

      if (x < EDGE_ZONE) {
        swipeRef.current = { startX: x, startY: e.clientY, edge: 'left' };
      } else if (x > w - EDGE_ZONE) {
        swipeRef.current = { startX: x, startY: e.clientY, edge: 'right' };
      } else {
        swipeRef.current.edge = null;
      }
    }

    function onPointerUp(e) {
      const { startX, edge } = swipeRef.current;
      if (!edge) return;

      const dx = e.clientX - startX;
      const dy = Math.abs(e.clientY - swipeRef.current.startY);

      if (dy > Math.abs(dx)) {
        swipeRef.current.edge = null;
        return;
      }

      if (edge === 'left' && dx > SWIPE_THRESHOLD) {
        setOrbitOpen(true);
      } else if (edge === 'right' && dx < -SWIPE_THRESHOLD) {
        setVibeOpen(true);
      }

      swipeRef.current.edge = null;
    }

    window.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('pointerup', onPointerUp);
    return () => {
      window.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('pointerup', onPointerUp);
    };
  }, []);

  const totalUnread = chats.reduce((sum, c) => sum + (c.unreadCount || 0), 0);

  // Открыть чат из ChatHub (с morph-анимацией)
  const handleOpenChat = useCallback((chatId, rect) => {
    if (windows[chatId]) {
      focusWindow(chatId);
    } else {
      openWindow(chatId, rect);
    }
  }, [windows, focusWindow, openWindow]);

  // Закрыть окно чата
  const handleCloseChat = useCallback((chatId) => {
    closeWindow(chatId);
    useChatStore.getState().closeChat(chatId);
  }, [closeWindow]);

  // Открыть чат из панели (Orbit/Vibe) — без morph
  const handlePanelOpenChat = useCallback((chatId) => {
    setOrbitOpen(false);
    setVibeOpen(false);
    setTimeout(() => {
      if (windows[chatId]) {
        focusWindow(chatId);
      } else {
        openWindow(chatId, null);
      }
    }, 200);
  }, [windows, focusWindow, openWindow]);

  return (
    <div className="main-screen">
      <AnimatedBackground subtle />

      {/* Индикаторы свайп-зон */}
      {!orbitOpen && !vibeOpen && (
        <>
          <div className="swipe-hint swipe-hint--left" />
          <div className="swipe-hint swipe-hint--right" />
        </>
      )}

      <div className="main-nav">
        <NavShelf
          activeTab={activeTab}
          onTabChange={setActiveTab}
          unread={{ chats: totalUnread }}
        />
        <NotificationBell onOpenChat={handleOpenChat} />
        <SpotlightProfile
          user={user}
          onLogout={onLogout}
          onNavigate={(action) => {
            if (action === 'settings') setActiveTab('settings');
            if (action === 'about') setAboutOpen(true);
            if (action === 'feedback') setFeedbackOpen(true);
          }}
        />
      </div>

      <div className="main-content">
        {activeTab === 'chats' && (
          <div className="main-screen__chat-area">
            <ChatHub
              onOpenChat={handleOpenChat}
              visible={true}
              openChatIds={openChatIds}
            />
          </div>
        )}

        {activeTab === 'channels' && (
          <div className="main-content__center section-enter">
            <div className="placeholder-icon">📢</div>
            <div className="placeholder-title">Каналы</div>
            <div className="placeholder-sub">Скоро</div>
          </div>
        )}

        {activeTab === 'friends' && (
          <div className="main-content__center section-enter">
            <div className="placeholder-icon">👥</div>
            <div className="placeholder-title">Друзья</div>
            <div className="placeholder-sub">Скоро</div>
          </div>
        )}
      </div>

      {/* Окна чатов — fixed, вне overflow:hidden */}
      {Object.values(windows).map((win) => (
        <ChatView
          key={win.chatId}
          chatId={win.chatId}
          morphRect={win.morphRect}
          windowState={win}
          isFocused={win.zIndex === maxZ}
          onClose={() => handleCloseChat(win.chatId)}
          onFocus={() => focusWindow(win.chatId)}
          onMove={(x, y) => moveWindow(win.chatId, x, y)}
          onResize={(x, y, w, h) => resizeWindow(win.chatId, x, y, w, h)}
          onMorphEnd={() => clearMorph(win.chatId)}
          socketRef={socketRef}
        />
      ))}

      {/* Панели */}
      <OrbitPanel
        open={orbitOpen}
        onClose={() => setOrbitOpen(false)}
        onOpenChat={handlePanelOpenChat}
      />
      <VibeMeter
        open={vibeOpen}
        onClose={() => setVibeOpen(false)}
        onOpenChat={handlePanelOpenChat}
      />

      {/* Модалки */}
      <AboutScreen open={aboutOpen} onClose={() => setAboutOpen(false)} />
      <FeedbackScreen open={feedbackOpen} onClose={() => setFeedbackOpen(false)} />
    </div>
  );
}
