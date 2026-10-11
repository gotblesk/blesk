import { useState, useCallback, useEffect, useMemo, memo, lazy, Suspense } from 'react';
import { AnimatePresence } from 'framer-motion';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
// Eager imports — всегда видимые компоненты
import AppShell from '../AppShell/AppShell';
import TopNav from '../TopNav/TopNav';
import Sidebar from '../Sidebar/Sidebar';
import ContentArea from '../ContentArea/ContentArea';
import ChatView from '../chat/ChatView';
import VoiceControls from '../voice/VoiceControls';
import SpacesPlaceholder from '../spaces/SpacesPlaceholder';

// Lazy imports — контент вкладок (загружаются при переключении)
const FriendsScreen = lazy(() => import('../friends/FriendsScreen'));
const VoiceRoomList = lazy(() => import('../voice/VoiceRoomList'));
const VoiceRoom = lazy(() => import('../voice/VoiceRoom'));
const ChannelBrowser = lazy(() => import('../channels/ChannelBrowser'));
const ChannelView = lazy(() => import('../channels/ChannelView'));
import UpdateBanner from '../ui/UpdateBanner';
import SpotlightSearch from '../ui/SpotlightSearch';
import ErrorBoundary from '../ui/ErrorBoundary';

// Lazy imports — условные/тяжёлые компоненты
const MetaballBackground = lazy(() => import('../ui/MetaballBackground'));
const OrbitPanel = lazy(() => import('../panels/OrbitPanel'));
const VibeMeter = lazy(() => import('../panels/VibeMeter'));
const AboutScreen = lazy(() => import('../settings/AboutScreen'));
const FeedbackScreen = lazy(() => import('../settings/FeedbackScreen'));
const SettingsScreen = lazy(() => import('../settings/SettingsScreen'));
const ProfileEditor = lazy(() => import('../profile/ProfileEditor'));
const ProfilePopover = lazy(() => import('../profile/ProfilePopover'));
const IncomingCallOverlay = lazy(() => import('../voice/IncomingCallOverlay'));
const CallScreen = lazy(() => import('../voice/CallScreen'));
const AdminPanel = lazy(() => import('../admin/AdminPanel'));
import { soundTabSwitch, soundWindowOpen, soundWindowClose, soundVoiceJoin, soundVoiceLeave, soundRingtoneStop, soundClick } from '../../utils/sounds';
import { initializeShield } from '../../utils/shieldService';
import { useSocket } from '../../hooks/useSocket';
import { useVoice } from '../../hooks/useVoice';
import { useChatStore } from '../../store/chatStore';
import { useVoiceStore } from '../../store/voiceStore';
import { useCallStore } from '../../store/callStore';
import { useSettingsStore } from '../../store/settingsStore';
import { useUIStore } from '../../store/uiStore';
import { useHotkeys } from '../../hooks/useHotkeys';
import DynamicIsland from '../DynamicIsland/DynamicIsland';
import { useIslandState } from '../DynamicIsland/useIslandState';
import { WifiSlash } from '@phosphor-icons/react';
import './MainScreen.css';

// Офлайн-баннер с временем последнего соединения и анимированными точками
// Не показывается первые 4 секунды после монтирования (ждём первое подключение)
const OfflineBanner = memo(function OfflineBanner({ lastConnectedAt, visible, onReconnect }) {
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
      <span>{isConnecting ? 'Подключение...' : `Нет подключения${elapsed ? ` \u00b7 ${elapsed}` : ''}`}</span>
      <div className="offline-banner__dots">
        <div className="offline-banner__dot" />
        <div className="offline-banner__dot" />
        <div className="offline-banner__dot" />
      </div>
      {!isConnecting && (
        <button className="offline-banner__reconnect" onClick={onReconnect}>
          Переподключить
        </button>
      )}
    </div>
  );
});

