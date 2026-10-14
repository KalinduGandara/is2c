import { defineConfig, devices } from '@playwright/test';

// Regenerates the user-guide screenshots (docs/user-guide/images) against a dedicated backend with a FRESH
// database (:8083) and Vite (:5176). Run: npx playwright test -c playwright.guide.config.ts
const BACKEND_PORT = 8083;
const FRONTEND_PORT = 5176;

// Screenshots are taken "at 10:30 today" whatever the real time is: the backend clock and the browser clock are
// both shifted by this offset, so the dashboard shows a normal working morning.
const now = new Date();
const target = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 10, 30);
process.env.GUIDE_CLOCK_OFFSET_MS ??= String(target.getTime() - now.getTime());

export default defineConfig({
  testDir: './guide',
  timeout: 30 * 60_000,
  expect: { timeout: 15_000 },
  workers: 1,
  reporter: [['list']],
  outputDir: 'test-results/guide',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    trace: 'retain-on-failure',
    actionTimeout: 15_000,
  },
  webServer: [
    {
      command:
        'cd ../backend && ./mvnw -q spring-boot:test-run -Dspring-boot.run.main-class=com.is2c.hrm.DevHrmApplication ' +
        `-Dspring-boot.run.jvmArguments="-Ddev.clean=true -Ddev.datadir=target/guidedb -Ddev.pg.port=54333 -Dserver.port=${BACKEND_PORT} -Dhrm.storage.path=target/guide-files -Ddev.clock.offset-ms=${process.env.GUIDE_CLOCK_OFFSET_MS}"`,
      url: `http://localhost:${BACKEND_PORT}/actuator/health`,
      timeout: 240_000,
      reuseExistingServer: true,
    },
    {
      command: `npx vite --port ${FRONTEND_PORT} --strictPort`,
      url: `http://localhost:${FRONTEND_PORT}`,
      env: { VITE_API_TARGET: `http://localhost:${BACKEND_PORT}` },
      timeout: 60_000,
      reuseExistingServer: true,
    },
  ],
});
