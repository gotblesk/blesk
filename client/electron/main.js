const { app, BrowserWindow, ipcMain, screen, desktopCapturer, safeStorage, shell, Tray, Menu, Notification, nativeImage, globalShortcut } = require('electron');
const { autoUpdater } = require('electron-updater');
const path = require('path');
const fs = require('fs');

process.on('uncaughtException', (err) => {
  console.error('Uncaught exception:', err);
});
process.on('unhandledRejection', (reason) => {
  console.error('Unhandled rejection:', reason);
});

const isDev = !app.isPackaged;
const iconPath = isDev
  ? path.join(__dirname, '../public/icon.ico')
  : path.join(process.resourcesPath, 'icon.ico');

// Имя приложения (в диспетчере задач, заголовке и т.д.)
app.name = 'blesk';
if (process.platform === 'win32') {
  app.setAppUserModelId('fun.blesk.app');
}

// [IMP-3] Блокировка второго инстанса
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
}

let splashWindow;
let mainWindow;
let tray = null;
let isQuitting = false; // Различать "закрыть в трей" и "выйти"
let unreadCount = 0;

// [IMP-3] Второй инстанс обрабатывается ниже (с deep links)

// Размеры
const SPLASH_WIDTH = 600;
const SPLASH_HEIGHT = 400;
const MAIN_WIDTH = 1280;
const MAIN_HEIGHT = 800;

// Сохранение/восстановление позиции и размера окна
const windowStatePath = path.join(app.getPath('userData'), 'window-state.json');

function loadWindowState() {
  try {
    const data = fs.readFileSync(windowStatePath, 'utf-8');
    const state = JSON.parse(data);
    if (typeof state.x !== 'number' || typeof state.y !== 'number' ||
        typeof state.width !== 'number' || typeof state.height !== 'number') {
      return null;
    }
    // Проверяем что окно попадает хотя бы на один из экранов
    const displays = screen.getAllDisplays();
    const visible = displays.some((d) => {
      const b = d.bounds;
      return state.x < b.x + b.width && state.x + state.width > b.x &&
             state.y < b.y + b.height && state.y + state.height > b.y;
    });
    if (!visible) return null;
    return state;
  } catch {
    return null;
  }
}

function saveWindowState() {
  if (!mainWindow || mainWindow.isDestroyed() || mainWindow.isMinimized()) return;
  try {
    const bounds = mainWindow.getBounds();
    const state = {
      x: bounds.x,
      y: bounds.y,
      width: bounds.width,
      height: bounds.height,
      maximized: mainWindow.isMaximized(),
    };
    fs.writeFileSync(windowStatePath, JSON.stringify(state));
  } catch { /* ignore */ }
}

function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: SPLASH_WIDTH,
    height: SPLASH_HEIGHT,
    frame: false,
    transparent: true,
    resizable: false,
    backgroundColor: '#00000000',
    center: true,
    webPreferences: {
      preload: path.join(__dirname, 'splash-preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true, // [CRIT-2]
    },
    icon: iconPath,
    title: 'blesk',
    show: false,
  });

  splashWindow.loadFile(path.join(__dirname, 'splash.html'));

  splashWindow.once('ready-to-show', () => {
    splashWindow.show();
  });
}

