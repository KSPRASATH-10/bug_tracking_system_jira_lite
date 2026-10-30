import { useEffect, useState, type FormEvent } from 'react';
import { Bug, Eye, EyeOff, Info, Loader2, X } from 'lucide-react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { errorMessage } from '@/lib/api';

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

interface FieldErrors {
  email?: string;
  password?: string;
}

/** Client-side rules mirror LoginRequest on the server (required, email shape, max lengths). */
function validate(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  const trimmed = email.trim();
  if (!trimmed) {
    errors.email = 'Email is required';
  } else if (trimmed.length > 254 || !EMAIL_PATTERN.test(trimmed)) {
    errors.email = 'Enter a valid email address';
  }
  if (!password) {
    errors.password = 'Password is required';
  } else if (password.length > 128) {
    errors.password = 'Password must be at most 128 characters';
  }
  return errors;
}

/**
 * Where to go after sign-in. Only same-site absolute paths are accepted ("/x"), so a crafted router state can
 * never turn the login page into an open redirect ("//evil.example", "/\\evil.example" and full URLs are refused).
 */
function safeTarget(from: unknown): string {
  if (typeof from === 'object' && from !== null) {
    const { pathname, search, hash } = from as { pathname?: unknown; search?: unknown; hash?: unknown };
    if (
      typeof pathname === 'string' &&
      pathname.startsWith('/') &&
      !pathname.startsWith('//') &&
      !pathname.includes('\\') &&
      pathname !== '/login'
    ) {
      return pathname + (typeof search === 'string' ? search : '') + (typeof hash === 'string' ? hash : '');
    }
  }
  return '/';
}

// Development convenience only. Both references are behind `import.meta.env.DEV`, which Vite replaces with
// `false` in production builds, so this block and the demo password never ship in the production bundle.
const DEMO_PASSWORD = 'Enterprise#Demo2026!';
const DEMO_ACCOUNTS = [
  { label: 'Administrator', email: 'admin@enterprise-demo.test' },
  { label: 'Project lead (Alpha)', email: 'lead.alpha@enterprise-demo.test' },
  { label: 'Developer (Alpha)', email: 'dev.alpha@enterprise-demo.test' },
  { label: 'Reporter (Alpha)', email: 'reporter.alpha@enterprise-demo.test' },
  { label: 'Project lead (Beta)', email: 'lead.beta@enterprise-demo.test' },
  { label: 'Developer (Beta)', email: 'dev.beta@enterprise-demo.test' },
] as const;

function DemoAccounts({ onPick }: { onPick: (email: string, password: string) => void }) {
  return (
    <div className="mt-4 rounded-md border border-dashed border-line bg-surface p-3">
      <p className="flex items-center gap-1.5 text-xs font-semibold text-ink">
        <Info className="h-3.5 w-3.5 text-brand" aria-hidden />
        Demo accounts (development build only)
      </p>
      <ul className="mt-2 grid grid-cols-1 gap-1">
        {DEMO_ACCOUNTS.map((account) => (
          <li key={account.email}>
            <button
              type="button"
              className="flex w-full items-center justify-between rounded px-2 py-1 text-left text-xs hover:bg-white"
              onClick={() => onPick(account.email, DEMO_PASSWORD)}
            >
              <span className="font-medium text-ink">{account.label}</span>
              <span className="font-mono text-[11px] text-slate-500">{account.email}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function LoginPage() {
  const { status, login, sessionExpired, dismissSessionExpired } = useAuth();
  const location = useLocation();
  const target = safeTarget((location.state as { from?: unknown } | null)?.from);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    document.title = 'Sign in \u00B7 Jira Lite';
  }, []);

  if (status === 'authenticated') {
    return <Navigate to={target} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    const errors = validate(email, password);
    setFieldErrors(errors);
    setFormError(null);
    if (errors.email || errors.password) {
      return;
    }
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      // On success the auth state flips to "authenticated" and the redirect above takes over.
    } catch (error) {
      setFormError(errorMessage(error, 'Sign-in failed. Please try again.'));
      setPassword('');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-midnight">
      <main className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">
          <div className="mb-6 flex items-center justify-center gap-2 text-white">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand">
              <Bug className="h-5 w-5 text-midnight" aria-hidden />
            </span>
            <span className="text-xl font-semibold tracking-tight">Jira Lite</span>
          </div>

          <div className="card p-6">
            <h1 className="text-lg font-semibold">Sign in</h1>
            <p className="mt-1 text-xs text-slate-600">Use your work account to continue.</p>

            {sessionExpired && (
              <div
                role="status"
                className="mt-4 flex items-start justify-between gap-2 rounded-md border border-status-reopened/30 bg-status-reopened-tint px-3 py-2 text-xs text-status-reopened-ink"
              >
                <span>Your session has ended. Please sign in again.</span>
                <button
                  type="button"
                  className="-mr-1 rounded p-0.5 hover:bg-white/60"
                  onClick={dismissSessionExpired}
                  aria-label="Dismiss message"
                >
                  <X className="h-3.5 w-3.5" aria-hidden />
                </button>
              </div>
            )}

            {formError && (
              <div
                role="alert"
                className="mt-4 rounded-md border border-sev-critical/30 bg-sev-critical-tint px-3 py-2 text-xs font-medium text-sev-critical-ink"
              >
                {formError}
              </div>
            )}

            <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-4">
              <div>
                <label htmlFor="login-email" className="label">
                  Email
                </label>
                <input
                  id="login-email"
                  name="email"
                  type="email"
                  autoComplete="username"
                  inputMode="email"
                  autoFocus
                  className="input"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  aria-invalid={fieldErrors.email ? true : undefined}
                  aria-describedby={fieldErrors.email ? 'login-email-error' : undefined}
                  disabled={submitting}
                />
                {fieldErrors.email && (
                  <p id="login-email-error" className="field-error">
                    {fieldErrors.email}
                  </p>
                )}
              </div>

              <div>
                <label htmlFor="login-password" className="label">
                  Password
                </label>
                <div className="relative">
                  <input
                    id="login-password"
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    className="input pr-10"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    aria-invalid={fieldErrors.password ? true : undefined}
                    aria-describedby={fieldErrors.password ? 'login-password-error' : undefined}
                    disabled={submitting}
                  />
                  <button
                    type="button"
                    className="btn-icon absolute right-0.5 top-0.5 h-7 w-7"
                    onClick={() => setShowPassword((visible) => !visible)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    aria-pressed={showPassword}
                  >
                    {showPassword ? (
                      <EyeOff className="h-4 w-4" aria-hidden />
                    ) : (
                      <Eye className="h-4 w-4" aria-hidden />
                    )}
                  </button>
                </div>
                {fieldErrors.password && (
                  <p id="login-password-error" className="field-error">
                    {fieldErrors.password}
                  </p>
                )}
              </div>

              <button type="submit" className="btn-primary w-full" disabled={submitting}>
                {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
                {submitting ? 'Signing in…' : 'Sign in'}
              </button>
            </form>

            {import.meta.env.DEV && (
              <DemoAccounts
                onPick={(demoEmail, demoPassword) => {
                  setEmail(demoEmail);
                  setPassword(demoPassword);
                  setFieldErrors({});
                  setFormError(null);
                }}
              />
            )}
          </div>

          <p className="mt-4 text-center text-xs text-slate-400">
            Authorised use only. Activity is logged and audited.
          </p>
        </div>
      </main>
    </div>
  );
}
