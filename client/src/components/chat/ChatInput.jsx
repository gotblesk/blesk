import { useState, useRef, useEffect, useCallback } from 'react';
import { ArrowUp, Paperclip, Smile, Mic, X } from 'lucide-react';
import AttachmentPreview from './AttachmentPreview';
import { soundSend } from '../../utils/sounds';
import './ChatInput.css';

export default function ChatInput({ onSend, onSendFiles, onTypingStart, onTypingStop, replyTo, onCancelReply }) {
  const [text, setText] = useState('');
  const [pendingFiles, setPendingFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState({});
  const [dragOver, setDragOver] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const [isFocused, setIsFocused] = useState(false);

  const typingRef = useRef(false);
  const typingTimeoutRef = useRef(null);
  const inputRef = useRef(null);
  const fileInputRef = useRef(null);
  const sendBtnRef = useRef(null);
  const blurTimeoutRef = useRef(null);

  // Anti-spam: максимум 5 сообщений за 3 секунды
  const sendTimestampsRef = useRef([]);

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
    };
  }, [onTypingStop]);

  // Если есть replyTo — раскрыть и фокус
  useEffect(() => {
    if (replyTo) {
      setIsExpanded(true);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [replyTo]);

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
    setPendingFiles((prev) => [...prev, ...valid].slice(0, 10));
  }, []);

  const removeFile = useCallback((index) => {
    setPendingFiles((prev) => prev.filter((_, i) => i !== index));
    setUploadProgress({});
  }, []);

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed && !pendingFiles.length) return;

    // Anti-spam: 5 сообщений за 3 секунды
    const now = Date.now();
    sendTimestampsRef.current = sendTimestampsRef.current.filter((t) => now - t < 3000);
    if (sendTimestampsRef.current.length >= 5) return;
    sendTimestampsRef.current.push(now);

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

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
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
    // Сворачиваем только если пустой текст и нет файлов
    blurTimeoutRef.current = setTimeout(() => {
      if (!text.trim() && !pendingFiles.length) {
        setIsExpanded(false);
      }
    }, 200);
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

      {/* Скрытый file input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="chat-input__file-hidden"
        onChange={handleFileSelect}
      />

      {/* Morph container — grid stack for crossfade */}
      <div className="chat-input-morph">
        {/* Compact state */}
        <div
          className={`chat-input-compact ${isExpanded ? 'chat-input-compact--hidden' : ''}`}
          onClick={expandAndFocus}
        >
          <span className="chat-input-compact__hint">Написать...</span>
          <button className="chat-input-compact__mic" onClick={(e) => e.stopPropagation()}>
            <Mic />
          </button>
        </div>

        {/* Expanded state */}
        <div className={`chat-input-expanded ${isExpanded ? 'chat-input-expanded--visible' : ''}`}>
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
                  className="chat-input__tool-btn"
                  onClick={() => fileInputRef.current?.click()}
                  title="Прикрепить файл"
                >
                  <Paperclip />
                </button>
                <button className="chat-input__tool-btn" title="Эмодзи">
                  <Smile />
                </button>
              </div>
            </div>
          </div>
          <button
            ref={sendBtnRef}
            className="chat-input__send"
            onClick={handleSend}
          >
            <ArrowUp />
          </button>
        </div>
      </div>
    </div>
  );
}
