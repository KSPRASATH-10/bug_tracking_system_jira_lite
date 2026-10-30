import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Archive, Pencil, RefreshCw } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { ActivityTimeline, MarkdownView } from '@/components/ActivityTimeline';
import { AssigneeSelect } from '@/components/AssigneeSelect';
import { AttachmentDropzone } from '@/components/AttachmentDropzone';
import { Breadcrumbs } from '@/components/Breadcrumbs';
import { CommentBox } from '@/components/CommentBox';
import { ErrorState } from '@/components/ErrorState';
import { Modal } from '@/components/Modal';
import { PriorityBadge } from '@/components/PriorityBadge';
import { LoadingBlock } from '@/components/Spinner';
import { StatusPill } from '@/components/StatusPill';
import { StatusTransition } from '@/components/StatusTransition';
import { useAuth } from '@/context/AuthContext';
import { api, errorMessage, isApiError, useApi } from '@/lib/api';
import { formatDateTime, formatRelative, formatUtc, priorityLabel, resolutionLabel } from '@/lib/format';
import { BUG_PRIORITIES, LIMITS } from '@/types/api';
import type { Bug, BugPriority, UpdateBugPayload } from '@/types/api';

// ---------------------------------------------------------------------------------------------
// Edit dialog (title, description, priority). Sends only what changed, plus the version it was based on.
// ---------------------------------------------------------------------------------------------

type EditErrors = Partial<Record<'title' | 'description' | 'priority', string>>;

