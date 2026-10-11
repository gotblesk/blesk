# Profile System Redesign — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragmented profile system (ProfileScreen, UserProfileModal, StatusEditor) with a unified component architecture (ProfileCard, ProfilePopover, ProfileEditor, UserBadge) featuring popover-based user profiles, banner with blurred avatar, segmented circle status switcher, and avatar lightbox.

**Architecture:** 5 new components replace 3 old ones. ProfileCard is the core (mode own/other). ProfilePopover positions it as a popover near avatars. ProfileEditor is a fullscreen editing panel. UserBadge is a reusable micro-component for avatar+name+status across the app. All use double-layer glass styling, Framer Motion animations, and Phosphor icons.

**Tech Stack:** React, Framer Motion, Phosphor Icons, CSS (double-layer glass tokens from global.css), Zustand (chatStore for online status)

**Spec:** `docs/superpowers/specs/2026-04-02-profile-redesign-design.md`

---

## File Structure

### New files to create:
- `client/src/components/profile/ProfileCard.jsx` — unified profile card (mode own/other)
- `client/src/components/profile/ProfileCard.css` — styles for ProfileCard
- `client/src/components/profile/ProfilePopover.jsx` — popover positioner wrapping ProfileCard
- `client/src/components/profile/ProfilePopover.css` — popover styles
- `client/src/components/profile/ProfileEditor.jsx` — fullscreen editing panel
- `client/src/components/profile/ProfileEditor.css` — editor styles
- `client/src/components/profile/UserBadge.jsx` — micro-component avatar+name+status
- `client/src/components/profile/UserBadge.css` — badge styles
- `client/src/components/profile/AvatarLightbox.jsx` — fullscreen avatar viewer
- `client/src/components/profile/AvatarLightbox.css` — lightbox styles
- `client/src/components/profile/SegmentedCircle.jsx` — radial status switcher
- `client/src/components/profile/SegmentedCircle.css` — segmented circle styles
- `client/src/utils/months.js` — month declension utility

### Files to modify:
- `client/src/components/TopNav/TopNav.jsx` — replace status dots with SegmentedCircle, update "Профиль" action
- `client/src/components/TopNav/TopNav.css` — remove old status dot styles, add new dropdown styles
- `client/src/components/main/MainScreen.jsx` — replace ProfileScreen/StatusEditor with ProfileEditor, add ProfilePopover
- `client/src/components/chat/ChatView.jsx` — replace UserProfileModal with ProfilePopover
- `client/src/components/friends/FriendsScreen.jsx` — replace UserProfileModal with ProfilePopover, replace manual avatar+name with UserBadge
- `client/src/components/voice/VoiceRoom.jsx` — replace UserProfileModal with ProfilePopover
- `client/src/components/chat/GroupMembersPanel.jsx` — replace UserProfileModal with ProfilePopover, use UserBadge

### Files to delete (after migration):
- `client/src/components/profile/ProfileScreen.jsx` (422 lines)
- `client/src/components/profile/ProfileScreen.css` (314 lines)
- `client/src/components/profile/StatusEditor.jsx` (115 lines)
- `client/src/components/profile/StatusEditor.css` (180 lines)
- `client/src/components/ui/UserProfileModal.jsx` (214 lines)
- `client/src/components/ui/UserProfileModal.css` (333 lines)

### Files kept unchanged:
- `client/src/components/profile/AvatarCropModal.jsx` (136 lines) — works well, reused in ProfileEditor
- `client/src/components/profile/AvatarCropModal.css` (213 lines)
- `client/src/components/ui/Avatar.jsx` (71 lines) — reused inside UserBadge and ProfileCard
- `client/src/components/ui/Avatar.css` (70 lines)

---

## Chunk 1: Foundation Components

### Task 1: Month declension utility

**Files:**
- Create: `client/src/utils/months.js`

- [ ] **Step 1: Create months.js**

```javascript
// client/src/utils/months.js
const MONTHS_GENITIVE = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'
];

export function formatJoinDate(dateString) {
  const date = new Date(dateString);
  const month = MONTHS_GENITIVE[date.getMonth()];
  const year = date.getFullYear();
  return `с ${month} ${year}`;
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/utils/months.js
git commit -m "feat: month declension utility for profile dates"
```

