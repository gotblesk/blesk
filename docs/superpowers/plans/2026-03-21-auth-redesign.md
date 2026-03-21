# Auth Screen Redesign — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current centered-card auth screen with Gravity Cards (floating tilted glass cards) + Organic Split (metaball world left, form right, animated SVG divider).

**Architecture:** Split AuthScreen into focused components: GravityCard (reusable floating card), StrengthDots (password indicator), PasswordCard (two-phase password), OrganicDivider (GSAP-animated SVG), and a rewritten AuthScreen orchestrator. Business logic (API calls, validation, token management) is preserved from current implementation — only the UI layer changes.

**Tech Stack:** React, Framer Motion (card animations, AnimatePresence, layoutId), GSAP (divider morph, button ripple), Lucide React (icons), CSS (Double Layer Glass styling).

**Spec:** `docs/superpowers/specs/2026-03-21-auth-redesign-design.md`

---

## Chunk 1: Foundation Components

### Task 1: StrengthDots Component

**Files:**
- Create: `client/src/components/auth/StrengthDots.jsx`

- [ ] **Step 1: Create StrengthDots component**

```jsx
// client/src/components/auth/StrengthDots.jsx
import { motion } from 'framer-motion';

// Оценка силы пароля → количество точек (0-5)
export function getPasswordScore(pass) {
  if (!pass || pass.length < 8) return 0;
  let score = 1;
  if (/[A-Z]/.test(pass) && /[a-z]/.test(pass)) score++;
  if (/\d/.test(pass)) score++;
  if (/[^a-zA-Z0-9]/.test(pass)) score++;
  if (pass.length >= 12) score++;
  return score;
}

const LABELS = ['', 'Слабый', 'Слабый', 'Средний', 'Сильный', 'Сильный'];

function dotColor(score) {
  if (score <= 2) return '#ef4444';
  if (score === 3) return '#eab308';
  return '#c8ff00';
}

export default function StrengthDots({ password }) {
  const score = getPasswordScore(password);
  if (!password) return null;

  const color = dotColor(score);

  return (
    <div className="strength-dots">
      {[0, 1, 2, 3, 4].map((i) => (
        <motion.div
          key={i}
          className="strength-dot"
          initial={{ scale: 0, opacity: 0 }}
          animate={{
            scale: i < score ? 1 : 1,
            opacity: 1,
            backgroundColor: i < score ? color : 'rgba(255,255,255,0.06)',
            boxShadow: i < score ? `0 0 8px ${color}40` : 'none',
          }}
          transition={{ delay: i * 0.05, duration: 0.2 }}
        />
      ))}
      {score > 0 && (
        <span
          className="strength-label"
          style={{ color: `${color}80` }}
        >
          {LABELS[score]}
        </span>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Verify it renders without errors**

Open the app in dev mode. StrengthDots is not yet wired — just confirm no import/syntax errors by checking the dev server console.

- [ ] **Step 3: Commit**

```bash
git add client/src/components/auth/StrengthDots.jsx
git commit -m "add StrengthDots component — password strength indicator with animated dots"
```

---

### Task 2: GravityCard Component + CSS

**Files:**
- Create: `client/src/components/auth/GravityCard.jsx`
- Create: `client/src/components/auth/GravityCard.css`

- [ ] **Step 1: Create GravityCard.css**

```css
/* client/src/components/auth/GravityCard.css */

.g-card {
  width: 360px;
  position: relative;
  border-radius: 22px;
  padding: 22px 26px;
  perspective: 800px;

  /* Double Layer Glass — outer */
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow:
    0 8px 40px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition: box-shadow 0.4s ease;
}

/* Inner glass layer */
.g-card::before {
  content: '';
  position: absolute;
  inset: 6px;
  border-radius: 17px;
  background: rgba(255, 255, 255, 0.015);
  border: 1px solid rgba(255, 255, 255, 0.025);
  pointer-events: none;
}

/* Edge glow top */
.g-card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 15%;
  right: 15%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent);
  pointer-events: none;
}

