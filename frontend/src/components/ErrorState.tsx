import { AlertTriangle, RefreshCw, SearchX, ShieldAlert, WifiOff, type LucideIcon } from 'lucide-react';
import { errorCorrelationId, errorMessage, isApiError } from '@/lib/api';

interface ErrorStateProps {
  error: unknown;
  /** Overrides the heading derived from the error. */
  title?: string;
  onRetry?: () => void;
}

function classify(error: unknown): { icon: LucideIcon; title: string; retryable: boolean } {
  if (isApiError(error)) {
    if (error.status === 0) {
      return { icon: WifiOff, title: 'Connection problem', retryable: true };
    }
    if (error.isForbidden) {
      return { icon: ShieldAlert, title: 'Access denied', retryable: false };
    }
    if (error.isNotFound) {
      return { icon: SearchX, title: 'Not found', retryable: false };
    }
  }
  return { icon: AlertTriangle, title: 'Something went wrong', retryable: true };
}

/** Distinct error state. Shows the server's correlation id so support can find the matching log line. */
export function ErrorState({ error, title, onRetry }: ErrorStateProps) {
  const { icon: Icon, title: derivedTitle, retryable } = classify(error);
  const correlationId = errorCorrelationId(error);

  return (
    <div role="alert" className="flex flex-col items-center justify-center px-4 py-10 text-center">
      <span className="flex h-11 w-11 items-center justify-center rounded-full bg-sev-critical-tint text-sev-critical">
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <h3 className="mt-3 text-sm font-semibold text-ink">{title ?? derivedTitle}</h3>
      <p className="mt-1 max-w-md text-xs text-slate-600">{errorMessage(error)}</p>
      {correlationId && (
        <p className="mt-2 text-[11px] text-slate-500">
          Reference: <span className="select-all font-mono">{correlationId}</span>
        </p>
      )}
      {onRetry && retryable && (
        <button type="button" className="btn-secondary mt-4" onClick={onRetry}>
          <RefreshCw className="h-4 w-4" aria-hidden />
          Try again
        </button>
      )}
    </div>
  );
}
