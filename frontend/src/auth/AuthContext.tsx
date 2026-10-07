import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, ensureSession, setUnauthorizedHandler, tokenStore, type Tokens } from '../api/client';
import type { Me } from './types';

type Status = 'loading' | 'anonymous' | 'authenticated';

interface AuthValue {
  status: Status;
  user: Me | null;
  login: (username: string, password: string) => Promise<Me>;
  logout: () => Promise<void>;
  reload: () => Promise<void>;
  can: (...permissions: string[]) => boolean;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<Me | null>(null);

  const reload = useCallback(async () => {
    const me = await api<Me>('/auth/me');
    setUser(me);
    setStatus('authenticated');
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null);
      setStatus('anonymous');
    });
    let cancelled = false;
    (async () => {
      if (tokenStore.hasSession() && (await ensureSession())) {
        try {
          const me = await api<Me>('/auth/me');
          if (!cancelled) {
            setUser(me);
            setStatus('authenticated');
          }
          return;
        } catch {
          // fall through to anonymous
        }
      }
      if (!cancelled) setStatus('anonymous');
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const tokens = await api<Tokens>('/auth/login', { method: 'POST', body: { username, password } });
    tokenStore.setTokens(tokens);
    const me = await api<Me>('/auth/me');
    setUser(me);
    setStatus('authenticated');
    return me;
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.refreshToken();
    try {
      await api('/auth/logout', { method: 'POST', body: { refreshToken } });
    } catch {
      // ignore: we clear the local session regardless
    }
    tokenStore.clear();
    setUser(null);
    setStatus('anonymous');
  }, []);

  const can = useCallback(
    (...permissions: string[]) => !!user && permissions.some((p) => user.permissions.includes(p)),
    [user],
  );

  const value = useMemo(() => ({ status, user, login, logout, reload, can }), [status, user, login, logout, reload, can]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
