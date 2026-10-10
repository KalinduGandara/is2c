import { expect, test, type Page } from '@playwright/test';
import { ADMIN, apiAs, createUser, loginAs, loginAsAdmin, selectCompany, setupCompany, type Org } from './helpers';

// App-shell UX: sign-in, navigation, command palette, theme, URL state, empty/error pages and permission gates.
// Specs share one database, so this file works in its own company.
const CODE = 'UXS';
let org: Org | null = null;

async function adminInCompany(page: Page): Promise<Org> {
  await loginAsAdmin(page);
  if (!org) {
    const existing = await apiAs(page, 'GET', '/companies');
    const found = existing.body.find((c: { code: string }) => c.code === CODE);
    if (found) {
      const employees = await apiAs(page, 'GET', `/employees?companyId=${found.id}&size=1`);
      org = { companyId: found.id, employeeId: employees.body.content[0].id, shiftId: '', employeeNumber: employees.body.content[0].employeeNumber };
    } else {
      org = await setupCompany(page, CODE);
    }
  }
  await page.reload();
  await expect(page.getByRole('button', { name: 'User menu' })).toBeVisible();
  await selectCompany(page, CODE);
  return org;
}

test.describe('sign-in screens', () => {
  test('FR-001 login validates required fields and reports bad credentials', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Username' })).toBeFocused();
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByText('Required')).toHaveCount(2);
    await page.getByRole('textbox', { name: 'Username' }).fill(ADMIN.username);
    await page.getByRole('textbox', { name: 'Password', exact: true }).fill('definitely-wrong');
    await page.getByRole('textbox', { name: 'Password', exact: true }).press('Enter');
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('FR-003 forced password change checks the rules and confirmation before saving', async ({ page, browser }) => {
    await loginAsAdmin(page);
    await createUser(page, 'uxrules', 'Rules#Initial123', ['EMPLOYEE']);
    const user = await (await browser.newContext()).newPage();
    await user.goto('/login');
    await user.getByRole('textbox', { name: 'Username' }).fill('uxrules');
    await user.getByRole('textbox', { name: 'Password', exact: true }).fill('Rules#Initial123');
    await user.getByRole('button', { name: 'Sign in' }).click();
    await expect(user).toHaveURL(/change-password/);
    await expect(user.getByText('You must change your password before continuing.')).toBeVisible();

    await user.getByRole('textbox', { name: 'Current password' }).fill('Rules#Initial123');
    await user.getByRole('textbox', { name: 'New password', exact: true }).fill('weakpass');
    await user.getByRole('textbox', { name: 'Confirm new password' }).fill('different');
    await user.getByRole('button', { name: 'Update password' }).click();
    await expect(user.getByText('Passwords do not match')).toBeVisible();
    await expect(user.getByText(/At least 10 characters with upper- and lower-case/).last()).toBeVisible();
    await expect(user).toHaveURL(/change-password/);

    await user.getByRole('textbox', { name: 'New password', exact: true }).fill('Rules#Changed123');
    await user.getByRole('textbox', { name: 'Confirm new password' }).fill('Rules#Changed123');
    await user.getByRole('button', { name: 'Update password' }).click();
    await expect(user.getByRole('button', { name: 'User menu' })).toBeVisible();
    await expect(user).toHaveURL(/\/$/);
  });
});