function createMainWindow() {
  const savedState = loadWindowState();

  mainWindow = new BrowserWindow({
    width: savedState ? savedState.width : MAIN_WIDTH,
    height: savedState ? savedState.height : MAIN_HEIGHT,
    x: savedState ? savedState.x : undefined,
    y: savedState ? savedState.y : undefined,
    minWidth: 900,
    minHeight: 600,
    frame: false,
    backgroundColor: '#0a0a0f',
    center: !savedState,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
    icon: iconPath,
    title: 'blesk',
    show: false,
  });

  // Восстановить maximized состояние
  if (savedState && savedState.maximized) {
    mainWindow.maximize();
  }

  // Сохранять позицию/размер при изменении
  mainWindow.on('resize', saveWindowState);
  mainWindow.on('move', saveWindowState);
  mainWindow.on('close', saveWindowState);

  // CSP headers (electron-development skill: Production Security Checklist)
  mainWindow.webContents.session.webRequest.onHeadersReceived((details, callback) => {
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        'Content-Security-Policy': [
          isDev
            ? "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: http://localhost:* https://*.blesk.fun https://api.blesk.fun; font-src 'self' data:; connect-src 'self' https://*.blesk.fun wss://*.blesk.fun ws://localhost:* http://localhost:*; media-src 'self' blob: http://localhost:*;"
            : "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.blesk.fun https://api.blesk.fun; font-src 'self' data:; connect-src 'self' https://*.blesk.fun wss://*.blesk.fun; media-src 'self' blob:;"
        ],
      },
    });
  });

  // Безопасность: блокируем открытие новых окон, внешние ссылки — в браузер
  const ALLOWED_EXTERNAL_HOSTS = new Set(['blesk.fun', 'www.blesk.fun', 'github.com']);
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    try {
      const parsed = new URL(url);
      if (parsed.protocol === 'https:' && (ALLOWED_EXTERNAL_HOSTS.has(parsed.hostname) || parsed.hostname.endsWith('.blesk.fun'))) {
        shell.openExternal(url);
      }
    } catch { /* invalid URL — ignore */ }
    return { action: 'deny' };
  });

  // [IMP-5] Безопасность: блокируем навигацию — в dev только localhost:5173
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const allowed = isDev
      ? ['http://localhost:5173', 'file://']
      : ['file://'];
    if (!allowed.some(a => url.startsWith(a))) {
      event.preventDefault();
    }
  });

  // Оповещаем renderer о maximize/unmaximize
  mainWindow.on('maximize', () => {
    mainWindow.webContents.send('window:maximized');
  });
  mainWindow.on('unmaximize', () => {
    mainWindow.webContents.send('window:unmaximized');
  });

  // Свернуть в трей при закрытии (не выходить)
  mainWindow.on('close', (event) => {
    if (!isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173');
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  }

  // DevTools: временно открываем для диагностики TDZ
  mainWindow.webContents.openDevTools({ mode: 'detach' });
}

// Плавный переход: сплеш "расширяется" (CSS) → основное окно появляется
function transitionToMain() {
  if (!splashWindow || !mainWindow) return;

  // [CRIT-2] Говорим сплешу запустить CSS-анимацию через IPC (не executeJavaScript)
  splashWindow.webContents.send('splash:expand-out');

  // Шаг 2: На середине анимации сплеша — увеличиваем окно сплеша
  // до размера основного, чтобы визуально казалось что он растёт
  const splashBounds = splashWindow.getBounds();
  const display = screen.getDisplayNearestPoint({
    x: splashBounds.x,
    y: splashBounds.y,
  });
  const screenBounds = display.workArea;

  // Финальная позиция — по центру экрана
  const endX = Math.round(screenBounds.x + (screenBounds.width - MAIN_WIDTH) / 2);
  const endY = Math.round(screenBounds.y + (screenBounds.height - MAIN_HEIGHT) / 2);

  // Шаг 3: Через 600мс (анимация сплеша закончилась) — показываем основное окно
  setTimeout(() => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.setBounds({ x: endX, y: endY, width: MAIN_WIDTH, height: MAIN_HEIGHT });
      mainWindow.show();
      mainWindow.focus();
    }
    // Закрываем сплеш
    if (splashWindow && !splashWindow.isDestroyed()) {
      splashWindow.close();
      splashWindow = null;
    }
  }, 600);
}

// Сплеш сообщает что загрузка завершена
ipcMain.on('splash:ready', () => {
  // Создаём основное окно параллельно
  createMainWindow();

  let transitioned = false;

  function doTransition() {
    if (transitioned) return;
    transitioned = true;
    transitionToMain();
  }

  // Ждём пока основное окно загрузит HTML
  mainWindow.webContents.once('did-finish-load', () => {
    // Запросить DND состояние из renderer
    mainWindow.webContents.send('request:dnd-state');

    // Даём React время отрисоваться
    setTimeout(() => {
      doTransition();
      // [IMP-1] Обработать pending deep link после загрузки mainWindow
      if (pendingDeepLink) {
        const dl = pendingDeepLink;
        pendingDeepLink = null;
        if (dl.raw) {
          handleDeepLink(dl.raw);
        } else {
          mainWindow.webContents.send('deeplink:action', { action: dl.action, param: dl.param });
        }
      }
    }, 600);
  });

  // Fallback — если did-finish-load не сработал за 5 сек
  setTimeout(doTransition, 5000);
});

