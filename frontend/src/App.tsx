import { Component, useEffect, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, SearchX } from 'lucide-react';
import { Link, Route, Routes, useLocation, useParams } from 'react-router-dom';
import { AppShell } from '@/components/AppShell';
import { EmptyState } from '@/components/EmptyState';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { BugDetailPage } from '@/pages/BugDetailPage';
import { BugListPage } from '@/pages/BugListPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { LoginPage } from '@/pages/LoginPage';
import { ProjectsPage } from '@/pages/ProjectsPage';

interface ErrorBoundaryState {
  failed: boolean;
}

/** Last line of defence: a render error shows a calm fallback instead of a blank screen, with no internals exposed. */
class ErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unhandled UI error', error, info.componentStack);
  }

  render(): ReactNode {
    if (!this.state.failed) {
      return this.props.children;
    }
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface px-4">
        <div role="alert" className="card max-w-md p-8 text-center">
          <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-sev-critical-tint text-sev-critical">
            <AlertTriangle className="h-5 w-5" aria-hidden />
          </span>
          <h1 className="mt-3 text-base font-semibold">Something went wrong</h1>
          <p className="mt-1 text-xs text-slate-600">
            The page hit an unexpected problem. Reloading usually fixes it, and your data is safe.
          </p>
          <button type="button" className="btn-primary mt-4" onClick={() => window.location.reload()}>
            Reload page
          </button>
        </div>
      </div>
    );
  }
}

/** Keyed wrappers remount the pages when the URL ids change, so no state leaks from one bug or project to another. */
function ProjectRoute() {
  const { projectId } = useParams<{ projectId: string }>();
  return <BugListPage key={projectId} />;
}

function BugRoute() {
  const { projectId, bugId } = useParams<{ projectId: string; bugId: string }>();
  return <BugDetailPage key={`${projectId}/${bugId}`} />;
}

function NotFoundPage() {
  useEffect(() => {
    document.title = 'Page not found \u00B7 Jira Lite';
  }, []);
  return (
    <div className="card">
      <EmptyState
        icon={SearchX}
        title="Page not found"
        description="The address may be mistyped, or the page may have moved."
        action={
          <Link to="/" className="btn-primary">
            Go to dashboard
          </Link>
        }
      />
    </div>
  );
}

export default function App() {
  const location = useLocation();
  return (
    <ErrorBoundary key={location.pathname}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="projects" element={<ProjectsPage />} />
            <Route path="projects/:projectId" element={<ProjectRoute />} />
            <Route path="projects/:projectId/bugs/:bugId" element={<BugRoute />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </ErrorBoundary>
  );
}
