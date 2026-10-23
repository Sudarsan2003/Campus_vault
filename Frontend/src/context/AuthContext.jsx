import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authService } from '../services/authService.js';
import { tokenStorage } from '../lib/storage.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [initialising, setInitialising] = useState(true);

  // Restore session on first load.
  useEffect(() => {
    const token = tokenStorage.get();
    if (!token) {
      setInitialising(false);
      return;
    }
    authService
      .me(token)
      .then(setUser)
      .catch(() => tokenStorage.clear())
      .finally(() => setInitialising(false));
  }, []);

  // Axios interceptor fires this when the API rejects an expired/invalid token.
  useEffect(() => {
    const onUnauthorized = () => setUser(null);
    window.addEventListener('cv:unauthorized', onUnauthorized);
    return () => window.removeEventListener('cv:unauthorized', onUnauthorized);
  }, []);

  const login = useCallback(async (credentials) => {
    const { token, user: u } = await authService.login(credentials);
    tokenStorage.set(token);
    setUser(u);
    return u;
  }, []);

  const register = useCallback((payload) => authService.register(payload), []);

  const logout = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, initialising, isAuthenticated: !!user, isAdmin: user?.role === 'ADMIN', login, register, logout }),
    [user, initialising, login, register, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
};