test.describe('application shell', () => {
  test('NFR-012 grouped sidebar marks the current page and sets the document title', async ({ page }) => {
    await adminInCompany(page);
    const nav = page.getByRole('navigation', { name: 'Main navigation' });
    for (const group of ['Overview', 'People', 'Time & attendance', 'Payroll', 'Insights & admin']) {
      await expect(nav.getByRole('group', { name: group })).toBeVisible();
    }
    await expect(nav.getByRole('group', { name: 'People' }).getByRole('link', { name: 'Employee Hub' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');

    await nav.getByRole('link', { name: 'Employee Hub' }).click();
    await expect(page).toHaveURL(/\/employees$/);
    await expect(nav.getByRole('link', { name: 'Employee Hub' })).toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current', 'page');
    await expect(page).toHaveTitle('Employee Hub · Premium HRM');

    // the company chip / switcher always tells the user which company they are working in
    await expect(page.getByRole('combobox', { name: 'Company' })).toHaveValue(CODE);
  });

  test('command palette opens from the keyboard, jumps to pages and finds employees', async ({ page }) => {
    const { employeeId } = await adminInCompany(page);
    // shortcuts are ignored while typing in a field, so start from the page itself
    await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
    await page.keyboard.press('ControlOrMeta+K');
    const search = page.getByRole('textbox', { name: 'Search pages and actions…' });
    await expect(search).toBeFocused();
    await search.fill('payroll');
    await expect(page.getByRole('button', { name: /^Payroll/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Employee Hub/ })).toHaveCount(0);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/payroll$/);

    // the header search button opens the same palette; employees are looked up on the server
    await page.getByRole('button', { name: 'Search' }).first().click();
    await search.fill('Nimal');
    await page.getByRole('button', { name: /Nimal Perera/ }).click();
    await expect(page).toHaveURL(new RegExp(`/employees/${employeeId}$`));
    await expect(search).toBeHidden(); // the shortcut toggles, so wait for the close animation

    await page.keyboard.press('ControlOrMeta+K');
    await search.fill('zzzz-nothing');
    await expect(page.getByText('Nothing found')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(search).toBeHidden();
  });

  test('dark mode can be toggled and is remembered after a reload', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await adminInCompany(page);
    const html = page.locator('html');
    await expect(html).toHaveAttribute('data-mantine-color-scheme', 'light');
    await page.getByRole('button', { name: 'Toggle dark mode' }).click();
    await expect(html).toHaveAttribute('data-mantine-color-scheme', 'dark');
    await page.reload();
    await expect(html).toHaveAttribute('data-mantine-color-scheme', 'dark');
    await page.getByRole('button', { name: 'Toggle dark mode' }).click();
    await expect(html).toHaveAttribute('data-mantine-color-scheme', 'light');
  });

  test('user menu shows the account and signs out', async ({ page }) => {
    await adminInCompany(page);
    await page.getByRole('button', { name: 'User menu' }).click();
    await expect(page.getByRole('menu')).toContainText('@admin');
    await page.getByRole('menuitem', { name: 'Change password' }).click();
    await expect(page).toHaveURL(/change-password/);
    await page.getByRole('link', { name: 'Cancel' }).click();
    await expect(page).toHaveURL(/\/$/);
    await page.getByRole('button', { name: 'User menu' }).click();
    await page.getByRole('menuitem', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);
    await page.goto('/employees');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('unknown routes show a friendly 404 with a way home', async ({ page }) => {
    await adminInCompany(page);
    await page.goto('/this/does/not/exist');
    await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
    await page.getByRole('link', { name: 'Go to dashboard' }).click();
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('heading', { name: /^Good (morning|afternoon|evening)/ })).toBeVisible();
  });

  test('dashboard KPIs and quick actions link into the modules', async ({ page }) => {
    await adminInCompany(page);
    await expect(page.getByText('Active employees', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Attendance today' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Headcount by department' })).toBeVisible();
    await expect(page.getByText('Operations', { exact: true })).toBeVisible();
    await page.getByRole('region', { name: 'Quick actions' }).getByRole('link', { name: /Add employee/ }).click();
    await expect(page).toHaveURL(/\/employees\/new$/);
  });
});

test.describe('state in the URL and keyboard use', () => {
  test('tabs are deep-linkable and survive a reload', async ({ page }) => {
    await adminInCompany(page);
    await page.goto('/organization?tab=branches');
    await expect(page.getByRole('tab', { name: 'Branches' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('cell', { name: 'Colombo', exact: true })).toBeVisible();
    await page.getByRole('tab', { name: 'Departments' }).click();
    await expect(page).toHaveURL(/tab=departments/);
    await page.reload();
    await expect(page.getByRole('tab', { name: 'Departments' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('cell', { name: 'Operations' })).toBeVisible();
  });

  test('table rows open with the keyboard', async ({ page }) => {
    const { employeeId } = await adminInCompany(page);
    await page.goto('/employees');
    const row = page.getByRole('row', { name: /Nimal Perera/ });
    await row.focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(new RegExp(`/employees/${employeeId}$`));
  });

  test('configuration lists create in a drawer and confirm before deleting', async ({ page }) => {
    await adminInCompany(page);
    await page.goto('/organization?tab=branches');
    await page.getByRole('button', { name: 'Create branch' }).click();
    const drawer = page.getByRole('dialog', { name: 'Create branch' });
    await drawer.getByRole('textbox', { name: 'Code' }).fill('KDY');
    await drawer.getByRole('textbox', { name: 'Name' }).fill('Kandy');
    await drawer.getByRole('button', { name: 'Save' }).click();
    await expect(drawer).toBeHidden();
    const row = page.getByRole('row', { name: /Kandy/ });
    await expect(row).toBeVisible();

    await row.getByRole('button', { name: 'Delete' }).click();
    const confirm = page.getByRole('dialog', { name: 'Delete branch?' });
    await expect(confirm).toContainText("can't be undone");
    await confirm.getByRole('button', { name: 'Cancel' }).click();
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('dialog', { name: 'Delete branch?' }).getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByRole('row', { name: /Kandy/ })).toHaveCount(0);
  });
});

test.describe('permissions', () => {
  test('FR-005 employees only see their modules and get a clear message elsewhere', async ({ page, browser }) => {
    const { employeeId } = await adminInCompany(page);
    await createUser(page, 'uxemp', 'Emp#Initial12345', ['EMPLOYEE'], employeeId);
    const employee = await (await browser.newContext()).newPage();
    await loginAs(employee, 'uxemp', 'Emp#Initial12345', 'Emp#Changed12345');
    const nav = employee.getByRole('navigation', { name: 'Main navigation' });
    await expect(nav.getByRole('group').getByRole('link')).toHaveText(['Dashboard', 'My Portal', 'Approvals']);
    await expect(employee.getByRole('region', { name: 'Quick actions' }).getByRole('link', { name: /My leave/ })).toBeVisible();

    await employee.goto('/payroll');
    await expect(employee.getByRole('alert')).toContainText("You don't have access to this page");
    await employee.getByRole('link', { name: 'Go to dashboard' }).click();
    await expect(employee).toHaveURL(/\/$/);
  });
});