.g-card:hover {
  box-shadow:
    0 14px 50px rgba(0, 0, 0, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 0 0 1px rgba(200, 255, 0, 0.04);
}

/* Error state */
.g-card.g-card--error {
  border-color: rgba(239, 68, 68, 0.2);
}
.g-card.g-card--error::after {
  background: linear-gradient(90deg, transparent, rgba(239, 68, 68, 0.15), transparent);
}

/* Focused sibling dimming */
.g-card.g-card--dimmed {
  opacity: 0.7;
}

/* ═══════ HEADER ═══════ */
.g-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  position: relative;
  z-index: 1;
}

.g-icon {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(200, 255, 0, 0.06);
  border: 1px solid rgba(200, 255, 0, 0.12);
  box-shadow: 0 0 12px rgba(200, 255, 0, 0.04);
  flex-shrink: 0;
  transition: background 0.3s, border-color 0.3s;
}

.g-card--error .g-icon {
  background: rgba(239, 68, 68, 0.06);
  border-color: rgba(239, 68, 68, 0.15);
}

.g-title {
  font-family: 'Bricolage Grotesque', sans-serif;
  font-size: 19px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.1;
}

.g-subtitle {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.25);
  margin-top: 2px;
}

/* ═══════ INPUT ═══════ */
.g-input-wrap {
  position: relative;
  z-index: 1;
}

.g-input {
  width: 100%;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  padding: 14px 18px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.85);
  font-family: 'Manrope', sans-serif;
  outline: none;
  transition: border-color 0.3s, box-shadow 0.3s, background 0.3s;
}

.g-input::placeholder {
  color: rgba(255, 255, 255, 0.15);
}

.g-input:focus {
  border-color: rgba(200, 255, 0, 0.25);
  background: rgba(200, 255, 0, 0.02);
  box-shadow: 0 0 0 3px rgba(200, 255, 0, 0.04), 0 0 24px rgba(200, 255, 0, 0.03);
}

.g-card--error .g-input {
  border-color: rgba(239, 68, 68, 0.2);
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.04);
}

.g-input-action {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0.2;
  cursor: pointer;
  transition: opacity 0.2s;
  background: none;
  border: none;
  padding: 4px;
  color: white;
}

.g-input-action:hover {
  opacity: 0.5;
}

/* ═══════ ERROR MESSAGE ═══════ */
.g-error-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 11px;
  color: rgba(239, 68, 68, 0.8);
  font-family: 'Manrope', sans-serif;
}

/* ═══════ STRENGTH DOTS ═══════ */
.strength-dots {
  display: flex;
  gap: 6px;
  margin-top: 10px;
  align-items: center;
}

.strength-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}

.strength-label {
  font-size: 10px;
  margin-left: 6px;
  font-family: 'Manrope', sans-serif;
}

/* ═══════ REDUCED MOTION ═══════ */
@media (prefers-reduced-motion: reduce) {
  .g-card {
    transition: none;
  }
  .g-input {
    transition: none;
  }
}
```

- [ ] **Step 2: Create GravityCard.jsx**

```jsx
// client/src/components/auth/GravityCard.jsx
import { useRef } from 'react';
import { motion, useMotionValue, useSpring, useTransform } from 'framer-motion';
import { AlertCircle } from 'lucide-react';
import useReducedMotion from '../../hooks/useReducedMotion';
import './GravityCard.css';

