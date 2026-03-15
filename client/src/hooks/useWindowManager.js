import { useReducer, useCallback } from 'react';

const DEFAULT_WIDTH = 420;
const DEFAULT_HEIGHT = 520;
const MIN_WIDTH = 320;
const MIN_HEIGHT = 400;
const CASCADE_OFFSET = 30;
const START_X = 80;
const START_Y = 80;

// Зона навигации сверху (titlebar 44px + nav ~56px)
const TOP_BOUND = 72;
// Отступ от краёв
const EDGE_PAD = 8;
// Расстояние для snap (прилипания)
const SNAP_DISTANCE = 20;

// Ограничить позицию окна строго в пределах экрана
function clampPosition(x, y, w, h) {
  const vw = window.innerWidth;
  const vh = window.innerHeight;

  // Не выше навигации
  y = Math.max(y, TOP_BOUND);
  // Не ниже экрана — окно целиком видно
  y = Math.min(y, vh - h - EDGE_PAD);
  // Не левее экрана
  x = Math.max(x, EDGE_PAD);
  // Не правее экрана
  x = Math.min(x, vw - w - EDGE_PAD);

  return { x, y };
}

// Snap к краям экрана и другим окнам
function snapPosition(x, y, w, h, windows, selfId) {
  const vw = window.innerWidth;
  const vh = window.innerHeight;

  let snappedX = x;
  let snappedY = y;

  // Snap к краям экрана
  // Левый край
  if (Math.abs(x - EDGE_PAD) < SNAP_DISTANCE) snappedX = EDGE_PAD;
  // Правый край
  if (Math.abs(x + w - (vw - EDGE_PAD)) < SNAP_DISTANCE) snappedX = vw - EDGE_PAD - w;
  // Верхний край (nav)
  if (Math.abs(y - TOP_BOUND) < SNAP_DISTANCE) snappedY = TOP_BOUND;
  // Нижний край
  if (Math.abs(y + h - (vh - EDGE_PAD)) < SNAP_DISTANCE) snappedY = vh - EDGE_PAD - h;

  // Snap к другим окнам (с зазором GAP px)
  const GAP = 8;
  for (const [id, win] of Object.entries(windows)) {
    if (id === selfId) continue;

    // Прилипание правого края текущего к левому краю другого (с зазором)
    if (Math.abs(x + w + GAP - win.x) < SNAP_DISTANCE) snappedX = win.x - w - GAP;
    // Прилипание левого края текущего к правому краю другого (с зазором)
    if (Math.abs(x - (win.x + win.width + GAP)) < SNAP_DISTANCE) snappedX = win.x + win.width + GAP;
    // Прилипание верха текущего к низу другого (с зазором)
    if (Math.abs(y - (win.y + win.height + GAP)) < SNAP_DISTANCE) snappedY = win.y + win.height + GAP;
    // Прилипание низа текущего к верху другого (с зазором)
    if (Math.abs(y + h + GAP - win.y) < SNAP_DISTANCE) snappedY = win.y - h - GAP;

    // Выравнивание по горизонтали (left = left)
    if (Math.abs(x - win.x) < SNAP_DISTANCE) snappedX = win.x;
    // Выравнивание по вертикали (top = top)
    if (Math.abs(y - win.y) < SNAP_DISTANCE) snappedY = win.y;
  }

  return { x: snappedX, y: snappedY };
}

function getNextPosition(windows) {
  const count = Object.keys(windows).length;
  const vw = window.innerWidth;
  const vh = window.innerHeight;

  let x = START_X + count * CASCADE_OFFSET;
  let y = TOP_BOUND + 8 + count * CASCADE_OFFSET;

  // Сброс каскада если окно выходит за 60% экрана
  if (x + DEFAULT_WIDTH > vw * 0.6 || y + DEFAULT_HEIGHT > vh * 0.6) {
    x = START_X + Math.round(Math.random() * 20);
    y = TOP_BOUND + 8 + Math.round(Math.random() * 20);
  }

  return { x, y };
}

