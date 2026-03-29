import API_URL from '../config';
import { getAuthHeaders } from './authFetch';

export default function uploadFile(chatId, file, { text, replyToId, onProgress } = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const formData = new FormData();
    formData.append('file', file);
    if (text) formData.append('text', text);
    if (replyToId) formData.append('replyToId', replyToId);

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    };

    xhr.onload = () => {
      try {
        const data = JSON.parse(xhr.responseText);
        if (xhr.status >= 200 && xhr.status < 300) resolve(data);
        else reject(new Error(data.error || 'Ошибка загрузки'));
      } catch {
        reject(new Error(xhr.status === 403 ? 'Нет доступа (CSRF)' : `Ошибка сервера (${xhr.status})`));
      }
    };

    xhr.onerror = () => reject(new Error('Нет подключения'));

    xhr.open('POST', `${API_URL}/api/chats/${chatId}/upload`);
    xhr.withCredentials = true;
    const authH = getAuthHeaders();
    if (authH.Authorization) xhr.setRequestHeader('Authorization', authH.Authorization);
    if (authH['X-CSRF-Token']) xhr.setRequestHeader('X-CSRF-Token', authH['X-CSRF-Token']);
    xhr.send(formData);
  });
}
