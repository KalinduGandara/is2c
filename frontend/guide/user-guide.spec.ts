/**
 * Generates the screenshots of the user guide (docs/user-guide/images) against a FRESH database.
 * It walks through the whole product as a new customer would: first sign-in, organisation setup,
 * employees, users, attendance, rosters, leave, approvals, payroll, reports and self-service.
 *
 *   cd frontend && npx playwright test -c playwright.guide.config.ts
 */
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { expect, test, type Browser, type BrowserContext, type Locator, type Page } from '@playwright/test';
import { card, field, OUT, shot } from './annotate';

const ADMIN = { username: 'admin', bootstrap: 'Admin#Pass123', password: 'Admin#Pass9876' };
const PASS = 'Welcome#2026x';
const NEW_PASS = 'Changed#2026x';
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const pad = (n: number) => String(n).padStart(2, '0');
const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

/** Simulated time (see playwright.guide.config.ts): the tour happens at 10:30 on a working morning. */
const OFFSET = Number(process.env.GUIDE_CLOCK_OFFSET_MS ?? 0);
const NOW = new Date(Date.now() + OFFSET);
const TODAY = new Date(NOW.getFullYear(), NOW.getMonth(), NOW.getDate());
const PREV = new Date(NOW.getFullYear(), NOW.getMonth() - 1, 1); // payroll month
const PAY_YEAR = PREV.getFullYear();
const PAY_MONTH = PREV.getMonth() + 1;
const PAY_LABEL = `${MONTHS[PAY_MONTH - 1]} ${PAY_YEAR}`;
/** Monday of next week — used for the roster that is planned ahead. */
const NEXT_MONDAY = addDays(TODAY, ((8 - TODAY.getDay()) % 7) || 7);
/** Most recent past weekday — the day an employee "forgot to punch out". */
const MISSED_PUNCH = (() => {
  let d = addDays(TODAY, -1);
  while (d.getDay() === 0 || d.getDay() === 6) d = addDays(d, -1);
  return d;
})();

/** Deterministic pseudo-random numbers so every run produces the same demo data. */
function rng(seed: number) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ---------------------------------------------------------------------------------------------- API helpers

