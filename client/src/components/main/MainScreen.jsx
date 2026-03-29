import { useState, useCallback, useRef, useEffect, useMemo, memo } from 'react';
import { AnimatePresence } from 'framer-motion';
import MetaballBackground from '../ui/MetaballBackground';
import AppShell from '../AppShell/AppShell';
import TopNav from '../TopNav/TopNav';
import Sidebar from '../Sidebar/Sidebar';
import ContentArea from '../ContentArea/ContentArea';
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
import CallScreen from '../voice/CallScreen';
import ChannelBrowser from '../channels/ChannelBrowser';
import ChannelView from '../channels/ChannelView';
import AdminPanel from '../admin/AdminPanel';
import UpdateBanner from '../ui/UpdateBanner';
import SpotlightSearch from '../ui/SpotlightSearch';
import ErrorBoundary from '../ui/ErrorBoundary';
import { soundTabSwitch, soundWindowOpen, soundWindowClose, soundVoiceJoin, soundVoiceLeave, soundRingtoneStop } from '../../utils/sounds';
import { initializeShield } from '../../utils/shieldService';
import { useSocket } from '../../hooks/useSocket';
import { useVoice } from '../../hooks/useVoice';
import { useChatStore } from '../../store/chatStore';
import { useVoiceStore } from '../../store/voiceStore';
import { useCallStore } from '../../store/callStore';
import { useSettingsStore } from '../../store/settingsStore';
import { useHotkeys } from '../../hooks/useHotkeys';
import DynamicIsland from '../DynamicIsland/DynamicIsland';
import { useIslandState } from '../DynamicIsland/useIslandState';
import { WifiSlash } from '@phosphor-icons/react';
import './MainScreen.css';

// Офлайн-баннер с временем последнего соединения и анимированными точками
// Не показывается первые 4 секунды после монтирования (ждём первое подключение)
const OfflineBanner = memo(function OfflineBanner({ lastConnectedAt, visible }) {
  const [elapsed, setElapsed] = useState('');

  useEffect(() => {
    if (!lastConnectedAt) return;
    const tick = () => {
      const diff = Math.floor((Date.now() - lastConnectedAt) / 1000);
      if (diff < 60) setElapsed(`${diff} сек назад`);
      else if (diff < 3600) setElapsed(`${Math.floor(diff / 60)} мин назад`);
      else setElapsed(`${Math.floor(diff / 3600)} ч назад`);
    };
    tick();
    const id = setInterval(tick, 10000);
    return () => clearInterval(id);
  }, [lastConnectedAt]);

  if (!visible) return null;

  // Если ещё не было успешного подключения — показать "Подключение..."
  const isConnecting = !lastConnectedAt;

  return (
    <div className="offline-banner">
      <WifiSlash size={14} weight="regular" />
      <span>{isConnecting ? 'Подключение...' : `Нет соединения${elapsed ? ` \u00b7 последний раз ${elapsed}` : ''}`}</span>
      <div className="offline-banner__dots">
        <div className="offline-banner__dot" />
        <div className="offline-banner__dot" />
        <div className="offline-banner__dot" />
      </div>
    </div>
  );
});

