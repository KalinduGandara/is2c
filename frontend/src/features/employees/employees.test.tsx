import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../app/router';
import { tokenStore } from '../../api/client';
import { adminMe, json, mockFetch, renderRoutes } from '../../test/utils';

const company = { id: 'c1', code: 'ACME', legalName: 'Acme', currency: 'LKR', statutoryPack: 'LK', timeZone: 'Asia/Colombo', version: 0 };
const option = (id: string, code: string) => ({ id, companyId: 'c1', code, name: code, rank: null, otEligible: true, version: 0, parentId: null, branchId: null, children: [] });
const employee = {
  id: 'e1', companyId: 'c1', branchId: 'b1', departmentId: 'd1', designationId: 'g1', gradeId: null, categoryId: 'k1',
  employeeNumber: 'ACME00001', title: null, firstName: 'Nimal', lastName: 'Perera', preferredName: null, nic: null,
  dateOfBirth: null, gender: null, maritalStatus: null, nationality: null, personalEmail: null, mobile: null, address: null,
  joinDate: '2025-01-15', probationEndDate: null, confirmationDate: null, contractEndDate: null, exitDate: null, exitReason: null,
  reportingManagerId: null, defaultShiftId: null, weeklyOffDays: null, badgeId: null, status: 'ACTIVE',
  emergencyContacts: [], customFields: { locker: 'L1' }, version: 3,
};

function baseRoutes(extra: Record<string, (url: string, init: RequestInit) => Response> = {}) {
  return mockFetch({
    'GET /auth/me': () => json(adminMe),
    'GET /companies/c1/branches': () => json([option('b1', 'CMB')]),
    'GET /companies/c1/departments': () => json([option('d1', 'OPS')]),
    'GET /companies/c1/designations': () => json([option('g1', 'SE')]),
    'GET /companies/c1/grades': () => json([]),
    'GET /companies/c1/employment-categories': () => json([option('k1', 'PERM')]),
    'GET /companies/c1/custom-fields': () =>
      json([{ id: 'f1', key: 'locker', label: 'Locker', type: 'TEXT', options: [], required: true, active: true, displayOrder: 0, version: 0 }]),
    'GET /companies': () => json([company]),
    'GET /employees': () => json({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 }),
    ...extra,
  });
}

describe('Employee hub', () => {
  beforeEach(() => tokenStore.setTokens({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 }));
  afterEach(() => vi.unstubAllGlobals());

  it('FR-018 lists employees and passes search filters to the API', async () => {
    const { calls } = baseRoutes({
      'GET /employees': () =>
        json({ content: [{ id: 'e1', companyId: 'c1', employeeNumber: 'ACME00001', firstName: 'Nimal', lastName: 'Perera', preferredName: null, branchName: 'Colombo', departmentName: 'Ops', designationName: 'SE', status: 'ACTIVE', joinDate: '2025-01-15' }], page: 0, size: 25, totalElements: 1, totalPages: 1 }),
    });
    const { router } = renderRoutes(routes, '/employees');
    expect(await screen.findByText('Nimal Perera')).toBeInTheDocument();
    await userEvent.type(screen.getByPlaceholderText('Number, name, NIC or badge'), 'nim');
    await waitFor(() => expect(calls.some((c) => c.url.includes('q=nim'))).toBe(true), { timeout: 2000 });
    expect(calls.find((c) => c.url.includes('q=nim'))?.url).toContain('status=ACTIVE');
    await userEvent.click(screen.getByText('Nimal Perera'));
    expect(router.state.location.pathname).toBe('/employees/e1');
  });

  it('FR-015 updates an employee sending version, custom fields and nulls for blanks', async () => {
    const { calls } = baseRoutes({
      'GET /employees/e1/history': () => json([]),
      'GET /employees/e1': () => json(employee),
      'PUT /employees/e1': () => json({ ...employee, version: 4 }),
    });
    renderRoutes(routes, '/employees/e1');
    const first = await screen.findByLabelText(/First name/);
    await userEvent.clear(first);
    await userEvent.type(first, 'Nimalka');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    const body = calls.find((c) => c.method === 'PUT')!.body as Record<string, unknown>;
    expect(body.firstName).toBe('Nimalka');
    expect(body.version).toBe(3);
    expect(body.customFields).toEqual({ locker: 'L1' });
    expect(body.joinDate).toBe('2025-01-15');
    expect(body.nic).toBeNull();
  });

  it('FR-019 shows server errors for custom fields', async () => {
    baseRoutes({
      'GET /employees/e1/history': () => json([]),
      'GET /employees/e1': () => json(employee),
      'PUT /employees/e1': () =>
        json({ code: 'VALIDATION_FAILED', detail: 'Validation failed', errors: [{ field: 'customFields.locker', message: 'Locker is required' }] }, 422),
    });
    renderRoutes(routes, '/employees/e1');
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Locker is required')).toBeInTheDocument();
  });

  it('FR-017 requires a status choice before changing status', async () => {
    baseRoutes({ 'GET /employees/e1/history': () => json([]), 'GET /employees/e1': () => json(employee) });
    renderRoutes(routes, '/employees/e1');
    await userEvent.click(await screen.findByRole('button', { name: 'Change status' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
  });
});
