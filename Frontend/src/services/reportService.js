import api from '../lib/api.js';

export const reportService = {
  list: async () => (await api.get('/reports')).data,
  create: async (formData) =>
    (await api.post('/reports', formData, { headers: { 'Content-Type': 'multipart/form-data' } })).data,
  match: async (lostId, foundId) => (await api.post(`/reports/${lostId}/match`, { foundId })).data,
  confirmReturn: async (id) => (await api.post(`/reports/${id}/return`)).data,  suggestions: async (id) => (await api.get(`/reports/${id}/suggestions`)).data,
  // Fetched with the auth token and shown from a blob URL, so photos aren't publicly reachable.
  image: async (id) => (await api.get(`/reports/${id}/image`, { responseType: 'blob' })).data,
};