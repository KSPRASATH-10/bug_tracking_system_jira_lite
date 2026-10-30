import { useCallback, useEffect, useRef, useState } from 'react';
import type {
  ApiErrorBody,
  AssignPayload,
  Attachment,
  Bug,
  BugListQuery,
  BugSummary,
  Comment,
  CreateBugPayload,
  CreateProjectPayload,
  Dashboard,
  Member,
  Page,
  Project,
  TimelineEntry,
  TransitionPayload,
  UpdateBugPayload,
  UpsertMemberPayload,
  User,
} from '@/types/api';

/**
 * Thin, typed HTTP layer. Security properties:
 *  - Authentication is the HttpOnly cookie only. There is no Authorization header and no token in JS.
 *  - Nothing is persisted client-side (no localStorage / sessionStorage).
 *  - Every failure becomes an ApiRequestError carrying the server's code and correlation id.
 *  - A 401 on any call other than the session probe/login signals "session expired" to the AuthContext.
 */
const API_BASE = String(import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/+$/, '');
const CREDENTIALS: RequestCredentials = /^https?:\/\//i.test(API_BASE) ? 'include' : 'same-origin';

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly correlationId: string | null;
  readonly fieldErrors: Record<string, string>;

  constructor(
    status: number,
    code: string,
    message: string,
    correlationId: string | null,
    fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
    this.fieldErrors = fieldErrors;
  }

  /** Stale version: someone else changed the bug first (HTTP 409 CONCURRENT_MODIFICATION). */
  get isConcurrentModification(): boolean {
    return this.status === 409 && this.code === 'CONCURRENT_MODIFICATION';
  }
  get isForbidden(): boolean {
    return this.status === 403;
  }
  get isNotFound(): boolean {
    return this.status === 404;
  }
  get isUnauthenticated(): boolean {
    return this.status === 401;
  }
}

export function isApiError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError;
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

