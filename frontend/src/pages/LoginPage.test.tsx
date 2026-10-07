import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../app/router';
import { adminMe, json, mockFetch, renderRoutes } from '../test/utils';
import { tokenStore } from '../api/client';

describe('LoginPage', () => {
  beforeEach(() => {
    tokenStore.clear();
    sessionStorage.clear();
  });
  afterEach(() => vi.unstubAllGlobals());

  it('FR-001 requires username and password', async () => {
    mockFetch({});
    renderRoutes(routes, '/login');
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(await screen.findAllByText('Required')).toHaveLength(2);
  });

  it('FR-001 shows the server message for invalid credentials', async () => {
    mockFetch({ 'POST /auth/login': () => json({ code: 'INVALID_CREDENTIALS', detail: 'Invalid username or password' }, 401) });
    renderRoutes(routes, '/login');
    await userEvent.type(await screen.findByLabelText('Username'), 'bob');
    await userEvent.type(screen.getByLabelText('Password'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password');
  });

  it('FR-001/FR-003 signs in and redirects to change password when required', async () => {
    mockFetch({
      'POST /auth/login': () => json({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }),
      'GET /auth/me': () => json({ ...adminMe, mustChangePassword: true }),
      'GET /companies': () => json([]),
    });
    const { router } = renderRoutes(routes, '/login');
    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'Secret#123456');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/change-password'));
    expect(await screen.findByText('You must change your password before continuing.')).toBeInTheDocument();
  });

  it('FR-003 change-password form checks confirmation before calling the API', async () => {
    tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 });
    const { calls } = mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies': () => json([]),
    });
    renderRoutes(routes, '/change-password');
    await userEvent.type(await screen.findByLabelText('Current password'), 'Old#Password1');
    await userEvent.type(screen.getByLabelText('New password'), 'New#Password1');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'Different#1');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();
    expect(calls.some((c) => c.url.includes('change-password'))).toBe(false);
  });
});
