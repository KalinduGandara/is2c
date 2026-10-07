import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderPage } from '../../test/utils';
import { AuditLogPanel } from '../admin/AuditLogPanel';
import { DashboardView } from './DashboardView';
import { ReportsPanel } from './ReportsPanel';

const catalog = [
  {
    key: 'daily-attendance', title: 'Daily attendance', params: [
      { name: 'companyId', label: 'Company', type: 'company', required: true },
      { name: 'date', label: 'Date', type: 'date', required: true },
      { name: 'departmentId', label: 'Department', type: 'department', required: false },
    ],
  },
  { key: 'employee-list', title: 'Employee list', params: [{ name: 'companyId', label: 'Company', type: 'company', required: true }] },
];
const table = {
  title: 'Daily attendance', headerLines: ['ACME Ltd', 'Date: 2026-03-16'],
  columns: [{ header: 'Employee No', type: 'TEXT' }, { header: 'Name', type: 'TEXT' }, { header: 'Late (min)', type: 'NUMBER' }],
  rows: [['E001', 'One Person', 15]], footer: null, landscape: true, generatedBy: 'admin', generatedAt: '2026-03-20T06:00:00Z',
};
const me = (perms: string[]) => ({ ...adminMe, permissions: [...adminMe.permissions, ...perms] });

describe('Reports, dashboard and audit UI', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-095 runs a report with its required parameters and shows the table', async () => {
    const { calls } = mockFetch({
      'GET /auth/me': () => json(me(['REPORT:VIEW', 'REPORT:EXPORT'])),
      'GET /companies/c1/departments': () => json([]),
      'GET /companies': () => json([]),
      'GET /reports/daily-attendance': () => json(table),
      'GET /reports': () => json(catalog),
    });
    renderPage(<ReportsPanel companyId="c1" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Daily attendance' }));
    const run = screen.getByRole('button', { name: 'Run' });
    expect(run).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/^Date/), '2026-03-16');
    await userEvent.click(run);
    expect(await screen.findByText('One Person')).toBeInTheDocument();
    expect(screen.getByText('Date: 2026-03-16')).toBeInTheDocument();
    const call = calls.find((c) => c.url.includes('/reports/daily-attendance'))!;
    expect(call.url).toContain('companyId=c1');
    expect(call.url).toContain('date=2026-03-16');
    expect(call.url).not.toContain('departmentId');
    expect(screen.getByRole('button', { name: 'Excel' })).toBeInTheDocument();
  });

  it('FR-095 hides exports without REPORT:EXPORT', async () => {
    mockFetch({
      'GET /auth/me': () => json(me(['REPORT:VIEW'])),
      'GET /companies': () => json([]),
      'GET /reports/employee-list': () => json({ ...table, title: 'Employee list' }),
      'GET /reports': () => json(catalog),
    });
    renderPage(<ReportsPanel companyId="c1" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Employee list' }));
    await userEvent.click(screen.getByRole('button', { name: 'Run' }));
    expect(await screen.findByText('One Person')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Excel' })).not.toBeInTheDocument();
  });

  it('FR-096 dashboard shows KPIs, headcount and payroll only when provided', async () => {
    mockFetch({
      'GET /auth/me': () => json(me([])),
      'GET /companies': () => json([]),
      'GET /dashboard': () => json({
        date: '2026-03-16', activeEmployees: 42, presentToday: 30, absentToday: 5, lateToday: 3, onLeaveToday: 4,
        pendingApprovals: 2, headcountByDepartment: { Operations: 30, Finance: 12 }, payroll: null,
      }),
    });
    renderPage(<DashboardView companyId="c1" />);
    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(screen.getByText('Late arrivals today')).toBeInTheDocument();
    expect(screen.getByText('Finance')).toBeInTheDocument();
    expect(screen.queryByText(/Payroll period/)).not.toBeInTheDocument();
  });

  it('FR-096 dashboard shows the payroll period and OT cost for payroll users', async () => {
    mockFetch({
      'GET /auth/me': () => json(me(['PAYROLL:VIEW'])),
      'GET /companies': () => json([]),
      'GET /dashboard': () => json({
        date: '2026-03-16', activeEmployees: 1, presentToday: 1, absentToday: 0, lateToday: 0, onLeaveToday: 0,
        pendingApprovals: 0, headcountByDepartment: {},
        payroll: { currentPeriod: { id: 'p1', year: 2026, month: 3, status: 'DRAFT' }, lastProcessedPeriod: { id: 'p1', year: 2026, month: 3, status: 'DRAFT' }, lastProcessedOtCost: 12500 },
      }),
    });
    renderPage(<DashboardView companyId="c1" />);
    expect(await screen.findByText('March 2026 — DRAFT')).toBeInTheDocument();
    expect(screen.getByText('12,500.00')).toBeInTheDocument();
  });

  it('FR-008 exports the filtered audit log', async () => {
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:x', revokeObjectURL: () => {} }));
    const { calls } = mockFetch({
      'GET /auth/me': () => json(me(['AUDIT:EXPORT'])),
      'GET /companies': () => json([]),
      'GET /audit/export': () => new Response('a,b', { headers: { 'Content-Disposition': 'attachment; filename="audit-log.csv"' } }),
      'GET /audit': () => json({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 25 }),
    });
    renderPage(<AuditLogPanel />);
    await userEvent.type(await screen.findByPlaceholderText(/Action/), 'approve');
    await userEvent.click(screen.getByRole('button', { name: 'Export CSV' }));
    await waitFor(() => expect(calls.some((c) => c.url.includes('/audit/export'))).toBe(true));
    const url = calls.find((c) => c.url.includes('/audit/export'))!.url;
    expect(url).toContain('format=csv');
    expect(url).toContain('action=APPROVE');
  });
});
