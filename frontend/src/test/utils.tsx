import { MantineProvider } from '@mantine/core';
import { ModalsProvider } from '@mantine/modals';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { CompanyProvider } from '../app/CompanyContext';
import { AuthProvider } from '../auth/AuthContext';
import '../i18n';
import { theme } from '../theme';

/** Renders routes inside all app providers with a fresh query client. */
export function renderRoutes(routes: RouteObject[], initialPath = '/') {
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const utils = render(
    <MantineProvider env="test" theme={theme}>
      <Notifications />
      <ModalsProvider>
        <QueryClientProvider client={client}>
          <AuthProvider>
            <CompanyProvider>
              <RouterProvider router={router} />
            </CompanyProvider>
          </AuthProvider>
        </QueryClientProvider>
      </ModalsProvider>
    </MantineProvider>,
  );
  return { ...utils, router };
}

export function renderPage(element: ReactElement, path = '/') {
  return renderRoutes([{ path: '*', element }], path);
}

export type FetchHandler = (url: string, init: RequestInit) => Response | Promise<Response>;

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

/** Installs a fetch mock routing by "METHOD path" prefix; unmatched requests return 404. */
export function mockFetch(routes: Record<string, FetchHandler>) {
  const calls: { method: string; url: string; body?: unknown; headers: Record<string, string> }[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = typeof input === 'string' ? input : input.toString();
    const method = (init.method ?? 'GET').toUpperCase();
    const body = typeof init.body === 'string' ? JSON.parse(init.body) : init.body;
    calls.push({ method, url, body, headers: (init.headers ?? {}) as Record<string, string> });
    const path = url.replace(/^\/api\/v1/, '');
    const key = Object.keys(routes)
      .filter((k) => {
        const [m, p] = k.split(' ');
        return m === method && path.startsWith(p);
      })
      .sort((a, b) => b.length - a.length)[0];
    if (!key) return json({ code: 'NOT_FOUND', detail: `No mock for ${method} ${path}` }, 404);
    return routes[key](url, init);
  });
  vi.stubGlobal('fetch', fn);
  return { fn, calls };
}

export const adminMe = {
  id: 'u1',
  username: 'admin',
  displayName: 'Admin User',
  employeeId: null,
  mustChangePassword: false,
  roles: ['SUPER_ADMIN'],
  permissions: [
    'ORG:VIEW', 'ORG:ADD', 'ORG:EDIT', 'ORG:DELETE', 'ORG:CONFIGURE',
    'EMPLOYEE:VIEW', 'EMPLOYEE:ADD', 'EMPLOYEE:EDIT', 'EMPLOYEE:DELETE', 'EMPLOYEE:CONFIGURE',
    'ADMIN:VIEW', 'ADMIN:ADD', 'ADMIN:EDIT', 'ADMIN:CONFIGURE', 'AUDIT:VIEW',
  ],
  scopeType: 'ALL',
  scopeCompanyIds: [],
  scopeBranchIds: [],
  scopeDepartmentIds: [],
};
