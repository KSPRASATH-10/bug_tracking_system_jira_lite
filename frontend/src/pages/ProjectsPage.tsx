import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { FolderKanban, Plus, Search } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { Breadcrumbs } from '@/components/Breadcrumbs';
import { EmptyState } from '@/components/EmptyState';
import { ErrorState } from '@/components/ErrorState';
import { Modal } from '@/components/Modal';
import { LoadingBlock } from '@/components/Spinner';
import { useAuth } from '@/context/AuthContext';
import { api, errorMessage, isApiError, useApi } from '@/lib/api';
import { formatDate, plural, roleLabel } from '@/lib/format';
import type { Project } from '@/types/api';

const KEY_PATTERN = /^[A-Z][A-Z0-9]{1,9}$/;

type FieldErrors = Partial<Record<'projectKey' | 'name' | 'description', string>>;

/** Client-side rules mirror CreateProjectRequest and the projects table constraints. */
function validate(projectKey: string, name: string, description: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!projectKey) {
    errors.projectKey = 'Project key is required';
  } else if (!KEY_PATTERN.test(projectKey)) {
    errors.projectKey = 'Use 2\u201310 characters: uppercase letters or digits, starting with a letter';
  }
  if (!name.trim()) {
    errors.name = 'Name is required';
  } else if (name.trim().length > 120) {
    errors.name = 'Name must be at most 120 characters';
  }
  if (description.length > 2000) {
    errors.description = 'Description must be at most 2000 characters';
  }
  return errors;
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={id} className="field-error">
      {message}
    </p>
  ) : null;
}

/** Mounted only while open, so its form state resets every time it is reopened. */
function CreateProjectModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (project: Project) => void;
}) {
  const [projectKey, setProjectKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    const errors = validate(projectKey, name, description);
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSubmitting(true);
    try {
      const created = await api.projects.create({
        projectKey,
        name: name.trim(),
        description: description.trim() || undefined,
      });
      onCreated(created);
    } catch (error) {
      if (isApiError(error) && error.status === 409) {
        setFieldErrors({ projectKey: error.message });
      } else if (isApiError(error) && Object.keys(error.fieldErrors).length > 0) {
        setFieldErrors(error.fieldErrors as FieldErrors);
      } else {
        setFormError(errorMessage(error, 'The project could not be created.'));
      }
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open
      title="New project"
      description="Projects isolate bugs and members. The key prefixes every bug id, for example ALPHA-12."
      onClose={onClose}
      busy={submitting}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" form="create-project-form" className="btn-primary" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create project'}
          </button>
        </>
      }
    >
      <form id="create-project-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <div
            role="alert"
            className="rounded-md border border-sev-critical/30 bg-sev-critical-tint px-3 py-2 text-xs font-medium text-sev-critical-ink"
          >
            {formError}
          </div>
        )}

        <div>
          <label htmlFor="project-key" className="label">
            Project key
          </label>
          <input
            id="project-key"
            data-autofocus
            className="input font-mono uppercase"
            value={projectKey}
            maxLength={10}
            autoComplete="off"
            spellCheck={false}
            onChange={(event) => setProjectKey(event.target.value.toUpperCase())}
            aria-invalid={fieldErrors.projectKey ? true : undefined}
            aria-describedby={fieldErrors.projectKey ? 'project-key-error' : 'project-key-hint'}
            disabled={submitting}
          />
          {fieldErrors.projectKey ? (
            <FieldError id="project-key-error" message={fieldErrors.projectKey} />
          ) : (
            <p id="project-key-hint" className="hint">
              2&ndash;10 characters. Cannot be changed later.
            </p>
          )}
        </div>

        <div>
          <label htmlFor="project-name" className="label">
            Name
          </label>
          <input
            id="project-name"
            className="input"
            value={name}
            maxLength={120}
            onChange={(event) => setName(event.target.value)}
            aria-invalid={fieldErrors.name ? true : undefined}
            aria-describedby={fieldErrors.name ? 'project-name-error' : undefined}
            disabled={submitting}
          />
          <FieldError id="project-name-error" message={fieldErrors.name} />
        </div>

        <div>
          <label htmlFor="project-description" className="label">
            Description <span className="font-normal text-slate-500">(optional)</span>
          </label>
          <textarea
            id="project-description"
            className="input min-h-[5rem] resize-y"
            value={description}
            maxLength={2000}
            onChange={(event) => setDescription(event.target.value)}
            aria-invalid={fieldErrors.description ? true : undefined}
            aria-describedby={fieldErrors.description ? 'project-description-error' : undefined}
            disabled={submitting}
          />
          <FieldError id="project-description-error" message={fieldErrors.description} />
        </div>
      </form>
    </Modal>
  );
}

