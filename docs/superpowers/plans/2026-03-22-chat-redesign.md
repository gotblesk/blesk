# Chat Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Полный редизайн ChatView — новые Double Layer Glass пузыри, island-header, morph capsule input, hue identity, анимации, и UX-фичи.

**Architecture:** Переписываем 4 ядра чата (ChatMessage, ChatHeader, ChatInput, ChatView CSS) с нуля поверх существующей логики. Новые компоненты: DateSeparator, MessageActionsPill, UnreadDivider, EmojiExplosion, LinkPreviewCard, TypingBubble. Всё строится на Glass.jsx паттерне (outer+inner layers). Стейт и сокеты НЕ трогаем — только UI слой.

**Tech Stack:** React, CSS (no modules), Lucide React, существующий Glass.jsx паттерн.

**Spec:** `docs/superpowers/specs/2026-03-22-chat-redesign-design.md`
**Mockup:** `.superpowers/brainstorm/2842-1774175835/chat-final-assembly.html`

---

## Chunk 1: Message Bubbles + Hue Identity

### Task 1: ChatMessage CSS — Double Layer Glass Bubbles

**Files:**
- Rewrite: `client/src/components/chat/ChatMessage.css`

- [ ] **Step 1: Backup old CSS and write new ChatMessage.css**

Полная перезапись. Новый CSS:

