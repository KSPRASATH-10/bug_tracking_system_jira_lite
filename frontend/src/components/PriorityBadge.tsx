import { priorityLabel, priorityTone, type SeverityTone } from '@/lib/format';
import type { BugPriority } from '@/types/api';

const TONES: Record<SeverityTone, { badge: string; dot: string }> = {
  critical: { badge: 'border-sev-critical/30 bg-sev-critical-tint text-sev-critical-ink', dot: 'bg-sev-critical' },
  high: { badge: 'border-sev-high/30 bg-sev-high-tint text-sev-high-ink', dot: 'bg-sev-high' },
  medium: { badge: 'border-sev-medium/30 bg-sev-medium-tint text-sev-medium-ink', dot: 'bg-sev-medium' },
  low: { badge: 'border-sev-low/30 bg-sev-low-tint text-sev-low-ink', dot: 'bg-sev-low' },
};

/**
 * Severity badge using the brief's palette: Blocker and Critical share crimson (#DC2626), High is amber
 * orange (#EA580C), Medium is cobalt (#2563EB), Low is slate (#475569). The label always carries the meaning,
 * never colour alone.
 */
export function PriorityBadge({ priority, className = '' }: { priority: BugPriority; className?: string }) {
  const tone = TONES[priorityTone(priority)];
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded border px-1.5 py-0.5 text-xs ${
        priority === 'BLOCKER' ? 'font-bold' : 'font-medium'
      } ${tone.badge} ${className}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${tone.dot}`} aria-hidden />
      {priorityLabel(priority)}
    </span>
  );
}
