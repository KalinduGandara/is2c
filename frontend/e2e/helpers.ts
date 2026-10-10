import { expect, type Page } from '@playwright/test';

export const ADMIN = { username: 'admin', bootstrap: 'Admin#Pass123', password: 'Admin#Pass9876' };

/** Logs in as the bootstrap admin, completing the forced password change on first use. */
export async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill(ADMIN.username);
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill(ADMIN.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  // First run of a fresh database: the bootstrap password is still active.
  const outcome = await Promise.race([
    page.getByRole('alert').waitFor().then(() => 'failed' as const),
    page.waitForURL((url) => !url.pathname.startsWith('/login')).then(() => 'ok' as const),
  ]);
  if (outcome === 'failed') {
    await page.getByRole('textbox', { name: 'Password', exact: true }).fill(ADMIN.bootstrap);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/change-password/);
    await page.getByRole('textbox', { name: 'Current password' }).fill(ADMIN.bootstrap);
    await page.getByRole('textbox', { name: 'New password', exact: true }).fill(ADMIN.password);
    await page.getByRole('textbox', { name: 'Confirm new password' }).fill(ADMIN.password);
    await page.getByRole('button', { name: 'Update password' }).click();
  }
  await expect(page.getByRole('button', { name: 'User menu' })).toBeVisible();
}

/** Calls the API with the page's session token (for fast fixture setup). */
export async function apiAs(page: Page, method: string, path: string, body?: unknown) {
  return page.evaluate(
    async ({ method, path, body }) => {
      const refresh = sessionStorage.getItem('hrm.refreshToken');
      const tokens = await (await fetch('/api/v1/auth/refresh', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: refresh }),
      })).json();
      sessionStorage.setItem('hrm.refreshToken', tokens.refreshToken);
      const res = await fetch(`/api/v1${path}`, {
        method,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens.accessToken}` },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await res.text();
      return { status: res.status, body: text ? JSON.parse(text) : null };
    },
    { method, path, body },
  );
}

/** Logs in a user created by an administrator, completing the forced password change on first use. */
export async function loginAs(page: Page, username: string, initial: string, changed: string) {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill(initial);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/change-password/);
  await page.getByRole('textbox', { name: 'Current password' }).fill(initial);
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill(changed);
  await page.getByRole('textbox', { name: 'Confirm new password' }).fill(changed);
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page.getByRole('button', { name: 'User menu' })).toBeVisible();
}

export interface Org { companyId: string; employeeId: string; shiftId: string; employeeNumber: string }

/** Company with one department structure, a day shift (08:30–17:30) and one employee (badge = code + "1"). */
export async function setupCompany(page: Page, code: string): Promise<Org> {
  const company = await apiAs(page, 'POST', '/companies', { code, legalName: `${code} Holdings (Pvt) Ltd` });
  expect(company.status).toBe(201);
  const cid = company.body.id;
  const ids: Record<string, string> = {};
  for (const [key, path, body] of [
    ['branch', `/companies/${cid}/branches`, { code: 'CMB', name: 'Colombo' }],
    ['department', `/companies/${cid}/departments`, { code: 'OPS', name: 'Operations' }],
    ['designation', `/companies/${cid}/designations`, { code: 'SE', name: 'Software Engineer' }],
    ['category', `/companies/${cid}/employment-categories`, { code: 'PERM', name: 'Permanent' }],
    ['ot', `/companies/${cid}/ot-policies`, { name: 'Standard' }],
  ] as const) {
    const r = await apiAs(page, 'POST', path, body);
    expect(r.status).toBe(201);
    ids[key] = r.body.id;
  }
  const shift = await apiAs(page, 'POST', `/companies/${cid}/shifts`, {
    code: 'DAY', name: 'Day', type: 'FIXED', s1Start: '08:30', s1End: '17:30', breakMinutes: 60, breakThresholdMinutes: 300, otPolicyId: ids.ot,
  });
  expect(shift.status).toBe(201);
  const employee = await apiAs(page, 'POST', '/employees', {
    companyId: cid, branchId: ids.branch, departmentId: ids.department, designationId: ids.designation, categoryId: ids.category,
    firstName: 'Nimal', lastName: 'Perera', joinDate: '2025-01-01', defaultShiftId: shift.body.id, badgeId: `${code}1`,
    personalEmail: 'nimal@example.com',
  });
  expect(employee.status).toBe(201);
  return { companyId: cid, employeeId: employee.body.id, shiftId: shift.body.id, employeeNumber: employee.body.employeeNumber };
}

export async function createUser(page: Page, username: string, password: string, roles: string[], employeeId?: string) {
  const r = await apiAs(page, 'POST', '/admin/users', {
    username, displayName: username, email: `${username}@example.com`, password, roles,
    employeeId: employeeId ?? null, scopeType: employeeId ? 'OWN' : 'ALL',
  });
  expect(r.status, JSON.stringify(r.body)).toBe(201);
}

/** Picks the working company in the header selector (rendered once the user's companies have loaded and there are several). */
export async function selectCompany(page: Page, code: string) {
  const selector = page.getByRole('combobox', { name: 'Company' });
  // With a single accessible company the header shows a chip instead of a selector.
  await expect(selector.or(page.getByRole('banner').getByText(code, { exact: true }))).toBeVisible();
  if (!(await selector.isVisible())) return;
  await selector.click();
  // options show the code with the legal name underneath
  await page.getByRole('option', { name: new RegExp(`^${code}\\b`) }).click();
  await expect(selector).toHaveValue(code);
}
