import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderPage, renderRoutes } from '../../test/utils';
import { ComponentsPanel } from './ComponentsPanel';
import { PeriodPage } from './PeriodPage';
import { SalaryStructurePanel } from './SalaryStructurePanel';

const perms = [...adminMe.permissions, 'PAYROLL:VIEW', 'PAYROLL:ADD', 'PAYROLL:EDIT', 'PAYROLL:APPROVE', 'PAYROLL:EXPORT', 'PAYROLL:CONFIGURE'];
const period = (over: Record<string, unknown> = {}) => ({
  id: 'p1', companyId: 'c1', year: 2026, month: 3, startDate: '2026-03-01', endDate: '2026-03-31', status: 'DRAFT',
  stale: false, approvalId: null, processedAt: '2026-04-05T06:00:00Z', finalizedAt: null, version: 1, ...over,
});
const result = {
  id: 'r1', employeeId: 'e1', employeeNumber: 'E001', employeeName: 'Nimal Perera', departmentName: 'Ops', designationName: 'SE',
  gross: 100000, deductions: 8000, net: 92000, employerContributions: 15000, workedDays: 22, noPayDays: 0, otHours: 0,
  payslipNo: null, locked: false,
};
const totals = { gross: 100000, deductions: 8000, net: 92000, employerContributions: 15000, employees: 1, byComponent: { BASIC: 100000 }, netByDepartment: { Ops: 92000 } };

function periodRoutes(p: ReturnType<typeof period>, extra: Record<string, Parameters<typeof mockFetch>[0][string]> = {}) {
  return mockFetch({
    'GET /auth/me': () => json({ ...adminMe, permissions: perms }),
    'GET /companies': () => json([]),
    'GET /payroll/periods/p1/results': () => json([result]),
    'GET /payroll/periods/p1/exceptions': () => json([]),
    'GET /payroll/periods/p1/totals': () => json(totals),
    'GET /payroll/periods/p1/items': () => json([]),
    'GET /payroll/periods/p1': () => json(p),
    ...extra,
  });
}

const renderPeriod = () => renderRoutes([{ path: '/payroll/periods/:id', element: <PeriodPage /> }], '/payroll/periods/p1');

describe('Payroll UI', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-086 a stale draft cannot be submitted until reprocessed', async () => {
    periodRoutes(period({ stale: true }));
    renderPeriod();
    expect(await screen.findByText(/changed since it was processed/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Submit for review' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Reprocess' })).toBeEnabled();
  });

  it('FR-087 shows the period heading, status and current workflow step', async () => {
    periodRoutes(period());
    renderPeriod();
    expect(await screen.findByRole('heading', { name: 'Payroll — March 2026' })).toBeInTheDocument();
    expect(screen.getByText('Draft')).toBeInTheDocument();
    const progress = screen.getByRole('list', { name: 'Payroll progress' });
    expect(within(progress).getByText('Process').closest('li')).toHaveAttribute('aria-current', 'step');
    expect(screen.getByRole('cell', { name: '92,000.00' })).toBeInTheDocument();
  });

  it('FR-087 submits a processed draft for review', async () => {
    const { calls } = periodRoutes(period(), { 'POST /payroll/periods/p1/submit': () => json(period({ status: 'IN_REVIEW' })) });
    renderPeriod();
    expect((await screen.findAllByText('92,000.00')).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Submit for review' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url.endsWith('/payroll/periods/p1/submit'))).toBe(true));
  });

  it('FR-087 sending back requires a comment', async () => {
    const { calls } = periodRoutes(period({ status: 'IN_REVIEW', approvalId: 't1' }), {
      'POST /payroll/periods/p1/send-back': () => json(period()),
    });
    renderPeriod();
    await userEvent.click(await screen.findByRole('button', { name: 'Send back' }));
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', { name: 'Send back' });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText(/Comment/), 'missed bonus');
    await userEvent.click(confirm);
    await waitFor(() => expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ comment: 'missed bonus' }));
    expect(screen.queryByRole('button', { name: 'Finalize' })).not.toBeInTheDocument();
  });

  it('FR-088 emails payslips of a finalized period and shows per-recipient results', async () => {
    periodRoutes(period({ status: 'FINALIZED', finalizedAt: '2026-04-06T06:00:00Z' }), {
      'POST /payroll/periods/p1/payslips/email': () => json({
        sent: 1, failed: 1, results: [
          { employeeId: 'e1', employeeNumber: 'E001', employeeName: 'Nimal Perera', status: 'SENT' },
          { employeeId: 'e2', employeeNumber: 'E002', employeeName: 'Kamal Silva', status: 'NO_EMAIL' },
        ],
      }),
    });
    renderPeriod();
    await userEvent.click(await screen.findByRole('tab', { name: 'Payslips & exports' }));
    expect(screen.getByRole('button', { name: 'Payroll register (Excel)' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Email payslips' }));
    expect(await screen.findByText('No email')).toBeInTheDocument();
    expect(screen.getByText('Kamal Silva')).toBeInTheDocument();
  });

  it('FR-080 the component form shows the fields of the chosen calculation type', async () => {
    mockFetch({
      'GET /auth/me': () => json({ ...adminMe, permissions: perms }),
      'GET /companies': () => json([]),
      'GET /companies/c1/payroll-components': () => json([]),
      'GET /companies/c1/slab-tables': () => json([]),
    });
    renderPage(<ComponentsPanel companyId="c1" />);
    await userEvent.click(await screen.findByRole('button', { name: /Create/ }));
    expect(screen.queryByLabelText(/Rate %/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('combobox', { name: /Calculation/ }));
    await userEvent.click(await screen.findByRole('option', { name: 'Percentage of' }));
    expect(screen.getByLabelText(/Rate %/)).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: /Slab table/ })).not.toBeInTheDocument();
  });

  it('FR-082 creates a salary structure with basic salary and component lines', async () => {
    const { calls } = mockFetch({
      'GET /auth/me': () => json({ ...adminMe, permissions: perms }),
      'GET /companies': () => json([]),
      'GET /employees/e1/salary-structures': () => json([]),
      'GET /companies/c1/payroll-components': () => json([
        { id: 'k1', code: 'ALLOWANCE', name: 'Allowance', kind: 'EARNING', calcType: 'FIXED', assignment: 'PER_EMPLOYEE', frequency: 'RECURRING', active: true, systemComponent: false },
        { id: 'k2', code: 'BASIC', name: 'Basic salary', kind: 'EARNING', calcType: 'FIXED', assignment: 'PER_EMPLOYEE', frequency: 'RECURRING', active: true, systemComponent: true },
      ]),
      'POST /employees/e1/salary-structures': () => json({ id: 's1' }, 201),
    });
    renderPage(<SalaryStructurePanel employeeId="e1" companyId="c1" />);
    await userEvent.click(await screen.findByRole('button', { name: 'New structure' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/Effective from/), '2026-04-01');
    await userEvent.type(within(dialog).getByLabelText(/Basic salary/), '100000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add component' }));
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Component 1' }));
    await userEvent.click(await screen.findByRole('option', { name: 'ALLOWANCE — Allowance' }));
    await userEvent.type(within(dialog).getByLabelText('Amount 1'), '5000');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')!.body).toEqual({
      effectiveFrom: '2026-04-01', basicSalary: 100000,
      lines: [{ componentId: 'k1', amount: 5000, rateOverride: null, multiplierOverride: null, excluded: false }],
    });
  });
});
