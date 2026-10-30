import { useState, type FormEvent } from 'react';
import { Modal } from '@/components/Modal';
import { api, errorMessage, isApiError } from '@/lib/api';
import { priorityLabel } from '@/lib/format';
import { BUG_PRIORITIES, LIMITS } from '@/types/api';
import type { Bug, BugPriority } from '@/types/api';

type FieldErrors = Partial<Record<'title' | 'description' | 'priority', string>>;

/** Client-side rules mirror CreateBugRequest (required title, max lengths) so most mistakes never reach the server. */
function validate(title: string, description: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!title.trim()) {
    errors.title = 'Title is required';
  } else if (title.trim().length > LIMITS.title) {
    errors.title = `Title must be at most ${LIMITS.title} characters`;
  }
  if (description.length > LIMITS.description) {
    errors.description = `Description must be at most ${LIMITS.description.toLocaleString()} characters`;
  }
  return errors;
}

function Counter({ value, max }: { value: number; max: number }) {
  return (
    <span className={`text-[11px] tabular-nums ${value > max ? 'font-semibold text-sev-critical-ink' : 'text-slate-500'}`}>
      {value.toLocaleString()}/{max.toLocaleString()}
    </span>
  );
}

interface CreateBugModalProps {
  projectId: string;
  onClose: () => void;
  onCreated: (bug: Bug) => void;
}

/** Mounted only while open, so the form starts empty every time. */
export function CreateBugModal({ projectId, onClose, onCreated }: CreateBugModalProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<BugPriority>('MEDIUM');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    const errors = validate(title, description);
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSubmitting(true);
    try {
      const created = await api.bugs.create(projectId, { title: title.trim(), description, priority });
      onCreated(created);
    } catch (error) {
      if (isApiError(error) && Object.keys(error.fieldErrors).length > 0) {
        setFieldErrors(error.fieldErrors as FieldErrors);
      } else {
        setFormError(errorMessage(error, 'The bug could not be created.'));
      }
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open
      size="lg"
      title="Report a bug"
      description="Describe what happened and how to reproduce it."
      onClose={onClose}
      busy={submitting}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" form="create-bug-form" className="btn-primary" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create bug'}
          </button>
        </>
      }
    >
      <form id="create-bug-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <div
            role="alert"
            className="rounded-md border border-sev-critical/30 bg-sev-critical-tint px-3 py-2 text-xs font-medium text-sev-critical-ink"
          >
            {formError}
          </div>
        )}

        <div>
          <div className="flex items-end justify-between">
            <label htmlFor="bug-title" className="label">
              Title
            </label>
            <Counter value={title.length} max={LIMITS.title} />
          </div>
          <input
            id="bug-title"
            data-autofocus
            className="input"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            aria-invalid={fieldErrors.title ? true : undefined}
            aria-describedby={fieldErrors.title ? 'bug-title-error' : undefined}
            disabled={submitting}
          />
          {fieldErrors.title && (
            <p id="bug-title-error" className="field-error">
              {fieldErrors.title}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="bug-priority" className="label">
            Priority
          </label>
          <select
            id="bug-priority"
            className="input"
            value={priority}
            onChange={(event) => {
              const next = BUG_PRIORITIES.find((value) => value === event.target.value);
              if (next) {
                setPriority(next);
              }
            }}
            aria-invalid={fieldErrors.priority ? true : undefined}
            disabled={submitting}
          >
            {BUG_PRIORITIES.map((value) => (
              <option key={value} value={value}>
                {priorityLabel(value)}
              </option>
            ))}
          </select>
          {fieldErrors.priority && <p className="field-error">{fieldErrors.priority}</p>}
        </div>

        <div>
          <div className="flex items-end justify-between">
            <label htmlFor="bug-description" className="label">
              Description <span className="font-normal text-slate-500">(optional)</span>
            </label>
            <Counter value={description.length} max={LIMITS.description} />
          </div>
          <textarea
            id="bug-description"
            className="input min-h-[10rem] resize-y font-mono text-[13px]"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            aria-invalid={fieldErrors.description ? true : undefined}
            aria-describedby={fieldErrors.description ? 'bug-description-error' : 'bug-description-hint'}
            disabled={submitting}
          />
          {fieldErrors.description ? (
            <p id="bug-description-error" className="field-error">
              {fieldErrors.description}
            </p>
          ) : (
            <p id="bug-description-hint" className="hint">
              Markdown is supported. Raw HTML is never rendered.
            </p>
          )}
        </div>
      </form>
    </Modal>
  );
}
