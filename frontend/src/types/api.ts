// Hand-maintained mirror of the backend DTOs (web/dto). Nullable server fields are `| null`.

export type SystemRole = 'ADMINISTRATOR' | 'USER';
export type ProjectRole = 'PROJECT_LEAD' | 'DEVELOPER' | 'REPORTER';
export type BugStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'REOPENED';
export type BugPriority = 'BLOCKER' | 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type Resolution = 'FIXED' | 'WONT_FIX' | 'DUPLICATE' | 'CANNOT_REPRODUCE' | 'WORKS_AS_DESIGNED';
export type HistoryEventType =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'ASSIGNED'
  | 'UNASSIGNED'
  | 'UPDATED'
  | 'COMMENTED'
  | 'ATTACHMENT_ADDED'
  | 'ARCHIVED';

/** Workflow order, used to render filters and dashboard tiles consistently. */
export const BUG_STATUSES: readonly BugStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REOPENED'];
/** Most severe first. */
export const BUG_PRIORITIES: readonly BugPriority[] = ['BLOCKER', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
export const RESOLUTIONS: readonly Resolution[] = [
  'FIXED',
  'WONT_FIX',
  'DUPLICATE',
  'CANNOT_REPRODUCE',
  'WORKS_AS_DESIGNED',
];

/** Server-side limits mirrored by client-side validation (see CreateBugRequest / CommentRequest / V1 schema). */
export const LIMITS = {
  title: 200,
  description: 20000,
  comment: 5000,
  fileBytes: 5 * 1024 * 1024,
} as const;

export interface User {
  id: string;
  email: string;
  fullName: string;
  systemRole: SystemRole;
}

export interface Project {
  id: string;
  projectKey: string;
  name: string;
  description: string | null;
  archived: boolean;
  /** Null for an administrator who is not an explicit member of the project. */
  myRole: ProjectRole | null;
  createdAt: string;
}

export interface Member {
  userId: string;
  email: string;
  fullName: string;
  role: ProjectRole;
}

export interface BugSummary {
  id: string;
  key: string;
  projectId: string;
  bugNumber: number;
  title: string;
  status: BugStatus;
  priority: BugPriority;
  resolution: Resolution | null;
  reporterName: string;
  assigneeId: string | null;
  assigneeName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Bug {
  id: string;
  key: string;
  projectId: string;
  projectKey: string;
  bugNumber: number;
  title: string;
  description: string;
  status: BugStatus;
  priority: BugPriority;
  resolution: Resolution | null;
  reporter: User;
  assignee: User | null;
  /** Optimistic-lock token: echo it on every write; a stale value yields HTTP 409. */
  version: number;
  /** Already filtered by the state machine AND the caller's project role. */
  allowedTransitions: BugStatus[];
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Comment {
  id: string;
  bugId: string;
  author: User;
  body: string;
  createdAt: string;
}

export interface TimelineEntry {
  kind: 'HISTORY' | 'COMMENT';
  id: string;
  createdAt: string;
  actor: User;
  eventType: HistoryEventType | null;
  fieldName: string | null;
  oldValue: string | null;
  newValue: string | null;
  /** Comment text (already sanitised server-side); null for HISTORY entries. */
  body: string | null;
}

export interface Attachment {
  id: string;
  bugId: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  uploader: User;
  createdAt: string;
}

export interface Dashboard {
  accessibleProjects: number;
  bugsByStatus: Record<BugStatus, number>;
  openAssignedToMe: number;
  myOpenBugs: BugSummary[];
}

/** The uniform error envelope returned by every failing API call. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  correlationId: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

export type SortField = 'createdAt' | 'updatedAt' | 'title' | 'status' | 'priority' | 'bugNumber';
export type SortDirection = 'asc' | 'desc';

export interface BugListQuery {
  status?: BugStatus;
  priority?: BugPriority;
  assigneeId?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: SortField;
  direction?: SortDirection;
}

export interface CreateBugPayload {
  title: string;
  description: string;
  priority: BugPriority;
}

export interface UpdateBugPayload {
  title?: string;
  description?: string;
  priority?: BugPriority;
  version: number;
}

export interface TransitionPayload {
  targetStatus: BugStatus;
  resolution?: Resolution;
  version: number;
}

export interface AssignPayload {
  assigneeId: string | null;
  version: number;
}

export interface CreateProjectPayload {
  projectKey: string;
  name: string;
  description?: string;
}

export interface UpsertMemberPayload {
  email: string;
  role: ProjectRole;
}
