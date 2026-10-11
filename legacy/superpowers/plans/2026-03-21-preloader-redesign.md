# Preloader Redesign — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current Glass-card splash screen with a metaball blob animation — blob'ы слетаются, слипаются, раскрывают логотип blesk.png, и постоянно деформируются.

**Architecture:** Single-file rewrite of `splash.html` with inline WebGL (raw, no Three.js — it's ESM-only and can't be loaded via `<script>` tag), GSAP from local copy for timeline control. Logo/tagline/dots/status as HTML elements positioned over the Canvas. Minor changes to `splash-preload.js` (add version IPC) and `main.js` (window height 400).

**Tech Stack:** Raw WebGL + GLSL shaders, GSAP (local copy), CSS animations, Electron IPC.

**Spec:** `docs/superpowers/specs/2026-03-21-preloader-redesign-design.md`

---

## Chunk 1: Setup & Dependencies

### Task 1: Copy local dependencies

**Files:**
- Create: `client/electron/lib/gsap.min.js` (copy from node_modules)
- Create: `client/electron/lib/manrope-var.woff2` (download)

Note: Three.js is NOT used — r183 only ships ESM modules which can't be loaded via `<script>` tag in plain HTML. We use raw WebGL instead (lighter, no dependency).

- [ ] **Step 1: Create lib directory and copy GSAP**

```bash
mkdir -p client/electron/lib
cp client/node_modules/gsap/dist/gsap.min.js client/electron/lib/gsap.min.js
```

- [ ] **Step 2: Download Manrope variable font**

```bash
curl -L "https://fonts.gstatic.com/s/manrope/v15/xn7gYHE41ni1AdIRggqxSuXd.woff2" -o client/electron/lib/manrope-var.woff2
```

If curl fails (offline), use the system font fallback — the CSS already has `-apple-system, system-ui, sans-serif` as fallback.

- [ ] **Step 3: Commit**

```bash
git add client/electron/lib/
git commit -m "add local GSAP and Manrope font for splash screen"
```

---

### Task 2: Update splash-preload.js — add version IPC

**Files:**
- Modify: `client/electron/splash-preload.js`

- [ ] **Step 1: Add version method to splashApi**

```js
// client/electron/splash-preload.js
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('splashApi', {
  ready: () => ipcRenderer.send('splash:ready'),
  version: () => ipcRenderer.sendSync('get-version'),
});
```

- [ ] **Step 2: Commit**

```bash
git add client/electron/splash-preload.js
git commit -m "add version IPC to splash preload"
```

---

### Task 3: Update main.js — height + version handler

**Files:**
- Modify: `client/electron/main.js`

- [ ] **Step 1: Change SPLASH_HEIGHT from 500 to 400**

Find line `const SPLASH_HEIGHT = 500;` and change to `const SPLASH_HEIGHT = 400;`

- [ ] **Step 2: Add get-version IPC handler**

Add after the existing `ipcMain.on('splash:ready', ...)` block:

```js
ipcMain.on('get-version', (event) => {
  event.returnValue = app.getVersion();
});
```

Make sure `app` is already imported (it should be: `const { app, BrowserWindow, ... } = require('electron');`).

- [ ] **Step 3: Commit**

```bash
git add client/electron/main.js
git commit -m "update splash window height to 400, add version IPC handler"
```

---

## Chunk 2: Splash HTML Rewrite

### Task 4: Rewrite splash.html

**Files:**
- Rewrite: `client/electron/splash.html`

This is the main task. The entire splash.html is rewritten with:
- Raw WebGL Canvas for metaball shader (no Three.js)
- GSAP timeline for blob positions + logo reveal
- HTML overlay for logo, tagline, dots, status, version
- CSS for styling and dot animations

- [ ] **Step 1: Write the complete splash.html**

The file structure:
1. `<head>`: meta, @font-face for Manrope, all CSS styles
2. `<body>`: Canvas (fullscreen), overlay div with logo/tagline/dots/status/version
3. `<script src="./lib/gsap.min.js">`: GSAP library
4. `<script>`: WebGL setup, shader compilation, GSAP timeline, status rotation

