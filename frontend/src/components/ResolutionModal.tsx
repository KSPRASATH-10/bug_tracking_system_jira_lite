import { useState, type FormEvent } from 'react';
import { Modal } from '@/components/Modal';
import { resolutionLabel } from '@/lib/format';
import { RESOLUTIONS } from '@/types/api';
import type { Resolution } from '@/types/api';

const DESCRIPTIONS: Record<Resolution, string> = {
  FIXED: 'The defect has been corrected.',
  WONT_FIX: 'The report is valid but will not be addressed.',
  DUPLICATE: 'The same problem is already tracked in another bug.',
  CANNOT_REPRODUCE: 'The behaviour could not be reproduced.',
  WORKS_AS_DESIGNED: 'The system is behaving as intended.',
};

interface ResolutionModalProps {
  bugKey: string;
  busy: boolean;
  /** A server error from the failed attempt, shown inside the dialog. */
  error: string | null;
  onCancel: () => void;
  onConfirm: (resolution: Resolution) => void;
}

/** Closing a bug requires a resolution: the server and the database both enforce this rule too. */
export function ResolutionModal({ bugKey, busy, error, onCancel, onConfirm }: ResolutionModalProps) {
  const [selected, setSelected] = useState<Resolution | null>(null);
  const [missing, setMissing] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) {
      return;
    }
    if (!selected) {
      setMissing(true);
      return;
    }
    onConfirm(selected);
  }

  return (
    <Modal
      open
      size="sm"
      title={`Close ${bugKey}`}
      description="Choose how this bug was resolved."
      onClose={onCancel}
      busy={busy}
      footer={
        <>
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button type="submit" form="resolution-form" className="btn-primary" disabled={busy}>
            {busy ? 'Closing…' : 'Close bug'}
          </button>
        </>
      }
    >
      <form id="resolution-form" onSubmit={handleSubmit} noValidate>
        {error && (
          <div
            role="alert"
            className="mb-3 rounded-md border border-sev-critical/30 bg-sev-critical-tint px-3 py-2 text-xs font-medium text-sev-critical-ink"
          >
            {error}
          </div>
        )}
        <fieldset disabled={busy} aria-describedby={missing ? 'resolution-error' : undefined}>
          <legend className="label">Resolution</legend>
          <div className="space-y-1.5">
            {RESOLUTIONS.map((resolution, index) => (
              <label
                key={resolution}
                className={`flex cursor-pointer items-start gap-2.5 rounded-md border px-3 py-2 transition-colors ${
                  selected === resolution ? 'border-brand bg-brand-50' : 'border-line hover:bg-surface'
                }`}
              >
                <input
                  type="radio"
                  name="resolution"
                  value={resolution}
                  data-autofocus={index === 0 ? true : undefined}
                  className="mt-0.5 h-4 w-4 accent-brand-600"
                  checked={selected === resolution}
                  onChange={() => {
                    setSelected(resolution);
                    setMissing(false);
                  }}
                />
                <span>
                  <span className="block text-sm font-medium text-ink">{resolutionLabel(resolution)}</span>
                  <span className="block text-xs text-slate-600">{DESCRIPTIONS[resolution]}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        {missing && (
          <p id="resolution-error" role="alert" className="field-error">
            Choose a resolution to close this bug
          </p>
        )}
      </form>
    </Modal>
  );
}
