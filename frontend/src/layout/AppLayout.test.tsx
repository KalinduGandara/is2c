import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../api/client';
import { routes } from '../app/router';
import { adminMe, json, mockFetch, renderRoutes } from '../test/utils';

const company = { id: 'c1', code: 'ACME', legalName: 'Acme (Pvt) Ltd', currency: 'LKR', statutoryPack: 'LK', timeZone: 'Asia/Colombo', version: 0 };

describe('AppLayout', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-005 groups navigation by area and shows the pending approvals count', async () => {
    mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies': () => json([company]),
      'GET /approval/inbox': () => json([{ id: 't1' }, { id: 't2' }]),
      'GET /dashboard': () => json({ date: '2026-03-16', activeEmployees: 0, presentToday: 0, absentToday: 0, lateToday: 0, onLeaveToday: 0, pendingApprovals: 2, headcountByDepartment: {}, payroll: null }),
    });
    renderRoutes(routes, '/');
    const nav = await screen.findByRole('navigation', { name: 'Main navigation' });
    const people = within(nav).getByRole('group', { name: 'People' });
    expect(within(people).getByRole('link', { name: 'Employee Hub' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');
    expect(await within(nav).findByLabelText('2 pending')).toHaveTextContent('2');
    await waitFor(() => expect(document.title).toBe('Dashboard · Premium HRM'));
  });

  it('shows the working company and opens the account menu', async () => {
    mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies': () => json([company]),
      'GET /approval/inbox': () => json([]),
      'GET /dashboard': () => json({ date: '2026-03-16', activeEmployees: 0, presentToday: 0, absentToday: 0, lateToday: 0, onLeaveToday: 0, pendingApprovals: 0, headcountByDepartment: {}, payroll: null }),
    });
    renderRoutes(routes, '/');
    expect((await screen.findAllByText('Acme (Pvt) Ltd')).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'User menu' }));
    expect(await screen.findByRole('menuitem', { name: 'Sign out' })).toBeInTheDocument();
    expect(screen.getByText('@admin · Super admin')).toBeInTheDocument();
  });
});
