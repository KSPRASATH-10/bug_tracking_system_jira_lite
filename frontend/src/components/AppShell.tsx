import { useState } from 'react';
import { Bug, FolderKanban, LayoutDashboard, LogOut, ShieldCheck } from 'lucide-react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { initials } from '@/lib/format';

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/projects', label: 'Projects', icon: FolderKanban, end: false },
] as const;

/** Authenticated layout: Midnight Mirage header, role-aware chip, primary navigation and the page outlet. */
export function AppShell() {
  const { user, isAdministrator, logout } = useAuth();
  const navigate = useNavigate();
  const [signingOut, setSigningOut] = useState(false);

  async function handleSignOut() {
    setSigningOut(true);
    await logout();
    navigate('/login', { replace: true, state: null });
  }

  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-50 focus:rounded-md focus:bg-white focus:px-3 focus:py-2 focus:text-ink focus:shadow-pop"
      >
        Skip to main content
      </a>

      <header className="bg-midnight text-white">
        <div className="mx-auto flex h-12 max-w-page items-center gap-5 px-4">
          <NavLink to="/" className="flex items-center gap-2 font-semibold tracking-tight">
            <span className="flex h-7 w-7 items-center justify-center rounded-md bg-brand">
              <Bug className="h-4 w-4 text-midnight" aria-hidden />
            </span>
            <span>Jira Lite</span>
          </NavLink>

          <nav aria-label="Primary" className="flex h-full items-stretch gap-1">
            {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  `flex items-center gap-2 border-b-2 px-3 text-sm font-medium transition-colors ${
                    isActive
                      ? 'border-brand bg-midnight-700 text-white'
                      : 'border-transparent text-slate-300 hover:bg-midnight-700 hover:text-white'
                  }`
                }
              >
                <Icon className="h-4 w-4" aria-hidden />
                {label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            {user && (
              <div className="flex items-center gap-2">
                <span
                  className="flex h-7 w-7 items-center justify-center rounded-full bg-midnight-600 text-xs font-semibold"
                  aria-hidden
                >
                  {initials(user.fullName)}
                </span>
                <div className="hidden leading-tight sm:block">
                  <div className="text-xs font-medium">{user.fullName}</div>
                  <div className="flex items-center gap-1 text-[11px] text-slate-300">
                    {isAdministrator && <ShieldCheck className="h-3 w-3 text-brand" aria-hidden />}
                    {isAdministrator ? 'Administrator' : user.email}
                  </div>
                </div>
              </div>
            )}
            <button
              type="button"
              onClick={handleSignOut}
              disabled={signingOut}
              className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium text-slate-200 transition-colors hover:bg-midnight-700 hover:text-white disabled:opacity-50"
            >
              <LogOut className="h-4 w-4" aria-hidden />
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main id="main" tabIndex={-1} className="mx-auto w-full max-w-page flex-1 px-4 py-5 focus:outline-none">
        <Outlet />
      </main>

      <footer className="border-t border-line bg-white py-3 text-center text-xs text-slate-500">
        Jira Lite &middot; Times are shown in your local time zone and stored as UTC
      </footer>
    </div>
  );
}
