import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, Bug as BugIcon, FilterX, Plus, Search } from 'lucide-react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { MarkdownView } from '@/components/ActivityTimeline';
import { Breadcrumbs } from '@/components/Breadcrumbs';
import { CreateBugModal } from '@/components/CreateBugModal';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { Pagination } from '@/components/Pagination';
import { PriorityBadge } from '@/components/PriorityBadge';
import { LoadingBlock } from '@/components/Spinner';
import { StatusPill } from '@/components/StatusPill';
import { useAuth } from '@/context/AuthContext';
import { api, useApi } from '@/lib/api';
import { formatRelative, formatUtc, plural, priorityLabel, roleLabel, statusLabel } from '@/lib/format';
import { BUG_PRIORITIES, BUG_STATUSES } from '@/types/api';
import type { BugListQuery, BugPriority, BugStatus, SortDirection, SortField } from '@/types/api';

interface ListState {
  status: BugStatus | undefined;
  priority: BugPriority | undefined;
  mine: boolean;
  search: string;
  page: number;
  size: number;
  sort: SortField;
  direction: SortDirection;
}

const PAGE_SIZES: readonly number[] = [10, 20, 50, 100];
const DEFAULT_SIZE = 20;
const SORT_FIELDS: readonly SortField[] = ['createdAt', 'updatedAt', 'title', 'status', 'priority', 'bugNumber'];
/** For priority, "desc" means most severe first (the server ranks Blocker..Low, not alphabetically). */
const DEFAULT_DIRECTION: Record<SortField, SortDirection> = {
  createdAt: 'desc',
  updatedAt: 'desc',
  bugNumber: 'desc',
  priority: 'desc',
  title: 'asc',
  status: 'asc',
};

/**
 * The list state lives in the URL (shareable, survives reload and Back). Every value is checked against an
 * allow-list or clamped, so a hand-edited URL can never send an unsupported sort field or page size.
 */
function parseState(params: URLSearchParams): ListState {
  const status = BUG_STATUSES.find((value) => value === params.get('status'));
  const priority = BUG_PRIORITIES.find((value) => value === params.get('priority'));
  const sort = SORT_FIELDS.find((value) => value === params.get('sort')) ?? 'createdAt';
  const dir = params.get('dir');
  const direction: SortDirection = dir === 'asc' || dir === 'desc' ? dir : DEFAULT_DIRECTION[sort];
  const pageNumber = Number.parseInt(params.get('page') ?? '', 10);
  const sizeNumber = Number.parseInt(params.get('size') ?? '', 10);
  return {
    status,
    priority,
    mine: params.get('mine') === '1',
    search: (params.get('q') ?? '').trim().slice(0, 100),
    page: Number.isInteger(pageNumber) && pageNumber > 0 ? Math.min(pageNumber, 100000) : 0,
    size: PAGE_SIZES.includes(sizeNumber) ? sizeNumber : DEFAULT_SIZE,
    sort,
    direction,
  };
}

function toParams(state: ListState): URLSearchParams {
  const params = new URLSearchParams();
  if (state.status) params.set('status', state.status);
  if (state.priority) params.set('priority', state.priority);
  if (state.mine) params.set('mine', '1');
  if (state.search) params.set('q', state.search);
  if (state.page > 0) params.set('page', String(state.page));
  if (state.size !== DEFAULT_SIZE) params.set('size', String(state.size));
  if (state.sort !== 'createdAt') params.set('sort', state.sort);
  if (state.direction !== DEFAULT_DIRECTION[state.sort]) params.set('dir', state.direction);
  return params;
}

