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
import SettingsScreen from '../settings/SettingsScreen';
import ProfileScreen from '../profile/ProfileScreen';
import StatusEditor from '../profile/StatusEditor';
import FriendsScreen from '../friends/FriendsScreen';
import VoiceRoomList from '../voice/VoiceRoomList';
import VoiceRoom from '../voice/VoiceRoom';
import VoiceControls from '../voice/VoiceControls';
import IncomingCallOverlay from '../voice/IncomingCallOverlay';
import ChannelBrowser from '../channels/ChannelBrowser';
import ChannelView from '../channels/ChannelView';
import UpdateBanner from '../ui/UpdateBanner';
import SpotlightSearch from '../ui/SpotlightSearch';
import { soundTabSwitch, soundWindowOpen, soundWindowClose, soundVoiceJoin, soundVoiceLeave, soundRingtoneStop } from '../../utils/sounds';
import { useSocket } from '../../hooks/useSocket';
import { useVoice } from '../../hooks/useVoice';
import { useChatStore } from '../../store/chatStore';
import { useVoiceStore } from '../../store/voiceStore';
import { useCallStore } from '../../store/callStore';
import { useSettingsStore } from '../../store/settingsStore';
import useWindowManager from '../../hooks/useWindowManager';
import { useHotkeys } from '../../hooks/useHotkeys';
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
  const [profileOpen, setProfileOpen] = useState(false);
  const [statusOpen, setStatusOpen] = useState(false);
  const [currentUser, setCurrentUser] = useState(user);
  const [spotlightOpen, setSpotlightOpen] = useState(false);
  const [voiceExpanded, setVoiceExpanded] = useState(false);
  const [activeChannelId, setActiveChannelId] = useState(null);
  const theme = useSettingsStore((s) => s.theme);
  const socketRef = useSocket();
  const { joinRoom, leaveRoom, joinCall, leaveCall, enableCamera, disableCamera, enableScreenShare, disableScreenShare } = useVoice(socketRef);

  // Применить тему при загрузке и смене
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Запросить разрешение на уведомления
  useEffect(() => {
    if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }, []);
  const { chats } = useChatStore();
  const voiceRoomId = useVoiceStore((s) => s.currentRoomId);
  const cameraOn = useVoiceStore((s) => s.cameraOn);
  const screenShareOn = useVoiceStore((s) => s.screenShareOn);
  const incomingCall = useCallStore((s) => s.incomingCall);
  const activeCall = useCallStore((s) => s.activeCall);

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

  // Переключение табов со звуком
  const switchTab = useCallback((tab) => {
    if (tab !== activeTab) soundTabSwitch();
    setActiveTab(tab);
  }, [activeTab]);

  // Горячие клавиши
  useHotkeys({
    search: () => setSpotlightOpen(true),
    tabChats: () => switchTab('chats'),
    tabVoice: () => switchTab('voice'),
    tabChannels: () => switchTab('channels'),
    tabFriends: () => switchTab('friends'),
    toggleMute: () => useVoiceStore.getState().toggleMute(),
    settings: () => switchTab('settings'),
  });

  const totalUnread = chats.reduce((sum, c) => sum + (c.unreadCount || 0), 0);

  // Открыть чат из ChatHub (с morph-анимацией)
  const handleOpenChat = useCallback((chatId, rect) => {
    if (windows[chatId]) {
      focusWindow(chatId);
    } else {
      soundWindowOpen();
      openWindow(chatId, rect);
    }
  }, [windows, focusWindow, openWindow]);

  // Закрыть окно чата
  const handleCloseChat = useCallback((chatId) => {
    soundWindowClose();
    closeWindow(chatId);
    useChatStore.getState().closeChat(chatId);
  }, [closeWindow]);

  // ═══ Звонки ═══
  const handleAcceptCall = useCallback(() => {
    const call = useCallStore.getState().incomingCall;
    if (!call) return;
    soundRingtoneStop();
    soundVoiceJoin();
    useCallStore.getState().acceptCall();
    const socket = socketRef.current;
    if (socket) socket.emit('call:accept', { chatId: call.chatId });
    joinCall(call.chatId, call.chatName || call.callerName);
  }, [socketRef, joinCall]);

  const handleDeclineCall = useCallback(() => {
    const call = useCallStore.getState().incomingCall;
    if (!call) return;
    soundRingtoneStop();
    const socket = socketRef.current;
    if (socket) socket.emit('call:decline', { chatId: call.chatId });
    useCallStore.getState().clearIncomingCall();
  }, [socketRef]);

  const handleInitiateCall = useCallback((chatId) => {
    const socket = socketRef.current;
    if (!socket) return;
    useCallStore.getState().initiateCall(chatId);
    socket.emit('call:initiate', { chatId });
    // Звонящий сразу подключается к голосу
    const chat = chats.find((c) => c.id === chatId);
    joinCall(chatId, chat?.name || chat?.otherUser?.username || 'Звонок');
  }, [socketRef, joinCall, chats]);

  // Открыть чат из панели (Orbit/Vibe) — без morph
  const windowsRef = useRef(windows);
  windowsRef.current = windows;

  const handlePanelOpenChat = useCallback((chatId) => {
    setOrbitOpen(false);
    setVibeOpen(false);
    setTimeout(() => {
      if (windowsRef.current[chatId]) {
        focusWindow(chatId);
      } else {
        openWindow(chatId, null);
      }
    }, 200);
  }, [focusWindow, openWindow]);

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
          onTabChange={switchTab}
          unread={{ chats: totalUnread }}
        />
        <NotificationBell onOpenChat={handleOpenChat} />
        <SpotlightProfile
          user={currentUser}
          onLogout={onLogout}
          onNavigate={(action) => {
            if (action === 'settings') setActiveTab('settings');
            if (action === 'about') setAboutOpen(true);
            if (action === 'feedback') setFeedbackOpen(true);
            if (action === 'profile') setProfileOpen(true);
            if (action === 'status') setStatusOpen(true);
            if (action === 'theme') {
              const next = useSettingsStore.getState().theme === 'dark' ? 'light' : 'dark';
              useSettingsStore.getState().setValue('theme', next);
            }
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

        {activeTab === 'voice' && (
          voiceRoomId && voiceExpanded ? (
            <VoiceRoom socketRef={socketRef} />
          ) : (
            <VoiceRoomList
              onJoinRoom={(roomId, roomName) => {
                soundVoiceJoin();
                joinRoom(roomId, roomName);
                setVoiceExpanded(true);
              }}
            />
          )
        )}

        {activeTab === 'channels' && (
          activeChannelId
            ? <ChannelView channelId={activeChannelId} onBack={() => setActiveChannelId(null)} user={currentUser} socketRef={socketRef} />
            : <ChannelBrowser onOpenChannel={(id) => setActiveChannelId(id)} />
        )}

        {activeTab === 'friends' && (
          <FriendsScreen onBack={() => setActiveTab('chats')} onOpenChat={handleOpenChat} />
        )}

        {activeTab === 'settings' && (
          <SettingsScreen onBack={() => setActiveTab('chats')} />
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
          onCall={() => handleInitiateCall(win.chatId)}
          activeCall={activeCall?.chatId === win.chatId ? activeCall : null}
          onJoinCall={() => joinCall(win.chatId)}
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
      <ProfileScreen
        open={profileOpen}
        onClose={() => setProfileOpen(false)}
        user={currentUser}
        onUserUpdate={(updated) => setCurrentUser(prev => ({ ...prev, ...updated }))}
      />
      <StatusEditor
        open={statusOpen}
        onClose={() => setStatusOpen(false)}
        user={currentUser}
        onUserUpdate={(updated) => setCurrentUser(prev => ({ ...prev, ...updated }))}
      />

      {/* Входящий звонок — оверлей */}
      {incomingCall && (
        <IncomingCallOverlay
          call={incomingCall}
          onAccept={handleAcceptCall}
          onDecline={handleDeclineCall}
        />
      )}

      {/* Баннер обновления */}
      <UpdateBanner socketRef={socketRef} />

      {/* Голосовая панель — видна на всех табах */}
      {/* Spotlight Search (Ctrl+K) */}
      <SpotlightSearch
        open={spotlightOpen}
        onClose={() => setSpotlightOpen(false)}
        onNavigate={switchTab}
        onOpenChat={(chatId) => handleOpenChat(chatId, null)}
      />

      {voiceRoomId && (
        <VoiceControls
          onLeave={() => {
            soundVoiceLeave();
            if (activeCall) {
              leaveCall(activeCall.chatId);
              useCallStore.getState().clearActiveCall();
            } else {
              leaveRoom();
            }
            setVoiceExpanded(false);
          }}
          onExpand={() => {
            if (!activeCall) {
              setActiveTab('voice');
              setVoiceExpanded(true);
            }
          }}
          cameraOn={cameraOn}
          screenShareOn={screenShareOn}
          onCameraToggle={() => cameraOn ? disableCamera() : enableCamera()}
          onScreenShareToggle={() => screenShareOn ? disableScreenShare() : enableScreenShare()}
        />
      )}
    </div>
  );
}
