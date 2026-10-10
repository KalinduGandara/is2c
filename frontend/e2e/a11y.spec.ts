import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { apiAs, createUser, loginAs, loginAsAdmin, selectCompany, setupCompany } from './helpers';

// WCAG 2.1 A/AA automated checks (axe-core) on the main screens, in light and dark mode.
// Only serious and critical issues fail the build; moderate findings are reported in the test output.
async function audit(page: Page, label: string) {
  await page.waitForLoadState('networkidle');
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const blocking = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  const summary = (vs: typeof results.violations) =>
    vs.map((v) => `${v.id} (${v.impact}): ${v.help} — ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
  const minor = results.violations.filter((v) => !blocking.includes(v));
  if (minor.length) console.log(`[a11y] ${label} non-blocking:\n  ${summary(minor).join('\n  ')}`);
  expect(summary(blocking), `${label} has serious accessibility violations`).toEqual([]);
}

test('NFR-012 sign-in screen has no serious accessibility violations', async ({ page }) => {
  await page.goto('/login');
  await audit(page, 'login');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await audit(page, 'login with errors');
});

for (const scheme of ['light', 'dark'] as const) {
  test(`NFR-012 main screens have no serious accessibility violations (${scheme})`, async ({ page, browser }) => {
    test.setTimeout(180_000);
    await page.emulateMedia({ colorScheme: scheme });
    await loginAsAdmin(page);
    const code = `${scheme === 'light' ? 'AXL' : 'AXD'}${Date.now().toString().slice(-5)}`;
    const org = await setupCompany(page, code);
    expect((await apiAs(page, 'GET', `/dashboard?companyId=${org.companyId}`)).status).toBe(200);
    await page.reload();
    await expect(page.getByRole('button', { name: 'User menu' })).toBeVisible();
    await selectCompany(page, code);

    for (const path of ['/', '/employees', `/employees/${org.employeeId}`, '/organization', '/attendance', '/shifts', '/leave', '/payroll', '/approvals', '/reports', '/admin']) {
      await page.goto(path);
      await expect(page.getByRole('heading', { level: 2 }).first()).toBeVisible();
      await audit(page, `${scheme} ${path}`);
    }

    const username = `ax${code}`.toLowerCase();
    await createUser(page, username, 'Emp#Initial12345', ['EMPLOYEE'], org.employeeId);
    const employee = await (await browser.newContext({ colorScheme: scheme })).newPage();
    await loginAs(employee, username, 'Emp#Initial12345', 'Emp#Changed12345');
    for (const tab of ['', '?tab=attendance', '?tab=schedule', '?tab=leave', '?tab=payslips']) {
      await employee.goto(`/me${tab}`);
      await expect(employee.getByRole('heading', { level: 2 }).first()).toBeVisible();
      await audit(employee, `${scheme} /me${tab}`);
    }
  });
}
