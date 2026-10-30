import { ChevronLeft, ChevronRight } from 'lucide-react';

const SIZE_OPTIONS = [10, 20, 50, 100] as const;

interface PaginationProps {
  /** Zero-based current page, exactly as the server returns it. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
  disabled?: boolean;
}

/** First, last and the neighbours of the current page; a single missing page is shown instead of a gap. */
function pageWindow(current: number, total: number): Array<number | 'gap'> {
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index);
  }
  const wanted = new Set<number>([0, total - 1, current - 1, current, current + 1]);
  const sorted = [...wanted].filter((page) => page >= 0 && page < total).sort((a, b) => a - b);
  const result: Array<number | 'gap'> = [];
  sorted.forEach((page, index) => {
    if (index > 0) {
      const previous = sorted[index - 1];
      if (page - previous === 2) {
        result.push(previous + 1);
      } else if (page - previous > 2) {
        result.push('gap');
      }
    }
    result.push(page);
  });
  return result;
}

export function Pagination({
  page,
  size,
  totalElements,
  totalPages,
  onPageChange,
  onSizeChange,
  disabled = false,
}: PaginationProps) {
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  const hasPrevious = page > 0;
  const hasNext = page + 1 < totalPages;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-2.5 text-xs text-slate-600">
      <div className="flex items-center gap-3">
        <span className="tabular-nums" aria-live="polite">
          {totalElements === 0
            ? 'No results'
            : `Showing ${from.toLocaleString()}\u2013${to.toLocaleString()} of ${totalElements.toLocaleString()}`}
        </span>
        {onSizeChange && (
          <label className="flex items-center gap-1.5">
            <span>Rows</span>
            <select
              className="input w-auto py-1 pr-7 text-xs"
              value={size}
              disabled={disabled}
              onChange={(event) => onSizeChange(Number(event.target.value))}
            >
              {SIZE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {totalPages > 1 && (
        <nav aria-label="Pagination" className="flex items-center gap-1">
          <button
            type="button"
            className="btn-icon"
            aria-label="Previous page"
            disabled={disabled || !hasPrevious}
            onClick={() => onPageChange(page - 1)}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden />
          </button>
          {pageWindow(page, totalPages).map((entry, index) =>
            entry === 'gap' ? (
              <span key={`gap-${index}`} className="px-1 text-slate-400" aria-hidden>
                &hellip;
              </span>
            ) : (
              <button
                key={entry}
                type="button"
                disabled={disabled}
                aria-label={`Page ${entry + 1}`}
                aria-current={entry === page ? 'page' : undefined}
                onClick={() => onPageChange(entry)}
                className={`h-8 min-w-8 rounded-md px-2 text-xs font-medium tabular-nums transition-colors ${
                  entry === page ? 'bg-midnight text-white' : 'text-ink hover:bg-surface-2'
                }`}
              >
                {entry + 1}
              </button>
            ),
          )}
          <button
            type="button"
            className="btn-icon"
            aria-label="Next page"
            disabled={disabled || !hasNext}
            onClick={() => onPageChange(page + 1)}
          >
            <ChevronRight className="h-4 w-4" aria-hidden />
          </button>
        </nav>
      )}
    </div>
  );
}
