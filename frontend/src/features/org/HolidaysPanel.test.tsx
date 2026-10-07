import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { json, mockFetch, renderPage, adminMe } from '../../test/utils';
import { tokenStore } from '../../api/client';
import { HolidaysPanel } from './HolidaysPanel';

describe('HolidaysPanel', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-025 lists holidays for the year and copies to another year', async () => {
    const year = new Date().getFullYear();
    const { calls } = mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies/c1/branches': () => json([]),
      'GET /companies/c1/holidays': () => json([{ id: 'h1', date: `${year}-02-04`, name: 'Independence Day', type: 'PUBLIC', allBranches: true, branchIds: [], version: 0 }]),
      'POST /companies/c1/holidays/copy': () => json({ copied: 1, skipped: 0 }),
      'GET /companies': () => json([]),
    });
    renderPage(<HolidaysPanel companyId="c1" />);
    expect(await screen.findByText('Independence Day')).toBeInTheDocument();
    expect(calls.some((c) => c.url.includes(`year=${year}`))).toBe(true);
    await userEvent.click(screen.getByRole('button', { name: 'Copy year…' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Copy' }));
    await waitFor(() => expect(calls.find((c) => c.url.endsWith('/holidays/copy'))?.body).toEqual({ fromYear: year, toYear: year + 1 }));
  });
});