export default function MainScreen({ user, onLogout, isAdmin }) {
  // ═══════ CORE STATE ═══════
  const [activeTab, setActiveTab] = useState('chats');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [activeChatId, setActiveChatId] = useState(null);

  // Модалки
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
  const [settingsOpen, setSettingsOpen] = useState(false);

  const theme = useSettingsStore((s) => s.theme);
  const islandState = useIslandState(currentUser);
  const socketRef = useSocket();
  const { joinRoom, leaveRoom, joinCall, leaveCall, enableCamera, disableCamera, enableScreenShare, disableScreenShare } = useVoice(socketRef);

  // Применить тему
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Уведомления
  useEffect(() => {
    if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }, []);

  // blesk Shield — инициализация E2E
  useEffect(() => {
    initializeShield().catch(err => console.error('Shield init:', err?.message || err));
  }, []);

  // [CRIT-3] Deep links — обработка blesk:// из main process
  useEffect(() => {
    if (!window.blesk) return;
    window.blesk.onDeepLink?.(({ action, param }) => {
      if (action === 'chat' && param) handleOpenChat(param);
      if (action === 'invite' && param) {
        console.log('Deep link invite:', param);
      }
      if (action === 'channel' && param) {
        handleTabChange('channels');
      }
    });
  }, []); // eslint-disable-line

  // [CRIT-3] Клик по системному уведомлению → открыть чат
  useEffect(() => {
    if (!window.blesk) return;
    window.blesk.onNotificationOpenChat?.((chatId) => {
      if (chatId) handleOpenChat(chatId);
    });
  }, []); // eslint-disable-line

  // [CRIT-4] DND из трея → синхронизировать с settingsStore
  useEffect(() => {
    if (!window.blesk) return;
    window.blesk.onDnd?.((enabled) => {
      useSettingsStore.setState({ dnd: enabled });
    });
  }, []);

  const { chats } = useChatStore();
  const isConnected = useChatStore((s) => s.isConnected);
  const lastConnectedAt = useChatStore((s) => s.lastConnectedAt);

  // Задержка перед показом офлайн-баннера (4 сек после монтирования)
  const [bannerReady, setBannerReady] = useState(false);
  useEffect(() => {
    const timer = setTimeout(() => setBannerReady(true), 4000);
    return () => clearTimeout(timer);
  }, []);

  const voiceRoomId = useVoiceStore((s) => s.currentRoomId);
  const cameraOn = useVoiceStore((s) => s.cameraOn);
  const screenShareOn = useVoiceStore((s) => s.screenShareOn);
  const muted = useVoiceStore((s) => s.isMuted);
  const deafened = useVoiceStore((s) => s.isDeafened);
  const localCameraStream = useVoiceStore((s) => s.localCameraStream);
  const videoStreams = useVoiceStore((s) => s.videoStreams);
  const incomingCall = useCallStore((s) => s.incomingCall);
  const activeCall = useCallStore((s) => s.activeCall);

  // ═══════ NAVIGATION ═══════
  const handleTabChange = useCallback((tab) => {
    soundTabSwitch();
    setActiveTab(tab);
  }, []);

  const handleToggleSidebar = useCallback(() => {
    setSidebarCollapsed(prev => !prev);
  }, []);

  // Открыть чат (из Sidebar, SpotlightSearch, панелей, deep links)
  const handleOpenChat = useCallback((chatId) => {
    soundWindowOpen();
    setActiveChatId(chatId);
    setActiveTab('chats');
    useChatStore.getState().openChat(chatId);
  }, []);

  // Переключить чат в sidebar
  const handleSelectChat = useCallback((chatId) => {
    if (chatId === activeChatId && activeTab === 'chats') return;
    soundTabSwitch();
    setActiveChatId(chatId);
    setActiveTab('chats');
    useChatStore.getState().openChat(chatId);
  }, [activeChatId, activeTab]);

  // Закрыть текущий чат (остаёмся в chats tab)
  const handleCloseChat = useCallback(() => {
    soundWindowClose();
    if (activeChatId) {
      useChatStore.getState().closeChat(activeChatId);
    }
    setActiveChatId(null);
  }, [activeChatId]);

  // ═══════ CALLS ═══════
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
    const chat = chats.find((c) => c.id === chatId);
    joinCall(chatId, chat?.name || chat?.otherUser?.username || 'Звонок');
  }, [socketRef, joinCall, chats]);

  // Panel chat open
  const handlePanelOpenChat = useCallback((chatId) => {
    setOrbitOpen(false);
    setVibeOpen(false);
    setTimeout(() => handleOpenChat(chatId), 200);
  }, [handleOpenChat]);

  // ═══════ ESCAPE ═══════
  useEffect(() => {
    const handleEscape = (e) => {
      if (e.key !== 'Escape') return;
      // Не перехватывать если открыта модалка или Spotlight
      if (spotlightOpen || settingsOpen || profileOpen || feedbackOpen || aboutOpen) return;
      const roomId = useVoiceStore.getState().currentRoomId;
      if (!roomId) return;
      soundVoiceLeave();
      const call = useCallStore.getState().activeCall;
      if (call) {
        leaveCall(call.chatId);
        useCallStore.getState().clearActiveCall();
      } else {
        leaveRoom();
      }
      setVoiceExpanded(false);
    };
    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [leaveRoom, leaveCall, spotlightOpen, settingsOpen, profileOpen, feedbackOpen, aboutOpen]);

  // ═══════ HOTKEYS ═══════
  useHotkeys({
    search: () => setSpotlightOpen(prev => !prev),
    tabChats: () => handleTabChange('chats'),
    tabVoice: () => handleTabChange('voice'),
    tabChannels: () => handleTabChange('channels'),
    tabFriends: () => handleTabChange('friends'),
    toggleMute: () => useVoiceStore.getState().toggleMute(),
    settings: () => setSettingsOpen(true),
  });

  // [IMP-2] Ambient hue
  const ambientHue = useMemo(() => {
    if (activeCall) {
      const callChat = chats.find(c => c.id === activeCall.chatId);
      if (callChat?.otherUser) {
        let hash = 0;
        const name = callChat.otherUser.username || '';
        for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
        return Math.abs(hash) % 360;
      }
    }
    if (!activeChatId) return null;
    const chat = chats.find(c => c.id === activeChatId);
    if (!chat?.otherUser) return null;
    let hash = 0;
    const name = chat.otherUser.username || '';
    for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
    return Math.abs(hash) % 360;
  }, [activeChatId, activeCall, chats]);

  // ═══════ CONTENT RENDERING ═══════
  const renderContent = () => {
    switch (activeTab) {
      case 'chats':
        if (!activeChatId) return null;
        return (
          <ChatView
            chatId={activeChatId}
            onClose={handleCloseChat}
            socketRef={socketRef}
            onCall={() => handleInitiateCall(activeChatId)}
            activeCall={activeCall?.chatId === activeChatId ? activeCall : null}
            onJoinCall={() => joinCall(activeChatId)}
          />
        );

      case 'voice':
        if (voiceRoomId && voiceExpanded) {
          return (
            <VoiceRoom
              socketRef={socketRef}
              onToggleCamera={() => cameraOn ? disableCamera() : enableCamera()}
              onToggleScreenShare={() => screenShareOn ? disableScreenShare() : enableScreenShare()}
              onLeave={() => { leaveRoom(); setVoiceExpanded(false); }}
            />
          );
        }
        return (
          <VoiceRoomList
            onJoinRoom={(roomId, roomName) => {
              soundVoiceJoin();
              joinRoom(roomId, roomName);
              setVoiceExpanded(true);
            }}
          />
        );

      case 'channels':
        if (activeChannelId) {
          return (
            <ChannelView
              channelId={activeChannelId}
              onBack={() => setActiveChannelId(null)}
              user={currentUser}
              socketRef={socketRef}
            />
          );
        }
        return <ChannelBrowser onOpenChannel={(id) => setActiveChannelId(id)} />;

      case 'friends':
        return (
          <FriendsScreen
            onBack={() => handleTabChange('chats')}
            onOpenChat={handleOpenChat}
          />
        );

      case 'admin':
        return <AdminPanel onBack={() => handleTabChange('chats')} />;

      default:
        return null;
    }
  };

  const showPlaceholder = activeTab === 'chats' && !activeChatId;

  // ═══════ RENDER ═══════
  return (
    <main className="main-screen">
      <MetaballBackground subtle ambientHue={ambientHue} />

      <AppShell
        topNav={
          <TopNav
            activeTab={activeTab}
            onTabChange={handleTabChange}
            onToggleSidebar={handleToggleSidebar}
            onSearch={() => setSpotlightOpen(true)}
            onSettings={() => setSettingsOpen(true)}
            onOpenChat={handleOpenChat}
            isAdmin={isAdmin}
          />
        }
        sidebar={
          <Sidebar
            collapsed={sidebarCollapsed}
            activeTab={activeTab}
            activeChatId={activeChatId}
            onSelectChat={handleSelectChat}
            onOpenChat={handleOpenChat}
          />
        }
        content={
          <ErrorBoundary compact>
            <ContentArea showPlaceholder={showPlaceholder}>
              {renderContent()}
            </ContentArea>
          </ErrorBoundary>
        }
        island={
          <DynamicIsland
            islandState={islandState}
            user={currentUser}
            onAcceptCall={handleAcceptCall}
            onDeclineCall={handleDeclineCall}
            onEndCall={() => {
              soundVoiceLeave();
              leaveCall(activeCall?.chatId);
              useCallStore.getState().clearActiveCall();
            }}
            onOpenChat={handleOpenChat}
            onOpenProfile={() => setProfileOpen(true)}
            onOpenSettings={() => setSettingsOpen(true)}
            onLogout={onLogout}
            onStatusChange={(status) => {
              const socket = socketRef.current;
              if (socket) socket.emit('user:status', { status });
              setCurrentUser(prev => ({ ...prev, status }));
            }}
          />
        }
        offline={
          null /* Статус соединения показывается через Dynamic Island (точка + loading state) */
        }
      />

      {/* ═══════ PANELS ═══════ */}
      <OrbitPanel open={orbitOpen} onClose={() => setOrbitOpen(false)} onOpenChat={handlePanelOpenChat} />
      <VibeMeter open={vibeOpen} onClose={() => setVibeOpen(false)} onOpenChat={handlePanelOpenChat} />

      {/* ═══════ MODALS ═══════ */}
      <SettingsScreen open={settingsOpen} onClose={() => setSettingsOpen(false)} onLogout={onLogout} onFeedback={() => setFeedbackOpen(true)} />

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

      {/* Входящий звонок */}
      {incomingCall && (
        <IncomingCallOverlay
          call={incomingCall}
          onAccept={handleAcceptCall}
          onDecline={handleDeclineCall}
        />
      )}

      {/* Личный звонок — Call Screen */}
      <AnimatePresence>
        {activeCall && (() => {
          const callChat = chats.find(c => c.id === activeCall.chatId);
          if (!callChat || callChat.type === 'group') return null;
          const otherUserId = callChat.otherUser?.id;
          const remoteStreams = otherUserId ? videoStreams[otherUserId] : null;
          return (
            <CallScreen
              key="call-screen"
              call={activeCall}
              user={callChat.otherUser}
              muted={muted}
              deafened={deafened}
              cameraOn={cameraOn}
              onToggleMute={() => useVoiceStore.getState().toggleMute()}
              onToggleDeafen={() => useVoiceStore.getState().toggleDeafen()}
              onToggleVideo={() => cameraOn ? disableCamera() : enableCamera()}
              onToggleScreenShare={() => screenShareOn ? disableScreenShare() : enableScreenShare()}
              screenShareOn={screenShareOn}
              localVideoStream={localCameraStream}
              remoteVideoStream={remoteStreams?.camera || null}
              remoteScreenStream={remoteStreams?.screen || null}
              onEndCall={() => {
                soundVoiceLeave();
                leaveCall(activeCall.chatId);
                useCallStore.getState().clearActiveCall();
              }}
            />
          );
        })()}
      </AnimatePresence>

      <UpdateBanner socketRef={socketRef} />

      {/* Spotlight Search (Ctrl+K) */}
      <SpotlightSearch
        open={spotlightOpen}
        onClose={() => setSpotlightOpen(false)}
        onNavigate={(viewName) => {
          if (viewName === 'settings') {
            setSettingsOpen(true);
          } else {
            handleTabChange(viewName);
          }
        }}
        onOpenChat={(chatId) => handleOpenChat(chatId)}
      />

      {/* Voice Controls — показываем когда в голосовой, но НЕ на вкладке voice с развёрнутой комнатой */}
      {voiceRoomId && !(activeTab === 'voice' && voiceExpanded) && (
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
              handleTabChange('voice');
              setVoiceExpanded(true);
            }
          }}
          cameraOn={cameraOn}
          screenShareOn={screenShareOn}
          onCameraToggle={() => cameraOn ? disableCamera() : enableCamera()}
          onScreenShareToggle={() => screenShareOn ? disableScreenShare() : enableScreenShare()}
        />
      )}
    </main>
  );
}
