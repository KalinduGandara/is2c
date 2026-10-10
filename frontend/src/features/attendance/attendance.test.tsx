import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderPage } from '../../test/utils';
import { TxnDrawer } from '../approvals/TxnDrawer';
import { DailyPanel } from './DailyPanel';
import { DayDetailDrawer } from './DayDetailDrawer';

const perms = [...adminMe.permissions, 'ATTENDANCE:VIEW', 'ATTENDANCE:ADD', 'ATTENDANCE:EDIT', 'ATTENDANCE:APPROVE'];
const daily = (over: Record<string, unknown> = {}) => ({
  id: 'd1', employeeId: 'e1', employeeNumber: 'E001', employeeName: 'Nimal Perera', date: '2026-03-16', dayType: 'WORKDAY',
  status: 'PRESENT', firstIn: '2026-03-16T09:00:00', lastOut: '2026-03-16T17:30:00', scheduledMinutes: 480, workedMinutes: 450,
  lateMinutes: 30, earlyMinutes: 0, shortMinutes: 30, otWorkdayMinutes: 0, otOffdayMinutes: 0, otHolidayMinutes: 0, extraMinutes: 0,
  otRequiresApproval: false, otApproved: false, otRejected: false, manual: false, exceptions: ['LATE'], ...over,
});

describe('Attendance UI', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-051 submits a correction with reason and next-day OUT', async () => {
    const { calls } = mockFetch({
      'GET /auth/me': () => json({ ...adminMe, permissions: perms }),
      'GET /companies': () => json([]),
      'GET /attendance/daily/e1/2026-03-16': () => json({ daily: daily(), events: [{ id: 'x', time: '2026-03-16T09:00:00', direction: 'IN', deviceId: 'D1' }], adjustments: [] }),
      'POST /attendance/adjustments': () => json({ status: 'APPLIED' }),
    });
    renderPage(<DayDetailDrawer employeeId="e1" date="2026-03-16" onClose={() => {}} />);
    expect(await screen.findByText('Nimal Perera')).toBeInTheDocument();
    expect(screen.getAllByText('Late').length).toBeGreaterThanOrEqual(2); // metric label + humanized exception badge
    expect(screen.getByText('Device D1')).toBeInTheDocument();
    const submit = screen.getByRole('button', { name: 'Submit correction' });
    expect(submit).toBeDisabled();
    await userEvent.type(screen.getByLabelText('IN (HH:mm)'), '08:30');
    await userEvent.type(screen.getByLabelText('OUT (HH:mm)'), '01:00');
    await userEvent.click(screen.getByLabelText('OUT is on the next day'));
    await userEvent.type(screen.getByLabelText(/Reason/), 'forgot to punch');
    await userEvent.click(submit);
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')!.body).toEqual({
      employeeId: 'e1', date: '2026-03-16', in: '2026-03-16T08:30:00', out: '2026-03-17T01:00:00', reason: 'forgot to punch', overrideLock: false,
    });
  });

  it('FR-056 approves selected pending overtime in bulk', async () => {
    const { calls } = mockFetch({
      'GET /auth/me': () => json({ ...adminMe, permissions: perms }),
      'GET /companies/c1/departments': () => json([]),
      'GET /companies': () => json([]),
      'GET /attendance/exceptions': () => json({ content: [daily({ id: 'd9', otRequiresApproval: true, otWorkdayMinutes: 60, exceptions: ['OT_PENDING'] })], page: 0, size: 50, totalElements: 1, totalPages: 1 }),
      'POST /attendance/ot/approve': () => json({ updated: 1 }),
    });
    renderPage(<DailyPanel companyId="c1" exceptionsOnly />);
    await userEvent.click(await screen.findByLabelText('Select E001 2026-03-16'));
    await userEvent.click(screen.getByRole('button', { name: 'Approve OT (1)' }));
    await waitFor(() => expect(calls.find((c) => c.url.endsWith('/ot/approve'))?.body).toEqual({ dailyIds: ['d9'] }));
  });

  it('FR-071 reject requires a comment; approve posts the action', async () => {
    const { calls } = mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies': () => json([]),
      'GET /approval/transactions/t1': () => json({
        transaction: { id: 't1', type: 'LEAVE_REQUEST', referenceId: 'r', requesterName: 'Staff', status: 'PENDING', currentLevel: 1, summary: 'Annual leave 2 days', submittedAt: '2026-03-16T04:00:00Z', completedAt: null },
        levels: [{ approverType: 'REPORTING_MANAGER', roleCode: null, userId: null }],
        history: [{ level: 1, action: 'SUBMIT', userName: 'Staff', onBehalfOf: null, comment: null, actedAt: '2026-03-16T04:00:00Z' }],
        currentApprovers: ['Manager'], canAct: true, canCancel: false,
      }),
      'POST /approval/transactions/t1/approve': () => json({}),
    });
    renderPage(<TxnDrawer id="t1" onClose={() => {}} />);
    expect(await screen.findByText('Annual leave 2 days')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(calls.some((c) => c.url.endsWith('/t1/approve'))).toBe(true));
  });
});
