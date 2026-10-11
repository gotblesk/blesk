# Preloader Redesign — Metaball Blob Reveal

## Обзор

Полная переработка прелоадера blesk. Текущий — Glass-карточка 350x500 с прогресс-баром. Новый — metaball blob'ы слетаются, слипаются, раскрывают логотип. Blob'ы постоянно деформируются, никогда не останавливаются.

## Решения (утверждённые)

| Вопрос | Решение |
|--------|---------|
| Стиль | Metaball blob'ы (слипание/деформация) |
| Размер окна | 350x400px, frameless, transparent |
| Раскрытие логотипа | 4-5 мелких blob'ов слетаются → слипаются в один → раскрываются → логотип внутри |
| Логотип | blesk.png (не текст "bl") |
| Прогресс | 4 dots пульсируют + текст статуса |
| Цвет blob'ов | Accent green #c8ff00 |
| Движение | Blob'ы деформируются ВСЕГДА, не останавливаются |
| Статусы | "Запуск..." → "Подключение к серверу..." → "Загрузка профиля..." → "Подготовка интерфейса..." → "Почти готово..." |
| Пасхалки | 10% шанс: "Полируем интерфейс...", "Настраиваем блеск...", "Прогреваем пиксели...", "Загружаем уют..." |

## Архитектура

### Файловая структура

```
client/electron/
├── splash.html           # ПЕРЕПИСАТЬ — Three.js Canvas + metaball shader
├── splash-preload.js     # БЕЗ ИЗМЕНЕНИЙ — IPC bridge
├── main.js               # МОДИФИКАЦИЯ — размер окна 350x400 (было 350x500)
```

### Технологии в splash.html

- **Three.js** (CDN, inline в HTML) — Canvas рендеринг metaball blob'ов
- **GLSL shader** — SDF metaball raymarching с noise displacement
- **GSAP** (CDN, inline) — timeline анимации: слёт → слипание → раскрытие
- **CSS** — dots пульсация, статус текст, версия

Примечание: splash.html — отдельный Electron файл, НЕ React. Three.js и GSAP подключаются через CDN или inline (не через npm, т.к. splash не проходит через Vite/webpack).

## Анимация (покадрово)

### Фаза 1: Blob'ы появляются (0 – 0.8s)

- 4-5 маленьких blob'ов (radius 8-12px) появляются из разных краёв окна
- Каждый blob — отдельная сфера в SDF shader
- Появление: scale 0→1, opacity 0→1, stagger 0.15s
- Blob'ы начинают двигаться к центру
- Поверхность каждого blob'а деформируется (noise displacement, непрерывно)
- Dots начинают пульсировать, статус: "Запуск..."

### Фаза 2: Blob'ы слетаются к центру (0.8 – 1.5s)

- Blob'ы движутся к центру окна (GSAP tweens на uniform позиции)
- По мере сближения — metaball bridge эффект (smin blending)
- Blob'ы начинают слипаться парами, потом все в одну массу
- Масса пульсирует, деформируется
- Статус: "Подключение к серверу..." (или пасхалка)

### Фаза 3: Единый blob пульсирует (1.5 – 2.0s)

- Все blob'ы слились в один большой blob по центру
- Blob "дышит" — scale 1.0↔1.08, noise displacement усиливается
- Поверхность живая — волны, деформации, specular highlights
- IPC: `splash:ready` отправляется, main window начинает создаваться
- Статус: "Загрузка профиля..." (или пасхалка)

### Фаза 4: Blob раскрывается (2.0 – 2.8s)

- Blob разделяется на две половинки (splitAmount uniform: 0→1)
- Между половинками растёт просвет
- Metaball bridge тянется и рвётся (классический metaball натяжение)
- blesk.png логотип fade-in + scale 0.8→1.0 из центра
- Drop-shadow glow нарастает: 0 0 20px rgba(200,255,0,0.25)
- Половинки blob'а продолжают деформироваться, отодвигаясь
- Статус: "Подготовка интерфейса..." (или пасхалка)

### Фаза 5: Логотип виден, blob'ы живые (2.8 – 3.0s+)

- Логотип полностью виден по центру
- Tagline fade-in: "твой блеск. твои правила." (9px, uppercase, letter-spacing 3px)
- Половинки blob'а остаются по бокам, продолжают деформироваться
- Blob'ы НИКОГДА не останавливаются — постоянная деформация до закрытия окна
- Статус: "Почти готово..."
- Версия fade-in внизу: "v0.5.7-beta" (из package.json, не hardcoded)

### Фаза 6: Переход в приложение (когда main window готов)

- Main window отправляет `did-finish-load` + 600ms задержка для React
- Splash: CSS `expand-out` — scale 1.0→1.3, opacity 1→0, border-radius shrink
- Main window показывается
- Splash закрывается
- Fallback: если main window не готов за 5 секунд — переход всё равно

## Компоненты splash.html

### Three.js Canvas (metaball shader)

**Vertex Shader:** стандартный fullscreen quad (position→gl_Position, uv→vUv).

**Fragment Shader:**
- 5 SDF сфер с `smin` blending (k=0.5 для мягкого слипания)
- Noise displacement на поверхности (simplex noise, непрерывный)
- Позиции сфер — uniforms, анимируются из JavaScript
- `uSplitAmount` uniform — управляет разделением (0=слито, 1=раскрыто)
- Specular highlight: Phong-like с точечным источником света сверху
- Цвет: #c8ff00 с вариациями яркости по нормалям
- Glow вокруг blob'ов: exp falloff
- Dithering: noise-based для предотвращения color banding
- Фон: transparent (alpha=0) — видна тёмная подложка окна

