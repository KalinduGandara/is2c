import { expect, test } from '@playwright/test';
import { apiAs, loginAsAdmin } from './helpers';

test('create a shift in the UI, roster an employee and publish', async ({ page }) => {
  await loginAsAdmin(page);
  const company = await apiAs(page, 'POST', '/companies', { code: 'ROS', legalName: 'Roster Co' });
  const cid = company.body.id;
  const ids: Record<string, string> = {};
  for (const [key, path, body] of [
    ['branch', `/companies/${cid}/branches`, { code: 'CMB', name: 'Colombo' }],
    ['dept', `/companies/${cid}/departments`, { code: 'OPS', name: 'Operations' }],
    ['desig', `/companies/${cid}/designations`, { code: 'OP', name: 'Operator' }],
    ['cat', `/companies/${cid}/employment-categories`, { code: 'PERM', name: 'Permanent' }],
  ] as const) {
    ids[key] = (await apiAs(page, 'POST', path, body)).body.id;
  }
  const emp = await apiAs(page, 'POST', '/employees', {
    companyId: cid, branchId: ids.branch, departmentId: ids.dept, designationId: ids.desig, categoryId: ids.cat,
    firstName: 'Roster', lastName: 'Person', joinDate: '2025-01-01',
  });
  expect(emp.status).toBe(201);
  await page.evaluate((id) => localStorage.setItem('hrm.companyId', id), cid);
  await page.goto('/shifts');

  await page.getByRole('tab', { name: 'Shifts' }).click();
  await page.getByRole('button', { name: /Create shift/ }).click();
  await page.getByRole('textbox', { name: 'Code' }).fill('DAY');
  await page.getByRole('textbox', { name: 'Name' }).fill('Day shift');
  await page.getByRole('textbox', { name: 'Start' }).fill('08:00');
  await page.getByRole('textbox', { name: 'End', exact: true }).fill('17:00');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('cell', { name: '08:00–17:00' })).toBeVisible();

  await page.getByRole('tab', { name: 'Rosters' }).click();
  await page.getByRole('button', { name: 'Create roster' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: 'Name' }).fill('Week of 16 March');
  await dialog.getByRole('textbox', { name: 'Start date' }).click();
  await dialog.getByRole('textbox', { name: 'Start date' }).fill('2026-03-16');
  await dialog.getByRole('textbox', { name: 'Name' }).click();
  await dialog.getByRole('button', { name: 'Create' }).click();

  const cell = page.getByLabel(`${emp.body.employeeNumber} 2026-03-16`);
  await cell.selectOption({ label: 'DAY' });
  await page.getByRole('button', { name: 'Save (1)' }).click();
  await expect(page.getByRole('button', { name: 'Save (0)' })).toBeVisible();
  await page.getByRole('button', { name: 'Publish' }).click();
  await expect(page.getByText('PUBLISHED', { exact: true })).toBeVisible();

  const schedule = await apiAs(page, 'GET', `/schedules?companyId=${cid}&from=2026-03-16&to=2026-03-16`);
  expect(schedule.body[0].days[0]).toMatchObject({ shiftCode: 'DAY', source: 'ROSTER' });
});
