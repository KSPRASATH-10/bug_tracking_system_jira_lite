import { resolutionLabel, statusLabel } from '@/lib/format';
import type { BugStatus, Resolution } from '@/types/api';

// Every class name is a literal string so Tailwind's scanner always sees it.
const TONES: Record<BugStatus, { pill: string; dot: string }> = {
  OPEN: {
    pill: 'border-status-open/30 bg-status-open-tint text-status-open-ink',
    dot: 'bg-status-open',
  },
  IN_PROGRESS: {
    pill: 'border-status-progress/30 bg-status-progress-tint text-status-progress-ink',
    dot: 'bg-status-progress',
  },
  RESOLVED: {
    pill: 'border-status-resolved/30 bg-status-resolved-tint text-status-resolved-ink',
    dot: 'bg-status-resolved',
  },
  CLOSED: {
    pill: 'border-status-closed/30 bg-status-closed-tint text-status-closed-ink',
    dot: 'bg-status-closed',
  },
  REOPENED: {
    pill: 'border-status-reopened/30 bg-status-reopened-tint text-status-reopened-ink',
    dot: 'bg-status-reopened',
  },
};

export const statusDotClass = (status: BugStatus): string => TONES[status].dot;

interface StatusPillProps {
  status: BugStatus;
  /** When the bug is CLOSED, the resolution is appended ("Closed · Fixed"). */
  resolution?: Resolution | null;
  className?: string;
}

export function StatusPill({ status, resolution, className = '' }: StatusPillProps) {
  const tone = TONES[status];
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2 py-0.5 text-xs font-medium ${tone.pill} ${className}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${tone.dot}`} aria-hidden />
      {statusLabel(status)}
      {status === 'CLOSED' && resolution ? (
        <span className="font-normal opacity-80">&middot; {resolutionLabel(resolution)}</span>
      ) : null}
    </span>
  );
}
