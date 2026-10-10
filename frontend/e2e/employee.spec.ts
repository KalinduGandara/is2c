import { expect, test } from '@playwright/test';
import { apiAs, loginAsAdmin, selectCompany } from './helpers';

test('org setup and employee creation through the UI', async ({ page }) => {
  await loginAsAdmin(page);
  const company = await apiAs(page, 'POST', '/companies', { code: 'E2E', legalName: 'E2E Holdings (Pvt) Ltd' });
  expect(company.status).toBe(201);
  const cid = company.body.id;
  for (const [path, body] of [
    [`/companies/${cid}/branches`, { code: 'CMB', name: 'Colombo' }],
    [`/companies/${cid}/departments`, { code: 'OPS', name: 'Operations' }],
    [`/companies/${cid}/designations`, { code: 'SE', name: 'Software Engineer' }],
    [`/companies/${cid}/employment-categories`, { code: 'PERM', name: 'Permanent' }],
  ] as const) {
    expect((await apiAs(page, 'POST', path, body)).status).toBe(201);
  }
  await page.reload();
  await selectCompany(page, 'E2E');

  await page.getByRole('link', { name: 'Organization Setup' }).click();
  await expect(page.getByRole('cell', { name: 'E2E Holdings (Pvt) Ltd' })).toBeVisible();
  await page.getByRole('tab', { name: 'Branches' }).click();
  await expect(page.getByRole('cell', { name: 'Colombo', exact: true })).toBeVisible();

  await page.getByRole('link', { name: 'Employee Hub' }).click();
  await page.getByRole('button', { name: 'New employee' }).click();
  await page.getByLabel('First name').fill('Nimal');
  await page.getByLabel('Last name').fill('Perera');
  for (const [label, option] of [['Branch', 'CMB — Colombo'], ['Department', 'OPS — Operations'], ['Designation', 'SE — Software Engineer'], ['Employment category', 'PERM — Permanent']]) {
    await page.getByRole('combobox', { name: label, exact: true }).click();
    await page.getByRole('option', { name: option }).click();
  }
  await page.getByRole('textbox', { name: 'Join date' }).click();
  await page.getByRole('textbox', { name: 'Join date' }).fill('2025-01-15');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('heading', { name: 'Nimal Perera' })).toBeVisible();
  await expect(page.getByRole('main').getByText('E2E00001', { exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'History' }).click();
  await expect(page.getByRole('cell', { name: 'Joined' })).toBeVisible();
});