---

### Task 2: AvatarLightbox component

**Files:**
- Create: `client/src/components/profile/AvatarLightbox.jsx`
- Create: `client/src/components/profile/AvatarLightbox.css`

- [ ] **Step 1: Create AvatarLightbox.jsx**

```jsx
// client/src/components/profile/AvatarLightbox.jsx
import { useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import API_URL from '../../config';
import './AvatarLightbox.css';

export default function AvatarLightbox({ avatarFilename, userId, isOpen, onClose }) {
  const handleKeyDown = useCallback((e) => {
    if (e.key === 'Escape') onClose();
  }, [onClose]);

  useEffect(() => {
    if (isOpen) {
      document.addEventListener('keydown', handleKeyDown);
      return () => document.removeEventListener('keydown', handleKeyDown);
    }
  }, [isOpen, handleKeyDown]);

  if (!avatarFilename) return null;

  const src = `${API_URL}/uploads/avatars/${avatarFilename}`;

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="avatar-lightbox"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          onClick={onClose}
          data-testid="avatar-lightbox"
        >
          <motion.div
            className="avatar-lightbox__backdrop"
            data-testid="avatar-lightbox-backdrop"
          />
          <motion.img
            className="avatar-lightbox__image"
            src={src}
            alt="Avatar"
            layoutId={`avatar-${userId}`}
            data-testid="avatar-lightbox-image"
            onClick={(e) => e.stopPropagation()}
          />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
```

- [ ] **Step 2: Create AvatarLightbox.css**

```css
/* client/src/components/profile/AvatarLightbox.css */
.avatar-lightbox {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal, 1000);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: zoom-out;
}

.avatar-lightbox__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.avatar-lightbox__image {
  position: relative;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  object-fit: cover;
  cursor: default;
  box-shadow: 0 0 60px rgba(0, 0, 0, 0.5);
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/components/profile/AvatarLightbox.jsx client/src/components/profile/AvatarLightbox.css
git commit -m "feat: avatar lightbox with shared layout animation"
```

---

### Task 3: SegmentedCircle component

**Files:**
- Create: `client/src/components/profile/SegmentedCircle.jsx`
- Create: `client/src/components/profile/SegmentedCircle.css`

- [ ] **Step 1: Create SegmentedCircle.jsx**

Radial status switcher. 3 colored arc segments around a status dot. SVG-based arcs with Framer Motion spring animations.

```jsx
// client/src/components/profile/SegmentedCircle.jsx
import { useState, useRef, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import './SegmentedCircle.css';

const STATUSES = [
  { key: 'online', label: 'В сети', color: '#4ade80' },
  { key: 'dnd', label: 'Не беспокоить', color: '#f59e0b' },
  { key: 'invisible', label: 'Невидимка', color: '#6b7280' },
];

// SVG arc path helper
function describeArc(cx, cy, r, startAngle, endAngle) {
  const start = polarToCartesian(cx, cy, r, endAngle);
  const end = polarToCartesian(cx, cy, r, startAngle);
  const largeArc = endAngle - startAngle <= 180 ? '0' : '1';
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArc} 0 ${end.x} ${end.y}`;
}

