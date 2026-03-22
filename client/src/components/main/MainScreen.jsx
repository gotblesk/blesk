import { useState, useCallback, useRef, useEffect } from 'react';
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
import { useHotkeys } from '../../hooks/useHotkeys';
import './MainScreen.css';

export default function MainScreen({ user, onLogout }) {
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

  const { chats } = useChatStore();
  const voiceRoomId = useVoiceStore((s) => s.currentRoomId);
  const cameraOn = useVoiceStore((s) => s.cameraOn);
  const screenShareOn = useVoiceStore((s) => s.screenShareOn);
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

  // Вернуться в Nebula
  const handleBackToNebula = useCallback(() => {
    soundWindowClose();
    setView('nebula');
    setActiveChatId(null);
    setSecondaryView(null);
  }, []);

  // Переключить чат в sidebar
  const handleSelectChat = useCallback((chatId) => {
    if (chatId === activeChatId) return;
    soundTabSwitch();
    setActiveChatId(chatId);
    setSecondaryView(null);
    useChatStore.getState().openChat(chatId);
  }, [activeChatId]);

  // Закрыть текущий чат
  const handleCloseChat = useCallback(() => {
    soundWindowClose();
    if (activeChatId) {
      useChatStore.getState().closeChat(activeChatId);
    }
    setActiveChatId(null);
    setView('nebula');
  }, [activeChatId]);

  // Переключение secondary views
  const switchToView = useCallback((viewName) => {
    soundTabSwitch();
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

  // ═══════ HOTKEYS ═══════
  useHotkeys({
    search: () => setSpotlightOpen(true),
    tabChats: () => { setSecondaryView(null); if (view !== 'nebula') setView('nebula'); },
    tabVoice: () => switchToView('voice'),
    tabChannels: () => switchToView('channels'),
    tabFriends: () => switchToView('friends'),
    toggleMute: () => useVoiceStore.getState().toggleMute(),
    settings: () => switchToView('settings'),
  });

  // ═══════ AMBIENT HUE ═══════
  // Hue Identity: фон меняет цвет под активного собеседника
  const getAmbientHue = () => {
    if (!activeChatId) return null;
    const chat = chats.find(c => c.id === activeChatId);
    if (!chat?.otherUser) return null;
    let hash = 0;
    const name = chat.otherUser.username || '';
    for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
    return Math.abs(hash) % 360;
  };

  // ═══════ RENDER ═══════
  return (
    <div className="main-screen">
      <MetaballBackground subtle ambientHue={getAmbientHue()} />

      {/* ═══════ NEBULA VIEW (главное меню) ═══════ */}
      {view === 'nebula' && !secondaryView && (
        <NebulaView
          onOpenChat={handleOpenChat}
          onNavigate={switchToView}
          onOpenProfile={() => setProfileOpen(true)}
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
            {/* Чат или secondary view */}
            {!secondaryView && activeChatId && (
              <ChatView
                chatId={activeChatId}
                onClose={handleCloseChat}
                socketRef={socketRef}
                onCall={() => handleInitiateCall(activeChatId)}
                activeCall={activeCall?.chatId === activeChatId ? activeCall : null}
                onJoinCall={() => joinCall(activeChatId)}
              />
            )}

            {secondaryView === 'voice' && (
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

            {secondaryView === 'channels' && (
              activeChannelId
                ? <ChannelView channelId={activeChannelId} onBack={() => setActiveChannelId(null)} user={currentUser} socketRef={socketRef} />
                : <ChannelBrowser onOpenChannel={(id) => setActiveChannelId(id)} />
            )}

            {secondaryView === 'friends' && (
              <FriendsScreen onBack={() => { setSecondaryView(null); }} onOpenChat={handleOpenChat} />
            )}

            {secondaryView === 'settings' && (
              <SettingsScreen onBack={() => { setSecondaryView(null); }} />
            )}
          </div>
        </div>
      )}

      {/* ═══════ SECONDARY VIEW в NEBULA MODE ═══════ */}
      {view === 'nebula' && secondaryView && (
        <div className="main-layout">
          <div className="main-layout__content">
            {secondaryView === 'voice' && (
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
            {secondaryView === 'channels' && (
              activeChannelId
                ? <ChannelView channelId={activeChannelId} onBack={() => setActiveChannelId(null)} user={currentUser} socketRef={socketRef} />
                : <ChannelBrowser onOpenChannel={(id) => setActiveChannelId(id)} />
            )}
            {secondaryView === 'friends' && (
              <FriendsScreen onBack={() => setSecondaryView(null)} onOpenChat={handleOpenChat} />
            )}
            {secondaryView === 'settings' && (
              <SettingsScreen onBack={() => setSecondaryView(null)} />
            )}
          </div>
        </div>
      )}

      {/* ═══════ PANELS ═══════ */}
      <OrbitPanel open={orbitOpen} onClose={() => setOrbitOpen(false)} onOpenChat={handlePanelOpenChat} />
      <VibeMeter open={vibeOpen} onClose={() => setVibeOpen(false)} onOpenChat={handlePanelOpenChat} />

      {/* ═══════ MODALS ═══════ */}
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

      {/* Обновление */}
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
        onNavigate={switchToView}
        onOpenProfile={() => setProfileOpen(true)}
        onOpenSearch={(query) => { setSpotlightOpen(true); }}
        onStatusChange={(status) => {
          setCurrentUser(prev => ({ ...prev, status }));
        }}
      />

      {/* Voice Controls */}
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
    </div>
  );
}