```css
/* ===== ChatMessage — blesk Signature Double Layer Glass ===== */

.chat-message {
  display: flex;
  gap: 8px;
  max-width: 72%;
  animation: msgAppear 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

.chat-message--own {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-message--other {
  align-self: flex-start;
}

/* ===== Avatar ===== */
.chat-message__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  flex-shrink: 0;
  align-self: flex-end;
}

.chat-message__avatar--hidden {
  visibility: hidden;
}

/* ===== Content column ===== */
.chat-message__col {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* ===== Sender name ===== */
.chat-message__name {
  font-size: 11px;
  font-weight: 600;
  padding-left: 12px;
  margin-bottom: 1px;
  color: var(--sender-hue-color, rgba(255,255,255,0.5));
}

/* ===== Time ===== */
.chat-message__time {
  font-size: 10px;
  color: rgba(255,255,255,0.18);
  padding: 1px 12px;
}

.chat-message--own .chat-message__time {
  text-align: right;
}

/* ===== Bubble Outer Layer ===== */
.chat-message__bubble-outer {
  padding: 3px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: box-shadow 0.3s;
}

.chat-message--other .chat-message__bubble-outer {
  background: var(--sender-bubble-bg, rgba(255,255,255,0.035));
  border: 0.5px solid var(--sender-bubble-border, rgba(255,255,255,0.06));
  box-shadow: 0 1.5px 6px rgba(0,0,0,0.1);
}

.chat-message--own .chat-message__bubble-outer {
  background: rgba(200,255,0,0.03);
  border: 0.5px solid rgba(200,255,0,0.06);
  box-shadow:
    0 1.5px 6px rgba(0,0,0,0.1),
    0 0 16px rgba(200,255,0,0.025);
}

/* ===== Bubble Inner Layer ===== */
.chat-message__bubble-inner {
  padding: 8px 13px;
  font-size: 14px;
  line-height: 1.5;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  position: relative;
}

/* Specular highlight */
.chat-message__bubble-inner::before {
  content: '';
  position: absolute;
  top: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.1), transparent);
  pointer-events: none;
}

.chat-message--other .chat-message__bubble-inner {
  background: var(--sender-bubble-inner-bg, rgba(255,255,255,0.06));
  border-top: 0.5px solid var(--sender-bubble-inner-border, rgba(255,255,255,0.07));
  box-shadow: inset 0 -0.5px 1px rgba(0,0,0,0.05);
}

.chat-message--own .chat-message__bubble-inner {
  background: rgba(200,255,0,0.07);
  border-top: 0.5px solid rgba(200,255,0,0.09);
  box-shadow: inset 0 -0.5px 1px rgba(0,0,0,0.05);
}

/* ===== Grouping radii ===== */

/* Solo */
.chat-message--solo .chat-message__bubble-outer { border-radius: 22px; }
.chat-message--solo .chat-message__bubble-inner { border-radius: 19px; }

/* First — other */
.chat-message--first.chat-message--other .chat-message__bubble-outer { border-radius: 22px 22px 22px 6px; }
.chat-message--first.chat-message--other .chat-message__bubble-inner { border-radius: 19px 19px 19px 4px; }

/* Mid — other */
.chat-message--mid.chat-message--other .chat-message__bubble-outer { border-radius: 6px 22px 22px 6px; }
.chat-message--mid.chat-message--other .chat-message__bubble-inner { border-radius: 4px 19px 19px 4px; }

/* Last — other */
.chat-message--last.chat-message--other .chat-message__bubble-outer { border-radius: 6px 22px 22px 22px; }
.chat-message--last.chat-message--other .chat-message__bubble-inner { border-radius: 4px 19px 19px 19px; }

/* First — own */
.chat-message--first.chat-message--own .chat-message__bubble-outer { border-radius: 22px 22px 6px 22px; }
.chat-message--first.chat-message--own .chat-message__bubble-inner { border-radius: 19px 19px 4px 19px; }

/* Mid — own */
.chat-message--mid.chat-message--own .chat-message__bubble-outer { border-radius: 22px 6px 6px 22px; }
.chat-message--mid.chat-message--own .chat-message__bubble-inner { border-radius: 19px 4px 4px 19px; }

/* Last — own */
.chat-message--last.chat-message--own .chat-message__bubble-outer { border-radius: 22px 6px 22px 22px; }
.chat-message--last.chat-message--own .chat-message__bubble-inner { border-radius: 19px 4px 19px 19px; }

/* ===== Emoji-only messages ===== */
.chat-message--emoji-only {
  max-width: none;
}

.chat-message--emoji-1 .chat-message__emoji { font-size: 48px; line-height: 1.2; }
.chat-message--emoji-2 .chat-message__emoji { font-size: 36px; line-height: 1.2; }
.chat-message--emoji-3 .chat-message__emoji { font-size: 28px; line-height: 1.2; }

.chat-message--emoji-only .chat-message__emoji {
  animation: emojiExplode 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

/* ===== Reply quote inside bubble ===== */
.chat-message__reply-quote {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  padding: 6px 10px;
  border-radius: 10px;
  background: rgba(255,255,255,0.04);
  cursor: pointer;
}

.chat-message__reply-bar {
  width: 3px;
  border-radius: 2px;
  flex-shrink: 0;
  background: var(--sender-hue-color, #c8ff00);
}

.chat-message__reply-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--reply-hue-color, #c8ff00);
}

.chat-message__reply-text {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
}

/* ===== Read receipt ===== */
.chat-message__read-icon {
  position: absolute;
  bottom: 4px;
  right: 8px;
  color: rgba(200,255,0,0.3);
  width: 12px;
  height: 12px;
}

/* Sweep glow on bubble when read */
.chat-message__bubble-outer--read-glow::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(90deg, transparent 0%, rgba(200,255,0,0.08) 50%, transparent 100%);
  animation: readSweep 0.8s ease-out forwards;
  pointer-events: none;
}

.chat-message__read-icon--animate {
  animation: readIconAppear 0.3s ease-out 0.5s both;
}

/* ===== System message ===== */
.chat-message--system {
  align-self: center;
  max-width: 80%;
  font-size: 12px;
  color: rgba(255,255,255,0.3);
  text-align: center;
  padding: 4px 0;
}

/* ===== Animations ===== */
@keyframes msgAppear {
  0% {
    opacity: 0;
    transform: scale(0.85) translateY(12px);
    filter: blur(4px);
  }
  60% {
    opacity: 1;
    transform: scale(1.02) translateY(-2px);
    filter: blur(0);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
    filter: blur(0);
  }
}

@keyframes emojiExplode {
  0% { transform: scale(0.3); opacity: 0; }
  50% { transform: scale(1.3); }
  70% { transform: scale(0.95); }
  100% { transform: scale(1); opacity: 1; }
}

@keyframes readSweep {
  0% { transform: translateX(-100%); opacity: 1; }
  100% { transform: translateX(100%); opacity: 0; }
}

@keyframes readIconAppear {
  0% { opacity: 0; }
  100% { opacity: 1; }
}

/* ===== Reduced motion ===== */
@media (prefers-reduced-motion: reduce) {
  .chat-message {
    animation: fadeIn 0.2s ease both;
  }

  .chat-message--emoji-only .chat-message__emoji {
    animation: fadeIn 0.2s ease both;
  }

  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }
}

/* ===== Light theme ===== */
[data-theme="light"] .chat-message--other .chat-message__bubble-outer {
  background: var(--sender-bubble-bg-light, rgba(0,0,0,0.04));
  border-color: var(--sender-bubble-border-light, rgba(0,0,0,0.06));
}

[data-theme="light"] .chat-message--other .chat-message__bubble-inner {
  background: var(--sender-bubble-inner-bg-light, rgba(0,0,0,0.06));
  border-top-color: rgba(255,255,255,0.4);
}

[data-theme="light"] .chat-message--own .chat-message__bubble-outer {
  background: rgba(74,140,0,0.06);
  border-color: rgba(74,140,0,0.1);
}

[data-theme="light"] .chat-message--own .chat-message__bubble-inner {
  background: rgba(74,140,0,0.1);
  border-top-color: rgba(255,255,255,0.5);
}

[data-theme="light"] .chat-message__time {
  color: rgba(0,0,0,0.25);
}

[data-theme="light"] .chat-message__bubble-inner::before {
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.4), transparent);
}
```

- [ ] **Step 2: Verify CSS file saved correctly**

Run: `wc -l client/src/components/chat/ChatMessage.css`
Expected: ~220 lines

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/ChatMessage.css
git commit -m "rewrite ChatMessage CSS — double layer glass bubbles with hue identity"
```

---

### Task 2: Hue utility function

**Files:**
- Create: `client/src/utils/hueIdentity.js`

- [ ] **Step 1: Create hue identity utility**

```js
/**
 * Генерирует hue (0-360) из строки (username/userId).
 * Используется для Hue Identity — каждый юзер = свой цвет.
 */
export function getHueFromString(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  return Math.abs(hash) % 360;
}

/**
 * Возвращает CSS custom properties для hue identity юзера.
 * Устанавливаются на msg-row через style prop.
 */
