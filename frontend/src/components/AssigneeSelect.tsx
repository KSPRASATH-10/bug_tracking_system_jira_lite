import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { api, errorMessage, isApiError } from '@/lib/api';
import { initials, roleLabel } from '@/lib/format';
import type { Bug, Member, ProjectRole } from '@/types/api';

/** Only developers and project leads may hold assignments; the server rejects anyone else. */
const ELIGIBLE: readonly ProjectRole[] = ['DEVELOPER', 'PROJECT_LEAD'];

interface AssigneeSelectProps {
  projectId: string;
  bug: Bug;
  /** Project members, or null while loading. */
  members: Member[] | null;
  membersError: unknown;
  /** Only project leads and administrators may assign; everyone else sees the assignee read-only. */
  canAssign: boolean;
  onAssigned: (bug: Bug) => void;
  onConflict: () => void;
}

export function AssigneeSelect({
  projectId,
  bug,
  members,
  membersError,
  canAssign,
  onAssigned,
  onConflict,
}: AssigneeSelectProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!canAssign) {
    return bug.assignee ? (
      <span className="inline-flex items-center gap-2">
        <span
          className="flex h-6 w-6 items-center justify-center rounded-full bg-surface-2 text-[11px] font-semibold text-ink"
          aria-hidden
        >
          {initials(bug.assignee.fullName)}
        </span>
        <span className="font-medium">{bug.assignee.fullName}</span>
      </span>
    ) : (
      <span className="text-slate-500">Unassigned</span>
    );
  }

  const eligible = (members ?? []).filter((member) => ELIGIBLE.includes(member.role));
  const assignee = bug.assignee;
  // The current assignee is always listed so the control reflects reality even if the member list is stale.
  const options: Array<{ id: string; label: string }> = eligible.map((member) => ({
    id: member.userId,
    label: `${member.fullName} (${roleLabel(member.role)})`,
  }));
  if (assignee && !options.some((option) => option.id === assignee.id)) {
    options.unshift({ id: assignee.id, label: assignee.fullName });
  }

  async function assign(assigneeId: string | null) {
    setBusy(true);
    setError(null);
    try {
      onAssigned(await api.bugs.assign(projectId, bug.id, { assigneeId, version: bug.version }));
    } catch (failure) {
      if (isApiError(failure) && failure.isConcurrentModification) {
        onConflict();
      } else {
        setError(errorMessage(failure, 'The assignee could not be changed.'));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <div className="flex items-center gap-2">
        <label htmlFor="assignee-select" className="sr-only">
          Assignee
        </label>
        <select
          id="assignee-select"
          className="input py-1"
          value={assignee?.id ?? ''}
          disabled={busy || members === null}
          onChange={(event) => {
            const id = event.target.value;
            if (id === '') {
              void assign(null);
            } else if (options.some((option) => option.id === id)) {
              void assign(id);
            }
          }}
        >
          <option value="">{members === null && !membersError ? 'Loading members…' : 'Unassigned'}</option>
          {options.map((option) => (
            <option key={option.id} value={option.id}>
              {option.label}
            </option>
          ))}
        </select>
        {busy && <Loader2 className="h-4 w-4 animate-spin text-brand" aria-label="Saving" />}
      </div>
      {membersError !== null && members === null && (
        <p role="alert" className="field-error">
          Project members could not be loaded.
        </p>
      )}
      {error && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