export default function MainScreen({ user, onLogout, isAdmin }) {
  // ═══════ UI STATE (Zustand) ═══════
  const activeTab = useUIStore((s) => s.activeTab);
  const setActiveTab = useUIStore((s) => s.setActiveTab);
  const sidebarCollapsed = useUIStore((s) => s.sidebarCollapsed);
  const setSidebarCollapsed = useUIStore((s) => s.setSidebarCollapsed);
  const activeChatId = useUIStore((s) => s.activeChatId);
  const setActiveChatId = useUIStore((s) => s.setActiveChatId);
  const orbitOpen = useUIStore((s) => s.orbitOpen);
  const setOrbitOpen = useUIStore((s) => s.setOrbitOpen);
  const vibeOpen = useUIStore((s) => s.vibeOpen);
  const setVibeOpen = useUIStore((s) => s.setVibeOpen);
  const aboutOpen = useUIStore((s) => s.aboutOpen);
  const setAboutOpen = useUIStore((s) => s.setAboutOpen);
  const feedbackOpen = useUIStore((s) => s.feedbackOpen);
  const setFeedbackOpen = useUIStore((s) => s.setFeedbackOpen);
  const editorOpen = useUIStore((s) => s.editorOpen);
  const setEditorOpen = useUIStore((s) => s.setEditorOpen);
  const profilePopover = useUIStore((s) => s.profilePopover);
  const setProfilePopover = useUIStore((s) => s.setProfilePopover);
  const spotlightOpen = useUIStore((s) => s.spotlightOpen);
  const setSpotlightOpen = useUIStore((s) => s.setSpotlightOpen);
  const voiceExpanded = useUIStore((s) => s.voiceExpanded);
  const setVoiceExpanded = useUIStore((s) => s.setVoiceExpanded);
  const activeChannelId = useUIStore((s) => s.activeChannelId);
  const setActiveChannelId = useUIStore((s) => s.setActiveChannelId);
  const settingsOpen = useUIStore((s) => s.settingsOpen);
  const setSettingsOpen = useUIStore((s) => s.setSettingsOpen);
  const focusMode = useUIStore((s) => s.focusMode);
  const setFocusMode = useUIStore((s) => s.setFocusMode);

  // Восстановить сохранённый статус из localStorage при монтировании
  const [currentUser, setCurrentUser] = useState(() => {
    const savedStatus = localStorage.getItem('blesk-user-status');
    if (savedStatus && user) return { ...user, status: savedStatus };
    return user;
  });

  const theme = useSettingsStore((s) => s.theme);
  const socketRef = useSocket();
  const { joinRoom, leaveRoom, joinCall, leaveCall, enableCamera, disableCamera, enableScreenShare, disableScreenShare, switchScreenSource } = useVoice(socketRef);

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

  // DND из settingsStore → синхронизировать с main process (трей, уведомления)
  const dnd = useSettingsStore((s) => s.dnd);
  useEffect(() => {
    window.blesk?.syncDnd?.(!!dnd);
  }, [dnd]);

  const chats = useChatStore((s) => s.chats);
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

  // Скрывать состояние звонка в Island если пользователь видит CallScreen или VoiceRoom
  const voiceViewVisible = !!(voiceRoomId && activeTab === 'voice' && voiceExpanded);
  const callScreenVisible = !!activeCall;
  const islandState = useIslandState(currentUser, { suppressCallState: voiceViewVisible || callScreenVisible });

  // ═══════ NAVIGATION ═══════
  const handleTabChange = useCallback((tab) => {
    soundTabSwitch();
    setActiveTab(tab);
  }, []);

  const handleToggleSidebar = useCallback(() => {
    useUIStore.getState().toggleSidebar();
  }, []);

  // Открыть чат по chatId или найти/создать DM по userId
  const handleOpenChat = useCallback((chatId, userId) => {
    if (chatId) {
      soundWindowOpen();
      setActiveChatId(chatId);
      setActiveTab('chats');
      useChatStore.getState().openChat(chatId);
      return;
    }
    // Если передан userId — найти существующий DM или создать новый
    if (userId) {
      const existingChat = useChatStore.getState().chats.find(
        c => (c.type === 'chat' || c.type === 'dm') && c.otherUser?.id === userId
      );
      if (existingChat) {
        soundWindowOpen();
        setActiveChatId(existingChat.id);
        setActiveTab('chats');
        useChatStore.getState().openChat(existingChat.id);
      } else {
        // Создать DM через API
        fetch(`${API_URL}/api/chats/dm`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
          credentials: 'include',
          body: JSON.stringify({ userId }),
        })
          .then(r => r.json())
          .then(data => {
            if (data.id) {
              useChatStore.getState().loadChats().then(() => {
                soundWindowOpen();
                setActiveChatId(data.id);
                setActiveTab('chats');
                useChatStore.getState().openChat(data.id);
              });
            }
          })
          .catch(err => console.error('DM create error:', err));
      }
    }
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

  const handleInitiateVideoCall = useCallback((chatId) => {
    const socket = socketRef.current;
    if (!socket) return;
    useCallStore.getState().initiateCall(chatId, { video: true });
    socket.emit('call:initiate', { chatId, video: true });
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
      if (spotlightOpen || settingsOpen || editorOpen || feedbackOpen || aboutOpen) return;
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
  }, [leaveRoom, leaveCall, spotlightOpen, settingsOpen, editorOpen, feedbackOpen, aboutOpen]);

  // ═══════ FOCUS MODE ═══════
  useEffect(() => {
    const handler = (e) => {
      if (e.ctrlKey && e.shiftKey && e.key === 'F') {
        e.preventDefault();
        soundClick();
        useUIStore.getState().toggleFocusMode();
      }
      if (e.key === 'Escape' && focusMode) {
        setFocusMode(false);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [focusMode]);

  // ═══════ HOTKEYS ═══════
  useHotkeys({
    search: () => useUIStore.getState().setSpotlightOpen(!useUIStore.getState().spotlightOpen),
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
            onVideoCall={() => handleInitiateVideoCall(activeChatId)}
            activeCall={activeCall?.chatId === activeChatId ? activeCall : null}
            onJoinCall={() => joinCall(activeChatId)}
          />
        );

      case 'voice':
        if (voiceRoomId && voiceExpanded) {
          return (
            <Suspense fallback={null}>
              <VoiceRoom
                socketRef={socketRef}
                onToggleCamera={() => cameraOn ? disableCamera() : enableCamera()}
                onToggleScreenShare={() => screenShareOn ? disableScreenShare() : enableScreenShare()}
                onDisableScreenShare={disableScreenShare}
                onSwitchScreenSource={switchScreenSource}
                onLeave={() => { leaveRoom(); setVoiceExpanded(false); }}
              />
            </Suspense>
          );
        }
        return (
          <Suspense fallback={null}>
            <VoiceRoomList
              onJoinRoom={(roomId, roomName) => {
                soundVoiceJoin();
                joinRoom(roomId, roomName);
                setVoiceExpanded(true);
              }}
            />
          </Suspense>
        );

      case 'channels':
        if (activeChannelId) {
          return (
            <Suspense fallback={null}>
              <ChannelView
                channelId={activeChannelId}
                onBack={() => setActiveChannelId(null)}
                user={currentUser}
                socketRef={socketRef}
              />
            </Suspense>
          );
        }
        return (
          <Suspense fallback={null}>
            <ChannelBrowser onOpenChannel={(id) => setActiveChannelId(id)} />
          </Suspense>
        );

      case 'spaces':
        return <SpacesPlaceholder />;

      case 'friends':
        return (
          <Suspense fallback={null}>
            <FriendsScreen
              onBack={() => handleTabChange('chats')}
              onOpenChat={handleOpenChat}
              socketRef={socketRef}
            />
          </Suspense>
        );

      case 'admin':
        if (!isAdmin) return null;
        return (
          <Suspense fallback={null}>
            <AdminPanel onBack={() => handleTabChange('chats')} />
          </Suspense>
        );

      default:
        return null;
    }
  };

  const showPlaceholder = activeTab === 'chats' && !activeChatId;

  // ═══════ RENDER ═══════
  return (
    <main className={`main-screen${focusMode ? ' main-screen--focus' : ''}`}>
      <Suspense fallback={<div className="bg-placeholder" />}>
        <MetaballBackground
          subtle
          ambientHue={ambientHue}
          contentActive={activeTab === 'chats' || activeTab === 'friends' || activeTab === 'channels' || activeTab === 'spaces'}
        />
      </Suspense>

      <AppShell
        topNav={
          <TopNav
            onOpenChat={handleOpenChat}
            isAdmin={isAdmin}
            user={currentUser}
            onLogout={onLogout}
            onNavigate={(viewName, anchorEl) => {
              if (viewName === 'profile') {
                setProfilePopover({ open: true, userId: currentUser?.id, anchorRef: anchorEl ? { current: anchorEl } : null });
              } else if (viewName === 'settings') setSettingsOpen(true);
            }}
            onStatusChange={(status) => {
              const socket = socketRef.current;
              if (socket) socket.emit('user:status', { status });
              localStorage.setItem('blesk-user-status', status);
              setCurrentUser(prev => ({ ...prev, status }));
            }}
          />
        }
        sidebar={
          <Sidebar
            onSelectChat={handleSelectChat}
            onOpenChat={handleOpenChat}
          />
        }
        content={
          <ErrorBoundary compact>
            <ContentArea showPlaceholder={showPlaceholder} onAction={(action) => {
              if (action === 'new-chat') handleTabChange('friends');
              else if (action === 'voice') handleTabChange('voice');
              else if (action === 'channels') handleTabChange('channels');
            }}>
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
          />
        }
        offline={
          <OfflineBanner
            visible={bannerReady && !isConnected}
            lastConnectedAt={lastConnectedAt}
            onReconnect={() => {
              const socket = socketRef.current;
              if (socket && !socket.connected) socket.connect();
            }}
          />
        }
      />

      {/* ═══════ PANELS ═══════ */}
      <Suspense fallback={null}>
        <OrbitPanel open={orbitOpen} onClose={() => setOrbitOpen(false)} onOpenChat={handlePanelOpenChat} />
      </Suspense>
      <Suspense fallback={null}>
        <VibeMeter open={vibeOpen} onClose={() => setVibeOpen(false)} onOpenChat={handlePanelOpenChat} />
      </Suspense>

      {/* ═══════ MODALS ═══════ */}
      <Suspense fallback={null}>
        <SettingsScreen open={settingsOpen} onClose={() => setSettingsOpen(false)} onLogout={onLogout} onFeedback={() => setFeedbackOpen(true)} />
      </Suspense>

      <Suspense fallback={null}>
        <AboutScreen open={aboutOpen} onClose={() => setAboutOpen(false)} />
      </Suspense>
      <Suspense fallback={null}>
        <FeedbackScreen open={feedbackOpen} onClose={() => setFeedbackOpen(false)} />
      </Suspense>
      <Suspense fallback={null}>
        <ProfileEditor
          open={editorOpen}
          onClose={() => setEditorOpen(false)}
          user={currentUser}
          onUserUpdate={(updated) => setCurrentUser(prev => ({ ...prev, ...updated }))}
        />
      </Suspense>
      <Suspense fallback={null}>
        <ProfilePopover
          anchorRef={profilePopover.anchorRef}
          userId={profilePopover.userId}
          user={currentUser}
          isOpen={profilePopover.open}
          onClose={() => setProfilePopover({ open: false, userId: null, anchorRef: null })}
          onEdit={() => { setProfilePopover({ open: false, userId: null, anchorRef: null }); setEditorOpen(true); }}
          onOpenChat={(userId) => handleOpenChat(null, userId)}
        />
      </Suspense>

      {/* Входящий звонок */}
      {incomingCall && (
        <Suspense fallback={null}>
          <IncomingCallOverlay
            call={incomingCall}
            onAccept={handleAcceptCall}
            onDecline={handleDeclineCall}
          />
        </Suspense>
      )}

      {/* Личный звонок — Call Screen */}
      <AnimatePresence>
        {activeCall && (() => {
          const callChat = chats.find(c => c.id === activeCall.chatId);
          if (!callChat || callChat.type === 'group') return null;
          const otherUserId = callChat.otherUser?.id;
          const remoteStreams = otherUserId ? videoStreams[otherUserId] : null;
          return (
            <Suspense fallback={null} key="call-screen-suspense">
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
                onDisableScreenShare={disableScreenShare}
                onSwitchScreenSource={switchScreenSource}
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
            </Suspense>
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