function polarToCartesian(cx, cy, r, angleDeg) {
  const rad = ((angleDeg - 90) * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}

// Arc angles: 3 segments of ~100° with 20° gaps
const ARCS = [
  { startAngle: 0, endAngle: 100 },     // top-left: online
  { startAngle: 120, endAngle: 220 },   // top-right: dnd
  { startAngle: 240, endAngle: 340 },   // bottom: invisible
];

export default function SegmentedCircle({ currentStatus, onStatusChange }) {
  const [expanded, setExpanded] = useState(false);
  const [hoveredKey, setHoveredKey] = useState(null);
  const containerRef = useRef(null);

  const currentColor = STATUSES.find(s => s.key === currentStatus)?.color || '#4ade80';

  const handleToggle = useCallback(() => {
    setExpanded(prev => !prev);
  }, []);

  const handleSelect = useCallback((key) => {
    onStatusChange?.(key);
    setExpanded(false);
  }, [onStatusChange]);

  const handleKeyDown = useCallback((e) => {
    if (!expanded) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setExpanded(true);
      }
      return;
    }
    const currentIdx = STATUSES.findIndex(s => s.key === (hoveredKey || currentStatus));
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault();
      const next = (currentIdx + 1) % STATUSES.length;
      setHoveredKey(STATUSES[next].key);
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault();
      const prev = (currentIdx - 1 + STATUSES.length) % STATUSES.length;
      setHoveredKey(STATUSES[prev].key);
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleSelect(hoveredKey || currentStatus);
    } else if (e.key === 'Escape') {
      setExpanded(false);
    }
  }, [expanded, hoveredKey, currentStatus, handleSelect]);

  // Close on outside click
  useEffect(() => {
    if (!expanded) return;
    const handleClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setExpanded(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [expanded]);

  const svgSize = 60;
  const center = svgSize / 2;
  const radius = 22;

  return (
    <div
      className="seg-circle"
      ref={containerRef}
      role="radiogroup"
      aria-label="Статус"
      tabIndex={0}
      onKeyDown={handleKeyDown}
      data-testid="topnav-status-dot"
    >
      {/* Dot trigger */}
      <motion.button
        className="seg-circle__dot"
        style={{ background: currentColor }}
        onClick={handleToggle}
        animate={{ scale: expanded ? 0 : 1 }}
        transition={{ type: 'spring', stiffness: 300, damping: 25 }}
        aria-label={`Статус: ${STATUSES.find(s => s.key === currentStatus)?.label}`}
      />

      {/* Expanded ring */}
      <AnimatePresence>
        {expanded && (
          <motion.div
            className="seg-circle__ring"
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 300, damping: 25 }}
          >
            <svg
              width={svgSize}
              height={svgSize}
              viewBox={`0 0 ${svgSize} ${svgSize}`}
              className="seg-circle__svg"
            >
              {STATUSES.map((status, i) => {
                const arc = ARCS[i];
                const isActive = status.key === currentStatus;
                const isHovered = status.key === hoveredKey;
                return (
                  <motion.path
                    key={status.key}
                    d={describeArc(center, center, radius, arc.startAngle, arc.endAngle)}
                    fill="none"
                    stroke={status.color}
                    strokeWidth={isHovered || isActive ? 6 : 4}
                    strokeLinecap="round"
                    className="seg-circle__arc"
                    role="radio"
                    aria-checked={isActive}
                    aria-label={status.label}
                    data-testid="topnav-status-segment"
                    data-status={status.key}
                    initial={{ pathLength: 0, opacity: 0 }}
                    animate={{ pathLength: 1, opacity: 1 }}
                    exit={{ pathLength: 0, opacity: 0 }}
                    transition={{
                      type: 'spring',
                      stiffness: 300,
                      damping: 25,
                      delay: i * 0.05,
                    }}
                    onMouseEnter={() => setHoveredKey(status.key)}
                    onMouseLeave={() => setHoveredKey(null)}
                    onClick={() => handleSelect(status.key)}
                    style={{ cursor: 'pointer' }}
                  />
                );
              })}
            </svg>

            {/* Tooltip */}
            <AnimatePresence>
              {hoveredKey && (
                <motion.div
                  className="seg-circle__tooltip"
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.1 }}
                >
                  {STATUSES.find(s => s.key === hoveredKey)?.label}
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
```

- [ ] **Step 2: Create SegmentedCircle.css**

```css
/* client/src/components/profile/SegmentedCircle.css */
.seg-circle {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  outline: none;
}

.seg-circle:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
  border-radius: 50%;
}

.seg-circle__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  transition: box-shadow var(--duration-fast) ease;
  padding: 0;
}

.seg-circle__dot:hover {
  box-shadow: 0 0 8px currentColor;
}

.seg-circle__ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
}

.seg-circle__svg {
  display: block;
}

.seg-circle__arc {
  transition: stroke-width var(--duration-fast) ease;
}

