import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { api, isAbortError, setUnauthorizedHandler } from '@/lib/api';
import type { User } from '@/types/api';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  user: User | null;
  status: AuthStatus;
  isAdministrator: boolean;
  /** True when a previously signed-in session was rejected by the server (expired or revoked). */
  sessionExpired: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  dismissSessionExpired: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the signed-in user in memory only. The session credential is the HttpOnly cookie, which JavaScript
 * cannot read; on a full page reload the user is restored from GET /api/auth/me.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [sessionExpired, setSessionExpired] = useState(false);
  const userRef = useRef<User | null>(null);

  useEffect(() => {
    userRef.current = user;
  }, [user]);

  // Restore the session after a reload. The abort guard also makes React StrictMode's double effect harmless.
  useEffect(() => {
    const controller = new AbortController();
    api.auth
      .me(controller.signal)
      .then((current) => {
        setUser(current);
        setStatus('authenticated');
      })
      .catch((error: unknown) => {
        if (isAbortError(error)) {
          return;
        }
        setUser(null);
        setStatus('anonymous');
      });
    return () => controller.abort();
  }, []);

  // Any API call that comes back 401 mid-session ends the session client-side; the route guard then
  // sends the user to the login page.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (userRef.current) {
        setSessionExpired(true);
      }
      setUser(null);
      setStatus('anonymous');
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const signedIn = await api.auth.login(email, password);
    setSessionExpired(false);
    setUser(signedIn);
    setStatus('authenticated');
    return signedIn;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.auth.logout();
    } catch {
      // The cookie may already be gone or the network down; the local session is cleared regardless.
    } finally {
      setSessionExpired(false);
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  const dismissSessionExpired = useCallback(() => setSessionExpired(false), []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      status,
      isAdministrator: user?.systemRole === 'ADMINISTRATOR',
      sessionExpired,
      login,
      logout,
      dismissSessionExpired,
    }),
    [user, status, sessionExpired, login, logout, dismissSessionExpired],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
