import { useState, useRef, useEffect, useCallback } from 'react';
import { ArrowUp, Paperclip, X, Smiley, Microphone, PaperPlaneTilt, Pause, Play, Gif } from '@phosphor-icons/react';
import Picker from '@emoji-mart/react';
import data from '@emoji-mart/data';
import AttachmentPreview from './AttachmentPreview';
import GifPicker from './GifPicker';
import MentionSuggestions from './MentionSuggestions';
import { soundSend } from '../../utils/sounds';
import { useSettingsStore } from '../../store/settingsStore';
import { useChatStore } from '../../store/chatStore';
import './ChatInput.css';

export default function ChatInput({ chatId, onSend, onSendFiles, onTypingStart, onTypingStop, replyTo, onCancelReply, editingMsg, onCancelEdit, uploadProgress: uploadProgressProp, participants = [] }) {
  const [text, setText] = useState(() => {
    if (!chatId) return '';
    return useChatStore.getState().getDraft(chatId) || '';
  });
  const [pendingFiles, setPendingFiles] = useState([]);
  const uploadProgress = uploadProgressProp || {};
  const [dragOver, setDragOver] = useState(false);
  const [fileError, setFileError] = useState(null);
  const [isExpanded, setIsExpanded] = useState(true);
  const [isFocused, setIsFocused] = useState(false);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [showGifPicker, setShowGifPicker] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [micError, setMicError] = useState('');
  const [waveformBars, setWaveformBars] = useState(() => Array(28).fill(0.15));
  const [sendPulse, setSendPulse] = useState(false);
  const [mentionQuery, setMentionQuery] = useState(null);
  const [mentionIndex, setMentionIndex] = useState(0);

  const typingRef = useRef(false);
  const typingTimeoutRef = useRef(null);
  const inputRef = useRef(null);
  const fileInputRef = useRef(null);
  const sendBtnRef = useRef(null);
  const blurTimeoutRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);
  const recordingTimerRef = useRef(null);
  const recordingStreamRef = useRef(null);
  const recordingStartTimeRef = useRef(null);
  const analyserRef = useRef(null);
  const waveformRafRef = useRef(null);
  const cancelledRef = useRef(false);

  const theme = useSettingsStore(s => s.theme);
  const draftTimerRef = useRef(null);

  // Загрузить черновик при смене чата
  useEffect(() => {
    if (!chatId) return;
    const draft = useChatStore.getState().getDraft(chatId);
    setText(draft || '');
    // Ресайз textarea после установки текста
    setTimeout(() => {
      const el = inputRef.current;
      if (el) { el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 120) + 'px'; }
    }, 0);
  }, [chatId]);

  // Сохранение черновика с debounce 500ms
  useEffect(() => {
    if (!chatId) return;
    if (draftTimerRef.current) clearTimeout(draftTimerRef.current);
    draftTimerRef.current = setTimeout(() => {
      useChatStore.getState().saveDraft(chatId, text);
    }, 500);
    return () => { if (draftTimerRef.current) clearTimeout(draftTimerRef.current); };
  }, [text, chatId]);

  // Очистить typing timeout при unmount
  useEffect(() => {
    return () => {
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
        typingTimeoutRef.current = null;
      }
      if (blurTimeoutRef.current) {
        clearTimeout(blurTimeoutRef.current);
        blurTimeoutRef.current = null;
      }
      if (typingRef.current) {
        onTypingStop?.();
        typingRef.current = false;
      }
      cancelledRef.current = true;
      if (waveformRafRef.current) cancelAnimationFrame(waveformRafRef.current);
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        mediaRecorderRef.current.stop();
      }
      if (recordingStreamRef.current) {
        recordingStreamRef.current.getTracks().forEach(t => t.stop());
        recordingStreamRef.current = null;
      }
      clearInterval(recordingTimerRef.current);
    };
  }, [onTypingStop]);

  // Закрыть emoji picker при клике вне
  useEffect(() => {
    if (!showEmojiPicker) return;
    const handleClickOutside = (e) => {
      if (!e.target.closest('.chat-input__emoji-picker-wrap') && !e.target.closest('.chat-input__tool-btn')) {
        setShowEmojiPicker(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showEmojiPicker]);

  // Если есть replyTo — раскрыть и фокус
  useEffect(() => {
    if (replyTo) {
      setIsExpanded(true);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [replyTo]);

  // Если editingMsg — заполнить текст и раскрыть
  useEffect(() => {
    if (editingMsg) {
      setText(editingMsg.text || '');
      setIsExpanded(true);
      setTimeout(() => {
        inputRef.current?.focus();
        // Курсор в конец
        if (inputRef.current) {
          inputRef.current.selectionStart = inputRef.current.value.length;
        }
      }, 50);
    }
  }, [editingMsg]);

  // Отмена записи голосового по Escape (глобальный listener, т.к. textarea скрыта при записи)
  useEffect(() => {
    if (!isRecording) return;
    const handleEscapeRecording = (e) => {
      if (e.key === 'Escape') {
        cancelRecording();
      }
    };
    document.addEventListener('keydown', handleEscapeRecording);
    return () => document.removeEventListener('keydown', handleEscapeRecording);
  }, [isRecording]);

  // Авто-ресайз textarea
  const resizeTextarea = () => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 120) + 'px';
  };

  const handleChange = (e) => {
    const val = e.target.value;
    setText(val);
    resizeTextarea();

    // Детекция @mention
    const cursorPos = e.target.selectionStart;
    const textBefore = val.slice(0, cursorPos);
    const atMatch = textBefore.match(/@(\w*)$/);
    if (atMatch) {
      setMentionQuery(atMatch[1]);
      setMentionIndex(0);
    } else {
      setMentionQuery(null);
    }

    if (!typingRef.current && val) {
      typingRef.current = true;
      onTypingStart?.();
    }

    clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => {
      typingRef.current = false;
      onTypingStop?.();
    }, 2000);
  };

  const addFiles = useCallback((newFiles) => {
    const arr = Array.from(newFiles);
    // Лимит зависит от типа файла (согласовано с сервером)
    const getMaxSize = (file) => {
      if (file.type.startsWith('video/')) return 50 * 1024 * 1024;  // 50 МБ для видео
      if (file.type.startsWith('audio/')) return 20 * 1024 * 1024;  // 20 МБ для аудио
      return 10 * 1024 * 1024;                                      // 10 МБ для остальных
    };
    const formatLimit = (file) => {
      if (file.type.startsWith('video/')) return '50 МБ';
      if (file.type.startsWith('audio/')) return '20 МБ';
      return '10 МБ';
    };
    const valid = arr.filter((f) => f.size <= getMaxSize(f));
    if (valid.length < arr.length) {
      const rejected = arr.length - valid.length;
      const first = arr.find((f) => f.size > getMaxSize(f));
      setFileError(`${rejected} файл(ов) отклонено: макс. ${first ? formatLimit(first) : '10 МБ'}`);
      setTimeout(() => setFileError(null), 4000);
    }
    setPendingFiles((prev) => {
      const totalSize = prev.reduce((sum, f) => sum + f.size, 0) + valid.reduce((sum, f) => sum + f.size, 0);
      if (totalSize > 50 * 1024 * 1024) {
        setFileError('Общий размер файлов не может превышать 50 МБ');
        setTimeout(() => setFileError(null), 4000);
        return prev;
      }
      return [...prev, ...valid].slice(0, 10);
    });
  }, []);

  const removeFile = useCallback((index) => {
    setPendingFiles((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const MAX_MESSAGE_LENGTH = 4000;

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed && !pendingFiles.length) return;

    // Лимит длины сообщения
    if (trimmed.length > MAX_MESSAGE_LENGTH) {
      setText(trimmed.slice(0, MAX_MESSAGE_LENGTH));
      return;
    }

    // Звук
    soundSend();

    // Pulse ring на кнопке отправки
    setSendPulse(true);
    setTimeout(() => setSendPulse(false), 500);

    // Ripple на кнопке отправки
    const btn = sendBtnRef.current;
    if (btn) {
      const ripple = document.createElement('div');
      ripple.className = 'chat-input__send-ripple';
      btn.appendChild(ripple);
      setTimeout(() => ripple.remove(), 600);
    }

    if (pendingFiles.length > 0) {
      onSendFiles?.(pendingFiles, trimmed);
      setPendingFiles([]);
    } else {
      onSend(trimmed);
    }

    setText('');
    setMentionQuery(null);
    if (chatId) useChatStore.getState().clearDraft(chatId);
    if (inputRef.current) inputRef.current.style.height = '';
    typingRef.current = false;
    onTypingStop?.();
    clearTimeout(typingTimeoutRef.current);
  };

  // Вставка @mention в текст
  const handleMentionSelect = useCallback((username) => {
    const el = inputRef.current;
    if (!el) return;
    const cursorPos = el.selectionStart;
    const textBefore = text.slice(0, cursorPos);
    const textAfter = text.slice(cursorPos);
    const atIdx = textBefore.lastIndexOf('@');
    if (atIdx === -1) return;
    const newText = textBefore.slice(0, atIdx) + '@' + username + ' ' + textAfter;
    setText(newText);
    setMentionQuery(null);
    setMentionIndex(0);
    // Курсор после вставленного username
    const newCursor = atIdx + username.length + 2; // @ + username + space
    setTimeout(() => {
      el.focus();
      el.selectionStart = el.selectionEnd = newCursor;
    }, 0);
  }, [text]);

  // Фильтрованные участники для mention (нужно для навигации клавишами)
  const mentionFiltered = mentionQuery !== null
    ? participants.filter(p => p.username.toLowerCase().includes(mentionQuery.toLowerCase())).slice(0, 6)
    : [];

  // [IMP-5] getState() вместо подписки — не вызывает ре-рендер
  const handleKeyDown = (e) => {
    // Клавиатурная навигация по @mention popup
    if (mentionQuery !== null && mentionFiltered.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setMentionIndex(i => (i + 1) % mentionFiltered.length);
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setMentionIndex(i => (i - 1 + mentionFiltered.length) % mentionFiltered.length);
        return;
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        handleMentionSelect(mentionFiltered[mentionIndex]?.username);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        setMentionQuery(null);
        return;
      }
    }

    if (e.key === 'Enter') {
      const enterToSend = useSettingsStore.getState().enterToSend;
      if (enterToSend && !e.shiftKey) {
        // Enter = отправка, Shift+Enter = новая строка
        e.preventDefault();
        handleSend();
      } else if (!enterToSend && (e.ctrlKey || e.metaKey)) {
        // Ctrl+Enter = отправка, Enter = новая строка
        e.preventDefault();
        handleSend();
      }
    }
    if (e.key === 'Escape' && editingMsg) {
      onCancelEdit?.();
      setText('');
      return;
    }
    if (e.key === 'Escape' && replyTo) {
      onCancelReply?.();
    }
  };

  // Вставка изображений из буфера обмена
  const handlePaste = useCallback((e) => {
    const items = e.clipboardData?.items;
    if (!items) return;
    const imageFiles = [];
    for (const item of items) {
      if (item.type.startsWith('image/')) {
        const file = item.getAsFile();
        if (file) imageFiles.push(file);
      }
    }
    if (imageFiles.length > 0) {
      e.preventDefault();
      addFiles(imageFiles);
    }
  }, [addFiles]);

  // Drag-and-drop
  const handleDragOver = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragOver(false);
  }, []);

  const handleDrop = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragOver(false);
    if (e.dataTransfer?.files?.length) {
      addFiles(e.dataTransfer.files);
    }
  }, [addFiles]);

  const handleFileSelect = useCallback((e) => {
    if (e.target.files?.length) {
      addFiles(e.target.files);
    }
    e.target.value = '';
  }, [addFiles]);

  // Compact → Expanded
  const expandAndFocus = () => {
    setIsExpanded(true);
    setTimeout(() => inputRef.current?.focus(), 50);
  };

  // Focus / Blur
  const handleFocus = () => {
    if (blurTimeoutRef.current) {
      clearTimeout(blurTimeoutRef.current);
      blurTimeoutRef.current = null;
    }
    setIsFocused(true);
    setIsExpanded(true);
  };

  const handleBlur = () => {
    setIsFocused(false);
  };

  // Emoji picker
  const handleEmojiSelect = (emoji) => {
    const sym = emoji.native;
    if (!sym) return;
    const el = inputRef.current;
    if (el) {
      const start = el.selectionStart;
      const end = el.selectionEnd;
      const newText = text.slice(0, start) + sym + text.slice(end);
      setText(newText);
      setTimeout(() => {
        el.selectionStart = el.selectionEnd = start + sym.length;
        el.focus();
      }, 0);
    } else {
      setText(prev => prev + sym);
    }
  };

  // GIF picker
  const handleGifSelect = (gifUrl) => {
    if (!gifUrl) return;
    soundSend();
    setSendPulse(true);
    setTimeout(() => setSendPulse(false), 500);
    onSend(gifUrl);
    setShowGifPicker(false);
  };

  // Voice recording
  const formatRecordTime = (seconds) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  const MAX_VOICE_DURATION = 60;
  const MIN_VOICE_DURATION = 1;
  const VOICE_WARNING_AT = 55; // предупреждение за 5 секунд до лимита

  // Выбор MIME-типа с fallback
  const getRecorderMimeType = () => {
    if (typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported('audio/webm;codecs=opus')) {
      return 'audio/webm;codecs=opus';
    }
    return 'audio/webm';
  };

  // Анимация waveform через AnalyserNode
  const startWaveformAnimation = (analyser) => {
    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);
    const BAR_COUNT = 28;

    const tick = () => {
      analyser.getByteFrequencyData(dataArray);
      const step = Math.floor(bufferLength / BAR_COUNT);
      const bars = Array.from({ length: BAR_COUNT }, (_, i) => {
        const slice = dataArray.slice(i * step, (i + 1) * step);
        const avg = slice.reduce((s, v) => s + v, 0) / slice.length;
        return Math.max(0.08, avg / 255);
      });
      setWaveformBars(bars);
      waveformRafRef.current = requestAnimationFrame(tick);
    };
    waveformRafRef.current = requestAnimationFrame(tick);
  };

  const stopWaveformAnimation = () => {
    if (waveformRafRef.current) {
      cancelAnimationFrame(waveformRafRef.current);
      waveformRafRef.current = null;
    }
    setWaveformBars(Array(28).fill(0.15));
  };

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 48000,
          channelCount: 1,
        },
      });

      // Обработка отключения микрофона во время записи
      const audioTrack = stream.getAudioTracks()[0];
      if (audioTrack) {
        audioTrack.onended = () => {
          console.warn('[blesk] Mic track ended during recording');
          cancelRecording();
          setMicError('Микрофон отключён');
          setTimeout(() => setMicError(''), 3000);
        };
      }

      // Web Audio analyser для живого waveform
      try {
        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        const source = audioCtx.createMediaStreamSource(stream);
        const analyser = audioCtx.createAnalyser();
        analyser.fftSize = 256;
        analyser.smoothingTimeConstant = 0.7;
        source.connect(analyser);
        analyserRef.current = analyser;
        startWaveformAnimation(analyser);
      } catch { /* Если Web Audio API недоступен — waveform будет статичным */ }

      const mimeType = getRecorderMimeType();
      const mediaRecorder = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 48000 });
      mediaRecorderRef.current = mediaRecorder;
      recordingStreamRef.current = stream;
      audioChunksRef.current = [];
      recordingStartTimeRef.current = Date.now();
      cancelledRef.current = false;

      // timeslice 100ms — чанки приходят регулярно, не только при stop()
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunksRef.current.push(e.data);
      };

      mediaRecorder.onstop = () => {
        stopWaveformAnimation();
        stream.getTracks().forEach(t => t.stop());
        recordingStreamRef.current = null;
        clearInterval(recordingTimerRef.current);

        if (cancelledRef.current) {
          // Запись отменена — не отправлять
          audioChunksRef.current = [];
          setRecordingTime(0);
          return;
        }

        const durationSec = (Date.now() - (recordingStartTimeRef.current || 0)) / 1000;
        const blob = new Blob(audioChunksRef.current, { type: 'audio/webm' });

        if (durationSec >= MIN_VOICE_DURATION && blob.size > 0) {
          const file = new File([blob], `voice-${Date.now()}.webm`, { type: 'audio/webm' });
          soundSend();
          onSendFiles?.([file], '');
        }

        setRecordingTime(0);
      };

      mediaRecorder.start(100);
      setIsRecording(true);
      setRecordingTime(0);
      window.__bleskIslandSet?.('recording');
      recordingTimerRef.current = setInterval(() => {
        setRecordingTime(t => {
          const next = t + 1;
          if (next >= MAX_VOICE_DURATION) {
            setTimeout(() => stopRecording(), 0);
            return t;
          }
          return next;
        });
      }, 1000);
    } catch (err) {
      console.error('Mic error:', err);
      let msg = 'Ошибка записи: ' + (err.message || 'неизвестная');
      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        msg = 'Нет доступа к микрофону. Разрешите в настройках системы.';
      } else if (err.name === 'NotFoundError') {
        msg = 'Микрофон не найден';
      }
      setMicError(msg);
      setTimeout(() => setMicError(''), 3000);
      setIsRecording(false);
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    setIsRecording(false);
    setIsPaused(false);
    clearInterval(recordingTimerRef.current);
    stopWaveformAnimation();
    window.__bleskIslandSet?.('recording:stop');
  };

  // V4 FIX: флаг выставляется ПОСЛЕ вызова stop(), чтобы onstop корректно прочитал его
  const cancelRecording = () => {
    stopWaveformAnimation();
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    // Флаг после stop() — onstop проверит его и не отправит запись
    cancelledRef.current = true;
    window.__bleskIslandSet?.('recording:stop');
    if (recordingStreamRef.current) {
      recordingStreamRef.current.getTracks().forEach(t => t.stop());
      recordingStreamRef.current = null;
    }
    setIsRecording(false);
    setIsPaused(false);
    setRecordingTime(0);
    clearInterval(recordingTimerRef.current);
    audioChunksRef.current = [];
  };

  // V2: пауза/возобновление записи
  const togglePauseRecording = () => {
    const mr = mediaRecorderRef.current;
    if (!mr) return;
    if (mr.state === 'recording') {
      mr.pause();
      setIsPaused(true);
      clearInterval(recordingTimerRef.current);
      stopWaveformAnimation();
    } else if (mr.state === 'paused') {
      mr.resume();
      setIsPaused(false);
      recordingTimerRef.current = setInterval(() => {
        setRecordingTime(t => {
          const next = t + 1;
          if (next >= MAX_VOICE_DURATION) {
            setTimeout(() => stopRecording(), 0);
            return t;
          }
          return next;
        });
      }, 1000);
      if (analyserRef.current) startWaveformAnimation(analyserRef.current);
    }
  };

  // Кнопка микрофона — клик запускает/останавливает запись (toggle, не hold)
  const handleMicClick = () => {
    if (isRecording) {
      stopRecording();
    } else {
      startRecording();
    }
  };

  return (
    <div
      className={`chat-input-zone ${dragOver ? 'chat-input-zone--drag-over' : ''}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* Превью прикреплённых файлов */}
      <AttachmentPreview
        files={pendingFiles}
        onRemove={removeFile}
        uploadProgress={uploadProgress}
      />

      {/* @mention автозавершение */}
      {mentionQuery !== null && participants.length > 0 && (
        <MentionSuggestions
          query={mentionQuery}
          participants={participants}
          selectedIndex={mentionIndex}
          onSelect={handleMentionSelect}
          onClose={() => setMentionQuery(null)}
        />
      )}

      {/* Ошибка файлов (вместо alert) */}
      {fileError && (
        <div style={{ padding: '4px 12px', fontSize: 12, color: 'var(--danger)', background: 'rgba(239,68,68,0.08)', borderRadius: 6, margin: '4px 8px 0' }}>
          {fileError}
        </div>
      )}

      {/* Превью ответа */}
      {replyTo && (
        <div className="chat-input__reply-preview">
          <div className="chat-input__reply-bar" />
          <div>
            <div className="chat-input__reply-name">
              {replyTo.user?.username || replyTo.username || 'Сообщение'}
            </div>
            <div className="chat-input__reply-text">
              {replyTo.text?.slice(0, 60) || (replyTo.attachments?.length ? (replyTo.attachments[0].type?.startsWith('image') ? 'Фото' : replyTo.attachments[0].type?.startsWith('audio') ? 'Голосовое сообщение' : `Файл: ${replyTo.attachments[0].name || 'файл'}`) : '[Сообщение]')}
              {replyTo.text && replyTo.text.length > 60 ? '...' : ''}
            </div>
          </div>
          <button className="chat-input__reply-close" onClick={onCancelReply} aria-label="Закрыть ответ">
            <X />
          </button>
        </div>
      )}

      {/* Превью редактирования */}
      {editingMsg && (
        <div className="chat-input__reply-preview">
          <div className="chat-input__reply-bar" style={{ background: 'var(--edit-color)' }} />
          <div>
            <div className="chat-input__reply-name" style={{ color: 'var(--edit-color)' }}>Редактирование</div>
            <div className="chat-input__reply-text">
              {editingMsg.text?.slice(0, 60) || ''}
              {editingMsg.text && editingMsg.text.length > 60 ? '...' : ''}
            </div>
          </div>
          <button className="chat-input__reply-close" onClick={() => { onCancelEdit?.(); setText(''); }} aria-label="Отменить редактирование">
            <X />
          </button>
        </div>
      )}

      {/* Скрытый file input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*,video/*,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar,.7z"
        className="chat-input__file-hidden"
        onChange={handleFileSelect}
        aria-label="Прикрепить файлы"
      />

      {/* Emoji picker popup — CSS transition вместо instant show/hide */}
      <div className={`chat-input__emoji-picker-wrap${showEmojiPicker ? ' chat-input__emoji-picker-wrap--open' : ''}`}>
        <div className="chat-input__emoji-picker">
          <Picker
            data={data}
            onEmojiSelect={handleEmojiSelect}
            theme={theme === 'dark' ? 'dark' : 'light'}
            locale="ru"
            previewPosition="none"
            skinTonePosition="search"
            set="native"
            perLine={8}
            maxFrequentRows={2}
          />
        </div>
      </div>

      {/* GIF picker popup */}
      {showGifPicker && (
        <GifPicker
          onSelect={handleGifSelect}
          onClose={() => setShowGifPicker(false)}
        />
      )}

      {/* Morph container — grid stack for crossfade */}
      <div className="chat-input-morph">
        {/* Recording bar — полностью заменяет input во время записи */}
        {isRecording && (
          <div className="chat-input__recording">
            {/* Кнопка отмены */}
            <button
              className="chat-input__recording-cancel"
              onClick={cancelRecording}
              title="Отменить"
              aria-label="Отменить запись"
            >
              <X size={16} />
            </button>

            {/* Живой waveform из AnalyserNode */}
            <div className="chat-input__rec-waveform">
              {waveformBars.map((h, i) => (
                <div
                  key={i}
                  className="chat-input__rec-bar"
                  style={{ '--bar-h': h }}
                />
              ))}
            </div>

            {/* Таймер — становится красным и пульсирует при < 5 сек до лимита */}
            <span className={`chat-input__rec-timer${recordingTime >= VOICE_WARNING_AT ? ' chat-input__rec-timer--warning' : ''}`}>
              {formatRecordTime(recordingTime)}
            </span>

            {/* Кнопка отправки */}
            <button
              className="chat-input__rec-send"
              onClick={stopRecording}
              title="Отправить"
              aria-label="Отправить голосовое сообщение"
            >
              <PaperPlaneTilt size={16} weight="fill" />
            </button>
          </div>
        )}

        {/* Expanded state */}
        {!isRecording && (
        <div className={`chat-input-expanded chat-input-expanded--visible`}>
          {/* Mic button — standalone circle on the left */}
          <div className="chat-input__mic-wrap">
            {micError && <div role="alert" aria-live="assertive" className="chat-input__mic-error">{micError}</div>}
            <button
              className="chat-input__mic-btn"
              onClick={handleMicClick}
              aria-label="Голосовое сообщение"
              title="Нажмите для записи"
            >
              <Microphone size={20} />
            </button>
          </div>
          <div className={`chat-input__outer ${isFocused ? 'chat-input__outer--focused' : ''}`}>
            <div className="chat-input__inner">
              <textarea
                ref={inputRef}
                className="chat-input__textarea"
                placeholder={`Сообщение... (${useSettingsStore.getState().enterToSend ? 'Enter' : 'Ctrl+Enter'} — отправить)`}
                value={text}
                onChange={handleChange}
                onKeyDown={handleKeyDown}
                onPaste={handlePaste}
                onFocus={handleFocus}
                onBlur={handleBlur}
                rows={1}
                aria-label="Написать сообщение"
                maxLength={4200}
              />
              {text.length > 3500 && (
                <span className={`chat-input__counter ${text.length > MAX_MESSAGE_LENGTH ? 'chat-input__counter--over' : ''}`}>
                  {text.length} / {MAX_MESSAGE_LENGTH}
                </span>
              )}
              <div className="chat-input__tools">
                <button
                  className={`chat-input__tool-btn ${showEmojiPicker ? 'chat-input__tool-btn--active' : ''}`}
                  onClick={() => { setShowEmojiPicker(v => !v); setShowGifPicker(false); }}
                  title="Эмодзи"
                  aria-label={showEmojiPicker ? 'Закрыть эмодзи' : 'Открыть эмодзи'}
                >
                  <Smiley size={18} />
                </button>
                <button
                  className={`chat-input__tool-btn ${showGifPicker ? 'chat-input__tool-btn--active' : ''}`}
                  onClick={() => { setShowGifPicker(v => !v); setShowEmojiPicker(false); }}
                  title="GIF"
                  aria-label={showGifPicker ? 'Закрыть GIF' : 'Открыть GIF'}
                >
                  <Gif size={18} />
                </button>
                <button
                  className="chat-input__tool-btn"
                  onClick={() => fileInputRef.current?.click()}
                  title="Прикрепить файл"
                  aria-label="Прикрепить файл"
                >
                  <Paperclip />
                </button>
                {/* Send button — inside the pill, on the right */}
                <button
                  ref={sendBtnRef}
                  className={`chat-input__send-inline${sendPulse ? ' chat-input__send-inline--sending' : ''}${(!text.trim() && !pendingFiles.length) ? ' chat-input__send-inline--hidden' : ''}`}
                  onClick={handleSend}
                  disabled={(!text.trim() && !pendingFiles.length) || Object.keys(uploadProgress).length > 0}
                  aria-label="Отправить сообщение"
                >
                  <ArrowUp size={16} weight="bold" />
                </button>
              </div>
            </div>
          </div>
        </div>
        )}
      </div>
    </div>
  );
}
