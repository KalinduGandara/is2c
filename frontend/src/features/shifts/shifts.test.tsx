import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderPage } from '../../test/utils';
import { RosterGrid } from './RosterGrid';
import { dateRange } from './types';

const perms = [...adminMe.permissions, 'ROSTER:VIEW', 'ROSTER:EDIT', 'ROSTER:APPROVE', 'SHIFT:VIEW'];
const shiftDay = { id: 's1', code: 'DAY', name: 'Day', type: 'FIXED', active: true };
const detail = (status: string) => ({
  roster: { id: 'r1', companyId: 'c1', departmentId: null, name: 'Week 12', periodType: 'WEEKLY', startDate: '2026-03-16', endDate: '2026-03-22', status, revision: status === 'DRAFT' ? 0 : 1, version: 0 },
  entries: [{ employeeId: 'e1', date: '2026-03-16', shiftId: 's1', off: false }],
  employees: [{ id: 'e1', employeeNumber: 'E001', name: 'One Ops', departmentId: 'd1' }],
});

describe('dateRange', () => {
  it('lists inclusive ISO dates across month ends', () => {
    expect(dateRange('2026-03-30', '2026-04-02')).toEqual(['2026-03-30', '2026-03-31', '2026-04-01', '2026-04-02']);
  });
});

describe('RosterGrid', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-036 sends only changed cells (OFF and cleared) in one save', async () => {
    const { calls } = mockFetch({
      'GET /auth/me': () => json({ ...adminMe, permissions: perms }),
      'GET /companies/c1/shifts': () => json([shiftDay]),
      'GET /rosters/r1': () => json(detail('DRAFT')),
      'PUT /rosters/r1/entries': () => json(detail('DRAFT').roster),
      'GET /companies': () => json([]),
    });
    renderPage(<RosterGrid rosterId="r1" otherRosters={[]} onClose={() => {}} />);
    const monday = await screen.findByLabelText('E001 2026-03-16');
    await waitFor(() => expect(monday).toHaveValue('s1'));
    await userEvent.selectOptions(screen.getByLabelText('E001 2026-03-17'), 'OFF');
    await userEvent.selectOptions(monday, '');
    await userEvent.click(screen.getByRole('button', { name: 'Save (2)' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    const body = calls.find((c) => c.method === 'PUT')!.body as { entries: unknown[]; reason: unknown };
    expect(body.reason).toBeNull();
    expect(body.entries).toEqual(expect.arrayContaining([
      { employeeId: 'e1', date: '2026-03-17', shiftId: null, off: true, clear: false },
      { employeeId: 'e1', date: '2026-03-16', shiftId: null, off: false, clear: true },
    ]));
  });

  it('FR-039 asks for a reason before saving changes to a published roster', async () => {
    const { calls } = mockFetch({
      'GET /auth/me': () => json({ ...adminMe, permissions: perms }),
      'GET /companies/c1/shifts': () => json([shiftDay]),
      'GET /rosters/r1': () => json(detail('PUBLISHED')),
      'PUT /rosters/r1/entries': () => json(detail('PUBLISHED').roster),
      'GET /companies': () => json([]),
    });
    renderPage(<RosterGrid rosterId="r1" otherRosters={[]} onClose={() => {}} />);
    await userEvent.selectOptions(await screen.findByLabelText('E001 2026-03-18'), 'OFF');
    await userEvent.click(screen.getByRole('button', { name: 'Save (1)' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'sick cover');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect((calls.find((c) => c.method === 'PUT')?.body as { reason: string })?.reason).toBe('sick cover'));
  });

  it('FR-039 shows a locked roster read-only', async () => {
    mockFetch({
      'GET /auth/me': () => json({ ...adminMe, permissions: perms }),
      'GET /companies/c1/shifts': () => json([shiftDay]),
      'GET /rosters/r1': () => json(detail('LOCKED')),
      'GET /companies': () => json([]),
    });
    renderPage(<RosterGrid rosterId="r1" otherRosters={[]} onClose={() => {}} />);
    expect(await screen.findByText('LOCKED')).toBeInTheDocument();
    expect(screen.queryByLabelText('E001 2026-03-16')).not.toBeInTheDocument();
    expect(await screen.findByText('DAY')).toBeInTheDocument();
  });
});