export default function GravityCard({
  tilt = 0,
  icon,
  title,
  subtitle,
  error,
  errorKey = 0, // increment to re-trigger shake
  index = 0,
  dimmed = false,
  children,
}) {
  const reduced = useReducedMotion();
  const cardRef = useRef(null);

  // Parallax tilt from mouse
  const mouseX = useMotionValue(0);
  const mouseY = useMotionValue(0);
  const springX = useSpring(mouseX, { stiffness: 150, damping: 25 });
  const springY = useSpring(mouseY, { stiffness: 150, damping: 25 });
  const rotateX = useTransform(springY, [-0.5, 0.5], [4, -4]);
  const rotateY = useTransform(springX, [-0.5, 0.5], [-4, 4]);

  function handleMouseMove(e) {
    if (reduced) return;
    const rect = cardRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = (e.clientX - rect.left) / rect.width - 0.5;
    const y = (e.clientY - rect.top) / rect.height - 0.5;
    mouseX.set(x);
    mouseY.set(y);
  }

  function handleMouseLeave() {
    mouseX.set(0);
    mouseY.set(0);
  }

  const className = [
    'g-card',
    error && 'g-card--error',
    dimmed && 'g-card--dimmed',
  ].filter(Boolean).join(' ');

  return (
    <motion.div
      ref={cardRef}
      className={className}
      initial={reduced ? {} : { opacity: 0, y: 30, rotate: 0 }}
      animate={{
        opacity: dimmed ? 0.7 : 1,
        y: 0,
        rotate: tilt,
        scale: dimmed ? 0.98 : 1,
      }}
      exit={{ opacity: 0, y: -20 }}
      transition={{
        type: 'spring',
        stiffness: 120,
        damping: 14,
        delay: index * 0.12,
      }}
      style={reduced ? {} : { rotateX, rotateY }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      // Error shake — errorKey forces re-trigger
      key={error ? `error-${errorKey}` : 'card'}
    >
      {/* Header */}
      <div className="g-header">
        <div className="g-icon">
          {icon}
        </div>
        <div>
          <div className="g-title">{title}</div>
          {subtitle && <div className="g-subtitle">{subtitle}</div>}
        </div>
      </div>

      {/* Content (inputs, etc.) */}
      {children}

      {/* Error message */}
      {error && (
        <div className="g-error-msg">
          <AlertCircle size={12} />
          {error}
        </div>
      )}
    </motion.div>
  );
}
```

- [ ] **Step 3: Verify no errors in dev console**

Run: check dev server console for import/compilation errors.

- [ ] **Step 4: Commit**

```bash
git add client/src/components/auth/GravityCard.jsx client/src/components/auth/GravityCard.css
git commit -m "add GravityCard component — floating tilted glass card with parallax tilt"
```

---

### Task 3: OrganicDivider Component

**Files:**
- Create: `client/src/components/auth/OrganicDivider.jsx`

- [ ] **Step 1: Create OrganicDivider component**

```jsx
// client/src/components/auth/OrganicDivider.jsx
import { useRef, useEffect } from 'react';
import gsap from 'gsap';
import useReducedMotion from '../../hooks/useReducedMotion';

// 3 path variants for morphing
const PATHS = [
  'M40 0 C58 40 18 80 45 120 C72 160 12 200 40 240 C68 280 15 320 42 360 C69 400 14 440 40 480 C66 520 22 560 40 588',
  'M40 0 C20 45 65 85 35 125 C5 165 55 205 40 245 C25 285 60 325 38 365 C16 405 58 445 40 485 C22 525 55 565 40 588',
  'M40 0 C55 35 25 75 48 115 C71 155 15 195 38 235 C61 275 20 315 45 355 C70 395 18 435 40 475 C62 515 28 555 40 588',
];

export default function OrganicDivider() {
  const pathRef = useRef(null);
  const reduced = useReducedMotion();

  useEffect(() => {
    if (reduced || !pathRef.current) return;

    const tl = gsap.timeline({ repeat: -1 });

    PATHS.forEach((d, i) => {
      const next = PATHS[(i + 1) % PATHS.length];
      tl.to(pathRef.current, {
        attr: { d: next },
        duration: 4,
        ease: 'power1.inOut',
      });
    });

    return () => tl.kill();
  }, [reduced]);

  return (
    <div className="auth-divider">
      <svg
        width="80"
        height="100%"
        viewBox="0 0 80 588"
        fill="none"
        preserveAspectRatio="none"
        style={{ height: '100%', width: '100%' }}
      >
        {/* Fill left side to cover background gap */}
        <path
          d={`${PATHS[0]} L0 588 L0 0 Z`}
          fill="var(--bg, #08060f)"
        />
        {/* Animated stroke line */}
        <path
          ref={pathRef}
          d={PATHS[0]}
          stroke="rgba(200,255,0,0.06)"
          strokeWidth="1"
          fill="none"
        />
      </svg>
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/components/auth/OrganicDivider.jsx
git commit -m "add OrganicDivider — GSAP-animated SVG blob edge for auth split"
```

---

### Task 4: PasswordCard Component

**Files:**
- Create: `client/src/components/auth/PasswordCard.jsx`

- [ ] **Step 1: Create PasswordCard component**

```jsx
// client/src/components/auth/PasswordCard.jsx
import { useState, useRef, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Lock, ShieldCheck, Eye, EyeOff, Check } from 'lucide-react';
import GravityCard from './GravityCard';
import StrengthDots, { getPasswordScore } from './StrengthDots';

export default function PasswordCard({
  tilt = -0.7,
  index = 2,
  dimmed = false,
  password,
  confirmPassword,
  onPasswordChange,
  onConfirmChange,
  error,
}) {
  const [phase, setPhase] = useState('create'); // 'create' | 'confirm'
  const [showPassword, setShowPassword] = useState(false);
  const confirmRef = useRef(null);
  const score = getPasswordScore(password);

  // Reset phase when password is cleared (e.g. mode switch)
  useEffect(() => {
    if (!password) {
      setPhase('create');
    }
  }, [password]);

  // Auto-transition to confirm phase
  function handleKeyDown(e) {
    if ((e.key === 'Tab' || e.key === 'Enter') && password.length >= 8 && score >= 3) {
      e.preventDefault();
      setPhase('confirm');
    }
  }

  // Focus confirm input when phase changes
  useEffect(() => {
    if (phase === 'confirm') {
      setTimeout(() => confirmRef.current?.focus(), 100);
    }
  }, [phase]);

  // Check match when confirming
  const matched = phase === 'confirm' && confirmPassword && password === confirmPassword;
  const mismatch = phase === 'confirm' && confirmPassword && password !== confirmPassword;

  const isCreate = phase === 'create';

  const icon = isCreate
    ? <Lock size={16} stroke={error ? '#ef4444' : '#c8ff00'} />
    : matched
      ? <Check size={16} stroke="#4ade80" />
      : <ShieldCheck size={16} stroke={mismatch ? '#ef4444' : '#c8ff00'} />;

  const title = isCreate ? 'Придумай пароль' : 'Подтверди пароль';
  const subtitle = isCreate
    ? 'Минимум 8 символов'
    : matched
      ? 'Пароли совпадают'
      : mismatch
        ? undefined
        : 'Повтори ввод';

  const cardError = mismatch ? 'Пароли не совпадают' : error;

  return (
    <GravityCard
      tilt={tilt}
      index={index}
      dimmed={dimmed}
      icon={icon}
      title={title}
      subtitle={subtitle}
      error={cardError}
    >
      <AnimatePresence mode="wait">
        {isCreate ? (
          <motion.div
            key="create"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.3 }}
          >
            <div className="g-input-wrap">
              <input
                className="g-input"
                type={showPassword ? 'text' : 'password'}
                placeholder="Пароль"
                value={password}
                onChange={(e) => onPasswordChange(e.target.value)}
                onKeyDown={handleKeyDown}
                aria-label="Пароль"
              />
              <button
                type="button"
                className="g-input-action"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <StrengthDots password={password} />
          </motion.div>
        ) : (
          <motion.div
            key="confirm"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            <div className="g-input-wrap">
              <input
                ref={confirmRef}
                className="g-input"
                type={showPassword ? 'text' : 'password'}
                placeholder="Повтори пароль"
                value={confirmPassword}
                onChange={(e) => onConfirmChange(e.target.value)}
                aria-label="Подтверждение пароля"
              />
              <button
                type="button"
                className="g-input-action"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <button
              type="button"
              onClick={() => { setPhase('create'); onConfirmChange(''); }}
              style={{
                background: 'none', border: 'none', color: 'rgba(200,255,0,0.4)',
                fontSize: '11px', marginTop: '8px', cursor: 'pointer',
                fontFamily: 'Manrope', padding: 0,
              }}
            >
              ← Изменить пароль
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </GravityCard>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/components/auth/PasswordCard.jsx
git commit -m "add PasswordCard — two-phase password input with animated transition"
```

---

## Chunk 2: AuthScreen Rewrite + CSS

### Task 5: AuthScreen.css — Complete Rewrite

**Files:**
- Rewrite: `client/src/components/auth/AuthScreen.css`

- [ ] **Step 1: Rewrite AuthScreen.css**

```css
/* client/src/components/auth/AuthScreen.css */
/* Auth Screen — Gravity Cards + Organic Split */

/* ═══════ SPLIT LAYOUT ═══════ */
.auth-screen {
  width: 100%;
  height: calc(100vh - 32px); /* minus titlebar */
  position: relative;
  overflow: hidden;
}

.auth-split {
  display: flex;
  height: 100%;
  position: relative;
}

/* ═══════ LEFT: METABALL WORLD ═══════ */
.auth-left {
  flex: 0 0 40%;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  z-index: 1;
}

.auth-logo {
  position: relative;
  z-index: 2;
  text-align: center;
}

.auth-logo img {
  width: 80px;
  height: 80px;
  filter: drop-shadow(0 0 30px rgba(200, 255, 0, 0.2));
  margin-bottom: 16px;
}

.auth-tagline {
  font-family: 'Manrope', sans-serif;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.25);
  letter-spacing: 3.5px;
  text-transform: uppercase;
}

.auth-version {
  font-family: 'Manrope', sans-serif;
  font-size: 9px;
  color: rgba(200, 255, 0, 0.25);
  margin-top: 16px;
  background: rgba(200, 255, 0, 0.03);
  padding: 4px 14px;
  border-radius: 20px;
  border: 1px solid rgba(200, 255, 0, 0.06);
  display: inline-block;
}

/* ═══════ ORGANIC DIVIDER ═══════ */
.auth-divider {
  position: absolute;
  left: 40%;
  top: 0;
  bottom: 0;
  width: 80px;
  z-index: 3;
  transform: translateX(-50%);
  pointer-events: none;
}

/* ═══════ RIGHT: GRAVITY CARDS ═══════ */
.auth-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 48px;
  gap: 16px;
  z-index: 2;
}

/* ═══════ ACTION BUTTON ═══════ */
.g-action {
  width: 360px;
}

.g-btn {
  width: 100%;
  background: var(--accent, #c8ff00);
  border: none;
  border-radius: 16px;
  padding: 15px;
  font-family: 'Manrope', sans-serif;
  font-weight: 700;
  font-size: 15px;
  color: var(--bg, #08060f);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 28px rgba(200, 255, 0, 0.2);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.g-btn:hover {
  box-shadow: 0 6px 36px rgba(200, 255, 0, 0.3);
  transform: translateY(-1px);
}

.g-btn:active {
  transform: scale(0.98);
}

/* Specular highlight */
.g-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: 12%;
  right: 12%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.45), transparent);
}

.g-btn:disabled {
  opacity: 0.65;
  pointer-events: none;
}

.g-btn--loading {
  opacity: 0.65;
  pointer-events: none;
}

.g-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(8, 6, 15, 0.15);
  border-top-color: var(--bg, #08060f);
  border-radius: 50%;
  animation: g-spin 0.6s linear infinite;
  margin-left: 8px;
  vertical-align: middle;
}

@keyframes g-spin {
  to { transform: rotate(360deg); }
}

/* ═══════ FOOTER ═══════ */
.auth-footer {
  display: flex;
  align-items: center;
  gap: 0;
  margin-top: 4px;
}

.auth-footer-link {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.2);
  background: none;
  border: none;
  cursor: pointer;
  font-family: 'Manrope', sans-serif;
  padding: 4px;
  transition: color 0.2s;
}

.auth-footer-link:hover {
  color: rgba(200, 255, 0, 0.5);
}

.auth-footer-sep {
  width: 1px;
  height: 12px;
  background: rgba(255, 255, 255, 0.08);
  margin: 0 12px;
}

/* ═══════ VERIFY CODE INPUTS ═══════ */
.verify-code-grid {
  display: flex;
  gap: 8px;
}

.verify-code-cell {
  flex: 1;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 12px 0;
  text-align: center;
  font-family: 'Bricolage Grotesque', sans-serif;
  font-size: 20px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.15);
  outline: none;
  width: 48px;
  transition: border-color 0.2s, background 0.2s;
}