export function getHueStyles(hue) {
  const s = 60, l = 65;
  return {
    '--sender-hue-color': `hsl(${hue}, ${s}%, ${l}%)`,
    '--sender-bubble-bg': `hsla(${hue}, ${s}%, ${l}%, 0.03)`,
    '--sender-bubble-border': `hsla(${hue}, ${s}%, ${l}%, 0.06)`,
    '--sender-bubble-inner-bg': `hsla(${hue}, ${s}%, ${l}%, 0.06)`,
    '--sender-bubble-inner-border': `hsla(${hue}, ${s}%, ${l}%, 0.08)`,
    // Light theme variants
    '--sender-bubble-bg-light': `hsla(${hue}, ${s}%, 40%, 0.06)`,
    '--sender-bubble-border-light': `hsla(${hue}, ${s}%, 40%, 0.1)`,
    '--sender-bubble-inner-bg-light': `hsla(${hue}, ${s}%, 40%, 0.1)`,
  };
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/utils/hueIdentity.js
git commit -m "add hue identity utility — per-user color from username hash"
```

---

### Task 3: ChatMessage JSX — Double Layer Glass + Hue Identity

**Files:**
- Rewrite: `client/src/components/chat/ChatMessage.jsx`

- [ ] **Step 1: Rewrite ChatMessage.jsx**

Компонент должен:
- Рендерить outer + inner glass layers (не использовать Glass.jsx — свой CSS для пузырей)
- Применять CSS custom properties через `getHueStyles(hue)` для other-сообщений
- Принимать `groupPosition` prop ('solo' | 'first' | 'mid' | 'last')
- Определять emoji-only сообщения (regex: `/^[\p{Emoji_Presentation}\p{Extended_Pictographic}\s]{1,3}$/u`)
- Показывать reply quote если есть replyTo
- Показывать read receipt icon (CheckCheck из Lucide) для own + isRead
- Показывать имя только для first/solo у other
- Показывать аватар только для last/solo у other, hidden для остальных
- Показывать время только для last/solo
- Сохранить edit/delete/reply action handling из текущей версии
- Убрать все старые стили, использовать новые CSS классы

Ключевая структура JSX:
```jsx
<div className={classes} style={isOwn ? {} : hueStyles}>
  {!isOwn && <Avatar />}
  <div className="chat-message__col">
    {showName && <div className="chat-message__name">{senderName}</div>}
    {isEmojiOnly ? (
      <div className="chat-message__emoji">{text}</div>
    ) : (
      <div className="chat-message__bubble-outer">
        <div className="chat-message__bubble-inner">
          {replyTo && <ReplyQuote />}
          {text}
          {isOwn && isRead && <CheckCheck className="chat-message__read-icon" />}
        </div>
      </div>
    )}
    {showTime && <div className="chat-message__time">{time}</div>}
  </div>
</div>
```

- [ ] **Step 2: Verify no import errors**

Run: `cd client && npx vite build 2>&1 | head -20`
Expected: no errors related to ChatMessage

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/ChatMessage.jsx
git commit -m "rewrite ChatMessage JSX — glass bubbles, hue identity, emoji detection"
```

---

## Chunk 2: ChatHeader + ChatInput

### Task 4: ChatHeader — Floating Compact Island

**Files:**
- Rewrite: `client/src/components/chat/ChatHeader.css`
- Rewrite: `client/src/components/chat/ChatHeader.jsx`

- [ ] **Step 1: Write new ChatHeader.css**

```css
/* ===== ChatHeader — Floating Compact Island ===== */

.chat-header-zone {
  padding: 10px 0;
  display: flex;
  justify-content: center;
  position: relative;
  z-index: 10;
}

.chat-header-island {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px 6px 6px;
  background: rgba(255,255,255,0.045);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 0.5px solid rgba(255,255,255,0.08);
  border-radius: 100px;
  box-shadow:
    0 2px 12px rgba(0,0,0,0.2),
    inset 0 0.5px 0 rgba(255,255,255,0.06);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  width: fit-content;
}

.chat-header-island:hover {
  background: rgba(255,255,255,0.06);
  transform: scale(1.02);
}

.chat-header__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  position: relative;
  flex-shrink: 0;
}

.chat-header__online {
  position: absolute;
  bottom: -1px;
  right: -1px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4ade80;
  border: 2px solid #0d0b16;
}

.chat-header__name {
  font-weight: 600;
  font-size: 13px;
  color: rgba(255,255,255,0.85);
}

.chat-header__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(255,255,255,0.2);
}

.chat-header__status {
  font-size: 11px;
  color: rgba(255,255,255,0.35);
}

.chat-header__btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255,255,255,0.06);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,0.4);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.chat-header__btn:hover {
  background: rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.6);
}

.chat-header__btn svg {
  width: 14px;
  height: 14px;
}

/* Light theme */
[data-theme="light"] .chat-header-island {
  background: rgba(255,255,255,0.5);
  border-color: rgba(0,0,0,0.06);
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

[data-theme="light"] .chat-header__name {
  color: rgba(0,0,0,0.85);
}

[data-theme="light"] .chat-header__status {
  color: rgba(0,0,0,0.4);
}

[data-theme="light"] .chat-header__online {
  border-color: #f0eef5;
}

@media (prefers-reduced-motion: reduce) {
  .chat-header-island {
    transition: none;
  }
}
```

- [ ] **Step 2: Write new ChatHeader.jsx**

Структура:
```jsx
import { Phone, MoreHorizontal } from 'lucide-react';
import Avatar from '../ui/Avatar';
// Сохранить существующую логику: isOnline, typingUsernames, lastSeenAt, status text
// Убрать старый layout, заменить на island

<div className="chat-header-zone">
  <div className="chat-header-island">
    <Avatar user={otherUser} size={28} showOnline />
    <span className="chat-header__name">{chatName}</span>
    <span className="chat-header__dot" />
    <span className="chat-header__status">{statusText}</span>
    <button className="chat-header__btn" onClick={onCall}><Phone /></button>
    <button className="chat-header__btn" onClick={onMore}><MoreHorizontal /></button>
  </div>
</div>
```

Сохранить из текущего: обработку typing indicator text, "был(а) N мин назад", online status. Убрать: grip bar, старую структуру.

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/ChatHeader.css client/src/components/chat/ChatHeader.jsx
git commit -m "rewrite ChatHeader — floating compact island capsule"
```

---

### Task 5: ChatInput — Morph Capsule

**Files:**
- Rewrite: `client/src/components/chat/ChatInput.css`
- Rewrite: `client/src/components/chat/ChatInput.jsx`

- [ ] **Step 1: Write new ChatInput.css**

```css
/* ===== ChatInput — Morph Capsule ===== */

.chat-input-zone {
  padding: 8px 12px 12px;
  position: relative;
  z-index: 10;
}

/* Compact state */
.chat-input-compact {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  width: 200px;
  margin: 0 auto;
  background: rgba(255,255,255,0.04);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 0.5px solid rgba(255,255,255,0.07);
  border-radius: 100px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  cursor: text;
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.chat-input-compact__hint {
  flex: 1;
  font-size: 13px;
  color: rgba(255,255,255,0.25);
}

.chat-input-compact__mic {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255,255,255,0.06);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,0.35);
  cursor: pointer;
}

