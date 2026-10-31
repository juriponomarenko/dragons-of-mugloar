import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests drive the real frontend against the real backend. The backend is started on its
 * test classpath (`spring-boot:test-run`), where the game API is replaced by a deterministic
 * in-memory stub, so the tests need no network and can assert exact numbers. Both servers use
 * ports of their own, so a backend and a dev server already running on 8080 and 5173 are left alone.
 */
const BACKEND_URL = 'http://localhost:8081' // server.port in backend/src/test/resources/application-e2e.properties
const FRONTEND_PORT = 5174

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `${process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw'} spring-boot:test-run -Dspring-boot.run.main-class=com.ponomarenko.mugloar.e2e.E2eBackend`,
      cwd: '../backend',
      url: `${BACKEND_URL}/actuator/health`,
      timeout: 180_000, // compiles the test classes first
      reuseExistingServer: !process.env.CI,
    },
    {
      command: `pnpm dev --port ${FRONTEND_PORT} --strictPort`,
      env: { BACKEND_URL },
      url: `http://localhost:${FRONTEND_PORT}`,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