// Повторная загрузка при ошибке на сплеше
ipcMain.on('splash:retry', () => {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.destroy();
    mainWindow = null;
  }
  createMainWindow();

  let transitioned = false;
  function doTransition() {
    if (transitioned) return;
    transitioned = true;
    transitionToMain();
  }

  mainWindow.webContents.once('did-finish-load', () => {
    mainWindow.webContents.send('request:dnd-state');
    setTimeout(doTransition, 600);
  });
  setTimeout(doTransition, 5000);
});

// [IMP-9] Управление окном — fromWebContents вместо getFocusedWindow (может быть null)
ipcMain.on('window:minimize', (event) => {
  const win = BrowserWindow.fromWebContents(event.sender);
  if (win) win.minimize();
});

ipcMain.on('window:maximize', (event) => {
  const win = BrowserWindow.fromWebContents(event.sender);
  if (win) {
    win.isMaximized() ? win.unmaximize() : win.maximize();
  }
});

// Закрытие окна — свернуть в трей (не выходить)
ipcMain.on('window:close', (event) => {
  const win = BrowserWindow.fromWebContents(event.sender);
  if (win) {
    if (isQuitting) {
      win.destroy();
    } else {
      win.hide();
    }
  }
});

// Ручное перетаскивание окна (обход бага -webkit-app-region на Windows)
let dragOffset = null;

// [IMP-1] Валидация координат drag
ipcMain.on('window:start-drag', (event, offsetX, offsetY) => {
  if (typeof offsetX !== 'number' || typeof offsetY !== 'number') return;
  dragOffset = {
    x: Math.max(0, Math.min(offsetX, MAIN_WIDTH)),
    y: Math.max(0, Math.min(offsetY, 80)),
  };
});

ipcMain.on('window:dragging', (event, screenX, screenY) => {
  if (!dragOffset) return;
  if (typeof screenX !== 'number' || typeof screenY !== 'number') return;
  const win = BrowserWindow.fromWebContents(event.sender);
  if (win && !win.isMaximized()) {
    const x = Math.round(screenX - dragOffset.x);
    const y = Math.max(-30, Math.round(screenY - dragOffset.y));
    win.setPosition(x, y);
  }
});

ipcMain.on('window:stop-drag', () => {
  dragOffset = null;
});

ipcMain.handle('app:version', () => app.getVersion());

// Безопасное открытие внешних ссылок — только http/https
ipcMain.on('open-external', (event, url) => {
  if (typeof url === 'string' && (url.startsWith('https://') || url.startsWith('http://'))) {
    shell.openExternal(url);
  }
});

// ═══ Refresh token — безопасное хранение для авто-логина ═══
const refreshTokenPath = path.join(app.getPath('userData'), 'blesk.refresh');

ipcMain.handle('auth:saveRefreshToken', async (_, token) => {
  try {
    if (typeof token !== 'string' || token.length > 2000) return false;
    const encrypted = safeStorage.encryptString(token);
    await fs.promises.writeFile(refreshTokenPath, encrypted);
    return true;
  } catch (err) {
    console.error('auth:saveRefreshToken error:', err);
    return false;
  }
});

ipcMain.handle('auth:getRefreshToken', async () => {
  try {
    await fs.promises.access(refreshTokenPath);
    const encrypted = await fs.promises.readFile(refreshTokenPath);
    return safeStorage.decryptString(encrypted);
  } catch (err) {
    if (err.code === 'ENOENT') return null;
    console.error('auth:getRefreshToken error:', err);
    return null;
  }
});

ipcMain.handle('auth:clearRefreshToken', async () => {
  try {
    await fs.promises.unlink(refreshTokenPath);
  } catch (e) { console.warn('Main process error:', e.message); }
  return true;
});

// ═══ E2E шифрование — безопасное хранение приватного ключа ═══
const keyPath = path.join(app.getPath('userData'), 'blesk.key');

