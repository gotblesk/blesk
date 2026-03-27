/**
 * blesk — IndexedDB кеш сообщений (через idb-keyval)
 * Сообщения сохраняются локально и доступны мгновенно при открытии чата,
 * даже без сети. После загрузки с сервера кеш обновляется.
 *
 * Структура: key = "msgs:<chatId>", value = Message[]
 * Хранится максимум MAX_CACHED_MESSAGES на чат, MAX_CACHED_CHATS всего.
 */
import { get, set, del, keys } from 'idb-keyval';

const PREFIX = 'msgs:';
const MAX_CACHED_MESSAGES = 100; // Макс сообщений на чат
const MAX_CACHED_CHATS = 50;     // Макс кешированных чатов

/**
 * Загрузить сообщения из кеша для chatId
 * @returns {Message[] | null}
 */
export async function getCachedMessages(chatId) {
  try {
    const data = await get(PREFIX + chatId);
    return data || null;
  } catch {
    return null;
  }
}

/**
 * Сохранить сообщения в кеш для chatId
 * Сохраняет последние MAX_CACHED_MESSAGES сообщений
 */
export async function setCachedMessages(chatId, messages) {
  try {
    // Обрезать до лимита (последние N)
    const trimmed = messages.slice(-MAX_CACHED_MESSAGES);
    // Убрать pending/failed сообщения из кеша (они не подтверждены)
    const clean = trimmed.filter(m => !m.pending && !m.failed);
    await set(PREFIX + chatId, clean);

    // Проверить лимит кешированных чатов
    await evictOldChats();
  } catch {
    // IndexedDB недоступен — молча игнорировать
  }
}

/**
 * Добавить одно сообщение в кеш (при получении нового)
 */
export async function appendCachedMessage(chatId, message) {
  if (message.pending || message.failed) return;
  try {
    const existing = (await get(PREFIX + chatId)) || [];
    // Дедупликация по id
    if (message.id && existing.some(m => m.id === message.id)) return;
    existing.push(message);
    // Обрезать
    const trimmed = existing.slice(-MAX_CACHED_MESSAGES);
    await set(PREFIX + chatId, trimmed);
  } catch {}
}

/**
 * Обновить сообщение в кеше (при редактировании)
 */
export async function updateCachedMessage(chatId, messageId, updates) {
  try {
    const existing = (await get(PREFIX + chatId)) || [];
    const updated = existing.map(m =>
      m.id === messageId ? { ...m, ...updates } : m
    );
    await set(PREFIX + chatId, updated);
  } catch {}
}

/**
 * Удалить сообщение из кеша
 */
export async function removeCachedMessage(chatId, messageId) {
  try {
    const existing = (await get(PREFIX + chatId)) || [];
    const filtered = existing.filter(m => m.id !== messageId);
    await set(PREFIX + chatId, filtered);
  } catch {}
}

/**
 * Очистить кеш для чата
 */
export async function clearChatCache(chatId) {
  try {
    await del(PREFIX + chatId);
  } catch {}
}

/**
 * Очистить весь кеш (при logout)
 */
export async function clearAllCache() {
  try {
    const allKeys = await keys();
    for (const key of allKeys) {
      if (typeof key === 'string' && key.startsWith(PREFIX)) {
        await del(key);
      }
    }
  } catch {}
}

/**
 * Вытеснить старые чаты из кеша если их больше MAX_CACHED_CHATS
 */
async function evictOldChats() {
  try {
    const allKeys = await keys();
    const msgKeys = allKeys.filter(k => typeof k === 'string' && k.startsWith(PREFIX));
    if (msgKeys.length <= MAX_CACHED_CHATS) return;

    // Удалить самые старые (те, что добавлены первыми — порядок keys())
    const toDelete = msgKeys.slice(0, msgKeys.length - MAX_CACHED_CHATS);
    for (const key of toDelete) {
      await del(key);
    }
  } catch {}
}
