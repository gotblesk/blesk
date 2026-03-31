import { useState, useEffect, useRef, useCallback } from 'react';
import { useChatStore } from '../../store/chatStore';
import { useCallStore } from '../../store/callStore';
import { useVoiceStore } from '../../store/voiceStore';
import { useSettingsStore } from '../../store/settingsStore';

// Priority: INCOMING(6) > CALL(5) > RECORDING(4) > MESSAGE(3) > UPLOADING(2.5) > TYPING(2) > UPDATE(1) > IDLE(0)
export function useIslandState(user, { suppressCallState = false } = {}) {
  const [state, setState] = useState('loading');
  const [messageData, setMessageData] = useState(null);
  const [typingData, setTypingData] = useState(null);
  const [updateData, setUpdateData] = useState(null);
  const [updateProgress, setUpdateProgress] = useState(null);
  const [recordingData, setRecordingData] = useState(null); // { elapsed: number }
  const [uploadingData, setUploadingData] = useState(null); // { filename, percent }

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
    if (incomingCall && !suppressCallState) { setState('incoming'); return; }
    if ((activeCall || voiceRoomId) && !suppressCallState) { setState('call'); return; }
    if (recordingData) { setState('recording'); return; }
    if (messageData) { setState('message'); return; }
    if (uploadingData) { setState('uploading'); return; }
    if (typingData) { setState('typing'); return; }
    if (updateProgress) { setState('update_progress'); return; }
    if (updateData) { setState('update'); return; }
    if (!isConnected) { setState('loading'); return; }
    setState('idle');
  }, [incomingCall, activeCall, voiceRoomId, recordingData, messageData, uploadingData, typingData, updateData, updateProgress, isConnected, suppressCallState]);

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

  // Recording state
  const startRecordingIsland = useCallback(() => {
    setRecordingData({ startedAt: Date.now() });
  }, []);

  const stopRecordingIsland = useCallback(() => {
    setRecordingData(null);
  }, []);

  // Upload state
  const showUploading = useCallback((filename, percent) => {
    setUploadingData({ filename, percent });
  }, []);

  const clearUploading = useCallback(() => {
    setUploadingData(null);
  }, []);

  // Cleanup
  useEffect(() => {
    return () => {
      if (msgTimeoutRef.current) clearTimeout(msgTimeoutRef.current);
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    };
  }, []);

  // Глобальный хук — позволяет вызывать из любого компонента без prop drilling
  // Использование: window.__bleskIslandSet('recording', { filename: 'file.jpg', percent: 42 })
  useEffect(() => {
    window.__bleskIslandSet = (newState, data = {}) => {
      switch (newState) {
        case 'recording':
          startRecordingIsland();
          break;
        case 'recording:stop':
          stopRecordingIsland();
          break;
        case 'uploading':
          showUploading(data.filename || '', data.percent ?? 0);
          break;
        case 'uploading:done':
          clearUploading();
          break;
        default:
          break;
      }
    };
    return () => { window.__bleskIslandSet = null; };
  }, [startRecordingIsland, stopRecordingIsland, showUploading, clearUploading]);

  return {
    state,
    messageData,
    typingData,
    updateData,
    updateProgress,
    recordingData,
    uploadingData,
    // Call data
    incomingCall,
    activeCall,
    voiceRoomId,
    voiceRoomName,

    // Actions
    showMessage,
    showTyping,
    clearTyping,
    showUpdate,
    dismissUpdate,
    setProgress,
    clearProgress,
    startRecordingIsland,
    stopRecordingIsland,
    showUploading,
    clearUploading,
    setMessageData,
  };
}