.chat-input-compact__mic svg {
  width: 14px;
  height: 14px;
}

/* Expanded state */
.chat-input-expanded {
  display: flex;
  align-items: center;
  gap: 0;
  opacity: 1;
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* Outer glass */
.chat-input__outer {
  flex: 1;
  padding: 3px;
  background: rgba(255,255,255,0.035);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 0.5px solid rgba(255,255,255,0.06);
  border-radius: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transition: border-color 0.3s, box-shadow 0.3s;
}

.chat-input__outer--focused {
  border-color: rgba(200,255,0,0.08);
  box-shadow: 0 0 20px rgba(200,255,0,0.04);
}

/* Inner glass */
.chat-input__inner {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 8px 6px 8px 16px;
  background: rgba(255,255,255,0.05);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 21px;
  border-top: 0.5px solid rgba(255,255,255,0.07);
}

/* Textarea */
.chat-input__textarea {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  color: rgba(255,255,255,0.85);
  font-family: 'Manrope', sans-serif;
  font-size: 14px;
  line-height: 1.5;
  padding: 4px 0;
  resize: none;
  max-height: 120px;
  overflow-y: auto;
}

.chat-input__textarea::placeholder {
  color: rgba(255,255,255,0.25);
}

/* Tool buttons */
.chat-input__tools {
  display: flex;
  gap: 1px;
  margin-bottom: 2px;
}

.chat-input__tool-btn {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: none;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,0.3);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.chat-input__tool-btn:hover {
  background: rgba(255,255,255,0.06);
  color: rgba(255,255,255,0.5);
}

.chat-input__tool-btn svg {
  width: 16px;
  height: 16px;
}

/* Send button */
.chat-input__send {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #c8ff00;
  color: #08060f;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  align-self: center;
  margin-left: 8px;
  box-shadow: 0 0 16px rgba(200,255,0,0.2);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  position: relative;
  overflow: hidden;
}

.chat-input__send:hover {
  transform: scale(1.05);
  box-shadow: 0 0 24px rgba(200,255,0,0.3);
}

.chat-input__send svg {
  width: 18px;
  height: 18px;
}

/* Send ripple */
.chat-input__send-ripple {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(255,255,255,0.3);
  transform: scale(0);
  animation: sendRipple 0.6s ease-out forwards;
  pointer-events: none;
}

@keyframes sendRipple {
  0% { transform: scale(0); opacity: 1; }
  100% { transform: scale(2.5); opacity: 0; }
}

/* Reply preview above input */
.chat-input__reply-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  margin: 0 12px 4px;
  background: rgba(255,255,255,0.03);
  backdrop-filter: blur(12px);
  border: 0.5px solid rgba(255,255,255,0.05);
  border-radius: 16px;
  animation: msgAppear 0.2s ease both;
}

.chat-input__reply-bar {
  width: 3px;
  height: 24px;
  border-radius: 2px;
  background: #c8ff00;
  flex-shrink: 0;
}

.chat-input__reply-name {
  font-size: 11px;
  font-weight: 600;
  color: #c8ff00;
}

.chat-input__reply-text {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.chat-input__reply-close {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(255,255,255,0.05);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,0.3);
  cursor: pointer;
  margin-left: auto;
}

.chat-input__reply-close svg {
  width: 12px;
  height: 12px;
}

/* Light theme */
[data-theme="light"] .chat-input-compact {
  background: rgba(255,255,255,0.5);
  border-color: rgba(0,0,0,0.06);
}

[data-theme="light"] .chat-input__outer {
  background: rgba(255,255,255,0.4);
  border-color: rgba(0,0,0,0.06);
}

