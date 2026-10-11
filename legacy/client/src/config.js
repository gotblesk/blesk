// Адрес API-сервера (задаётся через VITE_API_URL в .env)
const API_URL = import.meta.env.VITE_API_URL || (import.meta.env.DEV ? 'http://localhost:3000' : 'https://api.blesk.fun');

export default API_URL;
