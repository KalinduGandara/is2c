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
    await page.getByRole('button', { name: 'Save' }).click();
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