**Canvas setup:**
- Размер: 350x400px
- DPR: 1.0 (не retina, для производительности)
- Alpha: true (прозрачный фон)
- 40 raymarch steps max
- Рендер НЕ останавливается — requestAnimationFrame до закрытия окна

### Logo (HTML поверх Canvas)

- `<img src="blesk.png">` с absolute positioning по центру
- Начальное состояние: opacity 0, scale 0.8, pointer-events none
- Reveal: GSAP to({ opacity: 1, scale: 1, duration: 0.5, ease: "back.out(1.5)" })
- Drop-shadow: GSAP анимирует filter от 0 до 20px glow

### Tagline (HTML)

- "твой блеск. твои правила."
- Font: Manrope, 9px, rgba(255,255,255,0.2), uppercase, letter-spacing 3px
- Под логотипом, margin-top 12px
- Fade-in с задержкой 0.3s после логотипа

### Loading Dots (CSS)

- 4 точки, 6x6px, border-radius 50%
- Цвет: rgba(200,255,0,0.3) → rgba(200,255,0,1.0) при пульсации
- Animation: scale 0.8↔1.2, opacity 0.2↔1.0
- Stagger: 0.2s между точками
- Duration: 1.2s infinite
- Позиция: под tagline

### Status Text (HTML)

- Font: Manrope, 9px, rgba(255,255,255,0.2)
- Под dots, margin-top 8px
- Fade transition между статусами (opacity 0→1, 0.3s)
- 5 статусов, меняются каждые 600ms:
  1. "Запуск..."
  2. "Подключение к серверу..."
  3. "Загрузка профиля..."
  4. "Подготовка интерфейса..."
  5. "Почти готово..."
- Пасхалки (каждый шаг 2-4 независимо бросает 10% шанс):
  - "Полируем интерфейс..."
  - "Настраиваем блеск..."
  - "Прогреваем пиксели..."
  - "Загружаем уют..."

### Version (HTML)

- "v{version}" — из package.json, НЕ hardcoded
- Font: Manrope, 8px, rgba(200,255,0,0.25)
- Позиция: absolute bottom 12px
- Fade-in в фазе 5

## Изменения в main.js

- `SPLASH_HEIGHT`: 500 → 400
- Остальная логика transition без изменений

**IPC flow (текущий, сохраняется):**
1. Splash HTML вызывает `window.splashApi.ready()` → IPC `splash:ready` → main process
2. Main process создаёт main window в `ipcMain.on('splash:ready')`
3. Main window `did-finish-load` → main.js через 600ms выполняет JS на splash window: `document.getElementById('splash').classList.add('expand-out')`
4. Через 600ms splash закрывается, main window показывается
5. Fallback: если `did-finish-load` не приходит за 5 секунд — переход всё равно

**Версия из package.json:**
main.js передаёт версию в splash через `splash-preload.js`:
```js
// splash-preload.js — добавить:
contextBridge.exposeInMainWorld('splashApi', {
  ready: () => ipcRenderer.send('splash:ready'),
  version: () => ipcRenderer.sendSync('get-version'),
});

// main.js — добавить:
ipcMain.on('get-version', (event) => {
  event.returnValue = app.getVersion();
});
```
splash.html читает: `document.getElementById('version').textContent = 'v' + window.splashApi.version();`

**Путь к логотипу:**
- В dev: `../public/blesk.png`
- В prod (после сборки): `../dist/blesk.png`
- Определение: `const isDev = !window.splashApi; const logoPath = isDev ? '../public/blesk.png' : '../dist/blesk.png';`
- Fallback onerror: `this.style.display='none'`

## Производительность

- Canvas 350x400 = 140,000 пикселей — минимальная нагрузка
- DPR до 1.5 (не полный retina, но не blurry на HiDPI)
- 40 raymarch steps — достаточно для 5 blob'ов
- Three.js + GSAP через CDN (~150KB gzip total) — загружаются параллельно
- requestAnimationFrame с blob деформацией не останавливается, но при expand-out окно закрывается через 600ms

## Accessibility

- prefers-reduced-motion: blob'ы статичные (без деформации), логотип появляется сразу, dots без анимации
- Окно non-interactive (только визуальное), keyboard не применим

## Зависимости (локальные копии)

Three.js и GSAP НЕ через CDN (прелоадер может запуститься офлайн). Копии библиотек лежат рядом с splash.html:

```
client/electron/
├── splash.html
├── splash-preload.js
├── lib/
│   ├── three.min.js      # скопировать из node_modules/three/build/three.min.js
│   └── gsap.min.js       # скопировать из node_modules/gsap/dist/gsap.min.js
```

Подключение в splash.html:
```html
<script src="./lib/three.min.js"></script>
<script src="./lib/gsap.min.js"></script>
```

Шрифт Manrope — `@font-face` с локальным .woff2:
```
client/electron/lib/
│   └── manrope-var.woff2  # скопировать из node_modules или Google Fonts
```

```css
@font-face {
  font-family: 'Manrope';
  src: url('./lib/manrope-var.woff2') format('woff2');
  font-weight: 300 800;
}
```