[data-theme="light"] .chat-input__inner {
  background: rgba(255,255,255,0.5);
}

[data-theme="light"] .chat-input__textarea {
  color: rgba(0,0,0,0.85);
}

[data-theme="light"] .chat-input__outer--focused {
  border-color: rgba(74,140,0,0.15);
  box-shadow: 0 0 20px rgba(74,140,0,0.06);
}

@media (prefers-reduced-motion: reduce) {
  .chat-input-compact,
  .chat-input-expanded {
    transition: none;
  }
}
```

- [ ] **Step 2: Write new ChatInput.jsx**

Ключевая логика:
- State `isExpanded` — переключается при фокусе textarea
- Compact: клик → `setIsExpanded(true)` + auto-focus textarea
- Expanded: blur (если пустой текст) → `setIsExpanded(false)` с delay 200ms
- Send: создать ripple div, удалить через 600ms, вызвать onSend
- Сохранить: drag-drop файлов, paste image, typing indicator emit, file size validation, anti-spam
- Reply preview: показывать над input zone если replyTo != null
- Кнопки: Paperclip (attach), Smile (emoji), ArrowUp (send) — Lucide

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/ChatInput.css client/src/components/chat/ChatInput.jsx
git commit -m "rewrite ChatInput — morph capsule with send ripple and reply preview"
```

---

## Chunk 3: Typing Indicator + Date Separator + Unread Divider

### Task 6: TypingBubble component

**Files:**
- Create: `client/src/components/chat/TypingBubble.css`
- Create: `client/src/components/chat/TypingBubble.jsx`

- [ ] **Step 1: Write TypingBubble.css**

```css
.typing-bubble {
  display: flex;
  gap: 8px;
  align-self: flex-start;
  align-items: flex-end;
  animation: msgAppear 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

.typing-bubble__outer {
  padding: 3px;
  border-radius: 22px;
  background: var(--sender-bubble-bg, rgba(255,255,255,0.035));
  border: 0.5px solid var(--sender-bubble-border, rgba(255,255,255,0.06));
  box-shadow: 0 1.5px 6px rgba(0,0,0,0.1);
  backdrop-filter: blur(8px);
}

.typing-bubble__inner {
  padding: 10px 16px;
  border-radius: 19px;
  background: var(--sender-bubble-inner-bg, rgba(255,255,255,0.06));
  border-top: 0.5px solid var(--sender-bubble-inner-border, rgba(255,255,255,0.07));
  display: flex;
  gap: 4px;
  align-items: center;
}

.typing-bubble__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--sender-hue-color, rgba(255,255,255,0.4));
  opacity: 0.5;
  animation: typingWave 1.4s ease-in-out infinite;
}

.typing-bubble__dot:nth-child(2) { animation-delay: 0.15s; }
.typing-bubble__dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes typingWave {
  0%, 60%, 100% {
    transform: translateY(0) scale(1);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-6px) scale(1.2);
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .typing-bubble {
    animation: fadeIn 0.2s ease both;
  }
  .typing-bubble__dot {
    animation: typingPulse 1.4s ease-in-out infinite;
  }
  .typing-bubble__dot:nth-child(2) { animation-delay: 0.15s; }
  .typing-bubble__dot:nth-child(3) { animation-delay: 0.3s; }

  @keyframes typingPulse {
    0%, 60%, 100% { opacity: 0.3; }
    30% { opacity: 0.8; }
  }
}
```

- [ ] **Step 2: Write TypingBubble.jsx**

```jsx
import './TypingBubble.css';
import Avatar from '../ui/Avatar';
import { getHueStyles } from '../../utils/hueIdentity';

export default function TypingBubble({ user, hue }) {
  const hueStyles = getHueStyles(hue);

  return (
    <div className="typing-bubble" style={hueStyles}>
      <Avatar user={user} size={28} />
      <div className="typing-bubble__outer">
        <div className="typing-bubble__inner">
          <div className="typing-bubble__dot" />
          <div className="typing-bubble__dot" />
          <div className="typing-bubble__dot" />
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/TypingBubble.css client/src/components/chat/TypingBubble.jsx
git commit -m "add TypingBubble — metaball dots with hue identity"
```

---

### Task 7: DateSeparator component

**Files:**
- Create: `client/src/components/chat/DateSeparator.css`
- Create: `client/src/components/chat/DateSeparator.jsx`

- [ ] **Step 1: Write DateSeparator.css**

```css
.date-separator {
  display: flex;
  justify-content: center;
  margin: 16px 0;
  position: sticky;
  top: 0;
  z-index: 5;
}

.date-separator__pill {
  padding: 4px 14px;
  border-radius: 100px;
  background: rgba(255,255,255,0.04);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 0.5px solid rgba(255,255,255,0.06);
  font-size: 11px;
  font-weight: 500;
  color: rgba(255,255,255,0.35);
  transition: backdrop-filter 0.3s;
}

[data-theme="light"] .date-separator__pill {
  background: rgba(255,255,255,0.5);
  border-color: rgba(0,0,0,0.06);
  color: rgba(0,0,0,0.4);
}
```

- [ ] **Step 2: Write DateSeparator.jsx**

