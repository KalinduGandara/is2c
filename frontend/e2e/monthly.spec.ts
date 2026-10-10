import { expect, test, type Browser, type Page } from '@playwright/test';
import { apiAs, createUser, loginAs, loginAsAdmin, selectCompany, setupCompany } from './helpers';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const pad = (n: number) => String(n).padStart(2, '0');

async function as(browser: Browser, username: string, initial: string, changed: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await loginAs(page, username, initial, changed);
  return page;
}

test('NFR-020 FR-087 FR-088 FR-100 monthly flow: setup → leave → attendance → payroll approval & finalization → own payslip', async ({ page, browser }) => {
  test.setTimeout(240_000);
  // The payroll month is the previous calendar month (attendance is never processed for future dates).
  const now = new Date();
  const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const year = prev.getFullYear();
  const month = prev.getMonth() + 1;

  // --- administrator: organisation, employee, users, attendance feed --------------------------------------
  await loginAsAdmin(page);
  const org = await setupCompany(page, 'MFLOW');
  await createUser(page, 'hr1', 'Hr#Initial12345', ['HR_MANAGER']);
  await createUser(page, 'nimal', 'Emp#Initial12345', ['EMPLOYEE'], org.employeeId);
  const source = await apiAs(page, 'POST', `/companies/${org.companyId}/attendance-sources`, { name: 'Gate' });
  const events: Record<string, string>[] = [];
  for (let d = 1; d <= new Date(year, month, 0).getDate(); d++) {
    const date = `${year}-${pad(month)}-${pad(d)}`;
    if (new Date(year, month - 1, d).getDay() === 0) continue; // Sunday weekly off
    events.push({ externalId: `${date}-in`, employeeIdentifier: 'MFLOW1', timestamp: `${date}T${d === 2 ? '09:00' : '08:25'}:00`, direction: 'IN' });
    events.push({ externalId: `${date}-out`, employeeIdentifier: 'MFLOW1', timestamp: `${date}T17:35:00`, direction: 'OUT' });
  }
  const ingest = await page.request.post('/api/v1/integrations/attendance/events', { headers: { 'X-Api-Key': source.body.apiKey }, data: { events } });
  expect(ingest.ok()).toBe(true);
  expect((await ingest.json()).accepted).toBe(events.length);
  await page.reload();
  // other specs share this database: work explicitly in this company
  await selectCompany(page, 'MFLOW');

  // salary structure through the employee's Salary tab
  await page.getByRole('link', { name: 'Employee Hub' }).click();
  await page.getByText('Nimal').first().click();
  await page.getByRole('tab', { name: 'Salary' }).click();
  await page.getByRole('button', { name: 'New structure' }).click();
  await page.getByLabel('Effective from').fill('2025-01-01');
  await page.getByLabel('Basic salary').fill('100000');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Current', { exact: true })).toBeVisible();

  // --- employee: applies for leave in the portal -------------------------------------------------------------
  const employee = await as(browser, 'nimal', 'Emp#Initial12345', 'Emp#Changed12345');
  await employee.getByRole('link', { name: 'My Portal' }).click();
  await employee.getByRole('tab', { name: 'Leave' }).click();
  await employee.getByRole('button', { name: 'Apply for leave' }).click();
  const dialog = employee.getByRole('dialog');
  await dialog.getByRole('combobox', { name: 'Leave type' }).click();
  await employee.getByRole('option', { name: 'Annual leave' }).click();
  let leaveDay = 15;
  while (new Date(now.getFullYear(), now.getMonth(), leaveDay).getDay() === 0) leaveDay++;
  const label = `${leaveDay} ${MONTHS[now.getMonth()]} ${now.getFullYear()}`;
  await dialog.getByRole('button', { name: /Dates/ }).click();
  await employee.getByRole('button', { name: label, exact: true }).click();
  await employee.getByRole('button', { name: label, exact: true }).click();
  await dialog.getByRole('button', { name: 'Apply' }).click();
  await expect(employee.getByRole('cell', { name: 'Pending' })).toBeVisible();

  // --- HR manager: approves the leave from the inbox ----------------------------------------------------------
  const hr = await as(browser, 'hr1', 'Hr#Initial12345', 'Hr#Changed12345');
  await hr.getByRole('link', { name: /^Approvals/ }).click();
  await hr.getByRole('cell', { name: /Nimal Perera/ }).first().click();
  await hr.getByRole('button', { name: 'Approve' }).click();
  await expect(hr.getByRole('cell', { name: /Nimal Perera/ })).toHaveCount(0);
  await employee.reload();
  await employee.getByRole('tab', { name: 'Leave' }).click();
  await expect(employee.getByRole('cell', { name: 'Approved' })).toBeVisible();

  // --- payroll: open, process (incl. attendance reprocessing), submit ---------------------------------------
  await page.getByRole('link', { name: 'Payroll', exact: true }).click();
  await page.getByRole('button', { name: 'Open period' }).click();
  await page.getByLabel('Year').fill(String(year));
  await page.getByLabel('Month').fill(String(month));
  await page.getByRole('button', { name: 'Open', exact: true }).click();
  await expect(page.getByRole('heading', { name: `Payroll — ${MONTHS[month - 1]} ${year}` })).toBeVisible();
  await page.getByRole('button', { name: 'Process' }).click();
  await expect(page.getByRole('cell', { name: 'Nimal Perera' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '92,000.00' })).toBeVisible(); // 100,000 − EPF 8%
  await page.getByRole('button', { name: 'Submit for review' }).click();
  await expect(page.getByRole('main').getByText('In review', { exact: true })).toBeVisible();

  // --- HR manager: approves payroll and finalizes ------------------------------------------------------------
  await hr.getByRole('link', { name: /^Approvals/ }).click();
  await hr.reload();
  await selectCompany(hr, 'MFLOW');
  await hr.getByRole('cell', { name: /Payroll/ }).first().click();
  await hr.getByRole('button', { name: 'Approve' }).click();
  await hr.getByRole('link', { name: 'Payroll', exact: true }).click();
  await hr.getByRole('cell', { name: `${MONTHS[month - 1]} ${year}` }).click();
  await expect(hr.getByRole('heading', { name: `Payroll — ${MONTHS[month - 1]} ${year}` })).toBeVisible();
  await expect(hr.getByRole('main').getByText('Approved', { exact: true })).toBeVisible();
  await hr.getByRole('button', { name: 'Finalize' }).click();
  await hr.getByRole('dialog').getByRole('button', { name: 'Finalize' }).click();
  await expect(hr.getByRole('main').getByText('Finalized', { exact: true })).toBeVisible();
  await expect(hr.getByRole('cell', { name: /^PS-/ })).toBeVisible();

  // --- employee: downloads the payslip ------------------------------------------------------------------------
  await employee.getByRole('tab', { name: 'Payslips' }).click();
  await expect(employee.getByRole('cell', { name: '92,000.00' })).toBeVisible();
  const [download] = await Promise.all([
    employee.waitForEvent('download'),
    employee.getByRole('button', { name: `Download ${MONTHS[month - 1]} ${year}` }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/^payslip-.*\.pdf$/);
});