.verify-code-cell:focus,
.verify-code-cell.filled {
  background: rgba(200, 255, 0, 0.04);
  border-color: rgba(200, 255, 0, 0.15);
  color: var(--accent, #c8ff00);
}

.verify-resend {
  text-align: center;
  margin-top: 10px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.2);
  font-family: 'Manrope', sans-serif;
}

.verify-resend-timer {
  color: rgba(200, 255, 0, 0.4);
}

.verify-resend-btn {
  background: none;
  border: none;
  color: rgba(200, 255, 0, 0.5);
  cursor: pointer;
  font-family: 'Manrope', sans-serif;
  font-size: 11px;
  padding: 0;
}

.verify-resend-btn:hover {
  color: var(--accent, #c8ff00);
}

/* ═══════ SMALL WINDOW (< 900px) ═══════ */
@media (max-width: 900px) {
  .auth-left {
    display: none;
  }
  .auth-divider {
    display: none;
  }
  .auth-right {
    padding: 40px 24px;
  }
  /* Show logo on top of right side */
  .auth-logo-mobile {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 24px;
  }
}

@media (min-width: 901px) {
  .auth-logo-mobile {
    display: none;
  }
}

/* ═══════ REDUCED MOTION ═══════ */
@media (prefers-reduced-motion: reduce) {
  .g-btn {
    transition: none;
  }
  .g-spinner {
    animation: none;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/components/auth/AuthScreen.css
git commit -m "rewrite AuthScreen.css — Gravity Cards + Organic Split layout"
```

---

### Task 6: AuthScreen.jsx — Complete Rewrite

**Files:**
- Rewrite: `client/src/components/auth/AuthScreen.jsx`

- [ ] **Step 1: Rewrite AuthScreen.jsx**

The rewrite preserves ALL business logic from the current 873-line AuthScreen (API calls, validation, token handling, verification, forgot password flows). Only the UI layer changes to Gravity Cards + Organic Split.

**Key structural changes:**
- Remove Glass import, add GravityCard, PasswordCard, OrganicDivider, MetaballBackground imports
- Add framer-motion (AnimatePresence, motion) and GSAP imports
- Add `mode` state: `'login' | 'register' | 'verify' | 'forgot' | 'forgot-code' | 'forgot-reset'`
- Add `errorKey` state (number, incremented on each error to retrigger shake)
- Add `focusedField` state to track which card should glow (others dim)
- Brand intro: motion.div with initial={{ opacity: 0 }} animate={{ opacity: 1 }} for logo, then after 2s set phase to 'form'
- JSX: `auth-split` → `auth-left` (MetaballBackground + logo) + OrganicDivider + `auth-right` (AnimatePresence with mode switching)
- Login mode: 2 GravityCards (User icon "Кто ты?", Lock icon "Докажи")
- Register mode: 2 GravityCards (User "Придумай имя", Mail "Куда писать?") + PasswordCard
- Forgot mode: GravityCard (Mail "Вспомним?") for email
- Forgot-code mode: GravityCard (KeyRound "Проверь почту") with 6-digit code grid
- Forgot-reset mode: PasswordCard for new password
- Verify mode: GravityCard (Mail "Проверь почту") with 6-digit code grid + resend timer
- Action button with GSAP ripple (handleRipple function, see Task 8)
- Footer links change per mode
- MetaballBackground inside auth-left div (it's only in MainScreen currently, NOT in App.jsx)
- Mobile: auth-logo-mobile div shown < 900px, auth-left hidden

**All existing functions preserved verbatim:**
- validate(), handleSubmit(), submitCode(), handleCodeInput(), handleCodeKeyDown()
- handleResend(), handleForgotSend(), handleForgotCodeInput(), handleForgotCodeKeyDown()
- handleForgotReset(), switchTab() → renamed to switchMode()
- getPasswordStrength() → replaced by imported getPasswordScore from StrengthDots
- maskedEmail computation preserved

**Note:** This is the largest task. The implementer has full code for all sub-components (Tasks 1-4) and CSS (Task 5). AuthScreen.jsx wires them together using the exact same API call patterns from the current file. Copy the business logic functions verbatim from the current AuthScreen.jsx (lines 101-413) and wrap them in the new JSX layout described above.

- [ ] **Step 2: Verify the app loads without errors**

Run dev server, navigate to auth screen. Verify:
- Split layout visible (left panel with logo, right panel with cards)
- Cards render with correct titles and icons
- Input fields are functional (typing works)
- Tab/mode switching works (Login ↔ Register)
- No console errors

- [ ] **Step 3: Commit**

```bash
git add client/src/components/auth/AuthScreen.jsx
git commit -m "rewrite AuthScreen — Gravity Cards + Organic Split with all auth flows"
```

---

## Chunk 3: Integration & Polish

### Task 7: Wire MetaballBackground for Auth

**Files:**
- Modify: `client/src/components/auth/AuthScreen.jsx`

MetaballBackground is currently only rendered in `client/src/components/main/MainScreen.jsx` (line 218). It is NOT in App.jsx. AuthScreen needs its own instance inside `auth-left`.

- [ ] **Step 1: Add MetaballBackground import and render in AuthScreen.jsx**

In AuthScreen.jsx, add at the top:
```jsx
import MetaballBackground from '../ui/MetaballBackground';
```

In the auth-left div, add as first child:
```jsx
<MetaballBackground subtle />
```

This renders the Three.js metaball shader as the background of the left panel.

- [ ] **Step 2: Test background is visible behind left panel**

- [ ] **Step 3: Commit**

```bash
git add client/src/components/auth/AuthScreen.jsx
git commit -m "add MetaballBackground to auth screen left panel"
```

---

### Task 8: GSAP Button Ripple

**Files:**
- Modify: `client/src/components/auth/AuthScreen.jsx` (add ripple to submit button)

- [ ] **Step 1: Add ripple effect to action button**

Add a click handler that creates a GSAP-animated radial ripple from the click position:

```jsx
function handleRipple(e) {
  const btn = e.currentTarget;
  const rect = btn.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;

  const ripple = document.createElement('div');
  ripple.style.cssText = `
    position: absolute; border-radius: 50%;
    background: radial-gradient(circle, rgba(255,255,255,0.25), transparent);
    width: 0; height: 0; left: ${x}px; top: ${y}px;
    transform: translate(-50%, -50%); pointer-events: none;
  `;
  btn.appendChild(ripple);

  gsap.to(ripple, {
    width: rect.width * 2.5,
    height: rect.width * 2.5,
    opacity: 0,
    duration: 0.5,
    ease: 'power2.out',
    onComplete: () => ripple.remove(),
  });
}
```

- [ ] **Step 2: Commit**

```bash
git add client/src/components/auth/AuthScreen.jsx
git commit -m "add GSAP ripple effect to auth submit button"
```

---

### Task 9: Full Integration Test

- [ ] **Step 1: Test login flow**

1. Enter username + password
2. Click "Войти"
3. Verify error shake on wrong credentials
4. Verify loading spinner on button
5. Verify successful login transitions to MainScreen

- [ ] **Step 2: Test registration flow**

1. Click "Создать аккаунт"
2. Verify 3 cards appear with stagger animation
3. Fill username, email
4. Type password → verify StrengthDots animate
5. Press Tab on password → verify PasswordCard flips to "Подтверди"
6. Type matching password → verify green checkmark
7. Click "Создать аккаунт"
8. Verify transition to verification screen

- [ ] **Step 3: Test forgot password flow**

1. Click "Забыли пароль?"
2. Verify single email card appears
3. Submit email → verify code input appears
4. Enter code → verify new password card appears

- [ ] **Step 4: Test visual polish**

1. Move mouse over cards → verify parallax tilt
2. Hover cards → verify shadow enhancement
3. Focus input → verify glow border
4. Check OrganicDivider is animating
5. Resize window < 900px → verify mobile layout (logo on top, no left panel)

- [ ] **Step 5: Final commit**

```bash
git add client/src/components/auth/
git commit -m "auth redesign complete — Gravity Cards + Organic Split"
```
