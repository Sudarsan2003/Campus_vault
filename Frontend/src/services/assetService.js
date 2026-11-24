import api from '../lib/api.js';

export const assetService = {
  list: async () => (await api.get('/assets')).data,
  create: async (payload) => (await api.post('/assets', payload)).data,
  verify: async (id) => (await api.post(`/admin/assets/${id}/verify`)).data,
  loans: async () => (await api.get('/loans/mine')).data,
  overdueLoans: async () => (await api.get('/admin/loans/overdue')).data,
  borrow: async (assetId, days) => (await api.post('/loans', { assetId, days })).data,
  returnLoan: async (id, damaged) => (await api.post(`/loans/${id}/return`, { damaged })).data,
  ledger: async () => (await api.get('/ledger')).data,
  wallet: async () => (await api.get('/wallet')).data,
  faucet: async () => (await api.post('/wallet/faucet')).data,
};