**Key implementation details:**

**WebGL shader (fragment):**
- Fullscreen quad rendered with raw WebGL (2 triangles)
- Vertex shader: passthrough with UV
- Fragment shader:
  - 5 SDF spheres with `smin` blending (k=0.5)
  - Simplex noise displacement on surface (continuous, never stops)
  - Uniforms: `uTime`, `uResolution`, `uBlobPositions[5]` (vec2 array), `uBlobRadii[5]`, `uSplitAmount`
  - Specular highlight (Phong-like, light from top)
  - Color: #c8ff00 with brightness from normals
  - Glow: exp(-d * 2.0) falloff
  - Dithering: noise-based per pixel
  - Background: transparent (gl.clearColor 0,0,0,0)

**GSAP timeline phases:**
- Phase 1 (0-0.8s): Animate blob positions from edges to near-center. Animate blob radii from 0 to target.
- Phase 2 (0.8-1.5s): Animate blob positions to center cluster. smin creates metaball bridges.
- Phase 3 (1.5-2.0s): All positions at center. Radii pulse via uTime in shader.
- Phase 4 (2.0-2.8s): Animate uSplitAmount 0→1. Logo element opacity 0→1, scale 0.8→1.
- Phase 5 (2.8-3.0s): Tagline fade-in. Version fade-in.
- At 2.0s: call `window.splashApi.ready()` (IPC to create main window)

**Status text rotation:**
- JavaScript setInterval every 600ms
- Array of 5 statuses
- Steps 2-4: Math.random() < 0.1 picks from easter egg array
- Fade: opacity transition 0.3s via CSS class toggle

**Loading dots:**
- Pure CSS animation: scale + opacity keyframes
- 4 dots with animation-delay stagger 0.2s

**Reduced motion:**
- `window.matchMedia('(prefers-reduced-motion: reduce)')` check
- If true: skip GSAP timeline, show logo immediately, dots static

**Logo path:**
- Try `../dist/blesk.png` first (prod), onerror try `../public/blesk.png` (dev)

**Exit animation (triggered from main.js):**
- `#splash.expand-out`: CSS animation scale 1→1.3, opacity 1→0, 0.6s

- [ ] **Step 2: Test in Electron**

```bash
cd client && npx electron .
```

Verify:
- Splash window appears (350x400, frameless, transparent edges)
- Green blob'ы appear from edges, fly to center
- Blob'ы merge with metaball bridges
- Blob splits, logo appears inside
- Dots pulsate, status text changes every 600ms
- Version shown at bottom
- After ~3s, transition to main app (expand-out)
- Blob'ы deform continuously (noise displacement never stops)

- [ ] **Step 3: Commit**

```bash
git add client/electron/splash.html
git commit -m "rewrite splash screen — metaball blob reveal with WebGL shader"
```

---

## Chunk 3: Final Verification

### Task 5: Full Integration Test

- [ ] **Step 1: Cold start test**

Close all Electron processes, start fresh:
```bash
cd client && npx electron .
```

Time the full flow:
- Splash appears → blob animation → logo reveal → main window → splash closes
- Should complete in ~4-5 seconds total

- [ ] **Step 2: Verify IPC flow**

- splash:ready fires at ~2.0s (check main.js console)
- Main window creates and loads
- expand-out triggers after main window ready
- Splash closes cleanly (no orphan windows)

- [ ] **Step 3: Verify fallback**

If main window takes too long (simulate by adding delay in main.js), splash should transition after 5s anyway.

- [ ] **Step 4: Verify reduced motion**

Set OS to "reduce motion" preference. Splash should show logo immediately without blob animation.

- [ ] **Step 5: Verify version**

Version badge should show the version from package.json (currently v0.5.6-beta), NOT hardcoded.

- [ ] **Step 6: Easter egg test**

Launch app ~20 times. Verify that occasionally (roughly 10% per status step 2-4) a funny status appears instead of the normal one.

- [ ] **Step 7: Final commit**

```bash
git add client/electron/
git commit -m "preloader redesign complete — metaball blob reveal"
```
