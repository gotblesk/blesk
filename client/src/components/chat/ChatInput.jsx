import { useState, useRef, useEffect, useCallback } from 'react';
import { Paperclip } from 'lucide-react';
import AttachmentPreview from './AttachmentPreview';
import './ChatInput.css';

export default function ChatInput({ onSend, onSendFiles, onTypingStart, onTypingStop, replyTo, onCancelReply }) {
  const [text, setText] = useState('');
  const [pendingFiles, setPendingFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState({});
  const [dragOver, setDragOver] = useState(false);
  const typingRef = useRef(false);
  const typingTimeoutRef = useRef(null);
  const inputRef = useRef(null);
  const fileInputRef = useRef(null);

  // Очистить typing timeout при unmount
  useEffect(() => {
    return () => {
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
        typingTimeoutRef.current = null;
      }
      if (typingRef.current) {
        onTypingStop?.();
        typingRef.current = false;
      }
    };
  }, [onTypingStop]);

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
    // Лимит 10 МБ на файл (для обычных пользователей)
    const valid = arr.filter((f) => f.size <= 10 * 1024 * 1024);
    if (valid.length < arr.length) {
      const rejected = arr.length - valid.length;
      alert(`${rejected} файл(ов) отклонено: максимальный размер 10 МБ`);
    }
    setPendingFiles((prev) => [...prev, ...valid].slice(0, 10));
  }, []);

  const removeFile = useCallback((index) => {
    setPendingFiles((prev) => prev.filter((_, i) => i !== index));
    // Сбрасываем весь прогресс при удалении — индексы сдвигаются
    setUploadProgress({});
  }, []);

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed && !pendingFiles.length) return;

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
    // Сбросить value чтобы можно было выбрать тот же файл повторно
    e.target.value = '';
  }, [addFiles]);

  return (
    <div
      className={`chat-input ${dragOver ? 'chat-input--drag-over' : ''}`}
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
          <div className="chat-input__reply-info">
            <span className="chat-input__reply-author">
              {replyTo.user?.username || replyTo.username || 'Сообщение'}
            </span>
            <span className="chat-input__reply-text">
              {replyTo.text?.slice(0, 60) || '[Сообщение]'}
              {replyTo.text && replyTo.text.length > 60 ? '...' : ''}
            </span>
          </div>
          <button className="chat-input__reply-cancel" onClick={onCancelReply}>&times;</button>
        </div>
      )}

      <div className="chat-input__row">
        <input
          ref={fileInputRef}
          type="file"
          multiple
          className="chat-input__file-hidden"
          onChange={handleFileSelect}
        />
        <button
          className="chat-input__attach"
          onClick={() => fileInputRef.current?.click()}
          title="Прикрепить файл"
        >
          <Paperclip size={18} strokeWidth={1.5} />
        </button>
        <textarea
          ref={inputRef}
          className="chat-input__field"
          placeholder="Написать сообщение..."
          value={text}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          rows={1}
        />
        {(text.trim() || pendingFiles.length > 0) && (
          <button className="chat-input__send" onClick={handleSend}>
            &rarr;
          </button>
        )}
      </div>
    </div>
  );
}
