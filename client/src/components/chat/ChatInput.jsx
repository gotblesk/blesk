import { useState, useRef, useEffect, useCallback } from 'react';
import { ArrowUp, Paperclip, X, Smile, Mic } from 'lucide-react';
import Picker from '@emoji-mart/react';
import data from '@emoji-mart/data';
import AttachmentPreview from './AttachmentPreview';
import { soundSend } from '../../utils/sounds';
import { useSettingsStore } from '../../store/settingsStore';
import './ChatInput.css';

export default function ChatInput({ onSend, onSendFiles, onTypingStart, onTypingStop, replyTo, onCancelReply, editingMsg, onCancelEdit }) {
  const [text, setText] = useState('');
  const [pendingFiles, setPendingFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState({});
  const [dragOver, setDragOver] = useState(false);
  const [isExpanded, setIsExpanded] = useState(true);
  const [isFocused, setIsFocused] = useState(false);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);

  const typingRef = useRef(false);
  const typingTimeoutRef = useRef(null);
  const inputRef = useRef(null);
  const fileInputRef = useRef(null);
  const sendBtnRef = useRef(null);
  const blurTimeoutRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);
  const recordingTimerRef = useRef(null);

  const theme = useSettingsStore(s => s.theme);

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
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        mediaRecorderRef.current.stop();
        mediaRecorderRef.current.stream?.getTracks().forEach(t => t.stop());
      }
      clearInterval(recordingTimerRef.current);
    };
  }, [onTypingStop]);

  // Закрыть emoji picker при клике вне
  useEffect(() => {
    if (!showEmojiPicker) return;
    const handleClickOutside = (e) => {
      if (!e.target.closest('.chat-input__emoji-picker') && !e.target.closest('.chat-input__tool-btn')) {
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

  // Авто-ресайз textarea
  const resizeTextarea = () => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 120) + 'px';
  };

  const handleChange = (e) => {
    setText(e.target.value);
    resizeTextarea();

    if (!typingRef.current && e.target.value) {
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
    // Лимит 10 МБ на файл
    const valid = arr.filter((f) => f.size <= 10 * 1024 * 1024);
    if (valid.length < arr.length) {
      const rejected = arr.length - valid.length;
      alert(`${rejected} файл(ов) отклонено: максимальный размер 10 МБ`);
    }
    setPendingFiles((prev) => {
      const totalSize = prev.reduce((sum, f) => sum + f.size, 0) + valid.reduce((sum, f) => sum + f.size, 0);
      if (totalSize > 50 * 1024 * 1024) {
        alert('Общий размер файлов не может превышать 50 МБ');
        return prev;
      }
      return [...prev, ...valid].slice(0, 10);
    });
  }, []);

  const removeFile = useCallback((index) => {
    setPendingFiles((prev) => prev.filter((_, i) => i !== index));
    setUploadProgress({});
  }, []);

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed && !pendingFiles.length) return;

    // Звук
    soundSend();

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
      setUploadProgress({});
    } else {
      onSend(trimmed);
    }

    setText('');
    if (inputRef.current) inputRef.current.style.height = '';
    typingRef.current = false;
    onTypingStop?.();
    clearTimeout(typingTimeoutRef.current);
  };

  // [IMP-5] getState() вместо подписки — не вызывает ре-рендер
  const handleKeyDown = (e) => {
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

  // Voice recording
  const formatRecordTime = (seconds) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  // [Баг #36] Максимальная длина голосового сообщения — 5 минут
  const MAX_VOICE_DURATION = 300;

  const startRecording = async () => {
    try {
      // [Баг #11] Полные audio constraints — как в useVoice.getLocalStream
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 48000,
        },
      });
      const mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' });
      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunksRef.current.push(e.data);
      };

      mediaRecorder.onstop = () => {
        const blob = new Blob(audioChunksRef.current, { type: 'audio/webm' });
        const file = new File([blob], `voice-${Date.now()}.webm`, { type: 'audio/webm' });
        onSendFiles?.([file], '');
        stream.getTracks().forEach(t => t.stop());
        setRecordingTime(0);
        clearInterval(recordingTimerRef.current);
      };

      mediaRecorder.start();
      setIsRecording(true);
      setRecordingTime(0);
      recordingTimerRef.current = setInterval(() => {
        setRecordingTime(t => {
          // [Баг #36] Автоостановка при достижении максимальной длины
          if (t + 1 >= MAX_VOICE_DURATION) {
            stopRecording();
            return t;
          }
          return t + 1;
        });
      }, 1000);
    } catch (err) {
      console.error('Mic access denied:', err);
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    setIsRecording(false);
    clearInterval(recordingTimerRef.current);
  };

  const cancelRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.ondataavailable = null;
      mediaRecorderRef.current.onstop = null;
      mediaRecorderRef.current.stop();
      mediaRecorderRef.current.stream?.getTracks().forEach(t => t.stop());
    }
    setIsRecording(false);
    setRecordingTime(0);
    clearInterval(recordingTimerRef.current);
    audioChunksRef.current = [];
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

      {/* Превью ответа */}
      {replyTo && (
        <div className="chat-input__reply-preview">
          <div className="chat-input__reply-bar" />
          <div>
            <div className="chat-input__reply-name">
              {replyTo.user?.username || replyTo.username || 'Сообщение'}
            </div>
            <div className="chat-input__reply-text">
              {replyTo.text?.slice(0, 60) || '[Сообщение]'}
              {replyTo.text && replyTo.text.length > 60 ? '...' : ''}
            </div>
          </div>
          <button className="chat-input__reply-close" onClick={onCancelReply}>
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
          <button className="chat-input__reply-close" onClick={() => { onCancelEdit?.(); setText(''); }}>
            <X />
          </button>
        </div>
      )}

      {/* Скрытый file input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="chat-input__file-hidden"
        onChange={handleFileSelect}
      />

      {/* Emoji picker popup */}
      {showEmojiPicker && (
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
      )}

      {/* Morph container — grid stack for crossfade */}
      <div className="chat-input-morph">
        {/* Recording state */}
        {isRecording && (
          <div className="chat-input__recording">
            <div className="chat-input__recording-dot" />
            <span className="chat-input__recording-time">{formatRecordTime(recordingTime)}</span>
            <span className="chat-input__recording-label">Запись...</span>
            <div className="chat-input__recording-actions">
              <button className="chat-input__recording-cancel" onClick={cancelRecording} title="Отменить">
                <X size={16} />
              </button>
              <button className="chat-input__recording-stop" onClick={stopRecording} title="Отправить">
                <ArrowUp size={16} />
              </button>
            </div>
          </div>
        )}

        {/* Expanded state */}
        {!isRecording && (
        <div className={`chat-input-expanded chat-input-expanded--visible`}>
          <div className={`chat-input__outer ${isFocused ? 'chat-input__outer--focused' : ''}`}>
            <div className="chat-input__inner">
              <textarea
                ref={inputRef}
                className="chat-input__textarea"
                placeholder="Написать сообщение..."
                value={text}
                onChange={handleChange}
                onKeyDown={handleKeyDown}
                onPaste={handlePaste}
                onFocus={handleFocus}
                onBlur={handleBlur}
                rows={1}
              />
              <div className="chat-input__tools">
                <button
                  className={`chat-input__tool-btn ${showEmojiPicker ? 'chat-input__tool-btn--active' : ''}`}
                  onClick={() => setShowEmojiPicker(v => !v)}
                  title="Эмодзи"
                >
                  <Smile size={18} />
                </button>
                <button
                  className="chat-input__tool-btn"
                  onClick={() => fileInputRef.current?.click()}
                  title="Прикрепить файл"
                >
                  <Paperclip />
                </button>
                {!text.trim() && !pendingFiles.length && (
                  <button
                    className="chat-input__tool-btn chat-input__tool-btn--mic"
                    onClick={startRecording}
                    title="Голосовое сообщение"
                  >
                    <Mic size={18} />
                  </button>
                )}
              </div>
            </div>
          </div>
          <button
            ref={sendBtnRef}
            className="chat-input__send"
            onClick={handleSend}
            aria-label="Отправить сообщение"
          >
            <ArrowUp />
          </button>
        </div>
        )}
      </div>
    </div>
  );
}