function SortHeader({
  field,
  label,
  state,
  onSort,
}: {
  field: SortField;
  label: string;
  state: ListState;
  onSort: (field: SortField) => void;
}) {
  const active = state.sort === field;
  const Icon = !active ? ArrowUpDown : state.direction === 'asc' ? ArrowUp : ArrowDown;
  return (
    <th scope="col" aria-sort={active ? (state.direction === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button
        type="button"
        onClick={() => onSort(field)}
        className={`inline-flex items-center gap-1 uppercase tracking-wide hover:text-ink ${active ? 'text-ink' : ''}`}
      >
        {label}
        <Icon className={`h-3.5 w-3.5 ${active ? 'text-brand-700' : 'text-slate-400'}`} aria-hidden />
      </button>
    </th>
  );
}

export function BugListPage() {
  const { projectId = '' } = useParams<{ projectId: string }>();
  const { user, isAdministrator } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const state = useMemo(() => parseState(searchParams), [searchParams]);
  const [searchInput, setSearchInput] = useState(state.search);
  const [creating, setCreating] = useState(false);

  /** Applies a change. Anything other than an explicit page change returns to the first page. */
  function update(patch: Partial<ListState>) {
    const next: ListState = { ...state, ...patch };
    if (patch.page === undefined) {
      next.page = 0;
    }
    setSearchParams(toParams(next), { replace: true });
  }
  const updateRef = useRef(update);
  useEffect(() => {
    updateRef.current = update;
  });

  // Debounce typing in the search box (300 ms) before it becomes part of the query.
  useEffect(() => {
    const trimmed = searchInput.trim();
    if (trimmed === state.search) {
      return undefined;
    }
    const timer = window.setTimeout(() => updateRef.current({ search: trimmed }), 300);
    return () => window.clearTimeout(timer);
  }, [searchInput, state.search]);

  // Keep the box in sync when the URL changes from elsewhere (Back button, "Clear filters").
  useEffect(() => {
    setSearchInput((current) => (current.trim() === state.search ? current : state.search));
  }, [state.search]);

  const project = useApi((signal) => api.projects.get(projectId, signal), [projectId]);

  const query: BugListQuery = {
    status: state.status,
    priority: state.priority,
    assigneeId: state.mine ? user?.id : undefined,
    search: state.search || undefined,
    page: state.page,
    size: state.size,
    sort: state.sort,
    direction: state.direction,
  };
  const bugs = useApi(
    (signal) => api.bugs.list(projectId, query, signal),
    [projectId, state.status, state.priority, state.mine, state.search, state.page, state.size, state.sort, state.direction],
    { keepPreviousData: true },
  );

  useEffect(() => {
    document.title = `${project.data?.projectKey ?? 'Project'} \u00B7 Jira Lite`;
  }, [project.data?.projectKey]);

  // A stale ?page= beyond the last page (e.g. after filtering) jumps to the last real page.
  useEffect(() => {
    const data = bugs.data;
    if (data && !bugs.loading && state.page > 0 && state.page >= data.totalPages) {
      updateRef.current({ page: Math.max(0, data.totalPages - 1) });
    }
  }, [bugs.data, bugs.loading, state.page]);

  function handleSort(field: SortField) {
    if (state.sort === field) {
      update({ direction: state.direction === 'asc' ? 'desc' : 'asc' });
    } else {
      update({ sort: field, direction: DEFAULT_DIRECTION[field] });
    }
  }

  const hasFilters = Boolean(state.status || state.priority || state.mine || state.search);
  function clearFilters() {
    setSearchInput('');
    setSearchParams(
      toParams({ ...state, status: undefined, priority: undefined, mine: false, search: '', page: 0 }),
      { replace: true },
    );
  }

  // ---- project-level states
  if (project.error && !project.data) {
    return (
      <div>
        <Breadcrumbs items={[{ label: 'Projects', to: '/projects' }, { label: 'Project' }]} />
        <div className="card">
          <ErrorState error={project.error} title="This project could not be opened" onRetry={project.reload} />
        </div>
      </div>
    );
  }
  if (!project.data) {
    return (
      <div className="card">
        <LoadingBlock label="Loading project…" rows={6} />
      </div>
    );
  }

  const current = project.data;
  const canCreate = !current.archived;
  const newBugButton = canCreate ? (
    <button type="button" className="btn-primary" onClick={() => setCreating(true)}>
      <Plus className="h-4 w-4" aria-hidden />
      New bug
    </button>
  ) : null;
  const refreshing = bugs.loading && bugs.data !== null;

  return (
    <div>
      <Breadcrumbs items={[{ label: 'Projects', to: '/projects' }, { label: current.projectKey }]} />

      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="rounded bg-midnight px-1.5 py-0.5 font-mono text-[11px] font-semibold text-white">
              {current.projectKey}
            </span>
            <h1 className="truncate text-xl font-semibold">{current.name}</h1>
            <span className="whitespace-nowrap rounded-full border border-line bg-white px-2 py-0.5 text-xs text-slate-600">
              {current.myRole ? roleLabel(current.myRole) : isAdministrator ? 'Administrator' : 'Member'}
            </span>
          </div>
          {current.description && (
            <div className="mt-1 max-w-3xl text-slate-600">
              <MarkdownView source={current.description} />
            </div>
          )}
        </div>
        {newBugButton}
      </div>

      {current.archived && (
        <div
          role="status"
          className="mb-4 rounded-md border border-status-reopened/30 bg-status-reopened-tint px-3 py-2 text-xs font-medium text-status-reopened-ink"
        >
          This project is archived. Its bugs are read-only.
        </div>
      )}

      <section className="card" aria-label="Bugs">
        <div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-2.5">
          <div className="relative w-full sm:w-72">
            <Search
              className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
              aria-hidden
            />
            <input
              type="search"
              className="input pl-8"
              placeholder="Search title or key (e.g. ALPHA-12)"
              aria-label="Search bugs"
              maxLength={100}
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
          </div>

          <select
            aria-label="Filter by status"
            className="input w-auto"
            value={state.status ?? ''}
            onChange={(event) => update({ status: BUG_STATUSES.find((value) => value === event.target.value) })}
          >
            <option value="">All statuses</option>
            {BUG_STATUSES.map((status) => (
              <option key={status} value={status}>
                {statusLabel(status)}
              </option>
            ))}
          </select>

          <select
            aria-label="Filter by priority"
            className="input w-auto"
            value={state.priority ?? ''}
            onChange={(event) => update({ priority: BUG_PRIORITIES.find((value) => value === event.target.value) })}
          >
            <option value="">All priorities</option>
            {BUG_PRIORITIES.map((priority) => (
              <option key={priority} value={priority}>
                {priorityLabel(priority)}
              </option>
            ))}
          </select>

          <label className="flex items-center gap-1.5 text-xs font-medium text-ink">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-line accent-brand-600"
              checked={state.mine}
              onChange={(event) => update({ mine: event.target.checked })}
            />
            Assigned to me
          </label>

          {hasFilters && (
            <button type="button" className="btn-ghost" onClick={clearFilters}>
              <FilterX className="h-4 w-4" aria-hidden />
              Clear
            </button>
          )}

          <span className="ml-auto text-xs text-slate-600" aria-live="polite">
            {bugs.data ? plural(bugs.data.totalElements, 'bug') : ''}
          </span>
        </div>

        {bugs.error && !bugs.data ? (
          <ErrorState error={bugs.error} title="Bugs could not be loaded" onRetry={bugs.reload} />
        ) : !bugs.data ? (
          <LoadingBlock label="Loading bugs…" rows={8} />
        ) : bugs.data.content.length === 0 ? (
          hasFilters ? (
            <EmptyState
              icon={FilterX}
              title="No bugs match your filters"
              description="Try removing a filter or searching for something else."
              action={
                <button type="button" className="btn-secondary" onClick={clearFilters}>
                  Clear filters
                </button>
              }
            />
          ) : (
            <EmptyState
              icon={BugIcon}
              title="No bugs reported yet"
              description={canCreate ? 'Report the first bug to start tracking work.' : undefined}
              action={newBugButton}
            />
          )
        ) : (
          <>
            {bugs.error && (
              <div role="alert" className="border-b border-line bg-sev-critical-tint px-4 py-2 text-xs text-sev-critical-ink">
                The latest refresh failed, so this list may be out of date.{' '}
                <button type="button" className="font-semibold underline" onClick={bugs.reload}>
                  Retry
                </button>
              </div>
            )}
            <div className={`overflow-x-auto transition-opacity ${refreshing ? 'opacity-60' : ''}`} aria-busy={refreshing}>
              <table className="data-grid">
                <thead>
                  <tr>
                    <SortHeader field="bugNumber" label="Key" state={state} onSort={handleSort} />
                    <SortHeader field="title" label="Title" state={state} onSort={handleSort} />
                    <SortHeader field="priority" label="Priority" state={state} onSort={handleSort} />
                    <SortHeader field="status" label="Status" state={state} onSort={handleSort} />
                    <th scope="col">Assignee</th>
                    <SortHeader field="updatedAt" label="Updated" state={state} onSort={handleSort} />
                  </tr>
                </thead>
                <tbody>
                  {bugs.data.content.map((bug) => {
                    const href = `/projects/${bug.projectId}/bugs/${bug.id}`;
                    return (
                      <tr key={bug.id}>
                        <td className="whitespace-nowrap font-mono text-xs">
                          <Link to={href} className="text-brand-700 hover:underline">
                            {bug.key}
                          </Link>
                        </td>
                        <td>
                          <div className="max-w-[32rem] truncate">
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
                        <td className="whitespace-nowrap text-xs">
                          {bug.assigneeName ?? <span className="text-slate-400">Unassigned</span>}
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
            <Pagination
              page={bugs.data.page}
              size={bugs.data.size}
              totalElements={bugs.data.totalElements}
              totalPages={bugs.data.totalPages}
              disabled={refreshing}
              onPageChange={(page) => update({ page })}
              onSizeChange={(size) => update({ size })}
            />
          </>
        )}
      </section>

      {creating && (
        <CreateBugModal
          projectId={projectId}
          onClose={() => setCreating(false)}
          onCreated={(bug) => {
            setCreating(false);
            navigate(`/projects/${bug.projectId}/bugs/${bug.id}`);
          }}
        />
      )}
    </div>
  );
}
