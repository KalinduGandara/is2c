import { expect, test } from '@playwright/test';
import { apiAs, createUser, loginAs, loginAsAdmin, setupCompany } from './helpers';

// NFR-012: usable from 360 px. Runs in both projects (desktop and Pixel 7); the viewport is pinned to 360 px here.
test.use({ viewport: { width: 360, height: 780 } });

test('NFR-012 employee portal and dashboard at 360 px without horizontal scrolling', async ({ page, browser }) => {
  const code = `M${Date.now().toString().slice(-6)}`;
  await loginAsAdmin(page);
  const org = await setupCompany(page, code);
  await createUser(page, `emp${code}`.toLowerCase(), 'Emp#Initial12345', ['EMPLOYEE'], org.employeeId);
  expect((await apiAs(page, 'GET', `/dashboard?companyId=${org.companyId}`)).status).toBe(200);

  await page.reload();
  await expect(page.getByRole('button', { name: 'Toggle navigation' })).toBeVisible();
  await expect(page.getByText('Active employees', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  const employee = await (await browser.newContext({ viewport: { width: 360, height: 780 } })).newPage();
  await loginAs(employee, `emp${code}`.toLowerCase(), 'Emp#Initial12345', 'Emp#Changed12345');
  await employee.getByRole('button', { name: 'Toggle navigation' }).click();
  await employee.getByRole('link', { name: 'My Portal' }).click();
  await expect(employee.getByRole('heading', { name: 'Nimal Perera' }).first()).toBeVisible();
  for (const tab of ['Attendance', 'Schedule', 'Leave', 'Payslips']) {
    await employee.getByRole('tab', { name: tab }).click();
    expect(await employee.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), tab).toBe(true);
  }
});

test('NFR-012 every module page fits a 360 px screen and the drawer navigation closes after use', async ({ page }) => {
  test.setTimeout(120_000);
  const code = `R${Date.now().toString().slice(-6)}`;
  await loginAsAdmin(page);
  await setupCompany(page, code);
  await page.reload();
  await expect(page.getByRole('button', { name: 'User menu' })).toBeVisible();

  const nav = page.getByRole('navigation', { name: 'Main navigation' });
  await page.getByRole('button', { name: 'Toggle navigation' }).click();
  await expect(nav.getByRole('link', { name: 'Employee Hub' })).toBeInViewport();
  await nav.getByRole('link', { name: 'Employee Hub' }).click();
  await expect(page).toHaveURL(/\/employees$/);
  await expect(nav.getByRole('link', { name: 'Employee Hub' })).not.toBeInViewport();

  for (const path of ['/', '/employees', '/organization', '/attendance', '/shifts', '/leave', '/payroll', '/approvals', '/reports', '/admin', '/change-password']) {
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), path).toBe(true);
  }
});
