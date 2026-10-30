import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { ResolutionModal } from '@/components/ResolutionModal';
import { StatusPill } from '@/components/StatusPill';
import { api, errorMessage, isApiError } from '@/lib/api';
import { statusLabel } from '@/lib/format';
import type { Bug, BugStatus, Resolution } from '@/types/api';

interface StatusTransitionProps {
  projectId: string;
  bug: Bug;
  onTransitioned: (bug: Bug) => void;
  /** Called when the server reports a stale version (HTTP 409). */
  onConflict: () => void;
}

/**
 * Shows the current status and ONLY the transitions the server says are legal for this caller
 * (`bug.allowedTransitions` is already filtered by the state machine and the project role). Moving to
 * CLOSED first asks for a resolution. The server re-validates every transition regardless.
 */
export function StatusTransition({ projectId, bug, onTransitioned, onConflict }: StatusTransitionProps) {
  const [target, setTarget] = useState<BugStatus | ''>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [askingResolution, setAskingResolution] = useState(false);
  const allowed = bug.allowedTransitions;

  async function perform(targetStatus: BugStatus, resolution?: Resolution) {
    setBusy(true);
    setError(null);
    try {
      const updated = await api.bugs.transition(projectId, bug.id, {
        targetStatus,
        resolution,
        version: bug.version,
      });
      setTarget('');
      setAskingResolution(false);
      onTransitioned(updated);
    } catch (failure) {
      if (isApiError(failure) && failure.isConcurrentModification) {
        setTarget('');
        setAskingResolution(false);
        onConflict();
      } else {
        setError(errorMessage(failure, 'The status could not be changed.'));
      }
    } finally {
      setBusy(false);
    }
  }

  function handleApply() {
    if (!target) {
      return;
    }
    if (target === 'CLOSED') {
      setError(null);
      setAskingResolution(true);
    } else {
      void perform(target);
    }
  }

  return (
    <div>
      <StatusPill status={bug.status} resolution={bug.resolution} />

      {allowed.length === 0 ? (
        <p className="mt-2 text-xs text-slate-500">No status changes are available to you for this bug.</p>
      ) : (
        <div className="mt-2 flex items-center gap-2">
          <label htmlFor="status-target" className="sr-only">
            Move this bug to another status
          </label>
          <select
            id="status-target"
            className="input py-1"
            value={target}
            disabled={busy}
            onChange={(event) => setTarget(allowed.find((status) => status === event.target.value) ?? '')}
          >
            <option value="">Move to…</option>
            {allowed.map((status) => (
              <option key={status} value={status}>
                {statusLabel(status)}
              </option>
            ))}
          </select>
          <button type="button" className="btn-primary" disabled={!target || busy} onClick={handleApply}>
            {busy && !askingResolution && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            Apply
          </button>
        </div>
      )}

      {error && !askingResolution && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}

      {askingResolution && (
        <ResolutionModal
          bugKey={bug.key}
          busy={busy}
          error={error}
          onCancel={() => {
            setAskingResolution(false);
            setError(null);
          }}
          onConfirm={(resolution) => void perform('CLOSED', resolution)}
        />
      )}
    </div>
  );
}
