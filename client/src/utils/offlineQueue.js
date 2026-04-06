// offlineQueue.js -- localStorage-based queue for messages sent while offline.
// Messages are persisted so they survive app restarts.

const QUEUE_KEY = 'blesk-offline-queue';
const MAX_AGE_MS = 24 * 60 * 60 * 1000; // 24 hours -- discard stale entries

export function getQueue() {
  try {
    const raw = localStorage.getItem(QUEUE_KEY);
    if (!raw) return [];
    const queue = JSON.parse(raw);
    if (!Array.isArray(queue)) return [];
    // Drop entries older than MAX_AGE_MS
    const now = Date.now();
    return queue.filter(m => now - m.queuedAt < MAX_AGE_MS);
  } catch {
    return [];
  }
}

export function addToQueue(message) {
  const queue = getQueue();
  // Prevent duplicate tempIds
  if (queue.some(m => m.tempId === message.tempId)) return;
  queue.push({ ...message, queuedAt: Date.now() });
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
}

export function removeFromQueue(tempId) {
  const queue = getQueue().filter(m => m.tempId !== tempId);
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
}

export function clearQueue() {
  localStorage.removeItem(QUEUE_KEY);
}