// [IMP-2] Валидация типов на всех crypto IPC каналах
ipcMain.handle('crypto:saveSecretKey', async (_, b64Key) => {
  try {
    if (typeof b64Key !== 'string' || b64Key.length > 200) return false;
    const encrypted = safeStorage.encryptString(b64Key);
    await fs.promises.writeFile(keyPath, encrypted);
    return true;
  } catch (err) {
    console.error('crypto:saveSecretKey error:', err);
    return false;
  }
});

ipcMain.handle('crypto:getSecretKey', async () => {
  try {
    await fs.promises.access(keyPath);
    const encrypted = await fs.promises.readFile(keyPath);
    return safeStorage.decryptString(encrypted);
  } catch (err) {
    if (err.code === 'ENOENT') return null;
    console.error('crypto:getSecretKey error:', err);
    return null;
  }
});

ipcMain.handle('crypto:hasSecretKey', async () => {
  try {
    await fs.promises.access(keyPath);
    return true;
  } catch {
    return false;
  }
});

// ═══ blesk Shield — расширенное хранение ключей и сессий ═══
const signKeyPath = path.join(app.getPath('userData'), 'blesk.sign');
const spkPath = path.join(app.getPath('userData'), 'blesk.spk');
const sessionsDir = path.join(app.getPath('userData'), 'shield-sessions');

// Signing key (Ed25519)
ipcMain.handle('crypto:saveSigningKey', async (_, b64Key) => {
  try {
    if (typeof b64Key !== 'string' || b64Key.length > 200) return false;
    const encrypted = safeStorage.encryptString(b64Key);
    await fs.promises.writeFile(signKeyPath, encrypted);
    return true;
  } catch (err) {
    console.error('crypto:saveSigningKey error:', err);
    return false;
  }
});

ipcMain.handle('crypto:getSigningKey', async () => {
  try {
    await fs.promises.access(signKeyPath);
    const encrypted = await fs.promises.readFile(signKeyPath);
    return safeStorage.decryptString(encrypted);
  } catch (err) {
    if (err.code === 'ENOENT') return null;
    console.error('crypto:getSigningKey error:', err);
    return null;
  }
});

ipcMain.handle('crypto:hasSigningKey', async () => {
  try {
    await fs.promises.access(signKeyPath);
    return true;
  } catch {
    return false;
  }
});

// Signed PreKey (SPK)
ipcMain.handle('crypto:saveSPK', async (_, jsonStr) => {
  try {
    if (typeof jsonStr !== 'string' || jsonStr.length > 10000) return false;
    const encrypted = safeStorage.encryptString(jsonStr);
    await fs.promises.writeFile(spkPath, encrypted);
    return true;
  } catch (err) {
    console.error('crypto:saveSPK error:', err);
    return false;
  }
});

ipcMain.handle('crypto:getSPK', async () => {
  try {
    await fs.promises.access(spkPath);
    const encrypted = await fs.promises.readFile(spkPath);
    return safeStorage.decryptString(encrypted);
  } catch (err) {
    if (err.code === 'ENOENT') return null;
    console.error('crypto:getSPK error:', err);
    return null;
  }
});

// Shield Sessions — по одному файлу на собеседника
ipcMain.handle('crypto:saveSession', async (_, peerId, jsonStr) => {
  try {
    if (typeof peerId !== 'string' || peerId.length === 0 || peerId.length > 100) return false;
    if (typeof jsonStr !== 'string' || jsonStr.length > 1_000_000) return false;
    await fs.promises.mkdir(sessionsDir, { recursive: true });
    // [MED-3] SHA-256 хеш peerId для имени файла (без усечения)
    const { createHash } = require('crypto');
    const fileName = createHash('sha256').update(peerId).digest('hex') + '.session';
    const filePath = path.join(sessionsDir, fileName);
    const encrypted = safeStorage.encryptString(jsonStr);
    await fs.promises.writeFile(filePath, encrypted);
    return true;
  } catch (err) {
    console.error('crypto:saveSession error:', err);
    return false;
  }
});

