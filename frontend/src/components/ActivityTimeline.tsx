import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  Archive,
  ArrowRightLeft,
  MessageSquare,
  Paperclip,
  Pencil,
  Plus,
  UserMinus,
  UserPlus,
  type LucideIcon,
} from 'lucide-react';
import { describeHistory, formatRelative, formatUtc, initials } from '@/lib/format';
import type { HistoryEventType, TimelineEntry } from '@/types/api';

// ---------------------------------------------------------------------------------------------
// Safe Markdown rendering (also used for bug and project descriptions)
// ---------------------------------------------------------------------------------------------

/** Link allow-list: http, https, mailto and relative references. Everything else is dropped. */
function safeUrl(url: string): string | null {
  const trimmed = url.trim();
  if (/^(https?:|mailto:)/i.test(trimmed)) {
    return trimmed;
  }
  if (/^[a-z][a-z0-9+.-]*:/i.test(trimmed) || trimmed.startsWith('//')) {
    return null; // javascript:, data:, vbscript:, file:, protocol-relative, ...
  }
  return trimmed;
}

const MARKDOWN_COMPONENTS: Components = {
  a: ({ href, children }) => {
    const external = typeof href === 'string' && /^(https?:|mailto:)/i.test(href);
    return external ? (
      <a href={href} target="_blank" rel="noopener noreferrer nofollow">
        {children}
      </a>
    ) : (
      <a href={href}>{children}</a>
    );
  },
  // Remote images would act as tracking pixels (and the CSP forbids them anyway), so none are rendered.
  img: ({ alt }) => <span className="italic text-slate-500">[image{alt ? `: ${alt}` : ''} not shown]</span>,
};

/**
 * Renders user Markdown. Raw HTML is never interpreted: without a rehype-raw plugin, react-markdown shows
 * HTML as inert text. The server has already neutralised tag openers and script-scheme links; this is
 * the second, independent layer.
 */
export function MarkdownView({ source, className = '' }: { source: string; className?: string }) {
  return (
    <div className={`md-content ${className}`}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS} urlTransform={safeUrl}>
        {source}
      </ReactMarkdown>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------------------------

const EVENT_ICONS: Record<HistoryEventType, LucideIcon> = {
  CREATED: Plus,
  STATUS_CHANGED: ArrowRightLeft,
  ASSIGNED: UserPlus,
  UNASSIGNED: UserMinus,
  UPDATED: Pencil,
  COMMENTED: MessageSquare,
  ATTACHMENT_ADDED: Paperclip,
  ARCHIVED: Archive,
};

function Time({ iso }: { iso: string }) {
  return (
    <time dateTime={iso} title={formatUtc(iso)} className="whitespace-nowrap text-xs text-slate-500">
      {formatRelative(iso)}
    </time>
  );
}

/**
 * Chronological, read-only feed of the immutable change ledger and the comments (oldest first). There are
 * deliberately no edit or delete controls: history is append-only on the server and in the database.
 */
export function ActivityTimeline({ entries }: { entries: TimelineEntry[] }) {
  if (entries.length === 0) {
    return <p className="text-xs text-slate-500">No activity yet.</p>;
  }
  return (
    <ol className="relative space-y-4 border-l border-line pl-6" aria-label="Activity timeline">
      {entries.map((entry) => {
        const isComment = entry.kind === 'COMMENT';
        const Icon = isComment ? MessageSquare : entry.eventType ? EVENT_ICONS[entry.eventType] : Pencil;
        return (
          <li key={`${entry.kind}-${entry.id}`} className="relative">
            <span
              className={`absolute -left-[2.125rem] top-0.5 flex h-5 w-5 items-center justify-center rounded-full border ${
                isComment ? 'border-brand/40 bg-brand-50 text-brand-700' : 'border-line bg-white text-slate-500'
              }`}
              aria-hidden
            >
              <Icon className="h-3 w-3" />
            </span>

            {isComment ? (
              <div className="rounded-lg border border-line bg-white">
                <div className="flex items-center justify-between gap-2 border-b border-line bg-surface px-3 py-1.5">
                  <span className="flex items-center gap-2 text-xs">
                    <span
                      className="flex h-5 w-5 items-center justify-center rounded-full bg-midnight text-[10px] font-semibold text-white"
                      aria-hidden
                    >
                      {initials(entry.actor.fullName)}
                    </span>
                    <span className="font-semibold text-ink">{entry.actor.fullName}</span>
                    <span className="text-slate-500">commented</span>
                  </span>
                  <Time iso={entry.createdAt} />
                </div>
                <div className="px-3 py-2.5">
                  <MarkdownView source={entry.body ?? ''} />
                </div>
              </div>
            ) : (
              <p className="flex flex-wrap items-baseline gap-x-1.5 text-sm leading-6 text-slate-700">
                <span className="font-semibold text-ink">{entry.actor.fullName}</span>
                <span className="break-words">{describeHistory(entry)}</span>
                <span aria-hidden className="text-slate-300">
                  &middot;
                </span>
                <Time iso={entry.createdAt} />
              </p>
            )}
          </li>
        );
      })}
    </ol>
  );
}
