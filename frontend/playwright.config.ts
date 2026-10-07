import { defineConfig, devices } from '@playwright/test';

// E2E runs against a dedicated backend on :8081 with a fresh embedded database, and Vite on :5174.
const BACKEND_PORT = 8081;
const FRONTEND_PORT = 5174;

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] }, testMatch: /responsive\.spec\.ts/ },
  ],
  webServer: [
    {
      command:
        'cd ../backend && ./mvnw -q spring-boot:test-run -Dspring-boot.run.main-class=com.is2c.hrm.DevHrmApplication ' +
        `-Dspring-boot.run.jvmArguments="-Ddev.clean=true -Ddev.datadir=target/e2edb -Ddev.pg.port=54330 -Dserver.port=${BACKEND_PORT} -Dhrm.storage.path=target/e2e-files"`,
      url: `http://localhost:${BACKEND_PORT}/actuator/health`,
      timeout: 240_000,
      reuseExistingServer: false,
    },
    {
      command: `npx vite --port ${FRONTEND_PORT} --strictPort`,
      url: `http://localhost:${FRONTEND_PORT}`,
      env: { VITE_API_TARGET: `http://localhost:${BACKEND_PORT}` },
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
});