ipcMain.handle('crypto:getSession', async (_, peerId) => {
  try {
    if (typeof peerId !== 'string' || peerId.length === 0) return null;
    const { createHash } = require('crypto');
    const fileName = createHash('sha256').update(peerId).digest('hex') + '.session';
    const filePath = path.join(sessionsDir, fileName);
    await fs.promises.access(filePath);
    const encrypted = await fs.promises.readFile(filePath);
    return safeStorage.decryptString(encrypted);
  } catch (err) {
    if (err.code === 'ENOENT') return null;
    console.error('crypto:getSession error:', err);
    return null;
  }
});

ipcMain.handle('crypto:deleteSession', async (_, peerId) => {
  try {
    if (typeof peerId !== 'string' || peerId.length === 0) return false;
    const { createHash } = require('crypto');
    const fileName = createHash('sha256').update(peerId).digest('hex') + '.session';
    const filePath = path.join(sessionsDir, fileName);
    try {
      await fs.promises.unlink(filePath);
    } catch (e) {
      if (e.code !== 'ENOENT') throw e;
    }
    return true;
  } catch (err) {
    console.error('crypto:deleteSession error:', err);
    return false;
  }
});

ipcMain.handle('crypto:listSessions', async () => {
  try {
    const files = await fs.promises.readdir(sessionsDir);
    return files
      .filter(f => f.endsWith('.session'))
      .map(f => f.replace('.session', ''));
  } catch {
    return [];
  }
});

// OPK (one-time prekeys) — хранение секретных ключей
const opkPath = path.join(app.getPath('userData'), 'blesk.opk');

ipcMain.handle('crypto:saveOPKs', async (_, jsonStr) => {
  try {
    if (typeof jsonStr !== 'string' || jsonStr.length > 5_000_000) return false;
    const encrypted = safeStorage.encryptString(jsonStr);
    await fs.promises.writeFile(opkPath, encrypted);
    return true;
  } catch (err) {
    console.error('crypto:saveOPKs error:', err);
    return false;
  }
});

ipcMain.handle('crypto:getOPKs', async () => {
  try {
    await fs.promises.access(opkPath);
    const encrypted = await fs.promises.readFile(opkPath);
    return safeStorage.decryptString(encrypted);
  } catch (err) {
    if (err.code === 'ENOENT') return null;
    console.error('crypto:getOPKs error:', err);
    return null;
  }
});

// Очистка всех E2E ключей при выходе из аккаунта
ipcMain.handle('crypto:clearAll', async () => {
  const appDataPath = app.getPath('userData');
  const filesToDelete = [
    path.join(appDataPath, 'blesk.key'),
    path.join(appDataPath, 'blesk.sign'),
    path.join(appDataPath, 'blesk.spk'),
  ];

  for (const filePath of filesToDelete) {
    try {
      await fs.promises.unlink(filePath);
    } catch (e) {
      if (e.code !== 'ENOENT') console.error('Failed to delete key file:', filePath, e.message);
    }
  }

  // Удалить директорию shield-sessions
  const shieldDir = path.join(appDataPath, 'shield-sessions');
  try {
    await fs.promises.rm(shieldDir, { recursive: true, force: true });
  } catch (e) {
    console.error('Failed to delete shield-sessions:', e.message);
  }

  return { success: true };
});

// Получить список экранов и окон для демонстрации экрана
ipcMain.handle('screen:getSources', async () => {
  try {
    const sources = await desktopCapturer.getSources({
      types: ['screen', 'window'],
      thumbnailSize: { width: 320, height: 180 },
      fetchWindowIcons: true,
    });
    return sources.map((s) => ({
      id: s.id,
      name: s.name,
      thumbnail: s.thumbnail.toDataURL(),
      appIcon: s.appIcon ? s.appIcon.toDataURL() : null,
      display_id: s.display_id,
    }));
  } catch (err) {
    console.error('screen:getSources error:', err);
    return [];
  }
});

