import { useEffect } from 'react';
import { ArrowRight, FolderKanban, ListChecks } from 'lucide-react';
import { Link } from 'react-router-dom';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { PriorityBadge } from '@/components/PriorityBadge';
import { LoadingBlock } from '@/components/Spinner';
import { StatusPill, statusDotClass } from '@/components/StatusPill';
import { useAuth } from '@/context/AuthContext';
import { api, useApi } from '@/lib/api';
import { formatRelative, formatUtc, plural, roleLabel, statusLabel } from '@/lib/format';
import { BUG_STATUSES } from '@/types/api';
import type { BugSummary, Dashboard, Project } from '@/types/api';

function StatTile({ label, value, dotClass }: { label: string; value: number; dotClass?: string }) {
  return (
    <div className="card px-4 py-3">
      <div className="flex items-center gap-2 text-xs font-medium text-slate-600">
        {dotClass && <span className={`h-2 w-2 rounded-full ${dotClass}`} aria-hidden />}
        {label}
      </div>
      <div className="mt-1 text-2xl font-semibold tabular-nums text-ink">{value.toLocaleString()}</div>
    </div>
  );
}

function Stats({ data }: { data: Dashboard }) {
  return (
    <section aria-label="Summary" className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7">
      <StatTile label="Projects" value={data.accessibleProjects} />
      <StatTile label="Assigned to me (open)" value={data.openAssignedToMe} />
      {BUG_STATUSES.map((status) => (
        <StatTile
          key={status}
          label={statusLabel(status)}
          value={data.bugsByStatus[status] ?? 0}
          dotClass={statusDotClass(status)}
        />
      ))}
    </section>
  );
}

function MyOpenBugs({ bugs, total }: { bugs: BugSummary[]; total: number }) {
  return (
    <section className="card" aria-labelledby="my-bugs-heading">
      <div className="card-header">
        <h2 id="my-bugs-heading" className="text-sm font-semibold">
          My open bugs
        </h2>
        <span className="text-xs text-slate-600">
          {total > bugs.length ? `Showing ${bugs.length} of ${total}` : plural(total, 'bug')}
        </span>
      </div>
      {bugs.length === 0 ? (
        <EmptyState
          icon={ListChecks}
          title="Nothing assigned to you"
          description="Bugs a project lead assigns to you will appear here."
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="data-grid">
            <thead>
              <tr>
                <th scope="col">Key</th>
                <th scope="col">Title</th>
                <th scope="col">Priority</th>
                <th scope="col">Status</th>
                <th scope="col">Updated</th>
              </tr>
            </thead>
            <tbody>
              {bugs.map((bug) => {
                const href = `/projects/${bug.projectId}/bugs/${bug.id}`;
                return (
                  <tr key={bug.id}>
                    <td className="whitespace-nowrap font-mono text-xs">
                      <Link to={href} className="text-brand-700 hover:underline">
                        {bug.key}
                      </Link>
                    </td>
                    <td>
                      <div className="max-w-[28rem] truncate">
                        <Link to={href} className="font-medium text-ink hover:underline">
                          {bug.title}
                        </Link>
                      </div>
                    </td>
                    <td>
                      <PriorityBadge priority={bug.priority} />
                    </td>
                    <td>
                      <StatusPill status={bug.status} resolution={bug.resolution} />
                    </td>
                    <td className="whitespace-nowrap text-xs text-slate-600" title={formatUtc(bug.updatedAt)}>
                      {formatRelative(bug.updatedAt)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function ProjectShortcuts({ projects, isAdministrator }: { projects: Project[]; isAdministrator: boolean }) {
  return (
    <section className="card" aria-labelledby="projects-heading">
      <div className="card-header">
        <h2 id="projects-heading" className="text-sm font-semibold">
          {isAdministrator ? 'All projects' : 'My projects'}
        </h2>
        <Link to="/projects" className="inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:underline">
          View all
          <ArrowRight className="h-3.5 w-3.5" aria-hidden />
        </Link>
      </div>
      {projects.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title="No projects yet"
          description={
            isAdministrator
              ? 'Create the first project from the Projects page.'
              : 'You are not a member of any project. Ask a project lead to add you.'
          }
        />
      ) : (
        <ul className="divide-y divide-line">
          {projects.map((project) => (
            <li key={project.id}>
              <Link to={`/projects/${project.id}`} className="flex items-center gap-3 px-4 py-2.5 hover:bg-surface">
                <span className="rounded bg-midnight px-1.5 py-0.5 font-mono text-[11px] font-semibold text-white">
                  {project.projectKey}
                </span>
                <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">{project.name}</span>
                <span className="whitespace-nowrap text-xs text-slate-600">
                  {project.myRole ? roleLabel(project.myRole) : isAdministrator ? 'Administrator' : '\u2014'}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function DashboardPage() {
  const { user, isAdministrator } = useAuth();
  const dashboard = useApi((signal) => api.dashboard.get(signal), []);
  const projects = useApi((signal) => api.projects.list(signal), []);

  useEffect(() => {
    document.title = 'Dashboard \u00B7 Jira Lite';
  }, []);

  const firstName = user?.fullName.split(' ')[0] ?? '';
  const failure = !dashboard.data && dashboard.error ? dashboard.error : !projects.data && projects.error ? projects.error : null;
  const loading = !failure && (!dashboard.data || !projects.data);

  function reloadAll() {
    dashboard.reload();
    projects.reload();
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Welcome back{firstName ? `, ${firstName}` : ''}</h1>
          <p className="mt-0.5 text-xs text-slate-600">
            {isAdministrator
              ? 'Administrator view: figures cover every active project.'
              : 'Figures cover the projects you are a member of.'}
          </p>
        </div>
        {isAdministrator && (
          <Link to="/projects" className="btn-secondary">
            <FolderKanban className="h-4 w-4" aria-hidden />
            Manage projects
          </Link>
        )}
      </div>

      {failure ? (
        <div className="card">
          <ErrorState error={failure} title="The dashboard could not be loaded" onRetry={reloadAll} />
        </div>
      ) : loading || !dashboard.data || !projects.data ? (
        <div className="card">
          <LoadingBlock label="Loading dashboard…" rows={6} />
        </div>
      ) : (
        <>
          <Stats data={dashboard.data} />
          <div className="grid gap-4 xl:grid-cols-3">
            <div className="xl:col-span-2">
              <MyOpenBugs bugs={dashboard.data.myOpenBugs} total={dashboard.data.openAssignedToMe} />
            </div>
            <ProjectShortcuts projects={projects.data} isAdministrator={isAdministrator} />
          </div>
        </>
      )}
    </div>
  );
}
