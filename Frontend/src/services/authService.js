import api, { USE_MOCK } from '../lib/api.js';
import { mockAuth } from './mockAuth.js';

/**
 * Backend contract (Phase 2):
 *  POST /auth/login     { email, password }                                    -> { token, user }
 *  POST /auth/register  { fullName, email, studentId, department, password }   -> { message, user }
 *  GET  /auth/me                                                               -> user
 * user = { id, fullName, email, studentId, department, role: 'STUDENT' | 'ADMIN' }
 */
export const authService = {
  login: async (payload) => (USE_MOCK ? mockAuth.login(payload) : (await api.post('/auth/login', payload)).data),
  register: async (payload) => (USE_MOCK ? mockAuth.register(payload) : (await api.post('/auth/register', payload)).data),
  me: async (token) => (USE_MOCK ? mockAuth.me(token) : (await api.get('/auth/me')).data),
};