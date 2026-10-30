import type { BugPriority, BugStatus, ProjectRole, Resolution, TimelineEntry } from '@/types/api';

// ---------------------------------------------------------------- labels

const STATUS_LABELS: Record<BugStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  REOPENED: 'Reopened',
};

const PRIORITY_LABELS: Record<BugPriority, string> = {
  BLOCKER: 'Blocker',
  CRITICAL: 'Critical',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
};

const RESOLUTION_LABELS: Record<Resolution, string> = {
  FIXED: 'Fixed',
  WONT_FIX: "Won't fix",
  DUPLICATE: 'Duplicate',
  CANNOT_REPRODUCE: 'Cannot reproduce',
  WORKS_AS_DESIGNED: 'Works as designed',
};

const ROLE_LABELS: Record<ProjectRole, string> = {
  PROJECT_LEAD: 'Project lead',
  DEVELOPER: 'Developer',
  REPORTER: 'Reporter',
};

/** hasOwnProperty lookup so values such as "constructor" can never resolve to inherited members. */
function lookup<K extends string>(map: Record<K, string>, key: string): string | undefined {
  return Object.prototype.hasOwnProperty.call(map, key) ? map[key as K] : undefined;
}

export function humanizeEnum(value: string): string {
  const text = value.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export const statusLabel = (status: BugStatus): string => STATUS_LABELS[status];
export const priorityLabel = (priority: BugPriority): string => PRIORITY_LABELS[priority];
export const resolutionLabel = (resolution: Resolution): string => RESOLUTION_LABELS[resolution];
export const roleLabel = (role: ProjectRole): string => ROLE_LABELS[role];

/** Severity colour family: Blocker and Critical share the crimson badge. */
export type SeverityTone = 'critical' | 'high' | 'medium' | 'low';

export function priorityTone(priority: BugPriority): SeverityTone {
  switch (priority) {
    case 'BLOCKER':
    case 'CRITICAL':
      return 'critical';
    case 'HIGH':
      return 'high';
    case 'MEDIUM':
      return 'medium';
    default:
      return 'low';
  }
}

// ---------------------------------------------------------------- dates (stored as UTC, shown in local time)

const DATE_TIME = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const DATE_ONLY = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });
const RELATIVE = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

function parse(iso: string | null | undefined): Date | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? DATE_TIME.format(date) : '\u2014';
}

export function formatDate(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? DATE_ONLY.format(date) : '\u2014';
}

/** Exact UTC instant for tooltips, e.g. "2026-10-12 08:30:00 UTC". */
export function formatUtc(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? `${date.toISOString().replace('T', ' ').replace(/\.\d+Z$/, '')} UTC` : '';
}

const RELATIVE_UNITS: ReadonlyArray<readonly [Intl.RelativeTimeFormatUnit, number]> = [
  ['year', 31_536_000],
  ['month', 2_592_000],
  ['week', 604_800],
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
];

export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  const date = parse(iso);
  if (!date) {
    return '\u2014';
  }
  const seconds = Math.round((date.getTime() - now) / 1000);
  for (const [unit, size] of RELATIVE_UNITS) {
    if (Math.abs(seconds) >= size) {
      return RELATIVE.format(Math.round(seconds / size), unit);
    }
  }
  return 'just now';
}

// ---------------------------------------------------------------- misc

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '\u2014';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** "Dana Developer (Alpha)" -> "DD". */
export function initials(fullName: string): string {
  const words = fullName
    .replace(/\(.*?\)/g, ' ')
    .split(/\s+/)
    .filter(Boolean);
  const letters = words.slice(0, 2).map((word) => word.charAt(0).toUpperCase());
  return letters.join('') || '?';
}

export function plural(count: number, singular: string, pluralForm: string = `${singular}s`): string {
  return `${count.toLocaleString()} ${count === 1 ? singular : pluralForm}`;
}

// ---------------------------------------------------------------- activity timeline wording

function truncate(text: string, max: number): string {
  return text.length <= max ? text : `${text.slice(0, max - 1)}\u2026`;
}

const quote = (text: string | null): string => (text ? `\u201C${truncate(text, 80)}\u201D` : '\u201C\u201D');

/** Renders a stored history value (enum name or free text) for display. */
function valueLabel(field: string | null, value: string | null): string {
  if (value === null || value === '') {
    return 'none';
  }
  switch (field) {
    case 'status':
      return lookup(STATUS_LABELS, value) ?? humanizeEnum(value);
    case 'priority':
      return lookup(PRIORITY_LABELS, value) ?? humanizeEnum(value);
    case 'resolution':
      return lookup(RESOLUTION_LABELS, value) ?? humanizeEnum(value);
    default:
      return truncate(value, 80);
  }
}

function describeUpdate(field: string | null, oldValue: string | null, newValue: string | null): string {
  switch (field) {
    case 'title':
      return `renamed this bug from ${quote(oldValue)} to ${quote(newValue)}`;
    case 'description':
      return 'edited the description';
    case 'priority':
      return `changed priority from ${valueLabel('priority', oldValue)} to ${valueLabel('priority', newValue)}`;
    case 'resolution':
      return newValue
        ? `set the resolution to ${valueLabel('resolution', newValue)}`
        : `cleared the resolution (was ${valueLabel('resolution', oldValue)})`;
    default:
      return `updated ${field ?? 'a field'}`;
  }
}

/** The sentence fragment that follows the actor's name in the timeline, e.g. "changed status from Open to Closed". */
export function describeHistory(entry: TimelineEntry): string {
  const { eventType, fieldName, oldValue, newValue } = entry;
  switch (eventType) {
    case 'CREATED':
      return 'created this bug';
    case 'STATUS_CHANGED':
      return `changed status from ${valueLabel('status', oldValue)} to ${valueLabel('status', newValue)}`;
    case 'ASSIGNED':
      return oldValue
        ? `reassigned this bug from ${oldValue} to ${newValue ?? 'nobody'}`
        : `assigned this bug to ${newValue ?? 'nobody'}`;
    case 'UNASSIGNED':
      return `unassigned ${oldValue ?? 'the assignee'}`;
    case 'UPDATED':
      return describeUpdate(fieldName, oldValue, newValue);
    case 'ATTACHMENT_ADDED':
      return `attached ${quote(newValue)}`;
    case 'ARCHIVED':
      return fieldName === 'attachment' ? `removed the attachment ${quote(oldValue)}` : 'archived this bug';
    case 'COMMENTED':
      return 'commented';
    default:
      return 'made a change';
  }
}
