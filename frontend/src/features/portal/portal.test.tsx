import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderPage } from '../../test/utils';
import { MyPortalPage } from './MyPortalPage';

const employeeMe = { ...adminMe, username: 'nimal', roles: ['EMPLOYEE'], permissions: [], employeeId: 'e1', scopeType: 'SELF' };
const profile = {
  employee: { id: 'e1', companyId: 'c1', employeeNumber: 'E001', firstName: 'Nimal', lastName: 'Perera', personalEmail: 'n@example.com', mobile: null, joinDate: '2025-01-01', status: 'ACTIVE' },
  companyName: 'ACME Ltd', branchName: 'Colombo', departmentName: 'Operations', designationName: 'Engineer', reportingManagerName: 'Kamal Silva',
};
const routes = (extra: Parameters<typeof mockFetch>[0] = {}) => mockFetch({
  'GET /auth/me': () => json(employeeMe),
  'GET /companies': () => json([]),
  'GET /me/profile': () => json(profile),
  'GET /me/leave/balances': () => json([{ leaveTypeId: 't1', leaveTypeName: 'Annual leave', unlimited: false, opening: 0, entitled: 14, carriedForward: 0, adjustment: 0, taken: 2, pending: 1, expired: 0, available: 11 }]),
  'GET /me/leave/requests': () => json([{ id: 'l1', employeeId: 'e1', employeeNumber: 'E001', employeeName: 'Nimal Perera', leaveType: 'Annual leave', fromDate: '2026-04-02', toDate: '2026-04-02', halfDayPart: null, days: 1, status: 'PENDING', attachments: [] }]),
  'GET /me/payslips': () => json([{ periodId: 'p3', year: 2026, month: 3, payslipNo: 'PS-202603-000001', gross: 100000, net: 92000 }]),
  ...extra,
});

describe('Self-service portal', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-100 shows the own profile', async () => {
    routes();
    renderPage(<MyPortalPage />);
    expect(await screen.findByText('Nimal Perera')).toBeInTheDocument();
    expect(screen.getByText('Operations')).toBeInTheDocument();
    expect(screen.getByText('Kamal Silva')).toBeInTheDocument();
  });

  it('FR-100 shows balances and cancels an own pending leave', async () => {
    const { calls } = routes({ 'POST /me/leave/requests/l1/cancel': () => json({ status: 'CANCELLED' }) });
    renderPage(<MyPortalPage />);
    await userEvent.click(await screen.findByRole('tab', { name: 'Leave' }));
    expect(await screen.findByText('11')).toBeInTheDocument();
    const row = (await screen.findByText('2 Apr 2026')).closest('tr')!;
    expect(within(row).getByText('Pending')).toBeInTheDocument();
    await userEvent.click(within(row).getByRole('button', { name: 'Cancel' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel request' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url.endsWith('/me/leave/requests/l1/cancel'))).toBe(true));
  });

  it('FR-088 lists own payslips and downloads one', async () => {
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:x', revokeObjectURL: () => {} }));
    const { calls } = routes({ 'GET /me/payslips/p3.pdf': () => new Response('%PDF', { headers: { 'Content-Disposition': 'attachment; filename="p.pdf"' } }) });
    renderPage(<MyPortalPage />);
    await userEvent.click(await screen.findByRole('tab', { name: 'Payslips' }));
    expect(await screen.findByText('PS-202603-000001')).toBeInTheDocument();
    expect(screen.getByText('92,000.00')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Download March 2026' }));
    await waitFor(() => expect(calls.some((c) => c.url.endsWith('/me/payslips/p3.pdf'))).toBe(true));
  });
});
