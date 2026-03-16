const { app, BrowserWindow, ipcMain, screen } = require('electron');
const { autoUpdater } = require('electron-updater');
const path = require('path');

const isDev = !app.isPackaged;

let splashWindow;
let mainWindow;

// Размеры
const SPLASH_WIDTH = 350;
const SPLASH_HEIGHT = 500;
const MAIN_WIDTH = 1280;
const MAIN_HEIGHT = 800;

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
    },
    icon: path.join(__dirname, '../public/icon.png'),
    title: 'blesk',
    show: false,
  });

  splashWindow.loadFile(path.join(__dirname, 'splash.html'));

  splashWindow.once('ready-to-show', () => {
    splashWindow.show();
  });
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: MAIN_WIDTH,
    height: MAIN_HEIGHT,
    minWidth: 900,
    minHeight: 600,
    frame: false,
    backgroundColor: '#08060f',
    center: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    icon: path.join(__dirname, '../public/icon.png'),
    title: 'blesk',
    show: false,
  });

  // Оповещаем renderer о maximize/unmaximize
  mainWindow.on('maximize', () => {
    mainWindow.webContents.send('window:maximized');
  });
  mainWindow.on('unmaximize', () => {
    mainWindow.webContents.send('window:unmaximized');
  });

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173');
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  }
}

// Плавный переход: сплеш "расширяется" (CSS) → основное окно появляется
function transitionToMain() {
  if (!splashWindow || !mainWindow) return;

  // Шаг 1: Говорим сплешу запустить CSS-анимацию "расширения"
  // (контент масштабируется вверх и fade-out)
  splashWindow.webContents.executeJavaScript(`
    document.getElementById('splash').classList.add('expand-out');
  `).catch(() => {});

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
    // Даём React время отрисоваться
    setTimeout(doTransition, 600);
  });

  // Fallback — если did-finish-load не сработал за 5 сек
  setTimeout(doTransition, 5000);
});

// Управление окном из renderer
ipcMain.on('window:minimize', () => {
  const win = BrowserWindow.getFocusedWindow();
  if (win) win.minimize();
});

ipcMain.on('window:maximize', () => {
  const win = BrowserWindow.getFocusedWindow();
  if (win) {
    win.isMaximized() ? win.unmaximize() : win.maximize();
  }
});

ipcMain.on('window:close', () => {
  const win = BrowserWindow.getFocusedWindow();
  if (win) win.close();
});

// Ручное перетаскивание окна (обход бага -webkit-app-region на Windows)
let dragOffset = null;

ipcMain.on('window:start-drag', (event, offsetX, offsetY) => {
  dragOffset = { x: offsetX, y: offsetY };
});

ipcMain.on('window:dragging', (event, screenX, screenY) => {
  if (!dragOffset) return;
  const win = BrowserWindow.fromWebContents(event.sender);
  if (win && !win.isMaximized()) {
    win.setPosition(
      Math.round(screenX - dragOffset.x),
      Math.round(screenY - dragOffset.y)
    );
  }
});

ipcMain.on('window:stop-drag', () => {
  dragOffset = null;
});

ipcMain.handle('app:version', () => app.getVersion());

// --- Автообновления ---
function setupAutoUpdater() {
  if (isDev) return;

  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on('update-available', (info) => {
    console.log('Доступно обновление:', info.version);
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:available', info.version);
    }
  });

  autoUpdater.on('download-progress', (progress) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:progress', Math.round(progress.percent));
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
  });

  // Проверяем обновления через 5 сек после старта, потом каждый час
  setTimeout(() => autoUpdater.checkForUpdates().catch(() => {}), 5000);
  setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 60 * 60 * 1000);
}

// Renderer может запросить установку обновления
ipcMain.on('update:install', () => {
  autoUpdater.quitAndInstall(false, true);
});

app.whenReady().then(() => {
  createSplashWindow();
  setupAutoUpdater();
});

app.on('window-all-closed', () => {
  app.quit();
});