export function ProjectsPage() {
  const { isAdministrator } = useAuth();
  const navigate = useNavigate();
  const projects = useApi((signal) => api.projects.list(signal), []);
  const [filter, setFilter] = useState('');
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    document.title = 'Projects \u00B7 Jira Lite';
  }, []);

  const all = projects.data;
  const visible = useMemo(() => {
    const term = filter.trim().toLowerCase();
    const list = all ?? [];
    if (!term) {
      return list;
    }
    return list.filter(
      (project) => project.projectKey.toLowerCase().includes(term) || project.name.toLowerCase().includes(term),
    );
  }, [all, filter]);

  const newProjectButton = isAdministrator ? (
    <button type="button" className="btn-primary" onClick={() => setCreating(true)}>
      <Plus className="h-4 w-4" aria-hidden />
      New project
    </button>
  ) : null;

  return (
    <div>
      <Breadcrumbs items={[{ label: 'Projects' }]} />

      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Projects</h1>
          <p className="mt-0.5 text-xs text-slate-600">
            {isAdministrator ? 'All active projects.' : 'Projects you are a member of.'}
          </p>
        </div>
        {newProjectButton}
      </div>

      <div className="card">
        {projects.error && !all ? (
          <ErrorState error={projects.error} title="Projects could not be loaded" onRetry={projects.reload} />
        ) : !all ? (
          <LoadingBlock label="Loading projects…" rows={4} />
        ) : all.length === 0 ? (
          <EmptyState
            icon={FolderKanban}
            title="No projects yet"
            description={
              isAdministrator
                ? 'Create the first project to start tracking bugs.'
                : 'You are not a member of any project. Ask a project lead to add you.'
            }
            action={newProjectButton}
          />
        ) : (
          <>
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-2.5">
              <div className="relative w-full max-w-xs">
                <Search
                  className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                  aria-hidden
                />
                <input
                  type="search"
                  className="input pl-8"
                  placeholder="Filter by key or name"
                  aria-label="Filter projects"
                  value={filter}
                  onChange={(event) => setFilter(event.target.value)}
                />
              </div>
              <span className="text-xs text-slate-600" aria-live="polite">
                {plural(visible.length, 'project')}
              </span>
            </div>

            {visible.length === 0 ? (
              <EmptyState icon={Search} title="No projects match your filter" description="Try a different key or name." />
            ) : (
              <div className="overflow-x-auto">
                <table className="data-grid">
                  <thead>
                    <tr>
                      <th scope="col">Key</th>
                      <th scope="col">Name</th>
                      <th scope="col">Description</th>
                      <th scope="col">Your role</th>
                      <th scope="col">Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((project) => (
                      <tr key={project.id}>
                        <td className="whitespace-nowrap">
                          <Link
                            to={`/projects/${project.id}`}
                            className="rounded bg-midnight px-1.5 py-0.5 font-mono text-[11px] font-semibold text-white hover:bg-midnight-700"
                          >
                            {project.projectKey}
                          </Link>
                        </td>
                        <td className="whitespace-nowrap font-medium">
                          <Link to={`/projects/${project.id}`} className="text-ink hover:underline">
                            {project.name}
                          </Link>
                        </td>
                        <td>
                          <div className="line-clamp-1 max-w-md text-xs text-slate-600">
                            {project.description ?? '\u2014'}
                          </div>
                        </td>
                        <td className="whitespace-nowrap text-xs">
                          {project.myRole ? roleLabel(project.myRole) : isAdministrator ? 'Administrator' : '\u2014'}
                        </td>
                        <td className="whitespace-nowrap text-xs text-slate-600">{formatDate(project.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>

      {creating && (
        <CreateProjectModal
          onClose={() => setCreating(false)}
          onCreated={(project) => {
            setCreating(false);
            navigate(`/projects/${project.id}`);
          }}
        />
      )}
    </div>
  );
}
