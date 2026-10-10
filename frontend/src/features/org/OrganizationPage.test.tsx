import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../app/router';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderRoutes } from '../../test/utils';

const company = { id: 'c1', code: 'ACME', legalName: 'Acme (Pvt) Ltd', currency: 'LKR', statutoryPack: 'LK', timeZone: 'Asia/Colombo', version: 0 };

describe('OrganizationPage', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-010 lists companies and FR-011 shows branches of the selected company', async () => {
    mockFetch({
      'GET /auth/me': () => json(adminMe),
      'GET /companies/c1/branches': () => json([{ id: 'b1', companyId: 'c1', code: 'CMB', name: 'Colombo', address: null, phone: '011', version: 0 }]),
      'GET /companies': () => json([company]),
    });
    renderRoutes(routes, '/organization');
    expect(await screen.findByRole('cell', { name: 'Acme (Pvt) Ltd' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Branches' }));
    expect(await screen.findByText('Colombo')).toBeInTheDocument();
  });

  it('FR-005 hides create actions without permission', async () => {
    mockFetch({
      'GET /auth/me': () => json({ ...adminMe, permissions: ['ORG:VIEW'] }),
      'GET /companies': () => json([company]),
    });
    renderRoutes(routes, '/organization');
    expect(await screen.findByRole('cell', { name: 'Acme (Pvt) Ltd' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Create company/ })).not.toBeInTheDocument();
  });
});
