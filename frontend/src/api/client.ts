/**
 * HTTP client for the HRM API: bearer auth, transparent token refresh, and
 * RFC 9457 problem+json error parsing (NFR-009).
 */

export interface FieldError {
  field: string;
  message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: FieldError[];
  readonly correlationId?: string;

  constructor(status: number, code: string, message: string, errors: FieldError[] = [], correlationId?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.errors = errors;
    this.correlationId = correlationId;
  }
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

const REFRESH_KEY = 'hrm.refreshToken';
let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
let onUnauthorized: () => void = () => {};

function readRefresh(): string | null {
  try {
    return sessionStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

function writeRefresh(token: string | null) {
  try {
    if (token) sessionStorage.setItem(REFRESH_KEY, token);
    else sessionStorage.removeItem(REFRESH_KEY);
  } catch {
    // storage unavailable (private mode); session lasts for the page lifetime only
  }
}

export const tokenStore = {
  setTokens(tokens: Tokens) {
    accessToken = tokens.accessToken;
    writeRefresh(tokens.refreshToken);
  },
  clear() {
    accessToken = null;
    writeRefresh(null);
  },
  hasSession(): boolean {
    return accessToken !== null || readRefresh() !== null;
  },
  refreshToken(): string | null {
    return readRefresh();
  },
};

/** Called when the session cannot be recovered (refresh failed). */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export type QueryValue = string | number | boolean | null | undefined | string[];

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = path.startsWith('/api/') ? path : `/api/v1${path}`;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) value.forEach((v) => params.append(key, v));
    else params.append(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

export async function parseError(response: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    body = await response.json();
  } catch {
    // non-JSON error body
  }
  return new ApiError(
    response.status,
    typeof body.code === 'string' ? body.code : `HTTP_${response.status}`,
    typeof body.detail === 'string' ? body.detail : response.statusText || 'Request failed',
    Array.isArray(body.errors) ? (body.errors as FieldError[]) : [],
    typeof body.correlationId === 'string' ? body.correlationId : undefined,
  );
}

async function refreshSession(): Promise<boolean> {
  const refreshToken = readRefresh();
  if (!refreshToken) return false;
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch(buildUrl('/auth/refresh'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
        if (!res.ok) {
          tokenStore.clear();
          return false;
        }
        tokenStore.setTokens((await res.json()) as Tokens);
        return true;
      } catch {
        return false;
      } finally {
        refreshing = null;
      }
    })();
  }
  return refreshing;
}

/** Ensures an access token exists when only a refresh token survived (page reload). */
export async function ensureSession(): Promise<boolean> {
  if (accessToken) return true;
  return refreshSession();
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, QueryValue>;
  signal?: AbortSignal;
}

export async function apiFetch(path: string, options: RequestOptions = {}, retry = true): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  let body: BodyInit | undefined;
  if (options.body instanceof FormData) {
    body = options.body;
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const response = await fetch(buildUrl(path, options.query), {
    method: options.method ?? 'GET',
    headers,
    body,
    signal: options.signal,
  });
  if (response.status === 401 && retry && !path.startsWith('/auth/login')) {
    if (await refreshSession()) return apiFetch(path, options, false);
    tokenStore.clear();
    onUnauthorized();
  }
  if (!response.ok) throw await parseError(response);
  return response;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await apiFetch(path, options);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Downloads a binary response and triggers a browser save. */
export async function download(path: string, query?: Record<string, QueryValue>): Promise<void> {
  const response = await apiFetch(path, { query });
  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1]) : 'download';
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
