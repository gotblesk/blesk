import { useState, useCallback, useRef, useEffect, useMemo, memo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import MetaballBackground from '../ui/MetaballBackground';
import NebulaView from '../nebula/NebulaView';
import MiniCardsSidebar from '../nebula/MiniCardsSidebar';
import DynamicIsland from '../ui/DynamicIsland';
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
import { WifiSlash, ChatCircle } from '@phosphor-icons/react';
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
  // view: 'nebula' (главное меню с карточками) | 'chat' (sidebar + чат)
  // secondaryView: null | 'voice' | 'channels' | 'friends' | 'settings'
  const [view, setView] = useState('nebula');
  const [activeChatId, setActiveChatId] = useState(null);
  const [secondaryView, setSecondaryView] = useState(null);

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
        // Обработка инвайта — открыть URL с параметром
        console.log('Deep link invite:', param);
      }
      if (action === 'channel' && param) {
        switchToView('channels');
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
  // [Баг #26] Правильные ключи store — isMuted/isDeafened вместо muted/deafened
  const muted = useVoiceStore((s) => s.isMuted);
  const deafened = useVoiceStore((s) => s.isDeafened);
  // [Баг #10] Получить видеостримы для CallScreen
  const localCameraStream = useVoiceStore((s) => s.localCameraStream);
  const videoStreams = useVoiceStore((s) => s.videoStreams);
  const incomingCall = useCallStore((s) => s.incomingCall);
  const activeCall = useCallStore((s) => s.activeCall);

  // ═══════ NAVIGATION ═══════
  // Открыть чат из Nebula или Sidebar
  const handleOpenChat = useCallback((chatId) => {
    soundWindowOpen();
    setActiveChatId(chatId);
    setView('chat');
    setSecondaryView(null);
    // Загрузить сообщения
    useChatStore.getState().openChat(chatId);
  }, []);

  // Вернуться в Nebula (+ выйти из голосовой если подключены)
  const handleBackToNebula = useCallback(() => {
    soundWindowClose();
    // Если в голосовой комнате — выйти
    if (useVoiceStore.getState().currentRoomId) {
      soundVoiceLeave();
      const call = useCallStore.getState().activeCall;
      if (call) {
        leaveCall(call.chatId);
        useCallStore.getState().clearActiveCall();
      } else {
        leaveRoom();
      }
      setVoiceExpanded(false);
    }
    setView('nebula');
    setActiveChatId(null);
    setSecondaryView(null);
  }, [leaveRoom, leaveCall]);

  // Переключить чат в sidebar
  const handleSelectChat = useCallback((chatId) => {
    if (chatId === activeChatId) return;
    soundTabSwitch();
    setActiveChatId(chatId);
    setSecondaryView(null);
    useChatStore.getState().openChat(chatId);
  }, [activeChatId]);

  // Закрыть текущий чат (остаёмся в chat view с sidebar)
  const handleCloseChat = useCallback(() => {
    soundWindowClose();
    if (activeChatId) {
      useChatStore.getState().closeChat(activeChatId);
    }
    setActiveChatId(null);
  }, [activeChatId]);

  // Переключение secondary views
  const switchToView = useCallback((viewName) => {
    soundTabSwitch();
    // Settings открываются как модалка поверх текущего экрана
    if (viewName === 'settings') {
      setSettingsOpen(true);
      return;
    }
    if (view === 'nebula') {
      setView('chat');
    }
    setSecondaryView(viewName);
  }, [view]);

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

  // ═══════ ESCAPE — выйти из голосовой комнаты ═══════
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
      if (secondaryView === 'voice') {
        setSecondaryView(null);
        if (!activeChatId) setView('nebula');
      }
    };
    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [leaveRoom, leaveCall, spotlightOpen, settingsOpen, profileOpen, feedbackOpen, aboutOpen, secondaryView, activeChatId]);

  // ═══════ HOTKEYS ═══════
  useHotkeys({
    search: () => setSpotlightOpen(prev => !prev),
    tabChats: () => { setSecondaryView(null); if (!activeChatId) setView('nebula'); },
    tabVoice: () => switchToView('voice'),
    tabChannels: () => switchToView('channels'),
    tabFriends: () => switchToView('friends'),
    toggleMute: () => useVoiceStore.getState().toggleMute(),
    settings: () => switchToView('settings'),
  });

  // [IMP-2] Ambient hue — мемоизирован, учитывает активный звонок
  const ambientHue = useMemo(() => {
    // При активном звонке — использовать hue собеседника из звонка
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

  // ═══════ RENDER ═══════
  return (
    <main className="main-screen">
      <MetaballBackground subtle ambientHue={ambientHue} />

      {/* ═══════ NEBULA VIEW (главное меню) ═══════ */}
      {view === 'nebula' && !secondaryView && (
        <NebulaView
          onOpenChat={handleOpenChat}
          onNavigate={switchToView}
          onOpenProfile={() => setProfileOpen(true)}
          onOpenOrbit={() => setOrbitOpen(true)}
          onOpenVibe={() => setVibeOpen(true)}
          user={currentUser}
        />
      )}

      {/* ═══════ CHAT VIEW (sidebar + чат) ═══════ */}
      {view === 'chat' && (
        <div className="main-layout">
          <MiniCardsSidebar
            activeChatId={activeChatId}
            onSelectChat={handleSelectChat}
            onBack={handleBackToNebula}
          />

          <div className="main-layout__content">
            <AnimatePresence mode="wait">
              {/* Empty state: нет выбранного чата */}
              {!secondaryView && !activeChatId && (
                <motion.div key="no-chat" className="main-layout__animated main-layout__empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                  <ChatCircle size={40} weight="duotone" />
                  <span>Выберите чат, чтобы начать общение</span>
                </motion.div>
              )}

              {/* Чат */}
              {!secondaryView && activeChatId && (
                <motion.div key="chat" className="main-layout__animated" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
                  <ChatView
                    chatId={activeChatId}
                    onClose={handleCloseChat}
                    socketRef={socketRef}
                    onCall={() => handleInitiateCall(activeChatId)}
                    activeCall={activeCall?.chatId === activeChatId ? activeCall : null}
                    onJoinCall={() => joinCall(activeChatId)}
                  />
                </motion.div>
              )}

              {/* Voice */}
              {secondaryView === 'voice' && (
                <motion.div key="voice" className="main-layout__animated" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}>
                  <ErrorBoundary compact>
                    {voiceRoomId && voiceExpanded ? (
                      <VoiceRoom socketRef={socketRef} onToggleCamera={() => cameraOn ? disableCamera() : enableCamera()} onToggleScreenShare={() => screenShareOn ? disableScreenShare() : enableScreenShare()} onLeave={() => { leaveRoom(); setVoiceExpanded(false); }} />
                    ) : (
                      <VoiceRoomList
                        onJoinRoom={(roomId, roomName) => {
                          soundVoiceJoin();
                          joinRoom(roomId, roomName);
                          setVoiceExpanded(true);
                        }}
                      />
                    )}
                  </ErrorBoundary>
                </motion.div>
              )}

              {/* Channels */}
              {secondaryView === 'channels' && (
                <motion.div key="channels" className="main-layout__animated" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}>
                  <ErrorBoundary compact>
                    {activeChannelId
                      ? <ChannelView channelId={activeChannelId} onBack={() => setActiveChannelId(null)} user={currentUser} socketRef={socketRef} />
                      : <ChannelBrowser onOpenChannel={(id) => setActiveChannelId(id)} />}
                  </ErrorBoundary>
                </motion.div>
              )}

              {/* Friends */}
              {secondaryView === 'friends' && (
                <motion.div key="friends" className="main-layout__animated" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}>
                  <ErrorBoundary compact>
                    <FriendsScreen onBack={() => { setSecondaryView(null); if (!activeChatId) setView('nebula'); }} onOpenChat={handleOpenChat} />
                  </ErrorBoundary>
                </motion.div>
              )}

              {/* Admin */}
              {secondaryView === 'admin' && (
                <motion.div key="admin" className="main-layout__animated" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}>
                  <ErrorBoundary compact>
                    <AdminPanel onBack={() => { setSecondaryView(null); if (!activeChatId) setView('nebula'); }} />
                  </ErrorBoundary>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>
      )}

      {/* ═══════ SECONDARY VIEW в NEBULA MODE ═══════ */}
      {view === 'nebula' && secondaryView && (
        <div className="main-layout">
          <div className="main-layout__content">
            <AnimatePresence mode="wait">
              {secondaryView === 'voice' && (
                <motion.div key="neb-voice" className="main-layout__animated" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}>
                  {voiceRoomId && voiceExpanded ? (
                    <VoiceRoom socketRef={socketRef} onToggleCamera={() => cameraOn ? disableCamera() : enableCamera()} onToggleScreenShare={() => screenShareOn ? disableScreenShare() : enableScreenShare()} onLeave={() => { leaveRoom(); setVoiceExpanded(false); }} />
                  ) : (
                    <VoiceRoomList
                      onJoinRoom={(roomId, roomName) => {
                        soundVoiceJoin();
                        joinRoom(roomId, roomName);
                        setVoiceExpanded(true);
                      }}
                    />
                  )}
                </motion.div>
              )}
              {secondaryView === 'channels' && (
                <motion.div key="neb-channels" className="main-layout__animated" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}>
                  {activeChannelId
                    ? <ChannelView channelId={activeChannelId} onBack={() => setActiveChannelId(null)} user={currentUser} socketRef={socketRef} />
                    : <ChannelBrowser onOpenChannel={(id) => setActiveChannelId(id)} />}
                </motion.div>
              )}
              {secondaryView === 'friends' && (
                <motion.div key="neb-friends" className="main-layout__animated" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}>
                  <FriendsScreen onBack={() => { setSecondaryView(null); if (!activeChatId) setView('nebula'); }} onOpenChat={handleOpenChat} />
                </motion.div>
              )}
              {secondaryView === 'admin' && (
                <motion.div key="neb-admin" className="main-layout__animated" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}>
                  <AdminPanel onBack={() => { setSecondaryView(null); if (!activeChatId) setView('nebula'); }} />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>
      )}

      {/* ═══════ PANELS ═══════ */}
      <OrbitPanel open={orbitOpen} onClose={() => setOrbitOpen(false)} onOpenChat={handlePanelOpenChat} />
      <VibeMeter open={vibeOpen} onClose={() => setVibeOpen(false)} onOpenChat={handlePanelOpenChat} />

      {/* ═══════ MODALS ═══════ */}
      {/* Настройки — модалка поверх всего */}
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
          // [Баг #10] Достать удалённые стримы для собеседника
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

      {/* Обновление */}
      {/* [CRIT-2] Офлайн-баннер */}
      {!isConnected && <OfflineBanner lastConnectedAt={lastConnectedAt} visible={bannerReady} />}

      <UpdateBanner socketRef={socketRef} />

      {/* Spotlight Search (Ctrl+K) */}
      <SpotlightSearch
        open={spotlightOpen}
        onClose={() => setSpotlightOpen(false)}
        onNavigate={switchToView}
        onOpenChat={(chatId) => handleOpenChat(chatId)}
      />

      {/* Dynamic Island — навигация (всегда виден) */}
      <DynamicIsland
        user={currentUser}
        isAdmin={isAdmin}
        activeNav={secondaryView}
        onNavigate={switchToView}
        onOpenProfile={() => setProfileOpen(true)}
        onOpenSearch={(query) => { setSpotlightOpen(true); }}
        onStatusChange={(status) => {
          setCurrentUser(prev => ({ ...prev, status }));
        }}
      />

      {/* Voice Controls */}
      {voiceRoomId && !voiceExpanded && (
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
            // Вернуться из voice view — если был в голосовой секции, убрать её
            if (secondaryView === 'voice') {
              setSecondaryView(null);
              if (!activeChatId) setView('nebula');
            }
          }}
          onExpand={() => {
            if (!activeCall) {
              switchToView('voice');
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
