import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderPage } from '../../test/utils';
import { ApplyLeaveForm } from './ApplyLeaveForm';
import { LeaveCalendar } from './LeaveCalendar';
import { calendarDays, formatDateRange } from './types';

const types = [{ id: 't1', code: 'ANNUAL', name: 'Annual leave', active: true, allowHalfDay: true, attachmentAboveDays: null }];

describe('Leave UI', () => {
  beforeEach(() => {
    tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 });
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-03-10T09:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('FR-063 applies for a half day as multipart with a JSON data part', async () => {
    const { calls } = mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies/c1/leave-types': () => json(types),
      'GET /companies': () => json([]),
      'POST /me/leave/requests': () => json({ days: 0.5, status: 'PENDING' }, 201),
    });
    renderPage(<ApplyLeaveForm companyId="c1" />);
    const apply = await screen.findByRole('button', { name: 'Apply' });
    expect(apply).toBeDisabled();
    await waitFor(() => expect(calls.some((c) => c.url.includes('leave-types'))).toBe(true));
    await userEvent.click(screen.getByRole('combobox', { name: /Leave type/ }));
    await userEvent.click(await screen.findByRole('option', { name: 'Annual leave' }));
    await userEvent.click(screen.getByRole('button', { name: /Dates/ }));
    await userEvent.click(await screen.findByRole('button', { name: '16 March 2026' }));
    await userEvent.click(screen.getByRole('button', { name: '16 March 2026' }));
    await userEvent.click(await screen.findByText('Morning'));
    await userEvent.click(apply);
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    const form = calls.find((c) => c.method === 'POST')!.body as FormData;
    const data = JSON.parse(await (form.get('data') as Blob).text());
    expect(data).toMatchObject({ leaveTypeId: 't1', fromDate: '2026-03-16', toDate: '2026-03-16', halfDayPart: 'MORNING' });
  });

  it('FR-065 shows holidays and leave in the month grid', async () => {
    mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies': () => json([]),
      'GET /leave/calendar': () => json([
        { kind: 'HOLIDAY', id: 'h1', employeeId: null, employeeName: 'Poya', leaveType: null, color: null, from: '2026-03-03', to: '2026-03-03', status: null, halfDayPart: null },
        { kind: 'LEAVE', id: 'l1', employeeId: 'e1', employeeName: 'Nimal Perera', leaveType: 'Annual leave', color: null, from: '2026-03-16', to: '2026-03-17', status: 'APPROVED', halfDayPart: null },
      ]),
    });
    renderPage(<LeaveCalendar companyId="c1" />);
    expect(await screen.findByText('Poya')).toBeInTheDocument();
    expect(screen.getAllByText('Nimal')).toHaveLength(2);
  });

  it('FR-063 shows the selected type balance and the requested day count', async () => {
    mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies/c1/leave-types': () => json(types),
      'GET /companies': () => json([]),
      'GET /me/leave/balances': () => json([{ leaveTypeId: 't1', leaveTypeName: 'Annual leave', unlimited: false, available: 11, pending: 1 }]),
    });
    renderPage(<ApplyLeaveForm companyId="c1" />);
    await userEvent.click(await screen.findByRole('combobox', { name: /Leave type/ }));
    await userEvent.click(await screen.findByRole('option', { name: 'Annual leave' }));
    expect(await screen.findByText('11 days')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Dates/ }));
    await userEvent.click(await screen.findByRole('button', { name: '16 March 2026' }));
    await userEvent.click(screen.getByRole('button', { name: '18 March 2026' }));
    expect(await screen.findByText('3 days')).toBeInTheDocument();
  });

  it('FR-065 formats leave date ranges compactly', () => {
    expect(formatDateRange('2025-01-15', '2025-01-17')).toBe('15 – 17 Jan 2025');
    expect(formatDateRange('2025-01-30', '2025-02-02')).toBe('30 Jan – 2 Feb 2025');
    expect(formatDateRange('2025-12-30', '2026-01-02')).toBe('30 Dec 2025 – 2 Jan 2026');
    expect(formatDateRange('2025-01-15', '2025-01-15')).toBe('15 Jan 2025');
    expect(calendarDays('2025-12-30', '2026-01-02')).toBe(4);
  });
});