// --- Автообновления ---
function setupAutoUpdater() {
  if (isDev) return;

  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  // [IMP-4] allowPrerelease только в beta-версиях
  autoUpdater.allowPrerelease = app.getVersion().includes('-beta') || app.getVersion().includes('-alpha');

  autoUpdater.on('update-available', (info) => {
    console.log('Доступно обновление:', info.version);
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:available', info.version);
    }
  });

  autoUpdater.on('download-progress', (progress) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:progress', {
        percent: Math.round(progress.percent),
        speed: progress.bytesPerSecond || 0,
        transferred: progress.transferred || 0,
        total: progress.total || 0,
      });
    }
  });

  autoUpdater.on('update-downloaded', () => {
    console.log('Обновление загружено, установится при перезапуске');
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:downloaded');
    }
  });

  autoUpdater.on('error', (err) => {
    console.error('Ошибка обновления:', err.message);
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:error', err.message);
    }
  });

  // [IMP-4] Проверяем обновления через 5 сек после старта, потом каждый час
  setTimeout(() => autoUpdater.checkForUpdates().catch(() => {}), 5000);
  const updateInterval = setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 60 * 60 * 1000);
  app.on('before-quit', () => clearInterval(updateInterval));
}

// Renderer может запросить установку обновления
ipcMain.on('update:install', () => {
  autoUpdater.quitAndInstall(false, true);
});

// ═══ Автозапуск с Windows ═══
ipcMain.handle('app:setAutoStart', (_, enabled) => {
  app.setLoginItemSettings({ openAtLogin: !!enabled });
  return app.getLoginItemSettings().openAtLogin;
});

ipcMain.handle('app:getAutoStart', () => {
  return app.getLoginItemSettings().openAtLogin;
});

// ═══ System Tray ═══
let dndEnabled = false;

function createTray() {
  if (tray) return;

  const trayIcon = nativeImage.createFromPath(iconPath).resize({ width: 16, height: 16 });
  tray = new Tray(trayIcon);
  tray.setToolTip('blesk');

  const updateTrayMenu = () => {
    const contextMenu = Menu.buildFromTemplate([
      {
        label: 'Показать blesk',
        click: () => {
          if (mainWindow) { mainWindow.show(); mainWindow.focus(); }
        },
      },
      { type: 'separator' },
      {
        label: 'Не беспокоить',
        type: 'checkbox',
        // [IMP-4] Синхронизация состояния DND
        checked: dndEnabled,
        click: (item) => {
          dndEnabled = item.checked;
          if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.webContents.send('tray:dnd', dndEnabled);
          }
        },
      },
      { type: 'separator' },
      {
        label: 'Выйти',
        click: () => {
          isQuitting = true;
          app.quit();
        },
      },
    ]);
    tray.setContextMenu(contextMenu);
  };

  updateTrayMenu();

  // [IMP-3] Только один обработчик клика (без конфликта click/double-click)
  tray.on('click', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });

  // [IMP-4] Синхронизация DND из renderer → tray menu
  ipcMain.on('dnd:sync', (_, enabled) => {
    dndEnabled = !!enabled;
    updateTrayMenu();
  });

  // Запрос DND состояния из renderer при старте
  ipcMain.on('dnd:state', (_, enabled) => {
    dndEnabled = !!enabled;
    updateTrayMenu();
  });
}

// ═══ Системные уведомления (из main process — работают в трее) ═══
// [IMP-5] Rate limiting для уведомлений
let notifTimestamps = [];

ipcMain.on('notification:show', (_, data) => {
  if (!data || typeof data !== 'object') return;
  const { title, body, chatId, silent } = data;
  if (typeof title !== 'string' || typeof body !== 'string') return;

  // Rate limit: макс 5 уведомлений за 5 секунд
  const now = Date.now();
  notifTimestamps = notifTimestamps.filter(t => now - t < 5000);
  if (notifTimestamps.length >= 5) return;
  notifTimestamps.push(now);

  // [CRIT-4] Не показывать если DND включён
  if (dndEnabled) return;

  const notification = new Notification({
    title: title.slice(0, 100),
    body: body.slice(0, 200),
    icon: iconPath,
    // [IMP-4] Уважать настройку звуков пользователя
    silent: !!silent,
  });

  notification.on('click', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
      if (chatId && typeof chatId === 'string') {
        mainWindow.webContents.send('notification:open-chat', chatId);
      }
    }
  });

  notification.show();
});

// [CRIT-1] Badge — overlay icon из DataURL вместо SVG buffer
// nativeImage.createFromBuffer НЕ поддерживает SVG — используем DataURL
let badgeDebounce = null;