.seg-circle__tooltip {
  position: absolute;
  top: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  padding: 4px 10px;
  border-radius: var(--radius-sm, 8px);
  background: var(--dl-outer-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--glass-border);
  font-size: 12px;
  color: var(--text-secondary);
  pointer-events: none;
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/components/profile/SegmentedCircle.jsx client/src/components/profile/SegmentedCircle.css
git commit -m "feat: segmented circle radial status switcher"
```

---

### Task 4: UserBadge component

**Files:**
- Create: `client/src/components/profile/UserBadge.jsx`
- Create: `client/src/components/profile/UserBadge.css`

- [ ] **Step 1: Create UserBadge.jsx**

```jsx
// client/src/components/profile/UserBadge.jsx
import { memo } from 'react';
import Avatar from '../ui/Avatar';
import useChatStore from '../../store/chatStore';
import './UserBadge.css';

const SIZES = {
  sm: { avatar: 24, name: 13, sub: 0 },
  md: { avatar: 36, name: 15, sub: 13 },
  lg: { avatar: 48, name: 17, sub: 14 },
};

function UserBadge({
  user,
  userId,
  size = 'md',
  showStatus = true,
  showCustomStatus = false,
  clickable = true,
  subtitle,
  onClick,
  className = '',
}) {
  const s = SIZES[size] || SIZES.md;
  const userStatuses = useChatStore((state) => showStatus ? state.userStatuses : {});
  const customStatuses = useChatStore((state) => showCustomStatus ? state.customStatuses : {});

  const id = userId || user?.id;
  const isOnline = showStatus && userStatuses[id] && userStatuses[id] !== 'invisible';
  const userStatus = showStatus ? userStatuses[id] : undefined;
  const customStatus = showCustomStatus ? customStatuses[id] : undefined;

  const displayName = user?.displayName || user?.username || 'Unknown';
  const secondLine = subtitle || (showCustomStatus && customStatus) || null;

  return (
    <div
      className={`user-badge user-badge--${size} ${clickable ? 'user-badge--clickable' : ''} ${className}`}
      onClick={clickable ? onClick : undefined}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
      onKeyDown={clickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick?.(); } } : undefined}
      title={displayName}
      data-testid="user-badge"
    >
      <Avatar
        user={user}
        size={s.avatar}
        showOnline={showStatus}
        isOnline={isOnline}
        userStatus={userStatus}
        data-testid="user-badge-avatar"
      />
      <div className="user-badge__text">
        <span className="user-badge__name" style={{ fontSize: s.name }}>
          {displayName}
        </span>
        {secondLine && s.sub > 0 && (
          <span className="user-badge__sub" style={{ fontSize: s.sub }}>
            {secondLine}
          </span>
        )}
      </div>
    </div>
  );
}

export default memo(UserBadge, (prev, next) =>
  (prev.userId || prev.user?.id) === (next.userId || next.user?.id) &&
  prev.user?.avatar === next.user?.avatar &&
  prev.user?.username === next.user?.username &&
  prev.size === next.size
);
```

- [ ] **Step 2: Create UserBadge.css**

```css
/* client/src/components/profile/UserBadge.css */
.user-badge {
  display: flex;
  align-items: center;
  gap: var(--space-2, 8px);
  padding: var(--space-1, 4px);
  border-radius: var(--radius-sm, 8px);
  transition: background var(--duration-fast, 150ms) ease;
  user-select: none;
  min-width: 0;
}

.user-badge--clickable {
  cursor: pointer;
}

.user-badge--clickable:hover {
  background: var(--glass-bg);
}

.user-badge--clickable:active {
  transform: scale(0.97);
}

