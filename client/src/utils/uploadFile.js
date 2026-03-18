import API_URL from '../config';

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
      } catch { reject(new Error('Ошибка сервера')); }
    };

    xhr.onerror = () => reject(new Error('Нет подключения'));

    xhr.open('POST', `${API_URL}/api/chats/${chatId}/upload`);
    xhr.setRequestHeader('Authorization', `Bearer ${localStorage.getItem('token')}`);
    xhr.send(formData);
  });
}
