import { screen } from '@testing-library/react';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderPage } from '../../test/utils';
import { ApprovalsPage } from './ApprovalsPage';

describe('Approvals UI', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-071 lists inbox items with type, requester and status, and counts them on the tab', async () => {
    mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies': () => json([]),
      'GET /approval/inbox': () => json([
        { id: 't1', type: 'LEAVE_REQUEST', referenceId: 'r', requesterName: 'Nimal Perera', status: 'PENDING', currentLevel: 1, summary: 'Annual leave 2 days', submittedAt: '2026-03-16T04:00:00Z', completedAt: null },
      ]),
    });
    renderPage(<ApprovalsPage />);
    expect(await screen.findByText('Annual leave 2 days')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /Nimal Perera/ })).toBeInTheDocument();
    expect(screen.getByText('Pending')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Inbox/ })).toHaveTextContent('1');
  });

  it('FR-071 shows an all-caught-up state when the inbox is empty', async () => {
    mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies': () => json([]),
      'GET /approval/inbox': () => json([]),
    });
    renderPage(<ApprovalsPage />);
    expect(await screen.findByText('You’re all caught up')).toBeInTheDocument();
  });
});
