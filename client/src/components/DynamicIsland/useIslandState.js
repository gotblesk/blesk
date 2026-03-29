import { useState, useEffect, useRef, useCallback } from 'react';
import { useChatStore } from '../../store/chatStore';
import { useCallStore } from '../../store/callStore';
import { useVoiceStore } from '../../store/voiceStore';
import { useSettingsStore } from '../../store/settingsStore';

// Priority: INCOMING(5) > CALL(4) > MESSAGE(3) > TYPING(2) > UPDATE(1) > IDLE(0)
export function useIslandState(user) {
  const [state, setState] = useState('loading');
  const [messageData, setMessageData] = useState(null);
  const [typingData, setTypingData] = useState(null);
  const [updateData, setUpdateData] = useState(null);
  const [updateProgress, setUpdateProgress] = useState(null);
  const [profileOpen, setProfileOpen] = useState(false);

  const msgTimeoutRef = useRef(null);
  const typingTimeoutRef = useRef(null);
  const msgQueueRef = useRef([]);

  const isConnected = useChatStore(s => s.isConnected);
  const incomingCall = useCallStore(s => s.incomingCall);
  const activeCall = useCallStore(s => s.activeCall);
  const voiceRoomId = useVoiceStore(s => s.currentRoomId);
  const voiceRoomName = useVoiceStore(s => s.currentRoomName);
  const dnd = useSettingsStore(s => s.dnd);

  // Determine active state based on priority
  useEffect(() => {
    if (incomingCall) { setState('incoming'); setProfileOpen(false); return; }
    if (activeCall || voiceRoomId) { setState('call'); setProfileOpen(false); return; }
    if (profileOpen) { setState('profile'); return; }
    if (messageData) { setState('message'); return; }
    if (typingData) { setState('typing'); return; }
    if (updateProgress) { setState('update_progress'); return; }
    if (updateData) { setState('update'); return; }
    if (!isConnected) { setState('loading'); return; }
    setState('idle');
  }, [incomingCall, activeCall, voiceRoomId, profileOpen, messageData, typingData, updateData, updateProgress, isConnected]);

  // Show message notification (4 sec)
  const showMessage = useCallback((msg) => {
    if (dnd) return;
    if (activeCall || voiceRoomId) return;
    if (msgTimeoutRef.current) clearTimeout(msgTimeoutRef.current);
    setMessageData(msg);
    msgTimeoutRef.current = setTimeout(() => {
      setMessageData(null);
      msgTimeoutRef.current = null;
    }, 4000);
  }, [dnd, activeCall, voiceRoomId]);

  // Show typing (5 sec timeout)
  const showTyping = useCallback((data) => {
    if (messageData) return;
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    setTypingData(data);
    typingTimeoutRef.current = setTimeout(() => {
      setTypingData(null);
      typingTimeoutRef.current = null;
    }, 5000);
  }, [messageData]);

  const clearTyping = useCallback(() => {
    setTypingData(null);
    if (typingTimeoutRef.current) { clearTimeout(typingTimeoutRef.current); typingTimeoutRef.current = null; }
  }, []);

  const toggleProfile = useCallback(() => {
    if (state === 'idle') setProfileOpen(true);
    else if (state === 'profile') setProfileOpen(false);
  }, [state]);

  const closeProfile = useCallback(() => setProfileOpen(false), []);

  const showUpdate = useCallback((version) => {
    setUpdateData({ version });
  }, []);

  const dismissUpdate = useCallback(() => {
    setUpdateData(null);
  }, []);

  const setProgress = useCallback((percent) => {
    setUpdateProgress(percent);
  }, []);

  const clearProgress = useCallback(() => {
    setUpdateProgress(null);
  }, []);

  // Cleanup
  useEffect(() => {
    return () => {
      if (msgTimeoutRef.current) clearTimeout(msgTimeoutRef.current);
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    };
  }, []);

  return {
    state,
    messageData,
    typingData,
    updateData,
    updateProgress,
    profileOpen,

    // Call data
    incomingCall,
    activeCall,
    voiceRoomId,
    voiceRoomName,

    // Actions
    showMessage,
    showTyping,
    clearTyping,
    toggleProfile,
    closeProfile,
    showUpdate,
    dismissUpdate,
    setProgress,
    clearProgress,
    setMessageData,
  };
}
