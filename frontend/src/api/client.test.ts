import { ApiError, api, buildUrl, setUnauthorizedHandler, tokenStore } from './client';
import { json, mockFetch } from '../test/utils';

describe('buildUrl', () => {
  it('prefixes the API base and drops empty query values', () => {
    expect(buildUrl('/employees', { q: 'a b', page: 0, status: undefined, x: '', ids: ['1', '2'] })).toBe(
      '/api/v1/employees?q=a+b&page=0&ids=1&ids=2',
    );
  });
});

describe('api', () => {
  beforeEach(() => {
    tokenStore.clear();
    sessionStorage.clear();
  });
  afterEach(() => vi.unstubAllGlobals());

  it('NFR-009 parses problem+json into ApiError with field errors', async () => {
    mockFetch({
      'POST /employees': () =>
        json({ code: 'VALIDATION_FAILED', detail: 'Validation failed', errors: [{ field: 'lastName', message: 'is required' }], correlationId: 'c1' }, 422),
    });
    const error = (await api('/employees', { method: 'POST', body: {} }).catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(422);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.errors).toEqual([{ field: 'lastName', message: 'is required' }]);
    expect(error.correlationId).toBe('c1');
  });

  it('FR-002 refreshes once on 401 and retries with the new token', async () => {
    tokenStore.setTokens({ accessToken: 'old', refreshToken: 'r1', expiresIn: 900 });
    let refreshes = 0;
    const { calls } = mockFetch({
      'GET /auth/me': (_url, init) =>
        (init.headers as Record<string, string>).Authorization === 'Bearer new'
          ? json({ username: 'x' })
          : json({ code: 'UNAUTHENTICATED' }, 401),
      'POST /auth/refresh': () => {
        refreshes++;
        return json({ accessToken: 'new', refreshToken: 'r2', expiresIn: 900 });
      },
    });
    const [a, b] = await Promise.all([api<{ username: string }>('/auth/me'), api<{ username: string }>('/auth/me')]);
    expect(a.username).toBe('x');
    expect(b.username).toBe('x');
    expect(refreshes).toBe(1);
    expect(tokenStore.refreshToken()).toBe('r2');
    expect(calls.find((c) => c.url.endsWith('/auth/refresh'))?.body).toEqual({ refreshToken: 'r1' });
  });

  it('FR-002 clears the session and notifies when refresh fails', async () => {
    tokenStore.setTokens({ accessToken: 'old', refreshToken: 'r1', expiresIn: 900 });
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetch({
      'GET /auth/me': () => json({ code: 'UNAUTHENTICATED' }, 401),
      'POST /auth/refresh': () => json({ code: 'INVALID_REFRESH_TOKEN' }, 401),
    });
    await expect(api('/auth/me')).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalled();
    expect(tokenStore.hasSession()).toBe(false);
  });

  it('returns undefined for 204 responses', async () => {
    mockFetch({ 'DELETE /branches/1': () => new Response(null, { status: 204 }) });
    await expect(api('/branches/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});