function windowReducer(state, action) {
  switch (action.type) {
    case 'OPEN_WINDOW': {
      if (state.windows[action.chatId]) return state;
      const pos = getNextPosition(state.windows);
      const w = Math.min(DEFAULT_WIDTH, window.innerWidth - 40);
      const h = Math.min(DEFAULT_HEIGHT, window.innerHeight - TOP_BOUND - 40);
      return {
        ...state,
        nextZ: state.nextZ + 1,
        windows: {
          ...state.windows,
          [action.chatId]: {
            chatId: action.chatId,
            x: pos.x,
            y: pos.y,
            width: w,
            height: h,
            zIndex: state.nextZ,
            morphRect: action.morphRect || null,
          },
        },
      };
    }

    case 'CLOSE_WINDOW': {
      const { [action.chatId]: _, ...rest } = state.windows;
      return { ...state, windows: rest };
    }

    case 'FOCUS_WINDOW': {
      const win = state.windows[action.chatId];
      if (!win) return state;
      return {
        ...state,
        nextZ: state.nextZ + 1,
        windows: {
          ...state.windows,
          [action.chatId]: { ...win, zIndex: state.nextZ },
        },
      };
    }

    case 'MOVE_WINDOW': {
      const win = state.windows[action.chatId];
      if (!win) return state;
      // Ограничить + snap
      const clamped = clampPosition(action.x, action.y, win.width, win.height);
      const snapped = snapPosition(clamped.x, clamped.y, win.width, win.height, state.windows, action.chatId);
      return {
        ...state,
        windows: {
          ...state.windows,
          [action.chatId]: { ...win, x: snapped.x, y: snapped.y },
        },
      };
    }

    case 'RESIZE_WINDOW': {
      const win = state.windows[action.chatId];
      if (!win) return state;
      const w = Math.max(action.width, MIN_WIDTH);
      const h = Math.max(action.height, MIN_HEIGHT);
      const clamped = clampPosition(action.x, action.y, w, h);
      return {
        ...state,
        windows: {
          ...state.windows,
          [action.chatId]: {
            ...win,
            x: clamped.x,
            y: clamped.y,
            width: w,
            height: h,
          },
        },
      };
    }

    case 'CLEAR_MORPH': {
      const win = state.windows[action.chatId];
      if (!win) return state;
      return {
        ...state,
        windows: {
          ...state.windows,
          [action.chatId]: { ...win, morphRect: null },
        },
      };
    }

    default:
      return state;
  }
}

export default function useWindowManager() {
  const [state, dispatch] = useReducer(windowReducer, {
    windows: {},
    nextZ: 10,
  });

  const openWindow = useCallback((chatId, morphRect) => {
    dispatch({ type: 'OPEN_WINDOW', chatId, morphRect });
  }, []);

  const closeWindow = useCallback((chatId) => {
    dispatch({ type: 'CLOSE_WINDOW', chatId });
  }, []);

  const focusWindow = useCallback((chatId) => {
    dispatch({ type: 'FOCUS_WINDOW', chatId });
  }, []);

  const moveWindow = useCallback((chatId, x, y) => {
    dispatch({ type: 'MOVE_WINDOW', chatId, x, y });
  }, []);

  const resizeWindow = useCallback((chatId, x, y, width, height) => {
    dispatch({ type: 'RESIZE_WINDOW', chatId, x, y, width, height });
  }, []);

  const clearMorph = useCallback((chatId) => {
    dispatch({ type: 'CLEAR_MORPH', chatId });
  }, []);

  const maxZ = Object.values(state.windows).reduce(
    (max, w) => Math.max(max, w.zIndex),
    0
  );

  return {
    windows: state.windows,
    maxZ,
    openWindow,
    closeWindow,
    focusWindow,
    moveWindow,
    resizeWindow,
    clearMorph,
  };
}

export { MIN_WIDTH, MIN_HEIGHT };