ipcMain.on('badge:update', (_, count) => {
  if (typeof count !== 'number' || isNaN(count)) return;
  // [IMP-7] Clamp и debounce
  unreadCount = Math.max(0, Math.min(Math.round(count), 9999));

  clearTimeout(badgeDebounce);
  badgeDebounce = setTimeout(() => {
    // Windows overlay badge
    if (mainWindow && !mainWindow.isDestroyed()) {
      if (unreadCount > 0) {
        mainWindow.setOverlayIcon(
          createBadgeIcon(unreadCount),
          `${unreadCount} непрочитанных`
        );
      } else {
        mainWindow.setOverlayIcon(null, '');
      }
    }
    // Tray tooltip с лимитом
    if (tray) {
      tray.setToolTip(unreadCount > 0 ? `blesk (${Math.min(unreadCount, 999)})` : 'blesk');
    }
  }, 100);
});

// [CRIT-1] Badge icon через DataURL (PNG) вместо SVG buffer
function createBadgeIcon(count) {
  const label = count > 9 ? '9+' : String(count);
  const size = 64; // Высокое разрешение для чёткости на HiDPI
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">
    <circle cx="${size/2}" cy="${size/2}" r="${size/2}" fill="#ef4444"/>
    <text x="${size/2}" y="${size/2 + 10}" text-anchor="middle" fill="white" font-size="36" font-weight="bold" font-family="Arial,sans-serif">${label}</text>
  </svg>`;
  const dataUrl = `data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`;
  try {
    return nativeImage.createFromDataURL(dataUrl).resize({ width: 16, height: 16 });
  } catch {
    return nativeImage.createEmpty();
  }
}

// ═══ Deep Links (blesk:// протокол) ═══
if (!isDev) {
  app.setAsDefaultProtocolClient('blesk');
}

// [IMP-2] Whitelist допустимых deep link actions
const VALID_DEEPLINK_ACTIONS = new Set(['chat', 'channel', 'join', 'user']);

// [IMP-1] Pending deep link для cold start
let pendingDeepLink = null;

function handleDeepLink(url) {
  if (!url || typeof url !== 'string') return;
  if (!url.startsWith('blesk://')) return;
  // Лимит длины URL
  if (url.length > 2000) return;

  try {
    const parsed = new URL(url);
    const action = parsed.hostname;
    let param = parsed.pathname.slice(1);

    // [IMP-2] Проверить whitelist actions
    if (!VALID_DEEPLINK_ACTIONS.has(action)) return;

    // [IMP-2] Sanitize param: только alphanumeric + dash + underscore
    param = param.replace(/[^a-zA-Z0-9\-_]/g, '');
    if (!param) return;

    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.show();
      mainWindow.focus();
      mainWindow.webContents.send('deeplink:action', { action, param });
    } else {
      // [IMP-1] Сохранить для обработки когда mainWindow будет готов
      pendingDeepLink = { action, param };
    }
  } catch {
    // Невалидный URL — игнорировать
  }
}

// Windows: deep link при втором инстансе (+ single instance)
app.on('second-instance', (event, argv) => {
  if (mainWindow) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
  }
  const deepLink = argv.find(arg => arg.startsWith('blesk://'));
  if (deepLink) handleDeepLink(deepLink);
});

// macOS: open-url
app.on('open-url', (event, url) => {
  event.preventDefault();
  handleDeepLink(url);
});

app.whenReady().then(() => {
  createSplashWindow();
  createTray();
  setupAutoUpdater();

  // Глобальная горячая клавиша для восстановления окна из трея
  globalShortcut.register('Ctrl+Shift+B', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });

  // [IMP-1] Проверить deep link из командной строки (cold start)
  const deepLink = process.argv.find(arg => arg.startsWith('blesk://'));
  if (deepLink) pendingDeepLink = { raw: deepLink };
});

// [IMP-8] Перехват close — свернуть в трей; destroy tray при выходе
app.on('before-quit', () => {
  isQuitting = true;
  if (tray) { tray.destroy(); tray = null; }
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
});

// Безопасность: блокируем <webview> теги (electron-development checklist)
app.on('web-contents-created', (_event, contents) => {
  contents.on('will-attach-webview', (event) => {
    event.preventDefault();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin' && isQuitting) {
    app.quit();
  }
});