/** User-facing message for any thrown value. Server messages are service-authored and safe to display. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (isApiError(error)) {
    return error.message || fallback;
  }
  return fallback;
}

export function errorCorrelationId(error: unknown): string | null {
  return isApiError(error) ? error.correlationId : null;
}

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

/** Registered by AuthProvider so a mid-session 401 clears the user and the route guard redirects to login. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

type QueryValue = string | number | boolean | null | undefined;

interface RequestOptions {
  query?: Record<string, QueryValue>;
  body?: unknown;
  formData?: FormData;
  signal?: AbortSignal;
  /** Do not treat a 401 as "session expired" (used by the session probe and by login). */
  skipAuthRedirect?: boolean;
}

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`;
  if (!query) {
    return url;
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    params.set(key, String(value));
  }
  const queryString = params.toString();
  return queryString ? `${url}?${queryString}` : url;
}

function isErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiErrorBody).code === 'string' &&
    typeof (value as ApiErrorBody).message === 'string'
  );
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Your session is not valid. Please sign in again.';
  if (status === 403) return 'You do not have permission to perform this action.';
  if (status === 404) return 'The requested item could not be found.';
  if (status === 429) return 'Too many requests. Please wait a moment and try again.';
  if (status >= 500) return 'The server had a problem handling this request. Please try again shortly.';
  return 'The request could not be completed.';
}

function toApiError(response: Response, parsed: unknown): ApiRequestError {
  const correlationId =
    (isErrorBody(parsed) ? parsed.correlationId : null) ?? response.headers.get('X-Correlation-Id');
  if (isErrorBody(parsed)) {
    let message = parsed.message;
    const retryAfter = Number(response.headers.get('Retry-After'));
    if (response.status === 429 && Number.isFinite(retryAfter) && retryAfter > 0) {
      message = `Too many attempts. Please wait ${retryAfter} second${retryAfter === 1 ? '' : 's'} and try again.`;
    }
    return new ApiRequestError(response.status, parsed.code, message, correlationId, parsed.fieldErrors ?? {});
  }
  return new ApiRequestError(response.status, `HTTP_${response.status}`, defaultMessage(response.status), correlationId);
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  let body: BodyInit | undefined;
  if (options.formData) {
    body = options.formData; // the browser sets the multipart boundary itself
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      body,
      credentials: CREDENTIALS,
      signal: options.signal,
    });
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }
    throw new ApiRequestError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection and try again.', null);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let parsed: unknown = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = null; // e.g. an HTML error page from a proxy: never rendered, only mapped to a safe message
    }
  }

  if (!response.ok) {
    const error = toApiError(response, parsed);
    if (response.status === 401 && !options.skipAuthRedirect) {
      unauthorizedHandler?.();
    }
    throw error;
  }
  return parsed as T;
}

const seg = encodeURIComponent;
const bugPath = (projectId: string, bugId: string) => `/projects/${seg(projectId)}/bugs/${seg(bugId)}`;

export const api = {
  auth: {
    login: (email: string, password: string) =>
      request<User>('POST', '/auth/login', { body: { email, password }, skipAuthRedirect: true }),
    logout: () => request<void>('POST', '/auth/logout', { skipAuthRedirect: true }),
    me: (signal?: AbortSignal) => request<User>('GET', '/auth/me', { signal, skipAuthRedirect: true }),
  },

  dashboard: {
    get: (signal?: AbortSignal) => request<Dashboard>('GET', '/dashboard', { signal }),
  },

  projects: {
    list: (signal?: AbortSignal) => request<Project[]>('GET', '/projects', { signal }),
    get: (projectId: string, signal?: AbortSignal) =>
      request<Project>('GET', `/projects/${seg(projectId)}`, { signal }),
    create: (payload: CreateProjectPayload) => request<Project>('POST', '/projects', { body: payload }),
    archive: (projectId: string) => request<void>('DELETE', `/projects/${seg(projectId)}`),
    members: (projectId: string, signal?: AbortSignal) =>
      request<Member[]>('GET', `/projects/${seg(projectId)}/members`, { signal }),
    upsertMember: (projectId: string, payload: UpsertMemberPayload) =>
      request<Member>('PUT', `/projects/${seg(projectId)}/members`, { body: payload }),
    removeMember: (projectId: string, userId: string) =>
      request<void>('DELETE', `/projects/${seg(projectId)}/members/${seg(userId)}`),
  },

  bugs: {
    list: (projectId: string, query: BugListQuery, signal?: AbortSignal) =>
      request<Page<BugSummary>>('GET', `/projects/${seg(projectId)}/bugs`, {
        query: { ...query },
        signal,
      }),
    get: (projectId: string, bugId: string, signal?: AbortSignal) =>
      request<Bug>('GET', bugPath(projectId, bugId), { signal }),
    create: (projectId: string, payload: CreateBugPayload) =>
      request<Bug>('POST', `/projects/${seg(projectId)}/bugs`, { body: payload }),
    update: (projectId: string, bugId: string, payload: UpdateBugPayload) =>
      request<Bug>('PATCH', bugPath(projectId, bugId), { body: payload }),
    transition: (projectId: string, bugId: string, payload: TransitionPayload) =>
      request<Bug>('POST', `${bugPath(projectId, bugId)}/transition`, { body: payload }),
    assign: (projectId: string, bugId: string, payload: AssignPayload) =>
      request<Bug>('PUT', `${bugPath(projectId, bugId)}/assignee`, { body: payload }),
    archive: (projectId: string, bugId: string) => request<void>('DELETE', bugPath(projectId, bugId)),
    timeline: (projectId: string, bugId: string, signal?: AbortSignal) =>
      request<TimelineEntry[]>('GET', `${bugPath(projectId, bugId)}/timeline`, { signal }),
  },

  comments: {
    add: (projectId: string, bugId: string, body: string) =>
      request<Comment>('POST', `${bugPath(projectId, bugId)}/comments`, { body: { body } }),
  },

  attachments: {
    list: (projectId: string, bugId: string, signal?: AbortSignal) =>
      request<Attachment[]>('GET', `${bugPath(projectId, bugId)}/attachments`, { signal }),
    upload: (projectId: string, bugId: string, file: File) => {
      const formData = new FormData();
      formData.append('file', file);
      return request<Attachment>('POST', `${bugPath(projectId, bugId)}/attachments`, { formData });
    },
    archive: (projectId: string, bugId: string, attachmentId: string) =>
      request<void>('DELETE', `${bugPath(projectId, bugId)}/attachments/${seg(attachmentId)}`),
  },
};

/**
 * Download link for an attachment. The server always answers with `Content-Disposition: attachment` and
 * `nosniff`, so following this link saves the file and never renders or executes it in the browser.
 */
export function attachmentContentUrl(projectId: string, bugId: string, attachmentId: string): string {
  return buildUrl(`${bugPath(projectId, bugId)}/attachments/${seg(attachmentId)}/content`);
}

// ---------------------------------------------------------------------------------------------
// Data-loading hook
// ---------------------------------------------------------------------------------------------

export interface AsyncState<T> {
  data: T | null;
  error: unknown;
  loading: boolean;
  /** Re-runs the loader and keeps the current data on screen while it runs. */
  reload: () => void;
}

interface UseApiOptions {
  /** Keep showing the previous result while the inputs change (pagination, filters) instead of a skeleton. */
  keepPreviousData?: boolean;
}

/**
 * Runs `loader` on mount and whenever `deps` change. In-flight requests are aborted on change/unmount, so a
 * slow response can never overwrite a newer one. An aborted request is never reported as an error.
 */
export function useApi<T>(
  loader: (signal: AbortSignal) => Promise<T>,
  deps: readonly unknown[],
  options: UseApiOptions = {},
): AsyncState<T> {
  const [state, setState] = useState<{ data: T | null; error: unknown; loading: boolean }>({
    data: null,
    error: null,
    loading: true,
  });
  const [reloadToken, setReloadToken] = useState(0);
  const loaderRef = useRef(loader);
  const depsRef = useRef<readonly unknown[]>(deps);
  const keepPrevious = options.keepPreviousData === true;

  useEffect(() => {
    loaderRef.current = loader;
  });

  useEffect(() => {
    const controller = new AbortController();
    const depsChanged =
      depsRef.current.length !== deps.length || deps.some((dep, index) => !Object.is(dep, depsRef.current[index]));
    depsRef.current = deps;

    // A plain reload keeps the data; changed inputs drop it unless the caller asked to keep it.
    setState((previous) => ({
      data: depsChanged && !keepPrevious ? null : previous.data,
      error: null,
      loading: true,
    }));

    loaderRef.current(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setState({ data, error: null, loading: false });
        }
      },
      (error: unknown) => {
        if (controller.signal.aborted || isAbortError(error)) {
          return;
        }
        setState((previous) => ({ data: previous.data, error, loading: false }));
      },
    );
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken]);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);
  return { ...state, reload };
}
