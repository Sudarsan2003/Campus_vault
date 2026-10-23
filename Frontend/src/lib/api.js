import axios from 'axios';
import { tokenStorage } from './storage.js';

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (error) => {
    const url = error.config?.url || '';
    const isAuthCall = url.startsWith('/auth/login') || url.startsWith('/auth/register');
    if (error.response?.status === 401 && !isAuthCall) {
      tokenStorage.clear();
      window.dispatchEvent(new Event('cv:unauthorized'));
    }
    return Promise.reject(error);
  }
);

export function parseApiError(error) {
  if (!error.response) {
    return { message: 'Cannot reach the server. Check your connection and try again.', fieldErrors: {} };
  }
  const data = error.response.data || {};
  return {
    message: data.message || 'Something went wrong. Please try again.',
    fieldErrors: data.errors || {},
  };
}

export default api;