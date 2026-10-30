import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spinner } from '@/components/Spinner';
import { useAuth } from '@/context/AuthContext';

/**
 * Layout route that guards everything nested under it. Unauthenticated visitors are redirected to /login and
 * the page they asked for is remembered in router state (never in storage) so sign-in can return them.
 * This is a UX guard only: the server enforces authentication and authorisation on every API call.
 */
export function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface">
        <Spinner size="lg" label="Checking your session…" />
      </div>
    );
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}