function EditBugModal({
  projectId,
  bug,
  onClose,
  onSaved,
  onConflict,
}: {
  projectId: string;
  bug: Bug;
  onClose: () => void;
  onSaved: (bug: Bug) => void;
  onConflict: () => void;
}) {
  const [title, setTitle] = useState(bug.title);
  const [description, setDescription] = useState(bug.description);
  const [priority, setPriority] = useState<BugPriority>(bug.priority);
  const [errors, setErrors] = useState<EditErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) {
      return;
    }
    const found: EditErrors = {};
    if (!title.trim()) {
      found.title = 'Title is required';
    } else if (title.trim().length > LIMITS.title) {
      found.title = `Title must be at most ${LIMITS.title} characters`;
    }
    if (description.length > LIMITS.description) {
      found.description = `Description must be at most ${LIMITS.description.toLocaleString()} characters`;
    }
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) {
      return;
    }

    const payload: UpdateBugPayload = { version: bug.version };
    const nextTitle = title.trim();
    if (nextTitle !== bug.title) payload.title = nextTitle;
    if (description !== bug.description) payload.description = description;
    if (priority !== bug.priority) payload.priority = priority;
    if (Object.keys(payload).length === 1) {
      onClose(); // nothing changed
      return;
    }

    setSaving(true);
    try {
      onSaved(await api.bugs.update(projectId, bug.id, payload));
    } catch (error) {
      if (isApiError(error) && error.isConcurrentModification) {
        onConflict();
        onClose();
        return;
      }
      if (isApiError(error) && Object.keys(error.fieldErrors).length > 0) {
        setErrors(error.fieldErrors as EditErrors);
      } else {
        setFormError(errorMessage(error, 'The changes could not be saved.'));
      }
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      size="lg"
      title={`Edit ${bug.key}`}
      onClose={onClose}
      busy={saving}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button type="submit" form="edit-bug-form" className="btn-primary" disabled={saving}>
            {saving ? 'Saving…' : 'Save changes'}
          </button>
        </>
      }
    >
      <form id="edit-bug-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <div
            role="alert"
            className="rounded-md border border-sev-critical/30 bg-sev-critical-tint px-3 py-2 text-xs font-medium text-sev-critical-ink"
          >
            {formError}
          </div>
        )}
        <div>
          <label htmlFor="edit-title" className="label">
            Title
          </label>
          <input
            id="edit-title"
            data-autofocus
            className="input"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            aria-invalid={errors.title ? true : undefined}
            aria-describedby={errors.title ? 'edit-title-error' : undefined}
            disabled={saving}
          />
          {errors.title && (
            <p id="edit-title-error" className="field-error">
              {errors.title}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="edit-priority" className="label">
            Priority
          </label>
          <select
            id="edit-priority"
            className="input"
            value={priority}
            onChange={(event) => {
              const next = BUG_PRIORITIES.find((value) => value === event.target.value);
              if (next) {
                setPriority(next);
              }
            }}
            disabled={saving}
          >
            {BUG_PRIORITIES.map((value) => (
              <option key={value} value={value}>
                {priorityLabel(value)}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="edit-description" className="label">
            Description
          </label>
          <textarea
            id="edit-description"
            className="input min-h-[10rem] resize-y font-mono text-[13px]"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            aria-invalid={errors.description ? true : undefined}
            aria-describedby={errors.description ? 'edit-description-error' : 'edit-description-hint'}
            disabled={saving}
          />
          {errors.description ? (
            <p id="edit-description-error" className="field-error">
              {errors.description}
            </p>
          ) : (
            <p id="edit-description-hint" className="hint">
              Markdown is supported. Raw HTML is never rendered.
            </p>
          )}
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------------------------
// Archive confirmation (soft delete)
// ---------------------------------------------------------------------------------------------

function ArchiveBugModal({
  projectId,
  bug,
  onClose,
  onArchived,
}: {
  projectId: string;
  bug: Bug;
  onClose: () => void;
  onArchived: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await api.bugs.archive(projectId, bug.id);
      onArchived();
    } catch (failure) {
      setError(errorMessage(failure, 'The bug could not be archived.'));
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      size="sm"
      title={`Archive ${bug.key}?`}
      description="The bug disappears from lists and searches. Its history, comments and audit trail are retained."
      onClose={onClose}
      busy={busy}
      footer={
        <>
          <button type="button" data-autofocus className="btn-secondary" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="button" className="btn-danger" onClick={confirm} disabled={busy}>
            {busy ? 'Archiving…' : 'Archive bug'}
          </button>
        </>
      }
    >
      {error ? (
        <p role="alert" className="field-error">
          {error}
        </p>
      ) : (
        <p className="text-sm text-slate-700">{bug.title}</p>
      )}
    </Modal>
  );
}

// ---------------------------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------------------------

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-semibold text-slate-600">{label}</dt>
      <dd className="mt-1 text-sm">{children}</dd>
    </div>
  );
}

export function BugDetailPage() {
  const { projectId = '', bugId = '' } = useParams<{ projectId: string; bugId: string }>();
  const { user, isAdministrator } = useAuth();
  const navigate = useNavigate();

  const project = useApi((signal) => api.projects.get(projectId, signal), [projectId]);
  const bugLoad = useApi((signal) => api.bugs.get(projectId, bugId, signal), [projectId, bugId]);
  const timeline = useApi((signal) => api.bugs.timeline(projectId, bugId, signal), [projectId, bugId]);
  const attachments = useApi((signal) => api.attachments.list(projectId, bugId, signal), [projectId, bugId]);

  const [bug, setBug] = useState<Bug | null>(null);
  const [conflict, setConflict] = useState(false);
  const [editing, setEditing] = useState(false);
  const [archiving, setArchiving] = useState(false);

  // Mutations return the fresh bug and replace it locally; a (re)load replaces it from the server.
  useEffect(() => {
    if (bugLoad.data) {
      setBug(bugLoad.data);
    }
  }, [bugLoad.data]);
  const current = bug ?? bugLoad.data;

  const role = project.data?.myRole ?? null;
  const readOnly = project.data?.archived === true;
  const isLead = isAdministrator || role === 'PROJECT_LEAD';
  const canAssign = !readOnly && isLead;

  // Members populate the assignee dropdown, so they are only fetched for people who may assign.
  const members = useApi(
    async (signal) => (canAssign ? api.projects.members(projectId, signal) : []),
    [projectId, canAssign],
  );

  useEffect(() => {
    document.title = current ? `${current.key} \u00B7 ${current.title} \u00B7 Jira Lite` : 'Bug \u00B7 Jira Lite';
  }, [current]);

  const crumbs = [
    { label: 'Projects', to: '/projects' },
    { label: project.data?.projectKey ?? '\u2026', to: `/projects/${projectId}` },
    { label: current?.key ?? 'Bug' },
  ];

  if (!current) {
    return (
      <div>
        <Breadcrumbs items={crumbs} />
        <div className="card">
          {bugLoad.error ? (
            <ErrorState error={bugLoad.error} title="This bug could not be opened" onRetry={bugLoad.reload} />
          ) : (
            <LoadingBlock label="Loading bug…" rows={8} />
          )}
        </div>
      </div>
    );
  }

  const isDevOrLead = isLead || role === 'DEVELOPER';
  const isOwnReport = role === 'REPORTER' && current.reporter.id === user?.id;
  const canEdit = !readOnly && (isDevOrLead || isOwnReport);
  const canArchive = !readOnly && isLead;
  const canParticipate = !readOnly && (isAdministrator || role !== null);

  function reloadBug() {
    setConflict(false);
    bugLoad.reload();
    timeline.reload();
  }
  function handleBugUpdated(updated: Bug) {
    setBug(updated);
    setConflict(false);
    timeline.reload();
  }

  return (
    <div>
      <Breadcrumbs items={crumbs} />

      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded bg-midnight px-1.5 py-0.5 font-mono text-[11px] font-semibold text-white">
              {current.key}
            </span>
            <StatusPill status={current.status} resolution={current.resolution} />
            <PriorityBadge priority={current.priority} />
          </div>
          <h1 className="mt-2 break-words text-xl font-semibold">{current.title}</h1>
        </div>
        <div className="flex items-center gap-2">
          <button type="button" className="btn-secondary" onClick={reloadBug} aria-label="Refresh this bug">
            <RefreshCw className="h-4 w-4" aria-hidden />
          </button>
          {canEdit && (
            <button type="button" className="btn-secondary" onClick={() => setEditing(true)}>
              <Pencil className="h-4 w-4" aria-hidden />
              Edit
            </button>
          )}
          {canArchive && (
            <button type="button" className="btn-secondary" onClick={() => setArchiving(true)}>
              <Archive className="h-4 w-4" aria-hidden />
              Archive
            </button>
          )}
        </div>
      </div>

      {conflict && (
        <div
          role="alert"
          className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-md border border-status-reopened/30 bg-status-reopened-tint px-3 py-2 text-xs text-status-reopened-ink"
        >
          <span className="font-medium">
            Someone else changed this bug while you were looking at it. Your change was not applied.
          </span>
          <button type="button" className="btn-secondary py-1" onClick={reloadBug}>
            <RefreshCw className="h-3.5 w-3.5" aria-hidden />
            Load latest version
          </button>
        </div>
      )}

      {readOnly && (
        <div
          role="status"
          className="mb-4 rounded-md border border-line bg-surface-2 px-3 py-2 text-xs font-medium text-slate-700"
        >
          This project is archived. Bugs are read-only.
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <section className="card" aria-labelledby="description-heading">
            <div className="card-header">
              <h2 id="description-heading" className="text-sm font-semibold">
                Description
              </h2>
            </div>
            <div className="px-4 py-3">
              {current.description.trim() ? (
                <MarkdownView source={current.description} />
              ) : (
                <p className="text-xs text-slate-500">No description provided.</p>
              )}
            </div>
          </section>

          <section className="card" aria-labelledby="attachments-heading">
            <div className="card-header">
              <h2 id="attachments-heading" className="text-sm font-semibold">
                Attachments{attachments.data ? ` (${attachments.data.length})` : ''}
              </h2>
            </div>
            <AttachmentDropzone
              projectId={projectId}
              bugId={current.id}
              attachments={attachments.data}
              loading={attachments.loading}
              error={attachments.error}
              canUpload={canParticipate}
              canRemove={(attachment) => !readOnly && (isLead || attachment.uploader.id === user?.id)}
              onChanged={() => {
                attachments.reload();
                timeline.reload();
              }}
              onRetry={attachments.reload}
            />
          </section>

          <section className="card" aria-labelledby="activity-heading">
            <div className="card-header">
              <h2 id="activity-heading" className="text-sm font-semibold">
                Activity
              </h2>
              <span className="text-xs text-slate-500">History is permanent and cannot be edited</span>
            </div>
            <div className="px-4 py-4">
              {timeline.error && !timeline.data ? (
                <ErrorState error={timeline.error} title="Activity could not be loaded" onRetry={timeline.reload} />
              ) : !timeline.data ? (
                <LoadingBlock label="Loading activity…" rows={4} />
              ) : (
                <ActivityTimeline entries={timeline.data} />
              )}
            </div>
            <div className="border-t border-line bg-surface px-4 py-3">
              <CommentBox
                projectId={projectId}
                bugId={current.id}
                disabled={!canParticipate}
                disabledReason={readOnly ? 'This project is archived, so new comments are disabled.' : undefined}
                onAdded={timeline.reload}
              />
            </div>
          </section>
        </div>

        <aside className="lg:col-span-1">
          <section className="card" aria-labelledby="details-heading">
            <div className="card-header">
              <h2 id="details-heading" className="text-sm font-semibold">
                Details
              </h2>
            </div>
            <dl className="space-y-4 px-4 py-4">
              <DetailRow label="Status">
                <StatusTransition
                  projectId={projectId}
                  bug={current}
                  onTransitioned={handleBugUpdated}
                  onConflict={() => setConflict(true)}
                />
              </DetailRow>
              <DetailRow label="Assignee">
                <AssigneeSelect
                  projectId={projectId}
                  bug={current}
                  members={members.data}
                  membersError={members.error}
                  canAssign={canAssign}
                  onAssigned={handleBugUpdated}
                  onConflict={() => setConflict(true)}
                />
              </DetailRow>
              <DetailRow label="Priority">
                <PriorityBadge priority={current.priority} />
              </DetailRow>
              {current.resolution && <DetailRow label="Resolution">{resolutionLabel(current.resolution)}</DetailRow>}
              <DetailRow label="Reporter">
                <div className="font-medium">{current.reporter.fullName}</div>
                <div className="text-xs text-slate-500">{current.reporter.email}</div>
              </DetailRow>
              <DetailRow label="Created">
                <span title={formatUtc(current.createdAt)}>{formatDateTime(current.createdAt)}</span>
              </DetailRow>
              <DetailRow label="Last updated">
                <span title={formatUtc(current.updatedAt)}>{formatRelative(current.updatedAt)}</span>
              </DetailRow>
            </dl>
          </section>
        </aside>
      </div>

      {editing && (
        <EditBugModal
          projectId={projectId}
          bug={current}
          onClose={() => setEditing(false)}
          onSaved={(updated) => {
            setEditing(false);
            handleBugUpdated(updated);
          }}
          onConflict={() => setConflict(true)}
        />
      )}
      {archiving && (
        <ArchiveBugModal
          projectId={projectId}
          bug={current}
          onClose={() => setArchiving(false)}
          onArchived={() => navigate(`/projects/${projectId}`, { replace: true })}
        />
      )}
    </div>
  );
}