```jsx
import './DateSeparator.css';

function formatDateLabel(dateStr) {
  const date = new Date(dateStr);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  const msgDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());

  if (msgDate.getTime() === today.getTime()) return 'Сегодня';
  if (msgDate.getTime() === yesterday.getTime()) return 'Вчера';

  const day = date.getDate();
  const months = ['января','февраля','марта','апреля','мая','июня',
                  'июля','августа','сентября','октября','ноября','декабря'];
  const month = months[date.getMonth()];

  if (date.getFullYear() === now.getFullYear()) return `${day} ${month}`;
  return `${day} ${month} ${date.getFullYear()}`;
}

export default function DateSeparator({ date }) {
  return (
    <div className="date-separator">
      <div className="date-separator__pill">{formatDateLabel(date)}</div>
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/DateSeparator.css client/src/components/chat/DateSeparator.jsx
git commit -m "add DateSeparator — sticky glass pill with date formatting"
```

---

### Task 8: UnreadDivider component

**Files:**
- Create: `client/src/components/chat/UnreadDivider.css`
- Create: `client/src/components/chat/UnreadDivider.jsx`

- [ ] **Step 1: Write UnreadDivider.css**

```css
.unread-divider {
  display: flex;
  align-items: center;
  margin: 12px 0;
  gap: 12px;
  animation: msgAppear 0.3s ease both;
  transition: opacity 0.5s;
}

.unread-divider--fading {
  opacity: 0;
}

.unread-divider__line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(200,255,0,0.15), transparent);
}

.unread-divider__text {
  font-size: 11px;
  font-weight: 600;
  color: rgba(200,255,0,0.5);
  white-space: nowrap;
}

[data-theme="light"] .unread-divider__line {
  background: linear-gradient(90deg, transparent, rgba(74,140,0,0.2), transparent);
}

[data-theme="light"] .unread-divider__text {
  color: rgba(74,140,0,0.6);
}
```

- [ ] **Step 2: Write UnreadDivider.jsx**

```jsx
import { useState, useEffect, useRef } from 'react';
import './UnreadDivider.css';

export default function UnreadDivider({ onVisible }) {
  const [fading, setFading] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!ref.current) return;
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setTimeout(() => setFading(true), 5000);
        onVisible?.();
        observer.disconnect();
      }
    }, { threshold: 0.5 });
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [onVisible]);

  return (
    <div
      ref={ref}
      className={`unread-divider ${fading ? 'unread-divider--fading' : ''}`}
    >
      <div className="unread-divider__line" />
      <span className="unread-divider__text">Новые сообщения</span>
      <div className="unread-divider__line" />
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/UnreadDivider.css client/src/components/chat/UnreadDivider.jsx
git commit -m "add UnreadDivider — accent line with auto-fade on scroll"
```

---

## Chunk 4: Message Actions Pill + Link Preview

### Task 9: MessageActionsPill component

**Files:**
- Create: `client/src/components/chat/MessageActionsPill.css`
- Create: `client/src/components/chat/MessageActionsPill.jsx`

- [ ] **Step 1: Write MessageActionsPill.css**

```css
.msg-actions-pill {
  position: absolute;
  top: 50%;
  display: flex;
  gap: 2px;
  padding: 3px;
  background: rgba(255,255,255,0.06);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 0.5px solid rgba(255,255,255,0.08);
  border-radius: 100px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.2);
  opacity: 0;
  transform: translateY(-50%) scale(0.8);
  transition: opacity 0.15s ease-out, transform 0.15s ease-out;
  pointer-events: none;
  z-index: 20;
}

.chat-message:hover .msg-actions-pill {
  opacity: 1;
  transform: translateY(-50%) scale(1);
  pointer-events: auto;
}

/* Position based on own/other */
.chat-message--own .msg-actions-pill {
  right: calc(100% + 6px);
}

.chat-message--other .msg-actions-pill {
  left: calc(100% + 6px);
}

.msg-actions-pill__btn {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: none;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,0.5);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.msg-actions-pill__btn:hover {
  background: rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.8);
}

.msg-actions-pill__btn svg {
  width: 14px;
  height: 14px;
}

[data-theme="light"] .msg-actions-pill {
  background: rgba(255,255,255,0.7);
  border-color: rgba(0,0,0,0.06);
}

@media (prefers-reduced-motion: reduce) {
  .msg-actions-pill {
    transition: none;
  }
}
```

- [ ] **Step 2: Write MessageActionsPill.jsx**

```jsx
import { CornerUpLeft, SmilePlus, Pencil, Trash2 } from 'lucide-react';
import './MessageActionsPill.css';

export default function MessageActionsPill({ isOwn, onReply, onReact, onEdit, onDelete }) {
  return (
    <div className="msg-actions-pill">
      <button className="msg-actions-pill__btn" onClick={onReply} title="Ответить">
        <CornerUpLeft />
      </button>
      <button className="msg-actions-pill__btn" onClick={onReact} title="Реакция">
        <SmilePlus />
      </button>
      {isOwn && (
        <>
          <button className="msg-actions-pill__btn" onClick={onEdit} title="Редактировать">
            <Pencil />
          </button>
          <button className="msg-actions-pill__btn" onClick={onDelete} title="Удалить">
            <Trash2 />
          </button>
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Integrate into ChatMessage.jsx**

Добавить `<MessageActionsPill />` внутрь `.chat-message__col` (после bubble). Передать isOwn и коллбеки. Убрать старые hover action buttons.

- [ ] **Step 4: Commit**

```bash
git add client/src/components/chat/MessageActionsPill.css client/src/components/chat/MessageActionsPill.jsx client/src/components/chat/ChatMessage.jsx
git commit -m "add MessageActionsPill — hover glass capsule with reply/edit/delete"
```

---

### Task 10: LinkPreviewCard component

**Files:**
- Create: `client/src/components/chat/LinkPreviewCard.css`
- Create: `client/src/components/chat/LinkPreviewCard.jsx`

- [ ] **Step 1: Write LinkPreviewCard.css**

```css
.link-preview {
  margin-top: 8px;
  border-radius: 14px;
  background: rgba(255,255,255,0.04);
  border: 0.5px solid rgba(255,255,255,0.06);
  overflow: hidden;
  cursor: pointer;
  transition: background 0.2s;
}

