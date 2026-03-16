/**
 * Безопасное извлечение userId из JWT токена.
 * Возвращает null если токен невалидный или отсутствует.
 */
export function getCurrentUserId() {
  try {
    const token = localStorage.getItem('token');
    if (!token) return null;
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.userId || null;
  } catch {
    return null;
  }
}