async function api(page: Page, method: string, url: string, body?: unknown) {
  const r = await page.evaluate(
    async ({ method, url, body }) => {
      const refresh = sessionStorage.getItem('hrm.refreshToken');
      const tokens = await (await fetch('/api/v1/auth/refresh', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: refresh }),
      })).json();
      sessionStorage.setItem('hrm.refreshToken', tokens.refreshToken);
      const res = await fetch(`/api/v1${url}`, {
        method,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens.accessToken}` },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await res.text();
      return { status: res.status, body: text ? JSON.parse(text) : null };
    },
    { method, url, body },
  );
  if (r.status >= 300) throw new Error(`${method} ${url} → ${r.status}: ${JSON.stringify(r.body)}`);
  return r.body;
}

/** Leave application on behalf of an employee (multipart with a JSON "data" part). */
async function applyLeave(page: Page, data: Record<string, unknown>) {
  const r = await page.evaluate(async (data) => {
    const refresh = sessionStorage.getItem('hrm.refreshToken');
    const tokens = await (await fetch('/api/v1/auth/refresh', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: refresh }),
    })).json();
    sessionStorage.setItem('hrm.refreshToken', tokens.refreshToken);
    const form = new FormData();
    form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
    const res = await fetch('/api/v1/leave/requests', { method: 'POST', headers: { Authorization: `Bearer ${tokens.accessToken}` }, body: form });
    return { status: res.status, text: await res.text() };
  }, data);
  if (r.status >= 300) throw new Error(`leave → ${r.status}: ${r.text}`);
}

/** Approves everything waiting in this user's inbox. */
async function approveInbox(page: Page, filter: (item: { summary: string; type: string }) => boolean = () => true) {
  const inbox = (await api(page, 'GET', '/approval/inbox')) as { id: string; summary: string; type: string }[];
  for (const item of inbox.filter(filter)) await api(page, 'POST', `/approval/transactions/${item.id}/approve`, { comment: 'Approved' });
}

// ---------------------------------------------------------------------------------------------- UI helpers

async function newPage(browser: Browser, opts: { width?: number; height?: number; dark?: boolean } = {}): Promise<{ ctx: BrowserContext; page: Page }> {
  const ctx = await browser.newContext({
    viewport: { width: opts.width ?? 1440, height: opts.height ?? 900 },
    deviceScaleFactor: 1.25,
    colorScheme: opts.dark ? 'dark' : 'light',
    locale: 'en-GB',
  });
  // Toast notifications would cover the content of the next screenshot.
  await ctx.addInitScript(() => {
    localStorage.setItem('mantine-color-scheme-value', 'light');
    document.addEventListener('DOMContentLoaded', () => {
      const s = document.createElement('style');
      s.textContent = '.mantine-Notifications-root{display:none!important}';
      document.head.appendChild(s);
    });
  });
  await ctx.clock.install({ time: new Date(Date.now() + OFFSET) });
  return { ctx, page: await ctx.newPage() };
}

async function signIn(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username' }).fill(username);
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

/** First sign-in of a user created by an administrator: completes the forced password change. */
async function firstSignIn(page: Page, username: string) {
  await signIn(page, username, PASS);
  await expect(page).toHaveURL(/change-password/);
  await page.getByRole('textbox', { name: 'Current password' }).fill(PASS);
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill(NEW_PASS);
  await page.getByRole('textbox', { name: 'Confirm new password' }).fill(NEW_PASS);
  await page.getByRole('button', { name: 'Update password' }).click();
  await expect(page.getByRole('button', { name: 'User menu' }).first()).toBeVisible();
}

/** Mantine Select / MultiSelect: open by label and click the option. */
async function pick(scope: Page | Locator, page: Page, label: string, option: string | RegExp) {
  await scope.getByRole('textbox', { name: label, exact: true }).or(scope.getByRole('combobox', { name: label, exact: true })).first().click();
  await opt(page, option).click();
}

/** A visible Mantine combobox option (native <option> elements elsewhere on the page are ignored). */
const opt = (page: Page, name: string | RegExp) =>
  page.getByRole('option', { name, exact: typeof name === 'string' }).and(page.locator('[data-combobox-option]')).first();

const nav = (page: Page, name: string) => page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: new RegExp(`^${name}`) });
const drawer = (page: Page) => page.getByRole('dialog').last();

async function go(page: Page, link: string, tab?: string) {
  await nav(page, link).click();
  if (tab) await page.getByRole('tab', { name: tab, exact: true }).click();
  await page.waitForTimeout(300);
}

// ---------------------------------------------------------------------------------------------- demo data

interface Person {
  key: string; first: string; last: string; dept: string; desig: string; grade: string; cat: string; branch: string;
  join: string; basic: number; manager?: string; shift?: string; gender: 'MALE' | 'FEMALE'; allowance?: number;
}

const PEOPLE: Person[] = [
  { key: 'kasun', first: 'Kasun', last: 'Jayasinghe', dept: 'OPS', desig: 'OPM', grade: 'M1', cat: 'EXEC', branch: 'CMB', join: '2019-03-01', basic: 285000, gender: 'MALE', allowance: 25000 },
  { key: 'dilani', first: 'Dilani', last: 'Fernando', dept: 'OPS', desig: 'OPE', grade: 'S1', cat: 'PERM', branch: 'CMB', join: '2023-02-01', basic: 98000, manager: 'kasun', gender: 'FEMALE' },
  { key: 'chamara', first: 'Chamara', last: 'Silva', dept: 'OPS', desig: 'MOP', grade: 'E1', cat: 'CONT', branch: 'KDY', join: '2022-08-15', basic: 72000, manager: 'kasun', shift: 'NIGHT', gender: 'MALE' },
  { key: 'ishara', first: 'Ishara', last: 'Bandara', dept: 'OPS', desig: 'MOP', grade: 'E1', cat: 'PERM', branch: 'KDY', join: '2024-01-08', basic: 68000, manager: 'kasun', gender: 'FEMALE' },
  { key: 'ruwan', first: 'Ruwan', last: 'Wickramasinghe', dept: 'ENG', desig: 'ENL', grade: 'M1', cat: 'EXEC', branch: 'CMB', join: '2018-11-05', basic: 420000, gender: 'MALE', allowance: 40000 },
  { key: 'tharindu', first: 'Tharindu', last: 'Rathnayake', dept: 'ENG-SW', desig: 'SE', grade: 'S2', cat: 'PERM', branch: 'CMB', join: '2022-04-04', basic: 210000, manager: 'ruwan', gender: 'MALE', allowance: 15000 },
  { key: 'sachini', first: 'Sachini', last: 'Gunawardena', dept: 'ENG-SW', desig: 'SE', grade: 'S1', cat: 'PERM', branch: 'CMB', join: '2023-09-11', basic: 185000, manager: 'ruwan', gender: 'FEMALE' },
  { key: 'malith', first: 'Malith', last: 'Herath', dept: 'ENG-QA', desig: 'QAE', grade: 'S1', cat: 'PERM', branch: 'CMB', join: '2024-05-20', basic: 150000, manager: 'ruwan', gender: 'MALE' },
  { key: 'anushka', first: 'Anushka', last: 'Peiris', dept: 'FIN', desig: 'FIM', grade: 'M1', cat: 'EXEC', branch: 'CMB', join: '2020-01-15', basic: 310000, gender: 'FEMALE', allowance: 25000 },
  { key: 'hasini', first: 'Hasini', last: 'Abeysekara', dept: 'FIN', desig: 'ACC', grade: 'S1', cat: 'PERM', branch: 'CMB', join: '2023-03-01', basic: 125000, manager: 'anushka', gender: 'FEMALE' },
  { key: 'anjali', first: 'Anjali', last: 'Wijesinghe', dept: 'HR', desig: 'HRM', grade: 'M1', cat: 'EXEC', branch: 'CMB', join: '2019-07-01', basic: 265000, gender: 'FEMALE', allowance: 20000 },
];

// ---------------------------------------------------------------------------------------------- the tour

test('user guide screenshots', async ({ browser }) => {
  test.setTimeout(30 * 60_000);
  mkdirSync(test.info().outputDir, { recursive: true });
  // start from an empty image folder so renamed or removed screenshots do not linger
  for (const f of readdirSync(OUT)) if (f.endsWith('.png')) rmSync(path.join(OUT, f));
  const { page } = await newPage(browser);
  const ids: Record<string, string> = {};
  const emp: Record<string, { id: string; number: string }> = {};
  let cid = '';

  // ------------------------------------------------------------------ 1. first sign-in
  await test.step('first sign-in', async () => {
    await page.goto('/login');
    await expect(page.getByRole('button', { name: 'Sign in' }).first()).toBeVisible();
    await shot(page, 'login');
    await page.getByRole('textbox', { name: 'Username' }).fill(ADMIN.username);
    await page.getByRole('textbox', { name: 'Password', exact: true }).fill(ADMIN.bootstrap);
    await shot(page, 'login-filled', [
      { el: page.getByRole('textbox', { name: 'Username' }), n: 1, text: 'Username given by your administrator', place: 'left' },
      { el: page.getByRole('textbox', { name: 'Password', exact: true }), n: 2, text: 'Password (case-sensitive)', place: 'left' },
      { el: page.getByRole('button', { name: 'Sign in' }), n: 3, text: 'Click to sign in', place: 'left' },
    ]);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/change-password/);
    await page.getByRole('textbox', { name: 'Current password' }).fill(ADMIN.bootstrap);
    await page.getByRole('textbox', { name: 'New password', exact: true }).fill(ADMIN.password);
    await page.getByRole('textbox', { name: 'Confirm new password' }).fill(ADMIN.password);
    await shot(page, 'change-password', [
      { el: page.getByRole('textbox', { name: 'Current password' }), n: 1, text: 'The temporary password', place: 'left' },
      { el: page.getByRole('textbox', { name: 'New password', exact: true }), n: 2, text: 'New password — the checklist turns green as each rule is met', place: 'left' },
      { el: page.getByRole('textbox', { name: 'Confirm new password' }), n: 3, place: 'left', text: 'Type it again' },
      { el: page.getByRole('button', { name: 'Update password' }), n: 4, place: 'left', text: 'Save and continue' },
    ]);
    await page.getByRole('button', { name: 'Update password' }).click();
    await expect(page.getByRole('button', { name: 'User menu' }).first()).toBeVisible();
    await shot(page, 'dashboard-first-run', [
      { el: page.getByRole('navigation', { name: 'Main navigation' }), n: 1, text: 'Main menu — only the modules you have access to are listed', place: 'right', pad: 0 },
      { el: page.getByRole('button', { name: 'Search' }).first(), n: 2, text: 'Search pages and actions (Ctrl/⌘ + K)' },
      { el: page.getByRole('button', { name: 'Toggle dark mode' }), n: 3 },
      { el: page.getByRole('button', { name: 'User menu' }), n: 4, text: 'Your profile, password and sign-out', place: 'below', badgeAt: 'tr' },
    ]);
  });

  // ------------------------------------------------------------------ 2. company
  await test.step('company', async () => {
    await go(page, 'Organization Setup');
    await page.getByRole('button', { name: 'Create company' }).click();
    const d = drawer(page);
    await d.getByRole('textbox', { name: 'Code', exact: true }).fill('ACME');
    await d.getByLabel('Legal name').fill('ACME Holdings (Pvt) Ltd');
    await d.getByLabel('Registration number').fill('PV 00123456');
    await d.getByLabel('Address').fill('No. 25, Galle Road, Colombo 03');
    await d.getByLabel('Phone').fill('+94 11 234 5678');
    await d.getByLabel('Email').fill('hr@acme.example');
    await d.getByLabel('EPF employer number').fill('A/12345');
    await d.getByLabel('ETF number').fill('E/67890');
    await shot(page, 'org-company-create', [
      { el: d.getByRole('textbox', { name: 'Code', exact: true }), n: 1, text: 'Short unique code (used in employee numbers)', place: 'left' },
      { el: d.getByLabel('Legal name'), n: 2, text: 'Registered company name — printed on payslips', place: 'left' },
      { el: d.getByLabel('Registration number'), n: 3, text: 'Optional legal details', place: 'left' },
    ], { numbersOnly: true });
    await shot(page, 'org-company-create-2', [
      { el: d.getByRole('button', { name: 'Save' }), n: 6, text: 'Save the company', place: 'left' },
      { el: field(d.getByLabel('EPF employer number')), n: 4 },
      { el: d.getByLabel('Statutory pack'), n: 5, text: 'LK = Sri Lankan EPF / ETF / APIT rules', place: 'left' },
    ], { numbersOnly: true });
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'ACME Holdings (Pvt) Ltd' }).first()).toBeVisible();
    const companies = (await api(page, 'GET', '/companies')) as { id: string; code: string }[];
    cid = companies.find((c) => c.code === 'ACME')!.id;
    await page.reload();
    await expect(page.getByRole('tab', { name: 'Settings' }).first()).toBeVisible();
    await shot(page, 'org-companies', [
      { el: page.getByRole('tablist'), n: 1, text: 'Setup sections for the selected company', place: 'below' },
      { el: page.getByRole('banner').getByText('ACME', { exact: true }), n: 2, text: 'Working company', place: 'below' },
    ]);
  });

  // ------------------------------------------------------------------ 3. settings
  await test.step('company settings', async () => {
    await page.getByRole('tab', { name: 'Settings' }).click();
    await page.getByRole('combobox', { name: 'Default weekly-off days' }).click();
    await page.getByRole('option', { name: 'Saturday' }).click();
    await page.keyboard.press('Escape');
    await page.getByLabel('Employee number format').fill('{COMPANY}-{SEQ:4}');
    await shot(page, 'org-settings', [
      { el: field(page.getByRole('combobox', { name: 'Default weekly-off days' })), n: 1, text: 'Weekly days off (here Saturday and Sunday)', place: 'right' },
      { el: field(page.getByLabel('Employee number format')), n: 2, text: 'Numbering pattern', place: 'right' },
      { el: page.getByText('Next number looks like').locator('..'), n: 3, text: 'Live preview of the next number', place: 'right' },
    ]);
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    await page.waitForTimeout(800);
    await page.getByLabel('Payroll period start day (1-28)').scrollIntoViewIfNeeded();
    await shot(page, 'org-settings-payroll', [
      { el: field(page.getByLabel('Payroll period start day (1-28)')), n: 1 },
      { el: field(page.getByLabel('No-pay divisor (days)')), n: 2 },
      { el: page.getByText('Treat absent days as no-pay').locator('xpath=ancestor::div[contains(@class,"mantine-Checkbox-root")][1]'), n: 3 },
      { el: card(page.getByText('Attendance punch window', { exact: true })), n: 4 },
      { el: field(page.getByRole('combobox', { name: 'Roster week starts on' })), n: 5 },
    ]);
  });

  // ------------------------------------------------------------------ 4. structure
  await test.step('branches, departments, lookups', async () => {
    await page.getByRole('tab', { name: 'Branches' }).click();
    await page.getByRole('button', { name: 'Create branch' }).click();
    const d = drawer(page);
    await d.getByRole('textbox', { name: 'Code', exact: true }).fill('CMB');
    await d.getByRole('textbox', { name: 'Name', exact: true }).fill('Colombo Head Office');
    await d.getByLabel('Address').fill('No. 25, Galle Road, Colombo 03');
    await d.getByLabel('Phone').fill('+94 11 234 5678');
    await shot(page, 'org-branch-create', [
      { el: d.getByRole('textbox', { name: 'Code', exact: true }), n: 1, place: 'left', text: 'Branch code' },
      { el: d.getByRole('textbox', { name: 'Name', exact: true }), n: 2, place: 'left', text: 'Branch name' },
      { el: d.getByRole('button', { name: 'Save' }), n: 3, place: 'left', text: 'Save the branch' },
    ]);
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'Colombo Head Office' }).first()).toBeVisible();
    const branches = (await api(page, 'GET', `/companies/${cid}/branches`)) as { id: string; code: string }[];
    ids.CMB = branches[0].id;
    ids.KDY = (await api(page, 'POST', `/companies/${cid}/branches`, { code: 'KDY', name: 'Kandy Factory', address: 'Peradeniya Road, Kandy', phone: '+94 81 222 3344' })).id;
    await page.reload();
    await shot(page, 'org-branches');

    for (const [code, name, parent, branch, cc] of [
      ['OPS', 'Operations', null, null, 'CC-100'],
      ['ENG', 'Engineering', null, 'CMB', 'CC-200'],
      ['ENG-SW', 'Software Development', 'ENG', 'CMB', 'CC-210'],
      ['FIN', 'Finance', null, 'CMB', 'CC-300'],
      ['HR', 'Human Resources', null, 'CMB', 'CC-400'],
    ] as const) {
      ids[code] = (await api(page, 'POST', `/companies/${cid}/departments`, {
        code, name, parentId: parent ? ids[parent] : null, branchId: branch ? ids[branch] : null, costCenterCode: cc,
      })).id;
    }
    await page.getByRole('tab', { name: 'Departments' }).click();
    await page.getByRole('button', { name: 'Create department' }).click();
    const dd = drawer(page);
    await dd.getByRole('textbox', { name: 'Code', exact: true }).fill('ENG-QA');
    await dd.getByRole('textbox', { name: 'Name', exact: true }).fill('Quality Assurance');
    await pick(dd, page, 'Parent department', 'ENG — Engineering');
    await pick(dd, page, 'Branch', 'CMB — Colombo Head Office');
    await dd.getByLabel('Cost center code').fill('CC-220');
    await shot(page, 'org-department-create', [
      { el: dd.getByRole('combobox', { name: 'Parent department' }), n: 1, place: 'left', text: 'Pick a parent to build a hierarchy' },
      { el: dd.getByLabel('Cost center code'), n: 2, place: 'left', text: 'Optional cost center for reporting' },
    ]);
    await dd.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByText('Quality Assurance').first()).toBeVisible();
    const deps = (await api(page, 'GET', `/companies/${cid}/departments`)) as { id: string; code: string }[];
    ids['ENG-QA'] = deps.find((x) => x.code === 'ENG-QA')!.id;
    await page.mouse.move(0, 0);
    await shot(page, 'org-departments');

    for (const [code, name] of [['OPM', 'Operations Manager'], ['OPE', 'Operations Executive'], ['MOP', 'Machine Operator'], ['ENL', 'Engineering Lead'],
      ['SE', 'Software Engineer'], ['QAE', 'QA Engineer'], ['FIM', 'Finance Manager'], ['ACC', 'Accountant'], ['HRM', 'HR Manager']]) {
      ids[`d_${code}`] = (await api(page, 'POST', `/companies/${cid}/designations`, { code, name })).id;
    }
    for (const [code, name, rank] of [['E1', 'Entry', 1], ['S1', 'Staff', 2], ['S2', 'Senior Staff', 3], ['M1', 'Manager', 4]] as const) {
      ids[`g_${code}`] = (await api(page, 'POST', `/companies/${cid}/grades`, { code, name, rank })).id;
    }
    for (const [code, name, ot] of [['PERM', 'Permanent', true], ['CONT', 'Contract', true], ['EXEC', 'Executive', false]] as const) {
      ids[`c_${code}`] = (await api(page, 'POST', `/companies/${cid}/employment-categories`, { code, name, otEligible: ot })).id;
    }
    await page.getByRole('tab', { name: 'Designations' }).click();
    await shot(page, 'org-designations');
    await page.getByRole('tab', { name: 'Employment categories' }).click();
    await shot(page, 'org-categories', [{ el: page.getByRole('row').filter({ hasText: 'Executive' }).getByRole('cell').nth(2), n: 1, text: 'Executives are not paid overtime', place: 'below' }]);

    const year = TODAY.getFullYear();
    for (const [date, name, type] of [
      [`${year}-01-14`, 'Tamil Thai Pongal Day', 'PUBLIC'], [`${year}-02-04`, 'Independence Day', 'PUBLIC'], [`${year}-04-14`, 'Sinhala & Tamil New Year Day', 'PUBLIC'],
      [`${year}-05-01`, 'May Day', 'PUBLIC'], [`${PAY_YEAR}-${pad(PAY_MONTH)}-18`, 'Company Foundation Day', 'COMPANY'],
      [`${year}-12-25`, 'Christmas Day', 'PUBLIC'], [`${year}-12-31`, 'Year-end Bank Holiday', 'MERCANTILE'],
    ] as const) {
      await api(page, 'POST', `/companies/${cid}/holidays`, { date, name, type, allBranches: true, branchIds: [] });
    }
    await page.getByRole('tab', { name: 'Holidays' }).click();
    await expect(page.getByText('Independence Day').first()).toBeVisible();
    await shot(page, 'org-holidays', [
      { el: page.getByRole('button', { name: 'Copy year…' }), n: 1, text: 'Copy this year’s holidays to next year', place: 'left' },
      { el: page.getByRole('button', { name: 'Create holiday' }), n: 2, text: 'Add a holiday', place: 'below' },
    ]);

    await page.getByRole('tab', { name: 'Custom fields' }).click();
    await page.getByRole('button', { name: 'Create custom field' }).click();
    const cf = drawer(page);
    await cf.getByRole('textbox', { name: 'Key', exact: true }).fill('blood_group');
    await cf.getByRole('textbox', { name: 'Label', exact: true }).fill('Blood group');
    await pick(cf, page, 'Type', 'Dropdown list');
    await cf.getByLabel('Options (one per line)').fill('A+\nA-\nB+\nB-\nAB+\nAB-\nO+\nO-');
    await shot(page, 'org-custom-field', [
      { el: cf.getByRole('textbox', { name: 'Key', exact: true }), n: 1, place: 'left', text: 'Internal key (cannot change later)' },
      { el: cf.getByRole('combobox', { name: 'Type' }), n: 2, place: 'left', text: 'Field type' },
      { el: cf.getByLabel('Options (one per line)'), n: 3, place: 'left', text: 'Choices for a dropdown' },
    ], { numbersOnly: true });
    await cf.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'Blood group' }).first()).toBeVisible();
  });

  // ------------------------------------------------------------------ 5. shifts & OT
  await test.step('shifts and OT policies', async () => {
    await go(page, 'Shift & Roster', 'OT policies');
    await page.getByRole('button', { name: 'Create OT policy' }).click();
    const d = drawer(page);
    await d.getByRole('textbox', { name: 'Name', exact: true }).fill('Standard OT');
    await d.getByLabel('Maximum OT minutes per day').fill('240');
    await d.getByLabel('Overtime requires approval').check();
    await shot(page, 'shift-ot-policy', [
      { el: d.getByLabel('Minimum OT minutes'), n: 1, place: 'left', text: 'Shorter overtime is ignored' },
      { el: d.getByLabel('Round down to (minutes)'), n: 2, place: 'left', text: 'OT is rounded down to this unit' },
      { el: d.getByLabel('Maximum OT minutes per day'), n: 3, place: 'left', text: 'Daily cap' },
      { el: d.getByText('Overtime requires approval').locator('xpath=ancestor::div[contains(@class,"mantine-Checkbox-root")][1]'), n: 4, place: 'left', text: 'OT is paid only after a manager approves it' },
    ], { numbersOnly: true });
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'Standard OT' }).first()).toBeVisible();
    ids.ot = ((await api(page, 'GET', `/companies/${cid}/ot-policies`)) as { id: string }[])[0].id;

    await page.getByRole('tab', { name: 'Shifts', exact: true }).click();
    await page.getByRole('button', { name: 'Create shift' }).click();
    const s = drawer(page);
    await s.getByRole('textbox', { name: 'Code', exact: true }).fill('DAY');
    await s.getByRole('textbox', { name: 'Name', exact: true }).fill('Day shift');
    await s.getByLabel('Colour (#RRGGBB)').fill('#4C6EF5');
    await s.getByRole('textbox', { name: 'Start', exact: true }).fill('08:30');
    await s.getByRole('textbox', { name: 'End', exact: true }).fill('17:30');
    await s.getByLabel('Late grace (minutes)').fill('5');
    await s.getByLabel('Break minutes').fill('60');
    await s.getByLabel('Deduct break when worked ≥ (minutes)').fill('300');
    await s.getByLabel('Half day when worked < (minutes)').fill('240');
    await pick(s, page, 'OT policy', 'Standard OT');
    await s.getByRole('textbox', { name: 'Code', exact: true }).scrollIntoViewIfNeeded();
    await shot(page, 'shift-create', [
      { el: s.getByRole('textbox', { name: 'Code', exact: true }), n: 1 },
      { el: s.getByRole('combobox', { name: 'Type' }), n: 2 },
      { el: s.getByLabel('Colour (#RRGGBB)'), n: 3 },
      { el: s.getByRole('textbox', { name: 'Start', exact: true }), n: 4 },
      { el: s.getByRole('textbox', { name: 'End', exact: true }), n: 5 },
      { el: s.getByLabel('Late grace (minutes)'), n: 6 },
    ], { numbersOnly: true });
    await s.getByRole('button', { name: 'Save' }).scrollIntoViewIfNeeded();
    await shot(page, 'shift-create-2', [
      { el: s.getByLabel('Early-departure grace (minutes)'), n: 7 },
      { el: s.getByRole('combobox', { name: 'Break mode' }), n: 8 },
      { el: s.getByLabel('Break minutes'), n: 9 },
      { el: s.getByLabel('Deduct break when worked ≥ (minutes)'), n: 10 },
      { el: s.getByLabel('Half day when worked < (minutes)'), n: 11 },
      { el: s.getByRole('combobox', { name: 'OT policy' }), n: 12 },
      { el: s.getByRole('button', { name: 'Save' }), n: 13 },
    ], { numbersOnly: true });
    await s.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: '08:30–17:30' }).first()).toBeVisible();
    await api(page, 'POST', `/companies/${cid}/shifts`, {
      code: 'NIGHT', name: 'Night shift', type: 'NIGHT', color: '#7048E8', s1Start: '20:00', s1End: '06:00', lateGraceMinutes: 5,
      breakMode: 'FIXED', breakMinutes: 60, breakThresholdMinutes: 300, otPolicyId: ids.ot,
    });
    await api(page, 'POST', `/companies/${cid}/shifts`, {
      code: 'FLEX', name: 'Flexible office', type: 'FLEXIBLE', color: '#12B886', flexEarliestStart: '07:00', flexLatestEnd: '20:00', requiredMinutes: 480,
      breakMode: 'FIXED', breakMinutes: 60, breakThresholdMinutes: 300, lateGraceMinutes: 0, earlyGraceMinutes: 0,
    });
    const shifts = (await api(page, 'GET', `/companies/${cid}/shifts`)) as { id: string; code: string }[];
    for (const sh of shifts) ids[`s_${sh.code}`] = sh.id;
    await page.reload();
    await shot(page, 'shift-list');
  });

  // ------------------------------------------------------------------ 6. employees
  await test.step('employees', async () => {
    for (const p of PEOPLE) {
      const r = await api(page, 'POST', '/employees', {
        companyId: cid, branchId: ids[p.branch], departmentId: ids[p.dept], designationId: ids[`d_${p.desig}`], gradeId: ids[`g_${p.grade}`],
        categoryId: ids[`c_${p.cat}`], firstName: p.first, lastName: p.last, joinDate: p.join, gender: p.gender, nationality: 'Sri Lankan',
        reportingManagerId: p.manager ? emp[p.manager].id : null, defaultShiftId: ids[`s_${p.shift ?? (p.dept.startsWith('ENG') ? 'FLEX' : 'DAY')}`],
        personalEmail: `${p.first.toLowerCase()}.${p.last.toLowerCase()}@acme.example`, mobile: `+94 77 ${String(1000000 + PEOPLE.indexOf(p) * 13579).slice(0, 3)} ${String(4000 + PEOPLE.indexOf(p) * 37)}`,
        badgeId: `B${100 + PEOPLE.indexOf(p) + 1}`, customFields: {},
      });
      emp[p.key] = { id: r.id, number: r.employeeNumber };
    }

    // Nimal is added through the form, as an HR officer would.
    await go(page, 'Employee Hub');
    await page.getByRole('button', { name: 'New employee' }).click();
    await expect(page.getByRole('heading', { name: 'New employee' }).first()).toBeVisible();
    await pick(page, page, 'Title', 'Mr');
    await page.getByLabel('First name').fill('Nimal');
    await page.getByLabel('Last name').fill('Perera');
    await page.getByLabel('NIC / passport').fill('199012345678');
    await page.getByRole('textbox', { name: 'Date of birth' }).fill('1990-05-12');
    await page.keyboard.press('Escape');
    await page.getByRole('heading', { name: 'New employee' }).click();
    await expect(page.locator('[data-dates-dropdown]')).toHaveCount(0);
    await pick(page, page, 'Gender', 'Male');
    await pick(page, page, 'Marital status', 'Married');
    await page.getByLabel('Nationality').fill('Sri Lankan');
    await page.getByLabel('Personal email').fill('nimal.perera@acme.example');
    await page.getByLabel('Mobile').fill('+94 77 123 4567');
    await shot(page, 'employee-new-personal', [
      { el: field(page.getByLabel('First name')), n: 1 },
      { el: field(page.getByLabel('NIC / passport')), n: 2 },
      { el: field(page.getByLabel('Personal email')), n: 3 },
    ], { numbersOnly: true });
    for (const [label, option] of [['Branch', 'CMB — Colombo Head Office'], ['Department', 'OPS — Operations'], ['Designation', 'OPE — Operations Executive'],
      ['Grade', 'S2 — Senior Staff'], ['Employment category', 'PERM — Permanent']] as const) {
      await pick(page, page, label, option);
    }
    await page.getByRole('textbox', { name: 'Join date' }).fill('2021-06-14');
    await page.keyboard.press('Escape');
    await page.getByRole('heading', { name: 'New employee' }).click();
    await expect(page.locator('[data-dates-dropdown]')).toHaveCount(0);
    await pick(page, page, 'Reporting manager', /Kasun Jayasinghe/);
    await page.getByLabel('Attendance badge / device ID').fill('B112');
    await pick(page, page, 'Default shift', 'DAY — Day shift');
    await page.getByLabel('Employee number').scrollIntoViewIfNeeded();
    await shot(page, 'employee-new-employment', [
      { el: field(page.getByLabel('Employee number')), n: 4 },
      { el: field(page.getByRole('textbox', { name: 'Join date' })), n: 5 },
      { el: field(page.getByRole('combobox', { name: 'Reporting manager' })), n: 6 },
      { el: field(page.getByLabel('Attendance badge / device ID')), n: 7 },
      { el: field(page.getByRole('combobox', { name: 'Default shift' })), n: 8 },
      { el: field(page.getByText('Weekly-off days', { exact: true })), n: 9 },
    ], { numbersOnly: true });
    await page.getByRole('button', { name: 'Add contact' }).click();
    await page.getByRole('textbox', { name: 'Name', exact: true }).fill('Kumari Perera');
    await page.getByLabel('Relationship').fill('Spouse');
    await page.getByRole('textbox', { name: 'Phone', exact: true }).fill('+94 71 765 4321');
    await pick(page, page, 'Blood group', 'O+');
    await page.getByRole('button', { name: 'Add contact' }).scrollIntoViewIfNeeded();
    await shot(page, 'employee-new-contacts', [
      { el: page.getByRole('button', { name: 'Add contact' }), n: 10 },
      { el: card(page.getByRole('combobox', { name: 'Blood group' })), n: 11 },
      { el: page.getByRole('button', { name: 'Save' }), n: 12 },
    ], { numbersOnly: true });
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('heading', { name: 'Nimal Perera' }).first()).toBeVisible();
    emp.nimal = { id: page.url().split('/').pop()!.split('?')[0], number: '' };
    emp.nimal.number = ((await api(page, 'GET', `/employees/${emp.nimal.id}`)) as { employeeNumber: string }).employeeNumber;
    await page.evaluate(() => window.scrollTo(0, 0));
    await shot(page, 'employee-profile', [
      { el: page.getByRole('main').getByText(emp.nimal.number, { exact: true }), n: 1, text: 'Generated employee number', place: 'right' },
      { el: page.getByRole('tablist'), n: 2, text: 'Profile, history, documents and salary', place: 'right' },
      { el: page.getByRole('button', { name: 'Change status' }), n: 3, text: 'Resign, terminate or deactivate', place: 'left' },
    ]);

    // documents
    const pdf = path.join(test.info().outputDir, 'Appointment-letter.pdf');
    writeFileSync(pdf, '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n');
    await page.getByRole('tab', { name: 'Documents' }).click();
    await pick(page, page, 'Type', 'Contract');
    await page.getByLabel('Title').fill('Appointment letter');
    await page.locator('input[type=file]').setInputFiles(pdf);
    await shot(page, 'employee-documents-upload', [
      { el: field(page.getByRole('combobox', { name: 'Type' })), n: 1 },
      { el: field(page.getByLabel('Title')), n: 2 },
      { el: field(page.getByLabel('Expiry')), n: 3 },
      { el: field(page.getByText(/^File\s*\*?$/)), n: 4 },
      { el: page.getByRole('button', { name: 'Upload' }), n: 5 },
    ], { numbersOnly: true });
    await page.getByRole('button', { name: 'Upload' }).click();
    await expect(page.getByRole('cell', { name: /Appointment letter/ }).first()).toBeVisible();
    await shot(page, 'employee-documents');

    // a promotion, so the history shows more than the joining event
    await page.getByRole('tab', { name: 'Profile' }).click();
    await pick(page, page, 'Grade', 'M1 — Manager');
    await page.getByRole('textbox', { name: 'Change effective date' }).fill('2025-07-01');
    await page.keyboard.press('Escape');
    await page.getByLabel('Reason for change').click();
    await page.getByLabel('Reason for change').fill('Annual promotion 2025');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByText('All changes saved.')).toBeVisible();
    await page.getByRole('tab', { name: 'History' }).click();
    await expect(page.getByRole('cell', { name: 'Joined' }).first()).toBeVisible();
    await shot(page, 'employee-history');

    // salary structure
    const comps = (await api(page, 'GET', `/companies/${cid}/payroll-components`)) as { id: string; code: string }[];
    for (const c of comps) ids[`p_${c.code}`] = c.id;
    await page.getByRole('tab', { name: 'Salary' }).click();
    await page.getByRole('button', { name: 'New structure' }).click();
    const m = page.getByRole('dialog');
    await m.getByLabel('Effective from').fill('2025-01-01');
    await page.keyboard.press('Tab');
    await m.getByLabel('Basic salary').fill('145000');
    await m.getByRole('button', { name: 'Add component' }).click();
    await m.getByRole('combobox', { name: 'Component 1' }).click();
    await page.getByRole('option', { name: /^ALLOWANCE/ }).first().click();
    await m.getByRole('textbox', { name: 'Amount 1' }).fill('10000');
    await shot(page, 'employee-salary-new', [
      { el: field(m.getByLabel('Effective from')), n: 1 },
      { el: field(m.getByLabel('Basic salary')), n: 2 },
      { el: m.getByRole('combobox', { name: 'Component 1' }), n: 3 },
      { el: m.getByRole('textbox', { name: 'Amount 1' }), n: 4 },
      { el: m.getByRole('button', { name: 'Add component' }), n: 5 },
    ], { numbersOnly: true });
    await m.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByText('Current', { exact: true }).first()).toBeVisible();
    await shot(page, 'employee-salary');

    for (const p of PEOPLE) {
      await api(page, 'POST', `/employees/${emp[p.key].id}/salary-structures`, {
        effectiveFrom: p.join < '2025-01-01' ? '2025-01-01' : p.join, basicSalary: p.basic,
        lines: p.allowance ? [{ componentId: ids.p_ALLOWANCE, amount: p.allowance, rateOverride: null, multiplierOverride: null, excluded: false }] : [],
      });
    }
    await go(page, 'Employee Hub');
    await expect(page.getByRole('cell', { name: /Nimal Perera/ }).first()).toBeVisible();
    await shot(page, 'employee-list', [
      { el: card(page.getByRole('textbox', { name: 'Search employees' })), n: 1, text: 'Search by number, name, NIC or badge; filter by department and status', place: 'right' },
      { el: page.getByRole('button', { name: 'New employee' }), n: 2, text: 'Add an employee', place: 'left' },
      { el: page.getByRole('row', { name: /Kasun Jayasinghe/ }), n: 3, text: 'Click a row to open the employee', place: 'below' },
    ]);
  });

  // ------------------------------------------------------------------ 7. users & roles
  await test.step('users and roles', async () => {
    await go(page, 'Administration', 'Users');
    await page.getByRole('button', { name: 'Create user' }).click();
    const d = drawer(page);
    await d.getByLabel('Username').fill('nimal');
    await d.getByLabel('Display name').fill('Nimal Perera');
    await d.getByLabel('Email').fill('nimal.perera@acme.example');
    await d.getByLabel('Initial password').fill(PASS);
    // "Own records only" is the default scope (clicking it again would clear it)
    await pick(d, page, 'Linked employee (self-service)', /Nimal Perera/);
    await pick(d, page, 'Roles', 'Employee');
    await d.getByRole('heading', { name: 'Create user' }).click();
    await expect(d.getByRole('combobox', { name: 'Data scope' })).toHaveValue('Own records only');
    await shot(page, 'admin-user-create', [
      { el: field(d.getByLabel('Username')), n: 1, place: 'left', text: 'Sign-in name' },
      { el: field(d.getByLabel('Initial password')), n: 2, place: 'left', text: 'Temporary — changed at first sign-in' },
      { el: field(d.getByRole('combobox', { name: 'Roles' })), n: 3, place: 'left', text: 'What the user may do' },
      { el: field(d.getByRole('combobox', { name: 'Data scope' })), n: 4, place: 'left', text: 'Whose records the user may see' },
      { el: field(d.getByRole('combobox', { name: 'Linked employee (self-service)' })), n: 5, place: 'left', text: 'Links the login to an employee for My Portal' },
    ]);
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: /nimal/ }).first()).toBeVisible();

    const user = (body: Record<string, unknown>) => api(page, 'POST', '/admin/users', { password: PASS, active: true, ...body });
    await user({ username: 'hr.anjali', displayName: 'Anjali Wijesinghe', email: 'anjali.wijesinghe@acme.example', roles: ['HR_MANAGER'], scopeType: 'ALL', employeeId: emp.anjali.id });
    await user({ username: 'kasun', displayName: 'Kasun Jayasinghe', email: 'kasun.jayasinghe@acme.example', roles: ['DEPARTMENT_MANAGER'], scopeType: 'DEPARTMENT', scopeDepartmentIds: [ids.OPS], employeeId: emp.kasun.id });
    await user({ username: 'payroll.hasini', displayName: 'Hasini Abeysekara', email: 'hasini.abeysekara@acme.example', roles: ['PAYROLL_OFFICER'], scopeType: 'COMPANY', scopeCompanyIds: [cid], employeeId: emp.hasini.id });
    await page.reload();
    await shot(page, 'admin-users', [
      { el: page.getByRole('button', { name: 'User actions' }).last(), n: 1, text: 'Edit, reset password, deactivate', place: 'left' },
    ]);

    await page.getByRole('tab', { name: 'Roles & permissions' }).click();
    await shot(page, 'admin-roles');
    await page.getByText('HR Manager', { exact: true }).first().click();
    await expect(drawer(page).getByText('Permissions', { exact: true }).first()).toBeVisible();
    await shot(page, 'admin-role-permissions', [
      { el: drawer(page).getByRole('checkbox', { name: 'All EMPLOYEE permissions' }), n: 1, text: 'Tick a module to grant all its actions…', place: 'left' },
      { el: drawer(page).getByRole('checkbox', { name: 'PAYROLL:VIEW' }), n: 2, text: '…or tick single actions', place: 'right' },
    ]);
    await page.keyboard.press('Escape');
  });

  // ------------------------------------------------------------------ 8. attendance
  let apiKey = '';
  await test.step('attendance devices and data', async () => {
    await go(page, 'Attendance', 'Import & devices');
    await page.getByLabel('Source name').fill('Main gate terminal');
    await shot(page, 'attendance-source-add', [
      { el: page.getByLabel('Source name'), n: 1, text: 'Name the device or system', place: 'above' },
      { el: page.getByRole('button', { name: 'Add source' }), n: 2, text: 'Create it and get an API key', place: 'right' },
    ]);
    await page.getByRole('button', { name: 'Add source' }).click();
    const m = page.getByRole('dialog');
    await expect(m.getByText('Shown only once').first()).toBeVisible();
    apiKey = await m.getByLabel('API key').inputValue();
    await shot(page, 'attendance-api-key', [
      { el: m.getByText('Shown only once').locator('..').locator('..'), n: 1, text: 'Copy the key now — it is never shown again', place: 'right' },
      { el: m.getByRole('button', { name: 'Copy', exact: true }), n: 2, text: 'Copy to clipboard', place: 'below' },
      { el: m.getByText('How to use it').locator('..'), n: 3, text: 'Give the endpoint and key to the device vendor', place: 'right' },
    ]);
    await m.getByRole('button', { name: 'I have saved the key' }).click();

    // ~6 weeks of punches for everyone (deterministic variety: late arrivals, overtime, an absence, a missed punch)
    const rand = rng(42);
    const events: Record<string, string>[] = [];
    const holidays = new Set([`${PAY_YEAR}-${pad(PAY_MONTH)}-18`]);
    const leaveDays = new Set([`tharindu|${PAY_YEAR}-${pad(PAY_MONTH)}-08`, `tharindu|${PAY_YEAR}-${pad(PAY_MONTH)}-09`, `sachini|${PAY_YEAR}-${pad(PAY_MONTH)}-22`,
      `dilani|${PAY_YEAR}-${pad(PAY_MONTH)}-14`, `hasini|${PAY_YEAR}-${pad(PAY_MONTH)}-25`]);
    const t = (date: Date, minutes: number) => {
      const d = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, minutes);
      return `${iso(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
    };
    const everyone = [...PEOPLE.map((p) => ({ key: p.key, badge: `B${100 + PEOPLE.indexOf(p) + 1}`, shift: p.shift ?? (p.dept.startsWith('ENG') ? 'FLEX' : 'DAY') })),
      { key: 'nimal', badge: 'B112', shift: 'DAY' }];
    for (let day = new Date(PREV); day < TODAY; day = addDays(day, 1)) {
      const dow = day.getDay();
      if (dow === 0 || dow === 6 || holidays.has(iso(day))) continue;
      for (const e of everyone) {
        if (leaveDays.has(`${e.key}|${iso(day)}`)) continue;
        const r = rand();
        if (r < 0.025) continue; // absent
        const push = (minutes: number, direction: string) => events.push({
          externalId: `${e.badge}-${t(day, minutes)}-${direction}`, deviceId: 'GATE-01', employeeIdentifier: e.badge, timestamp: t(day, minutes), direction,
        });
        if (e.shift === 'NIGHT') {
          push(19 * 60 + 40 + Math.floor(rand() * 25), 'IN');
          push(30 * 60 + Math.floor(rand() * 70), 'OUT');
          continue;
        }
        const base = e.shift === 'FLEX' ? 8 * 60 + 30 : 8 * 60 + 30;
        const late = r > 0.86 ? 10 + Math.floor(rand() * 35) : -Math.floor(rand() * 20);
        push(base + late, 'IN');
        if (e.key === 'dilani' && iso(day) === iso(MISSED_PUNCH)) continue; // forgot to punch out
        const ot = rand() > 0.6 ? 45 + Math.floor(rand() * 120) : Math.floor(rand() * 20);
        push(17 * 60 + 30 + ot, 'OUT');
      }
    }
    // this morning (10:30): most people are in, two are late, one has not arrived, one is on leave
    for (const e of everyone) {
      if (e.shift === 'NIGHT' || e.key === 'malith' || e.key === 'sachini') continue;
      const late = e.key === 'ishara' || e.key === 'hasini' ? 22 + (e.key === 'hasini' ? 11 : 0) : -Math.floor(rand() * 20);
      const ts = t(TODAY, 8 * 60 + 30 + late);
      events.push({ externalId: `${e.badge}-${ts}-IN`, deviceId: 'GATE-01', employeeIdentifier: e.badge, timestamp: ts, direction: 'IN' });
    }
    const ingest = await page.request.post('/api/v1/integrations/attendance/events', { headers: { 'X-Api-Key': apiKey }, data: { events } });
    expect(ingest.ok(), await ingest.text()).toBe(true);

    // CSV upload of a few late punches incl. one unknown badge
    const y = iso(addDays(TODAY, -1));
    const csv = path.join(test.info().outputDir, 'punches.csv');
    writeFileSync(csv, `employee,timestamp,direction,device\nB999,${y}T08:12:00,IN,GATE-02\nB999,${y}T17:41:00,OUT,GATE-02\n`);
    await page.reload();
    await page.locator('input[type=file]').setInputFiles(csv);
    await page.getByRole('button', { name: 'Import' }).click();
    await expect(page.getByText(/Import complete|Imported with/).first()).toBeVisible();
    await shot(page, 'attendance-import', [
      { el: card(page.getByRole('heading', { name: 'Import punches from CSV' })), n: 1 },
    ]);
    await page.getByRole('heading', { name: 'Attendance sources' }).scrollIntoViewIfNeeded();
    await shot(page, 'attendance-sources', [
      { el: page.getByRole('button', { name: 'New key' }), n: 1, text: 'Replace a lost or leaked key', place: 'above' },
      { el: card(page.getByRole('heading', { name: 'Unmatched punches' })), n: 2 },
    ]);
  });

  // ------------------------------------------------------------------ 9. leave (seed) + daily attendance views
  await test.step('seed approved leave', async () => {
    const mm = `${PAY_YEAR}-${pad(PAY_MONTH)}`;
    const types = (await api(page, 'GET', `/companies/${cid}/leave-types`)) as { id: string; code: string; name: string }[];
    for (const lt of types) ids[`l_${lt.code}`] = lt.id;
    const annual = types.find((x) => /annual/i.test(x.name))!.id;
    const casual = types.find((x) => /casual/i.test(x.name))!.id;
    const medical = types.find((x) => /medical/i.test(x.name))!.id;
    ids.annual = annual;
    ids.casual = casual;
    for (const [k, type, from, to, reason] of [
      ['tharindu', annual, `${mm}-08`, `${mm}-09`, 'Family trip'], ['sachini', medical, `${mm}-22`, `${mm}-22`, 'Fever'],
      ['dilani', casual, `${mm}-14`, `${mm}-14`, 'Personal matter'], ['hasini', annual, `${mm}-25`, `${mm}-25`, 'Wedding'],
      ['sachini', casual, iso(TODAY), iso(TODAY), 'Family function'],
    ] as const) {
      await applyLeave(page, { employeeId: emp[k].id, leaveTypeId: type, fromDate: from, toDate: to, halfDayPart: null, reason });
    }
    for (const u of ['kasun', 'hr.anjali']) {
      const { ctx, page: p } = await newPage(browser);
      await firstSignIn(p, u);
      await approveInbox(p);
      await ctx.close();
    }
    // a pending request for the manager's inbox
    const next = addDays(TODAY, 13);
    await applyLeave(page, { employeeId: emp.ishara.id, leaveTypeId: casual, fromDate: iso(next), toDate: iso(next), halfDayPart: null, reason: 'Medical appointment' });
  });

  await test.step('daily attendance', async () => {
    // the attendance tables need a wider screen to show status badges in full
    await page.setViewportSize({ width: 1680, height: 900 });
    await go(page, 'Attendance', 'Daily attendance');
    await expect(page.getByRole('cell', { name: /Present/ }).first()).toBeVisible();
    await shot(page, 'attendance-daily', [
      { el: page.getByText('Present', { exact: true }).first().locator('xpath=ancestor::div[contains(@class,"mantine-SimpleGrid-root")][1]'), n: 1, text: 'Totals for the rows shown', place: 'right' },
      { el: card(page.getByRole('textbox', { name: 'From' })), n: 2 },
      { el: page.getByRole('button', { name: 'Process range' }), n: 3, text: 'Recalculate from punches', place: 'above' },
      { el: page.getByRole('row').nth(1), n: 4 },
    ]);
    // the forgotten punch-out
    const incomplete = page.getByRole('row').filter({ hasText: 'Dilani Fernando' }).filter({ hasText: 'Incomplete' }).first();
    await page.getByRole('combobox', { name: 'Status' }).click();
    await page.getByRole('option', { name: 'Incomplete' }).click();
    await expect(incomplete.first()).toBeVisible();
    await shot(page, 'attendance-incomplete', [{ el: incomplete, n: 1, text: 'Missing punch — click the row to see the day', place: 'above' }]);
    await incomplete.click();
    const d = drawer(page);
    await expect(d.getByText('Punches').first()).toBeVisible();
    await d.getByLabel('OUT (HH:mm)').fill('17:45');
    await d.getByLabel('Reason').fill('Forgot to punch out — confirmed by supervisor');
    await shot(page, 'attendance-correction', [
      { el: d.getByRole('heading', { name: 'Punches' }).locator('..'), n: 1, text: 'Raw punches from the device (never changed)', place: 'left' },
      { el: field(d.getByLabel('OUT (HH:mm)')), n: 2, text: 'Corrected time (24-hour)', place: 'above' },
      { el: field(d.getByLabel('Reason')), n: 3, text: 'A reason is mandatory and audited', place: 'left' },
    ]);
    await d.getByRole('button', { name: 'Submit correction' }).click();
    await expect(d.getByRole('heading', { name: 'Corrections' }).first()).toBeVisible();
    await d.getByRole('heading', { name: 'Corrections' }).scrollIntoViewIfNeeded();
    await shot(page, 'attendance-corrected', [{ el: d.getByRole('heading', { name: 'Corrections' }).locator('..'), n: 1, text: 'Before → after, reason and time are kept', place: 'left' }]);
    await page.keyboard.press('Escape');

    await page.getByRole('tab', { name: 'Exceptions' }).click();
    await page.getByRole('combobox', { name: 'Exception' }).click();
    await page.getByRole('option', { name: 'Late' }).click();
    await page.waitForTimeout(600);
    await shot(page, 'attendance-exceptions');
    await page.getByRole('combobox', { name: 'Exception' }).click();
    await page.getByRole('option', { name: 'Ot pending' }).click();
    const boxes = page.getByRole('checkbox', { name: /^Select / });
    await expect(boxes.first()).toBeVisible();
    for (let i = 0; i < 4; i++) await boxes.nth(i).click();
    await expect(page.getByRole('button', { name: 'Approve OT (4)' })).toBeEnabled();
    await shot(page, 'attendance-ot-approval', [
      { el: field(page.getByRole('combobox', { name: 'Exception' })), n: 1 },
      { el: boxes.first(), n: 2 },
      { el: page.getByRole('button', { name: /Approve OT/ }).locator('..'), n: 3 },
    ], { numbersOnly: true });
    await page.getByRole('button', { name: /Approve OT/ }).click();
    await page.waitForTimeout(800);
    // approve the rest so payroll pays all overtime
    for (let guard = 0; guard < 10; guard++) {
      const res = (await api(page, 'GET', `/attendance/exceptions?companyId=${cid}&from=${iso(PREV)}&to=${iso(TODAY)}&type=OT_PENDING&size=200`)) as { content: { id: string }[] };
      if (!res.content.length) break;
      await api(page, 'POST', '/attendance/ot/approve', { dailyIds: res.content.map((x) => x.id) });
    }
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  // ------------------------------------------------------------------ 10. rotations & rosters
  await test.step('rotations and rosters', async () => {
    await go(page, 'Shift & Roster', 'Rotations');
    await page.getByRole('button', { name: 'Create pattern' }).click();
    const d = drawer(page);
    await d.getByLabel('Name').fill('2 days, 2 nights, 2 off');
    for (let i = 0; i < 5; i++) await d.getByRole('button', { name: 'Add day' }).click();
    for (const [i, code] of [[1, 'DAY'], [2, 'DAY'], [3, 'NIGHT'], [4, 'NIGHT']] as const) {
      await d.getByRole('combobox', { name: `Day ${i}`, exact: true }).click();
      await opt(page, code).click();
    }
    await shot(page, 'roster-rotation', [
      { el: d.getByText('Cycle days').locator('..').locator('..'), n: 1, text: 'One row per day of the cycle; it repeats', place: 'left' },
      { el: d.getByRole('button', { name: 'Add day' }), n: 2, text: 'Add days (up to 62)', place: 'left' },
    ]);
    await d.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: '2 days, 2 nights, 2 off' }).first()).toBeVisible();
    await shot(page, 'roster-rotations', [{ el: page.getByRole('button', { name: 'Assign employees' }), n: 1, text: 'Assign employees from a start date', place: 'below' }]);

    await page.getByRole('tab', { name: 'Rosters' }).click();
    if (NEXT_MONDAY.getMonth() !== TODAY.getMonth()) {
      await page.getByLabel('Month').click();
      await page.getByRole('button', { name: MONTHS[NEXT_MONDAY.getMonth()].slice(0, 3) }).click();
    }
    await page.getByRole('button', { name: 'Create roster' }).click();
    const m = page.getByRole('dialog');
    const rosterName = `Operations — week of ${NEXT_MONDAY.getDate()} ${MONTHS[NEXT_MONDAY.getMonth()].slice(0, 3)}`;
    await m.getByRole('textbox', { name: 'Name' }).fill(rosterName);
    await m.getByRole('textbox', { name: 'Start date' }).fill(iso(NEXT_MONDAY));
    await m.getByRole('textbox', { name: 'Name' }).click();
    await pick(m, page, 'Department (optional)', 'OPS — Operations');
    await shot(page, 'roster-create', [
      { el: field(m.getByRole('textbox', { name: 'Name' })), n: 1, text: 'A name people will recognise', place: 'left' },
      { el: field(m.getByRole('combobox', { name: 'Period' })), n: 2, text: 'Weekly or monthly', place: 'left' },
      { el: field(m.getByRole('textbox', { name: 'Start date' })), n: 3, text: 'First day (the week-start day for weekly rosters)', place: 'right' },
      { el: field(m.getByRole('combobox', { name: 'Department (optional)' })), n: 4, text: 'Limit the roster to one department', place: 'left' },
    ]);
    await m.getByRole('button', { name: 'Create' }).click();
    await expect(page.getByRole('button', { name: 'Bulk assign' }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Bulk assign' }).click();
    const b = page.getByRole('dialog');
    await b.getByRole('button', { name: /^Select all/ }).click();
    await b.getByRole('combobox', { name: 'Only on weekdays' }).click();
    for (const day of ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']) await page.getByRole('option', { name: day }).click();
    await page.keyboard.press('Escape');
    await pick(b, page, 'Shift', 'DAY');
    const dayLabel = (d: Date) => `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
    for (const [labelText, d] of [['From', NEXT_MONDAY], ['To', addDays(NEXT_MONDAY, 4)]] as const) {
      await b.getByRole('textbox', { name: labelText, exact: true }).click();
      await page.getByRole('button', { name: dayLabel(d), exact: true }).last().click();
      await page.getByRole('dialog').last().getByRole('heading').first().click();
      await expect(page.locator('[data-dates-dropdown]')).toHaveCount(0);
    }
    await b.getByText('Bulk assign', { exact: true }).first().click().catch(() => undefined);
    await shot(page, 'roster-bulk-assign', [
      { el: field(b.getByRole('combobox', { name: 'Employees' })), n: 1, text: 'Pick employees (or “Select all”)', place: 'left' },
      { el: b.getByRole('textbox', { name: 'From', exact: true }).locator('xpath=ancestor::div[contains(@class,"mantine-SimpleGrid-root")][1]'), n: 2, text: 'Date range inside the roster', place: 'left' },
      { el: field(b.getByRole('combobox', { name: 'Only on weekdays' })), n: 3, text: 'Optional: only these weekdays', place: 'left' },
      { el: field(b.getByRole('combobox', { name: 'Shift' })), n: 4, text: 'Shift (or OFF) to assign', place: 'left' },
      { el: b.getByRole('button', { name: 'Assign' }), n: 5, text: 'Assign', place: 'below' },
    ]);
    await b.getByRole('button', { name: 'Assign' }).click();
    await expect(b).toHaveCount(0);
    const rosters = (await api(page, 'GET', `/rosters?companyId=${cid}&from=${iso(NEXT_MONDAY)}&to=${iso(addDays(NEXT_MONDAY, 6))}`)) as { id: string }[];
    const rosterId = rosters[0].id;
    const ops = ['kasun', 'dilani', 'ishara', 'nimal'].map((k) => emp[k].id);
    await api(page, 'POST', `/rosters/${rosterId}/bulk-assign`, { employeeIds: [emp.chamara.id], from: iso(NEXT_MONDAY), to: iso(addDays(NEXT_MONDAY, 4)), weekdays: [], shiftId: ids.s_NIGHT, off: false, reason: null });
    await api(page, 'POST', `/rosters/${rosterId}/bulk-assign`, { employeeIds: [...ops, emp.chamara.id], from: iso(addDays(NEXT_MONDAY, 5)), to: iso(addDays(NEXT_MONDAY, 6)), weekdays: [], shiftId: null, off: true, reason: null });
    await page.getByRole('button', { name: 'Back' }).click();
    await page.getByRole('row', { name: `Open roster ${rosterName}` }).click();
    await page.getByLabel(`${emp.ishara.number} ${iso(addDays(NEXT_MONDAY, 2))}`).selectOption({ label: 'NIGHT' });
    await page.getByLabel(`${emp.dilani.number} ${iso(addDays(NEXT_MONDAY, 5))}`).selectOption({ label: 'DAY' });
    await shot(page, 'roster-grid', [
      { el: page.getByRole('button', { name: 'Bulk assign' }), n: 1 },
      { el: page.getByLabel(`${emp.ishara.number} ${iso(addDays(NEXT_MONDAY, 2))}`), n: 2 },
      { el: page.getByRole('button', { name: 'Save (2)' }), n: 3 },
      { el: page.getByRole('button', { name: 'Validate' }), n: 4 },
      { el: page.getByRole('button', { name: 'Publish' }), n: 5 },
    ], { numbersOnly: true });
    await page.getByRole('button', { name: 'Save (2)' }).click();
    await expect(page.getByRole('button', { name: 'Save (0)' }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Publish' }).click();
    await expect(page.getByText('Published', { exact: true }).first()).toBeVisible();
    await shot(page, 'roster-published', [
      { el: page.getByText('Published', { exact: true }), n: 1, text: 'Visible to employees; later edits need a reason', place: 'right' },
      { el: page.getByRole('button', { name: 'Lock' }), n: 2, text: 'Lock to freeze the roster', place: 'below' },
    ]);
    await page.getByRole('button', { name: 'Back' }).click();
    await page.getByRole('tab', { name: 'Schedule' }).click();
    await page.getByRole('button', { name: 'Next week' }).click();
    await page.waitForTimeout(600);
    await shot(page, 'roster-schedule');
  });

  // ------------------------------------------------------------------ 11. leave UI
  const { page: nimal } = await newPage(browser);
  const { page: kasun } = await newPage(browser);
  await test.step('leave', async () => {
    await go(page, 'Leave Management', 'Leave types');
    await shot(page, 'leave-types');
    await page.getByRole('cell', { name: 'Annual leave' }).click();
    await shot(page, 'leave-type-edit', [
      { el: field(drawer(page).getByLabel('Days per year')), n: 1 },
      { el: field(drawer(page).getByRole('combobox', { name: 'Accrual' })), n: 2 },
      { el: drawer(page).getByText('Pro-rate for joiners').locator('xpath=ancestor::div[contains(@class,"mantine-Checkbox-root")][1]'), n: 3 },
      { el: field(drawer(page).getByLabel('Max carry-forward days')), n: 4 },
      { el: field(drawer(page).getByLabel('Carry-forward expires after month (0 = never)')), n: 5 },
    ], { numbersOnly: true });
    await page.keyboard.press('Escape');

    // employee applies in the portal
    await firstSignIn(nimal, 'nimal');
    await shot(nimal, 'portal-dashboard', [{ el: nav(nimal, 'My Portal'), n: 1, text: 'Your own records', place: 'below' }]);
    await nav(nimal, 'My Portal').click();
    await nimal.getByRole('tab', { name: 'Leave' }).click();
    await expect(nimal.getByText(/Leave balances/).first()).toBeVisible();
    await nimal.getByRole('button', { name: 'Apply for leave' }).click();
    const dlg = nimal.getByRole('dialog');
    await dlg.getByRole('combobox', { name: 'Leave type' }).click();
    await nimal.getByRole('option', { name: 'Annual leave' }).click();
    let start = addDays(TODAY, 8);
    while (start.getDay() === 0 || start.getDay() === 6 || start.getDay() === 5) start = addDays(start, 1);
    const end = addDays(start, 1);
    await dlg.getByRole('button', { name: /Dates/ }).click();
    const label = (d: Date) => `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
    if (start.getMonth() !== TODAY.getMonth()) await nimal.getByRole('button', { name: 'Next month' }).last().click();
    await nimal.getByRole('button', { name: label(start), exact: true }).click();
    await nimal.getByRole('button', { name: label(end), exact: true }).click();
    await dlg.getByLabel('Reason').fill('Visiting family in Galle');
    await shot(nimal, 'portal-apply-leave', [
      { el: field(dlg.getByRole('combobox', { name: 'Leave type' })), n: 1, text: 'Choose the leave type', place: 'left' },
      { el: card(dlg.getByText(/^Available/)), n: 2, text: 'Your available balance', place: 'left' },
      { el: field(dlg.getByRole('button', { name: /Dates/ })), n: 3, text: 'First and last day (click a day twice for one day)', place: 'left' },
      { el: dlg.getByText(/days? ·/), n: 4, text: 'Working days are counted for you', place: 'left' },
      { el: dlg.getByRole('button', { name: 'Apply' }), n: 5, text: 'Submit for approval', place: 'below' },
    ]);
    await dlg.getByRole('button', { name: 'Apply' }).click();
    await expect(nimal.getByRole('cell', { name: 'Pending' }).first()).toBeVisible();
    await shot(nimal, 'portal-leave', [
      { el: nimal.getByText(/Leave balances/), n: 1, text: 'Balances for the year', place: 'right' },
      { el: nimal.getByRole('cell', { name: 'Pending' }), n: 2, text: 'Waiting for your manager', place: 'below' },
    ]);

    // manager approves
    await signIn(kasun, 'kasun', NEW_PASS);
    await expect(kasun.getByRole('button', { name: 'User menu' }).first()).toBeVisible();
    await nav(kasun, 'Approvals').click();
    await expect(kasun.getByRole('cell', { name: /Nimal Perera/ }).first()).toBeVisible();
    await shot(kasun, 'approvals-inbox', [
      { el: nav(kasun, 'Approvals'), n: 1 },
      { el: kasun.getByRole('tab', { name: /Inbox/ }), n: 2, text: 'Requests waiting for your decision', place: 'right' },
      { el: kasun.getByRole('row').filter({ hasText: 'Nimal Perera' }).first(), n: 3, text: 'Click a request to review it', place: 'below' },
    ]);
    await kasun.getByRole('row').filter({ hasText: 'Nimal Perera' }).first().click();
    const td = drawer(kasun);
    await expect(td.getByRole('button', { name: 'Approve' }).first()).toBeVisible();
    await td.getByLabel('Comment').fill('Enjoy your time off');
    await shot(kasun, 'approvals-decide', [
      { el: td.getByRole('heading', { name: 'Approval progress' }).locator('..'), n: 1, text: 'Who has approved and who is next', place: 'left' },
      { el: field(td.getByLabel('Comment')), n: 2, text: 'Comment — optional to approve, required to reject', place: 'left' },
      { el: td.getByRole('button', { name: 'Reject' }), n: 3, text: 'Reject', place: 'below' },
      { el: td.getByRole('button', { name: 'Approve' }), n: 4, text: 'Approve', place: 'below' },
    ]);
    await td.getByRole('button', { name: 'Approve' }).click();
    await expect(kasun.getByRole('cell', { name: /Nimal Perera/ })).toHaveCount(0);
    await kasun.getByRole('tab', { name: 'Delegations' }).click();
    await kasun.getByRole('button', { name: 'Delegate my approvals' }).click();
    await shot(kasun, 'approvals-delegate-manager', [], { clip: kasun.getByRole('dialog'), margin: 30 });
    await kasun.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();

    // HR views
    await page.getByRole('tab', { name: 'Requests' }).click();
    await page.getByRole('combobox', { name: 'Status' }).click();
    await page.getByRole('option', { name: 'Approved' }).click();
    await page.waitForTimeout(500);
    await shot(page, 'leave-requests', [
      { el: page.getByRole('textbox', { name: 'Search employee' }), n: 1 },
      { el: page.getByRole('combobox', { name: 'Status' }), n: 2 },
      { el: page.getByRole('combobox', { name: 'Leave type filter' }), n: 3 },
      { el: page.getByRole('button', { name: 'Apply on behalf' }), n: 4 },
    ], { numbersOnly: true });
    await page.getByRole('tab', { name: 'Calendar' }).click();
    await page.getByRole('button', { name: 'Previous month' }).click();
    await page.waitForTimeout(800);
    await shot(page, 'leave-calendar');
    await page.getByRole('tab', { name: 'Balances' }).click();
    await page.getByRole('combobox', { name: 'Employee' }).click();
    await page.getByRole('option', { name: /Tharindu/ }).click();
    await expect(page.getByRole('cell', { name: 'Annual leave' }).first()).toBeVisible();
    await shot(page, 'leave-balances', [
      { el: page.getByRole('columnheader', { name: 'Available' }), n: 1 },
      { el: page.getByRole('button', { name: 'Opening' }).first(), n: 2 },
      { el: page.getByRole('button', { name: 'Adjust' }).first(), n: 3 },
    ], { numbersOnly: true });
    await page.getByRole('tab', { name: 'Jobs' }).click();
    await shot(page, 'leave-jobs');
  });

  // ------------------------------------------------------------------ 12. approvals configuration
  await test.step('workflows', async () => {
    await go(page, 'Approvals', 'Workflows');
    await shot(page, 'approvals-workflows');
    await page.getByRole('row').filter({ hasText: /Leave/ }).first().click();
    await shot(page, 'approvals-workflow-edit', [
      { el: field(page.getByRole('dialog').getByRole('combobox', { name: 'Branch (optional)' })), n: 1 },
      { el: page.getByRole('dialog').getByText('Enabled', { exact: true }).locator('xpath=ancestor::div[contains(@class,"mantine-Switch-root")][1]'), n: 2 },
      { el: field(page.getByRole('dialog').getByRole('combobox', { name: 'Level 1 approver' })), n: 3 },
      { el: page.getByRole('dialog').getByRole('button', { name: 'Add level' }), n: 4 },
    ], { numbersOnly: true });
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
    await page.getByRole('tab', { name: 'Delegations' }).click();
    await page.getByRole('button', { name: 'Delegate my approvals' }).click();
    const dm = page.getByRole('dialog');
    await pick(dm, page, 'Delegate', /Anjali/);
    const dl = (d: Date) => `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
    for (const [labelText, d] of [['From', addDays(TODAY, 7)], ['To', addDays(TODAY, 11)]] as const) {
      await dm.getByRole('textbox', { name: labelText, exact: true }).click();
      await page.getByRole('button', { name: dl(d), exact: true }).last().click();
      await page.getByRole('dialog').last().getByRole('heading').first().click();
      await expect(page.locator('[data-dates-dropdown]')).toHaveCount(0);
    }
    await shot(page, 'approvals-delegate', [
      { el: field(dm.getByRole('combobox', { name: 'Delegate' })), n: 1, text: 'Who approves on your behalf', place: 'left' },
      { el: dm.getByRole('textbox', { name: 'From', exact: true }).locator('xpath=ancestor::div[contains(@class,"mantine-SimpleGrid-root")][1]'), n: 2, text: 'While you are away', place: 'left' },
    ]);
    await dm.getByRole('button', { name: 'Cancel' }).click();
  });

  // ------------------------------------------------------------------ 13. payroll
  await test.step('payroll configuration', async () => {
    await go(page, 'Payroll', 'Components');
    await shot(page, 'payroll-components');
    await page.getByRole('tab', { name: 'Statutory' }).click();
    await shot(page, 'payroll-statutory');
    await page.getByRole('tab', { name: 'Slab tables' }).click();
    await shot(page, 'payroll-slabs');
  });

  let periodUrl = '';
  await test.step('payroll run', async () => {
    await page.getByRole('tab', { name: 'Periods' }).click();
    await page.getByRole('button', { name: 'Open period' }).click();
    const m = page.getByRole('dialog');
    await m.getByLabel('Year').fill(String(PAY_YEAR));
    await m.getByLabel('Month').fill(String(PAY_MONTH));
    await shot(page, 'payroll-open-period', [
      { el: field(m.getByLabel('Year')), n: 1, text: 'Year', place: 'left' },
      { el: field(m.getByLabel('Month')), n: 2, text: 'Month to pay (1 = January)', place: 'right' },
      { el: m.getByRole('button', { name: 'Open', exact: true }), n: 3, text: 'Open the period', place: 'below' },
    ]);
    await m.getByRole('button', { name: 'Open', exact: true }).click();
    await expect(page.getByRole('heading', { name: `Payroll — ${PAY_LABEL}` }).first()).toBeVisible();
    periodUrl = page.url();
    await shot(page, 'payroll-period-open', [
      { el: page.getByRole('list', { name: 'Payroll progress' }), n: 1, text: 'Where the period is in its lifecycle', place: 'below' },
      { el: page.getByRole('button', { name: 'Process' }), n: 2, text: 'Calculate pay for everyone', place: 'left' },
    ]);
    await page.getByRole('button', { name: 'Process' }).click();
    await expect(page.getByRole('cell', { name: 'Nimal Perera' }).first()).toBeVisible({ timeout: 60_000 });
    await shot(page, 'payroll-results', [
      { el: page.getByText('Gross pay', { exact: true }).locator('xpath=ancestor::div[contains(@class,"mantine-SimpleGrid-root")][1]'), n: 1, text: 'Totals for the period', place: 'below' },
      { el: page.getByRole('row', { name: 'Kasun Jayasinghe — view breakdown' }), n: 2, text: 'Click a row for the full breakdown', place: 'below' },
    ]);

    await page.getByRole('tab', { name: 'One-time items' }).click();
    await page.getByRole('button', { name: 'Add item' }).click();
    const it = page.getByRole('dialog');
    await pick(it, page, 'Employee', /Nimal Perera/);
    await pick(it, page, 'Component', /^Bonus/);
    await it.getByLabel('Amount').fill('25000');
    await it.getByLabel('Note').fill('Q3 performance bonus');
    await shot(page, 'payroll-one-time-item', [
      { el: it.getByRole('combobox', { name: 'Component' }), n: 1, text: 'Bonus, incentive, commission, advance…', place: 'right' },
      { el: it.getByLabel('Amount'), n: 2, text: 'Amount for this month only', place: 'right' },
    ]);
    await it.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByText('Results are out of date').first()).toBeVisible();
    await shot(page, 'payroll-stale', [
      { el: page.getByText('Results are out of date').locator('xpath=ancestor::div[@role="alert"][1]'), n: 1 },
      { el: page.getByRole('button', { name: 'Reprocess' }), n: 2, text: 'Reprocess before submitting', place: 'below' },
    ]);
    await page.getByRole('button', { name: 'Reprocess' }).click();
    await expect(page.getByText('Results are out of date')).toHaveCount(0, { timeout: 60_000 });
    await page.getByRole('tab', { name: 'Results' }).click();
    await page.getByRole('row', { name: 'Nimal Perera — view breakdown' }).click();
    await expect(drawer(page).getByText('Net pay').first()).toBeVisible();
    await shot(page, 'payroll-result-detail');
    await page.keyboard.press('Escape');

    await page.getByRole('button', { name: 'Submit for review' }).click();
    await expect(page.getByRole('main').getByText('In review', { exact: true }).first()).toBeVisible();
    await page.getByRole('tab', { name: 'Exceptions' }).click().catch(() => undefined);
    await shot(page, 'payroll-in-review');

    const { page: hr } = await newPage(browser);
    await signIn(hr, 'hr.anjali', NEW_PASS);
    await nav(hr, 'Approvals').click();
    await hr.getByRole('row').filter({ hasText: /Payroll/ }).first().click();
    await expect(drawer(hr).getByRole('button', { name: 'Approve' }).first()).toBeVisible();
    await shot(hr, 'payroll-approve');
    await drawer(hr).getByRole('button', { name: 'Approve' }).click();
    await hr.waitForTimeout(800);

    await page.goto(periodUrl);
    await expect(page.getByRole('button', { name: 'Finalize' }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Finalize' }).click();
    await shot(page, 'payroll-finalize-confirm', [], { clip: page.getByRole('dialog'), margin: 30 });
    await page.getByRole('dialog').getByRole('button', { name: 'Finalize' }).click();
    await expect(page.getByRole('main').getByText('Finalized', { exact: true }).first()).toBeVisible();
    await page.getByRole('tab', { name: /Payslips/ }).click().catch(() => undefined);
    await page.evaluate(() => window.scrollTo(0, 0));
    await shot(page, 'payroll-finalized', [
      { el: page.getByRole('list', { name: 'Payroll progress' }), n: 1, text: 'All steps complete — results are locked', place: 'below' },
    ]);
    await shot(page, 'payroll-exports', [
      { el: card(page.getByRole('heading', { name: 'Exports' })), n: 1, text: 'Payroll register and department summary (Excel, CSV, PDF)', place: 'above' },
    ]);
    await shot(page, 'payroll-payslips', [
      { el: page.getByRole('button', { name: 'All payslips (PDF)' }), n: 1, text: 'All payslips in one PDF', place: 'below' },
      { el: page.getByRole('button', { name: 'Email payslips' }), n: 2, text: 'Email each employee their payslip', place: 'below' },
    ]);
  });

  // ------------------------------------------------------------------ 14. self-service
  await test.step('self-service portal', async () => {
    await nimal.reload();
    for (const tab of ['Profile', 'Attendance', 'Schedule', 'Payslips']) {
      await nimal.getByRole('tab', { name: tab }).click();
      await nimal.waitForTimeout(700);
      if (tab === 'Schedule') await nimal.getByRole('tab', { name: 'Schedule' }).scrollIntoViewIfNeeded();
      await shot(nimal, `portal-${tab.toLowerCase()}`);
    }
    // payslip PDF rendered to an image for the guide
    const periods = (await api(nimal, 'GET', '/me/payslips')) as { periodId: string }[];
    const pdfBytes = await nimal.evaluate(async (pid) => {
      const refresh = sessionStorage.getItem('hrm.refreshToken');
      const tokens = await (await fetch('/api/v1/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: refresh }) })).json();
      sessionStorage.setItem('hrm.refreshToken', tokens.refreshToken);
      const res = await fetch(`/api/v1/me/payslips/${pid}.pdf`, { headers: { Authorization: `Bearer ${tokens.accessToken}` } });
      return Array.from(new Uint8Array(await res.arrayBuffer()));
    }, periods[0].periodId);
    const pdf = path.join(test.info().outputDir, 'payslip.pdf');
    writeFileSync(pdf, Buffer.from(pdfBytes));
    try {
      const full = path.join(test.info().outputDir, 'payslip-full.png');
      execFileSync('sips', ['-s', 'format', 'png', '-Z', '1400', pdf, '--out', full]);
      // keep the top of the A4 page (the payslip itself); the rest is blank paper
      const viewer = await nimal.context().newPage();
      await viewer.setViewportSize({ width: 989, height: 680 });
      await viewer.setContent(`<body style="margin:0"><img src="data:image/png;base64,${readFileSync(full).toString('base64')}"></body>`);
      await viewer.screenshot({ path: path.join(OUT, 'payslip-pdf.png'), clip: { x: 0, y: 0, width: 989, height: 680 } });
      await viewer.close();
    } catch {
      // sips is macOS-only; the guide keeps the previous image elsewhere
    }
    await nav(nimal, 'Approvals').click();
    await nimal.getByRole('tab', { name: 'My requests' }).click();
    await shot(nimal, 'approvals-my-requests');
  });

  // ------------------------------------------------------------------ 15. reports, audit, dashboard, shell
  await test.step('reports and audit', async () => {
    await go(page, 'Reports');
    await page.getByRole('button', { name: 'Attendance summary' }).click();
    await page.getByRole('textbox', { name: 'From' }).fill(iso(PREV));
    await page.getByRole('textbox', { name: 'To' }).fill(iso(new Date(PAY_YEAR, PAY_MONTH, 0)));
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'Run' }).click();
    await expect(page.getByRole('group', { name: 'Export' }).first()).toBeVisible();
    await shot(page, 'reports-run', [
      { el: page.getByRole('button', { name: 'Run' }), n: 2, text: 'Set options and run', place: 'left' },
      { el: page.getByRole('button', { name: 'Attendance summary' }), n: 1, text: 'Pick a report from the catalogue', place: 'below' },
      { el: page.getByRole('group', { name: 'Export' }), n: 3, text: 'Download as PDF, Excel or CSV', place: 'left' },
    ]);
    await go(page, 'Administration', 'Audit log');
    await shot(page, 'admin-audit', [
      { el: page.getByRole('row', { name: /^View / }).first(), n: 1, place: 'below' },
    ]);
    await page.getByRole('row', { name: /^View Update on Employee|^View Create on Employee|^View / }).first().click();
    await shot(page, 'admin-audit-detail');
    await page.keyboard.press('Escape');
  });

  await test.step('dashboard and shell', async () => {
    await go(page, 'Dashboard');
    await page.waitForTimeout(800);
    await shot(page, 'dashboard', [
      { el: page.getByText('Active employees').first().locator('xpath=ancestor::div[contains(@class,"mantine-SimpleGrid-root")][1]'), n: 1, text: 'Today’s KPIs — click a card to drill down', place: 'right' },
    ]);
    await page.keyboard.press('ControlOrMeta+k');
    await page.getByPlaceholder(/Search pages/).fill('nim');
    await page.waitForTimeout(600);
    await shot(page, 'command-palette');
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'User menu' }).click();
    await shot(page, 'user-menu', [], { clip: page.getByRole('menu'), margin: 40 });
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'Toggle dark mode' }).click();
    await page.waitForTimeout(500);
    await shot(page, 'dashboard-dark');
    await page.getByRole('button', { name: 'Toggle dark mode' }).click();

    const { page: mobile } = await newPage(browser, { width: 390, height: 844 });
    await signIn(mobile, ADMIN.username, ADMIN.password);
    await expect(mobile.getByRole('button', { name: 'User menu' }).first()).toBeVisible();
    await shot(mobile, 'mobile-dashboard');
    await mobile.getByRole('button', { name: 'Toggle navigation' }).click();
    await shot(mobile, 'mobile-menu');
  });
});