.user-badge--clickable:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.user-badge__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.user-badge__name {
  font-family: var(--font-body, 'Onest', sans-serif);
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-badge__sub {
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Size-specific tweaks */
.user-badge--sm {
  padding: 2px;
  gap: var(--space-1, 4px);
}

.user-badge--sm .user-badge__text {
  flex-direction: row;
}
```

- [ ] **Step 3: Commit**

```bash
git add client/src/components/profile/UserBadge.jsx client/src/components/profile/UserBadge.css
git commit -m "feat: reusable UserBadge micro-component"
```

---

## Chunk 2: ProfileCard

### Task 5: ProfileCard component

**Files:**
- Create: `client/src/components/profile/ProfileCard.jsx`
- Create: `client/src/components/profile/ProfileCard.css`

- [ ] **Step 1: Create ProfileCard.jsx**

Core unified profile card. Mode 'own' shows edit button, mode 'other' shows action buttons. Banner with blurred avatar, avatar with lightbox, placeholder sections for future features.

Key structure:
- Banner (blurred avatar or hue gradient fallback)
- Avatar 96px with hue ring and online glow (click → AvatarLightbox)
- Username (var(--font-display) Nekst, 22px) + tag (#0000)
- Status dot + text + edit button (own only)
- Custom status (italic)
- Bio (max 3 lines, ellipsis)
- Placeholder sections: "Теги" and "Коллекция" with Lock icon + "Скоро"
- Join date chip: "На blesk с марта 2026" (using formatJoinDate)
- Primary action button
- 3D tilt on mousemove (4deg max)

Props:
```
mode: 'own' | 'other'
userId: string
user: object (for mode own — from MainScreen currentUser prop)
onEdit: () => void (own mode)
onOpenChat: (userId) => void (other mode)
onClose: () => void
```

Data loading (mode other): `GET /api/users/:id` with loading skeleton and error state.
Friend state: determined by `isFriend` from API response.
Friend request: `POST /api/friends/request` with body `{ userId }`.
Banned user: Warning icon + "Пользователь заблокирован".

References:
- @ui-ux-pro-max for visual quality
- @frontend-design for glass component patterns
- @motion-framer for animation implementation
- @react-patterns for component structure

Full implementation: ~250-300 lines. Build incrementally — banner first, then avatar section, then info, then actions, then placeholders.

- [ ] **Step 2: Create ProfileCard.css**

Styles: 340px width, double-layer glass, banner 80px with blur(40px) + overlay, avatar negative margin, hue-based ring animation, glass-inner placeholder sections with shimmer on hover, accent primary button, 3D tilt perspective container, reduced-motion overrides.

Light theme: all values via CSS variables, automatic inversion.

- [ ] **Step 3: Verify ProfileCard renders in isolation**

Temporarily import into MainScreen to test both modes. Check:
- Banner renders with blurred avatar
- Avatar shows with hue ring
- Username/tag/status display correctly
- Placeholder sections show "Скоро"
- Join date formatted correctly ("с марта 2026")
- 3D tilt works on mousemove
- Light theme renders correctly
- Reduced motion fallbacks work

- [ ] **Step 4: Commit**

```bash
git add client/src/components/profile/ProfileCard.jsx client/src/components/profile/ProfileCard.css
git commit -m "feat: unified ProfileCard component with banner and lightbox"
```

---

## Chunk 3: ProfilePopover

### Task 6: ProfilePopover component

**Files:**
- Create: `client/src/components/profile/ProfilePopover.jsx`
- Create: `client/src/components/profile/ProfilePopover.css`

- [ ] **Step 1: Create ProfilePopover.jsx**

React Portal to `document.body`. Positions ProfileCard near anchor element.

Positioning logic:
1. Get anchor rect via `anchorRef.current.getBoundingClientRect()`
2. Try right side (anchor.right + 8px, centered vertically)
3. If overflows viewport right → try left side (anchor.left - cardWidth - 8px)
4. If overflows both → center of screen (fallback)
5. Clamp to viewport with 16px padding from edges
6. Re-calculate on open (not on scroll/resize — just close)

Props: `anchorRef`, `userId`, `user` (for own mode), `isOpen`, `onClose`, `onOpenChat`, `onEdit`

Determines mode: if userId matches current user → 'own', else 'other'.

Closing: click outside, Escape, window resize, scroll.
Animation: spring scale 0.92→1 + opacity, transform-origin based on position side.
Backdrop: transparent div covering viewport for click interception.

- [ ] **Step 2: Create ProfilePopover.css**

Position fixed, z-index var(--z-dropdown). Transparent backdrop. Card container with pointer-events.

- [ ] **Step 3: Commit**

```bash
git add client/src/components/profile/ProfilePopover.jsx client/src/components/profile/ProfilePopover.css
git commit -m "feat: ProfilePopover with smart positioning and portal"
```

---

## Chunk 4: ProfileEditor

### Task 7: ProfileEditor component

**Files:**
- Create: `client/src/components/profile/ProfileEditor.jsx`
- Create: `client/src/components/profile/ProfileEditor.css`

- [ ] **Step 1: Create ProfileEditor.jsx**

Fullscreen panel (replaces main content area like Settings). Slide-in from right animation.

Sections:
1. Header: ArrowLeft "Назад" + title "Профиль"
2. Avatar 96px with Camera overlay → opens AvatarCropModal
3. Username + tag + join date (display only)
4. Section "Основное": nickname input, email cell (expandable), phone cell, password cell (expandable)
5. Section "О себе": textarea 200 chars with counter (orange >180)
6. Section "Статус": 3 preset buttons + custom status input (50 chars)
7. Save button (accent, disabled when clean)

Migrate ALL logic from current ProfileScreen.jsx (422 lines):
- Avatar upload: `POST /api/users/me/avatar` via AvatarCropModal
- Nickname change: `PUT /api/users/me` with validation (2-32 chars, debounce 500ms uniqueness check)
- Email verification: `/api/auth/resend-code` → `/api/auth/verify-email`
- Password change: `/api/auth/change-password/request` → `/api/auth/change-password/confirm`
- Bio update: `PUT /api/users/me`
- Status change: `PUT /api/users/me` + socket emit

Migrate status logic from StatusEditor.jsx (115 lines):
- 3 presets (online/dnd/invisible) as buttons
- Custom status text input

Dirty state tracking. Ctrl+S to save. Escape to go back (with confirm if dirty).
Expandable cells: motion.div animate height auto.

Props: `open`, `onClose`, `user`, `onUserUpdate`

- [ ] **Step 2: Create ProfileEditor.css**

Double layer glass panel. Section labels (text-tertiary, uppercase, 12px). Glass-inner cells with Phosphor icon left. Expandable cell transitions. Accent save button with disabled state. Slide-in animation (translateX 100%→0).

- [ ] **Step 3: Verify ProfileEditor**

Test all edit flows:
- Avatar upload + crop
- Nickname change with validation
- Email expansion + verification code
- Password change flow
- Bio with char counter
- Status preset selection
- Custom status text
- Dirty state → Save enabled
- Ctrl+S saves
- Escape with unsaved → confirm dialog

- [ ] **Step 4: Commit**

```bash
git add client/src/components/profile/ProfileEditor.jsx client/src/components/profile/ProfileEditor.css
git commit -m "feat: fullscreen ProfileEditor with all edit flows"
```

---

## Chunk 5: Integration — Replace Old Components

### Task 8: Update TopNav dropdown

**Files:**
- Modify: `client/src/components/TopNav/TopNav.jsx`
- Modify: `client/src/components/TopNav/TopNav.css`

- [ ] **Step 1: Replace status dots with SegmentedCircle**

In TopNav.jsx:
- Add import: `import SegmentedCircle from '../profile/SegmentedCircle';`
- Remove STATUS_OPTIONS constant (lines 19-23)
- Replace status dots section (lines 126-141) with:
```jsx
<SegmentedCircle currentStatus={currentStatus} onStatusChange={(key) => { onStatusChange?.(key); setUserMenuOpen(false); }} />
```
- Place SegmentedCircle in the header next to username, after the status text
- Remove old `um__status-row` JSX and its styles from TopNav.css

- [ ] **Step 2: Update dropdown layout**

- Avatar size: 44px (was 40px)
- Show tag (#0000) under username
- Width: 260px
- "Профиль" button: instead of `onNavigate?.('profile')`, should trigger ProfilePopover (own mode) — pass callback prop from MainScreen

- [ ] **Step 3: Clean up TopNav.css**

Remove old status dot styles (`um__status-row`, `um__status-dot-btn`, etc.). Add any new styles for updated dropdown header.

- [ ] **Step 4: Commit**

```bash
git add client/src/components/TopNav/TopNav.jsx client/src/components/TopNav/TopNav.css
git commit -m "redesign: TopNav dropdown with SegmentedCircle and updated layout"
```

---

### Task 9: Update MainScreen — replace ProfileScreen and StatusEditor

**Files:**
- Modify: `client/src/components/main/MainScreen.jsx`

- [ ] **Step 1: Replace imports**

Remove:
```javascript
import ProfileScreen from '../profile/ProfileScreen';     // line 14
import StatusEditor from '../profile/StatusEditor';        // line 15
```

Add:
```javascript
import ProfileEditor from '../profile/ProfileEditor';
import ProfilePopover from '../profile/ProfilePopover';
```

- [ ] **Step 2: Replace state**

Remove:
```javascript
const [profileOpen, setProfileOpen] = useState(false);     // line 94
const [statusOpen, setStatusOpen] = useState(false);       // line 95
```

Add:
```javascript
const [editorOpen, setEditorOpen] = useState(false);
const [profilePopover, setProfilePopover] = useState({ open: false, userId: null, anchorRef: null });
```

- [ ] **Step 3: Replace rendering**

Remove ProfileScreen rendering (lines 485-490) and StatusEditor rendering (lines 491-496).

Add ProfileEditor:
```jsx
<ProfileEditor
  open={editorOpen}
  onClose={() => setEditorOpen(false)}
  user={currentUser}
  onUserUpdate={(updated) => setCurrentUser(prev => ({ ...prev, ...updated }))}
/>
```

Add ProfilePopover (at root level for portal):
```jsx
<ProfilePopover
  anchorRef={profilePopover.anchorRef}
  userId={profilePopover.userId}
  user={currentUser}
  isOpen={profilePopover.open}
  onClose={() => setProfilePopover({ open: false, userId: null, anchorRef: null })}
  onEdit={() => { setProfilePopover({ open: false, userId: null, anchorRef: null }); setEditorOpen(true); }}
  onOpenChat={(userId) => { /* navigate to chat with userId */ }}
/>
```

- [ ] **Step 4: Update navigation handler**

Change `onNavigate` handler (line 418-419): 'profile' now opens ProfilePopover (own mode) instead of ProfileScreen. Pass anchorRef from TopNav avatar button.

- [ ] **Step 5: Commit**

```bash
git add client/src/components/main/MainScreen.jsx
git commit -m "redesign: replace ProfileScreen/StatusEditor with ProfileEditor and ProfilePopover"
```

---

### Task 10: Update ChatView — replace UserProfileModal

**Files:**
- Modify: `client/src/components/chat/ChatView.jsx`

- [ ] **Step 1: Replace import and component**

Remove:
```javascript
import UserProfileModal from '../ui/UserProfileModal';    // line 22
```

Add:
```javascript
import ProfilePopover from '../profile/ProfilePopover';
```

- [ ] **Step 2: Update state and rendering**

Change state to track anchor ref:
```javascript
const [profilePopover, setProfilePopover] = useState({ open: false, userId: null, anchorRef: null });
```

Replace `<UserProfileModal>` rendering (line 853) with:
```jsx
<ProfilePopover
  anchorRef={profilePopover.anchorRef}
  userId={profilePopover.userId}
  isOpen={profilePopover.open}
  onClose={() => setProfilePopover({ open: false, userId: null, anchorRef: null })}
  onOpenChat={(userId) => { /* handle opening chat */ }}
/>
```

Update avatar click handler (line 667) to pass anchor ref.

- [ ] **Step 3: Commit**

```bash
git add client/src/components/chat/ChatView.jsx
git commit -m "redesign: replace UserProfileModal with ProfilePopover in ChatView"
```

---

### Task 11: Update FriendsScreen — replace UserProfileModal + add UserBadge

**Files:**
- Modify: `client/src/components/friends/FriendsScreen.jsx`

- [ ] **Step 1: Replace imports**

Remove:
```javascript
import UserProfileModal from '../ui/UserProfileModal';    // line 5
```

Add:
```javascript
import ProfilePopover from '../profile/ProfilePopover';
import UserBadge from '../profile/UserBadge';
```

- [ ] **Step 2: Replace manual avatar+name with UserBadge**

Find where friends are rendered with `<Avatar>` + name text. Replace with:
```jsx
<UserBadge
  user={friend}
  userId={friend.id}
  size="md"
  showCustomStatus
  clickable
  onClick={(e) => setProfilePopover({ open: true, userId: friend.id, anchorRef: { current: e.currentTarget } })}
/>
```

- [ ] **Step 3: Replace UserProfileModal with ProfilePopover**

Same pattern as ChatView.

- [ ] **Step 4: Commit**

```bash
git add client/src/components/friends/FriendsScreen.jsx
git commit -m "redesign: UserBadge and ProfilePopover in FriendsScreen"
```

---

### Task 12: Update VoiceRoom and GroupMembersPanel

**Files:**
- Modify: `client/src/components/voice/VoiceRoom.jsx`
- Modify: `client/src/components/chat/GroupMembersPanel.jsx`

- [ ] **Step 1: VoiceRoom — replace UserProfileModal**

Same pattern: remove UserProfileModal import, add ProfilePopover, update state and rendering.

- [ ] **Step 2: GroupMembersPanel — replace UserProfileModal + add UserBadge**

Replace import, use UserBadge for member list, ProfilePopover for click action.

- [ ] **Step 3: Commit**

```bash
git add client/src/components/voice/VoiceRoom.jsx client/src/components/chat/GroupMembersPanel.jsx
git commit -m "redesign: ProfilePopover in VoiceRoom and GroupMembersPanel"
```

---

## Chunk 6: Cleanup & Polish

### Task 13: Delete old components

**Files:**
- Delete: `client/src/components/profile/ProfileScreen.jsx`
- Delete: `client/src/components/profile/ProfileScreen.css`
- Delete: `client/src/components/profile/StatusEditor.jsx`
- Delete: `client/src/components/profile/StatusEditor.css`
- Delete: `client/src/components/ui/UserProfileModal.jsx`
- Delete: `client/src/components/ui/UserProfileModal.css`

- [ ] **Step 1: Verify no remaining imports**

Search codebase for any remaining imports of ProfileScreen, UserProfileModal, StatusEditor. Should be zero.

```bash
grep -r "ProfileScreen\|UserProfileModal\|StatusEditor" client/src/ --include="*.jsx" --include="*.js"
```

- [ ] **Step 2: Delete files**

```bash
rm client/src/components/profile/ProfileScreen.jsx
rm client/src/components/profile/ProfileScreen.css
rm client/src/components/profile/StatusEditor.jsx
rm client/src/components/profile/StatusEditor.css
rm client/src/components/ui/UserProfileModal.jsx
rm client/src/components/ui/UserProfileModal.css
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "cleanup: remove old ProfileScreen, UserProfileModal, StatusEditor"
```

---

### Task 14: Full integration test

- [ ] **Step 1: Build check**

```bash
cd client && npx vite build
```

Fix any build errors (missing imports, broken references).

- [ ] **Step 2: Manual flow testing**

Test ALL flows:
1. TopNav dropdown → SegmentedCircle status change works
2. TopNav → "Профиль" → ProfileCard (own) popover appears
3. ProfileCard → "Редактировать" → ProfileEditor opens (slide-in)
4. ProfileEditor → all edit flows work (avatar, nick, email, password, bio, status)
5. ProfileEditor → "Назад" returns correctly
6. ChatView → click avatar → ProfilePopover (other) appears near avatar
7. ProfileCard (other) → "Написать" works
8. ProfileCard (other) → "Добавить в друзья" works
9. FriendsScreen → UserBadge displays correctly
10. FriendsScreen → click → ProfilePopover works
11. VoiceRoom → click participant → ProfilePopover works
12. GroupMembersPanel → click member → ProfilePopover works
13. Avatar click → lightbox opens, Escape closes
14. Light theme → all components render correctly
15. Reduced motion → animations disabled

- [ ] **Step 3: Fix any issues found**

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "redesign: profile system v2 — complete integration"
```

---

### Task 15: Version bump

- [ ] **Step 1: Update package.json version**

Bump to v1.1.2-beta (or next appropriate version).

- [ ] **Step 2: Commit**

```bash
git add client/package.json package.json
git commit -m "bump: v1.1.2-beta"
```
