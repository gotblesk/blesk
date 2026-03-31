import { useState, useRef, useEffect, useCallback } from 'react';
import { ArrowUp, Paperclip, X, Smiley, Microphone, PaperPlaneTilt, Pause, Play } from '@phosphor-icons/react';
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
  const [isPaused, setIsPaused] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [micError, setMicError] = useState('');
  const [waveformBars, setWaveformBars] = useState(() => Array(28).fill(0.15));

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
        },
      });

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
      const mediaRecorder = new MediaRecorder(stream, { mimeType });
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
          <div className={`chat-input__outer ${isFocused ? 'chat-input__outer--focused' : ''}`}>
            <div className="chat-input__inner">
              <textarea
                ref={inputRef}
                className="chat-input__textarea"
                placeholder={`Написать сообщение... (${useSettingsStore.getState().enterToSend ? 'Enter' : 'Ctrl+Enter'} — отправить)`}
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
                  <Smiley size={18} />
                </button>
                <button
                  className="chat-input__tool-btn"
                  onClick={() => fileInputRef.current?.click()}
                  title="Прикрепить файл"
                >
                  <Paperclip />
                </button>
              </div>
            </div>
          </div>
          {/* Mic button (input empty) или Send button (есть текст/файлы) */}
          {!text.trim() && !pendingFiles.length ? (
            <div className="chat-input__mic-wrap">
              {micError && <span className="chat-input__mic-error">{micError}</span>}
              <button
                className="chat-input__mic-hold"
                onClick={handleMicClick}
                aria-label="Голосовое сообщение"
                title="Нажмите для записи"
              >
                <Microphone size={18} />
              </button>
            </div>
          ) : (
            <button
              ref={sendBtnRef}
              className="chat-input__send"
              onClick={handleSend}
              disabled={Object.keys(uploadProgress).length > 0}
              aria-label="Отправить сообщение"
            >
              <ArrowUp />
            </button>
          )}
        </div>
        )}
      </div>
    </div>
  );
}
