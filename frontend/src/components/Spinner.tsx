import { Loader2 } from 'lucide-react';

const SIZES = { sm: 'h-4 w-4', md: 'h-5 w-5', lg: 'h-8 w-8' } as const;

export function Spinner({ size = 'md', label }: { size?: keyof typeof SIZES; label?: string }) {
  return (
    <span role="status" aria-live="polite" className="inline-flex items-center gap-2 text-slate-600">
      <Loader2 className={`${SIZES[size]} animate-spin text-brand`} aria-hidden />
      {label ? <span className="text-sm">{label}</span> : <span className="sr-only">Loading</span>}
    </span>
  );
}

/** Skeleton placeholder for a table or list region while its data loads. */
export function LoadingBlock({ label = 'Loading…', rows = 5 }: { label?: string; rows?: number }) {
  return (
    <div role="status" aria-busy="true" aria-live="polite" className="space-y-2 p-4">
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="skeleton h-8 w-full" aria-hidden />
      ))}
    </div>
  );
}
