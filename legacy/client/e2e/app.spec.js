const { test, expect, _electron } = require('@playwright/test');
const path = require('path');

let app;
let window;

test.beforeAll(async () => {
  app = await _electron.launch({
    args: [path.join(__dirname, '../electron/main.js')],
    env: { ...process.env, NODE_ENV: 'test' },
  });
  window = await app.firstWindow();
  await window.waitForLoadState('domcontentloaded');
});

test.afterAll(async () => {
  if (app) await app.close();
});

test('app launches and shows window', async () => {
  const title = await window.title();
  expect(title).toContain('blesk');
});

test('window has correct minimum dimensions', async () => {
  const size = await window.evaluate(() => ({
    width: window.innerWidth,
    height: window.innerHeight,
  }));
  expect(size.width).toBeGreaterThanOrEqual(800);
  expect(size.height).toBeGreaterThanOrEqual(600);
});

test('auth screen is shown on first launch', async () => {
  const authForm = await window.locator('.auth-screen, .auth-autoLogin').first();
  await expect(authForm).toBeVisible({ timeout: 10000 });
});