.link-preview:hover {
  background: rgba(255,255,255,0.06);
}

.link-preview__image {
  width: 100%;
  height: 120px;
  object-fit: cover;
  display: block;
}

.link-preview__body {
  padding: 10px;
}

.link-preview__title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 2px;
}

.link-preview__desc {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 4px;
}

.link-preview__domain {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: rgba(255,255,255,0.25);
}

.link-preview__domain svg {
  width: 12px;
  height: 12px;
}

[data-theme="light"] .link-preview {
  background: rgba(0,0,0,0.04);
  border-color: rgba(0,0,0,0.06);
}

[data-theme="light"] .link-preview__desc {
  color: rgba(0,0,0,0.45);
}

[data-theme="light"] .link-preview__domain {
  color: rgba(0,0,0,0.3);
}
```

- [ ] **Step 2: Write LinkPreviewCard.jsx**

```jsx
import { Globe } from 'lucide-react';
import './LinkPreviewCard.css';

export default function LinkPreviewCard({ preview }) {
  if (!preview) return null;
  const { url, title, description, image, domain } = preview;

  const handleClick = () => {
    window.electronAPI?.openExternal?.(url) || window.open(url, '_blank');
  };

  return (
    <div className="link-preview" onClick={handleClick}>
      {image && <img className="link-preview__image" src={image} alt="" loading="lazy" />}
      <div className="link-preview__body">
        {title && <div className="link-preview__title">{title}</div>}
        {description && <div className="link-preview__desc">{description}</div>}
        <div className="link-preview__domain">
          <Globe />
          <span>{domain || new URL(url).hostname}</span>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/LinkPreviewCard.css client/src/components/chat/LinkPreviewCard.jsx
git commit -m "add LinkPreviewCard — glass card for URL previews"
```

---

## Chunk 5: ChatView Integration

### Task 11: Update ChatView.css

**Files:**
- Rewrite: `client/src/components/chat/ChatView.css`

- [ ] **Step 1: Update ChatView.css**

Убрать все старые стили сообщений, header, input. Оставить:
- `.chat-view` (floating window с drag/resize — без изменений)
- `.chat-view--inline` (inline mode)
- `.chat-view--morph` / `--closing` (window animations)
- `.chat-view__drag-zone` (если всё ещё нужен для drag)
- `.chat-view__resize--*` (resize handles)

Добавить:
```css
.chat-view__messages {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 20px 12px;
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: smooth;
}
```

Удалить: все `.chat-message*`, `.chat-header*`, `.chat-input*` стили (теперь в отдельных файлах).

- [ ] **Step 2: Commit**

```bash
git add client/src/components/chat/ChatView.css
git commit -m "clean ChatView CSS — remove old message/header/input styles"
```

---

### Task 12: Update ChatView.jsx — integrate all new components

**Files:**
- Modify: `client/src/components/chat/ChatView.jsx`

- [ ] **Step 1: Update imports**

Добавить импорты:
```jsx
import TypingBubble from './TypingBubble';
import DateSeparator from './DateSeparator';
import UnreadDivider from './UnreadDivider';
import { getHueFromString } from '../../utils/hueIdentity';
```

- [ ] **Step 2: Add date separator logic**

В рендере сообщений — перед каждым сообщением проверять: если дата изменилась относительно предыдущего, вставить `<DateSeparator date={msg.createdAt} />`.

- [ ] **Step 3: Add unread divider**

Между последним прочитанным и первым непрочитанным вставить `<UnreadDivider />`. Убрать при отправке своего сообщения.

- [ ] **Step 4: Add typing bubble**

Заменить текущий typing indicator на `<TypingBubble user={typingUser} hue={getHueFromString(typingUser.username)} />` в конце списка сообщений.

- [ ] **Step 5: Pass groupPosition to ChatMessage**

Обновить логику группировки: передавать `groupPosition` prop ('solo', 'first', 'mid', 'last'). Текущая логика группировки (prev/next userId comparison) остаётся, но передаётся как prop.

- [ ] **Step 6: Pass hue to ChatMessage**

```jsx
const hue = getHueFromString(msg.user?.username || msg.userId);
<ChatMessage key={msg.id} message={msg} hue={hue} groupPosition={pos} ... />
```

- [ ] **Step 7: Verify app builds**

Run: `cd client && npx vite build 2>&1 | tail -5`
Expected: build success

- [ ] **Step 8: Commit**

```bash
git add client/src/components/chat/ChatView.jsx
git commit -m "integrate new chat components — date separators, unread divider, typing bubble, hue identity"
```

---

### Task 13: Final visual testing and polish

- [ ] **Step 1: Run dev server and test in Electron**

Run: `cd client && npm run dev`

Проверить:
- Messages отображаются с двуслойным glass
- Own = green tint, other = hue color
- Grouping радиусы корректны (solo/first/mid/last)
- Header island по центру
- Input morph: compact → expanded → compact
- Send ripple при отправке
- Typing dots анимированы
- Date separators sticky при скролле
- Emoji-only большие без пузыря
- Message actions pill на hover
- Reply preview над input

- [ ] **Step 2: Fix any visual issues found**

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "chat redesign — visual polish and fixes"
```

---

## Chunk 6: Emoji Particles + Reply Scroll + LinkPreview Integration

### Task 14: Emoji explosion particles in ChatMessage

**Files:**
- Modify: `client/src/components/chat/ChatMessage.jsx`
- Modify: `client/src/components/chat/ChatMessage.css`

- [ ] **Step 1: Add particle CSS**

Добавить в ChatMessage.css:

```css
/* Emoji explosion particles */
.chat-message__emoji-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.chat-message__particle {
  position: absolute;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #c8ff00;
  top: 50%;
  left: 50%;
  animation: particleFly 0.6s ease-out forwards;
}

.chat-message__particle:nth-child(1) { --dx: -20px; --dy: -25px; }
.chat-message__particle:nth-child(2) { --dx: 22px; --dy: -18px; }
.chat-message__particle:nth-child(3) { --dx: -15px; --dy: 20px; }
.chat-message__particle:nth-child(4) { --dx: 25px; --dy: 15px; }
.chat-message__particle:nth-child(5) { --dx: -8px; --dy: -28px; }
.chat-message__particle:nth-child(6) { --dx: 18px; --dy: 22px; }

@keyframes particleFly {
  0% { transform: translate(0, 0) scale(1); opacity: 1; }
  100% { transform: translate(var(--dx), var(--dy)) scale(0); opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .chat-message__particle { display: none; }
}
```

- [ ] **Step 2: Add particle rendering in ChatMessage.jsx**

Для emoji-only сообщений, рендерить 6 particle divs внутри `.chat-message__emoji-particles` обёртки:

```jsx
{isEmojiOnly && (
  <div style={{ position: 'relative', display: 'inline-block' }}>
    <div className="chat-message__emoji">{text}</div>
    <div className="chat-message__emoji-particles">
      {[...Array(6)].map((_, i) => <div key={i} className="chat-message__particle" />)}
    </div>
  </div>
)}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/ChatMessage.jsx client/src/components/chat/ChatMessage.css
git commit -m "add emoji explosion particles — 6 accent dots fly out on emoji-only messages"
```

---

### Task 15: Reply scroll-to-original + highlight

**Files:**
- Modify: `client/src/components/chat/ChatMessage.jsx`
- Modify: `client/src/components/chat/ChatMessage.css`

- [ ] **Step 1: Add highlight CSS**

Добавить в ChatMessage.css:

```css
.chat-message--highlighted .chat-message__bubble-outer {
  box-shadow: 0 0 20px rgba(200,255,0,0.1);
  animation: highlightPulse 1.5s ease-out;
}

@keyframes highlightPulse {
  0% { box-shadow: 0 0 30px rgba(200,255,0,0.2); }
  100% { box-shadow: 0 1.5px 6px rgba(0,0,0,0.1); }
}
```

- [ ] **Step 2: Add scroll-to-original logic in ChatMessage.jsx**

При клике на `.chat-message__reply-quote`:
```jsx
const handleReplyClick = () => {
  const originalEl = document.getElementById(`msg-${replyTo.id}`);
  if (originalEl) {
    originalEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
    originalEl.classList.add('chat-message--highlighted');
    setTimeout(() => originalEl.classList.remove('chat-message--highlighted'), 1500);
  }
};
```

Каждый ChatMessage должен иметь `id={`msg-${message.id}`}` на корневом div.

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/ChatMessage.jsx client/src/components/chat/ChatMessage.css
git commit -m "add reply scroll-to-original with highlight pulse"
```

---

### Task 16: Integrate LinkPreviewCard into ChatMessage

**Files:**
- Modify: `client/src/components/chat/ChatMessage.jsx`

- [ ] **Step 1: Add LinkPreviewCard render**

Внутри `.chat-message__bubble-inner`, после текста и перед read icon:

```jsx
import LinkPreviewCard from './LinkPreviewCard';

// внутри bubble-inner:
{message.linkPreview && <LinkPreviewCard preview={message.linkPreview} />}
```

**Примечание:** Серверный endpoint для фетчинга OG-метаданных (og:title, og:description, og:image) отложен. LinkPreviewCard готов к использованию когда сервер начнёт присылать `linkPreview` объект в message data. На данном этапе — компонент создан, интеграция на месте, но данных пока не будет.

- [ ] **Step 2: Commit**

```bash
git add client/src/components/chat/ChatMessage.jsx
git commit -m "integrate LinkPreviewCard into ChatMessage — ready for server OG data"
```

---

## Отложено (не в этом плане)

- **Reply Thread Line** (вертикальная соединительная линия между ответом и оригиналом) — требует знания DOM позиций обоих сообщений + dynamic line rendering. Сложная layout-фича, реализуем отдельно.
- **Link Preview server endpoint** — серверный фетчер OG-метаданных, отложен.
- **Voice Message Waveform** — требует backend для записи/хранения аудио, отложен до v0.6+.
