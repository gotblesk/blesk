import { getUserId } from './authFetch';

/**
 * Возвращает userId из in-memory store.
 * userId устанавливается при логине/refresh через setUserId().
 */
export function getCurrentUserId() {
  return getUserId();
}
