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
  await expect(page.getByText('Active employees')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  const employee = await (await browser.newContext({ viewport: { width: 360, height: 780 } })).newPage();
  await loginAs(employee, `emp${code}`.toLowerCase(), 'Emp#Initial12345', 'Emp#Changed12345');
  await employee.getByRole('button', { name: 'Toggle navigation' }).click();
  await employee.getByText('My Portal').click();
  await expect(employee.getByText('Nimal Perera')).toBeVisible();
  for (const tab of ['Attendance', 'Schedule', 'Leave', 'Payslips']) {
    await employee.getByRole('tab', { name: tab }).click();
    expect(await employee.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), tab).toBe(true);
  }
});
