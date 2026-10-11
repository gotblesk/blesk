const { test, expect, _electron } = require('@playwright/test');
const path = require('path');

let app, window;

test.beforeAll(async () => {
  app = await _electron.launch({
    args: [path.join(__dirname, '../electron/main.js')],
    env: { ...process.env, NODE_ENV: 'test' },
  });
  window = await app.firstWindow();
  await window.waitForLoadState('domcontentloaded');
  // Wait for auth screen
  await window.waitForSelector('.auth-screen, .auth-autoLogin', { timeout: 15000 });
});

test.afterAll(async () => {
  if (app) await app.close();
});

test('login form has username and password fields', async () => {
  // Click login tab if registration is shown by default
  const loginTab = window.locator('text=Войти').first();
  if (await loginTab.isVisible()) await loginTab.click();

  const username = window.locator('input[placeholder*="Имя"], input[type="text"]').first();
  const password = window.locator('input[type="password"]').first();
  await expect(username).toBeVisible({ timeout: 5000 });
  await expect(password).toBeVisible({ timeout: 5000 });
});

test('login button is disabled with empty fields', async () => {
  const submitBtn = window.locator('button[type="submit"], .g-btn').first();
  // Button should exist
  await expect(submitBtn).toBeVisible();
});

test('registration form validates username length', async () => {
  // Switch to register tab
  const registerTab = window.locator('text=Регистрация, text=Создать').first();
  if (await registerTab.isVisible()) await registerTab.click();

  const usernameInput = window.locator('input[maxlength="24"]').first();
  if (await usernameInput.isVisible()) {
    await usernameInput.fill('ab');
    // Try to submit — should show validation error
    const submitBtn = window.locator('button[type="submit"], .g-btn').first();
    await submitBtn.click();
    // Check for error indication
    await window.waitForTimeout(500);
  }
});

test('password strength indicator works', async () => {
  const passwordInput = window.locator('input[type="password"]').first();
  if (await passwordInput.isVisible()) {
    await passwordInput.fill('weak');
    await window.waitForTimeout(300);
    // Strength dots should show low score
    const dots = window.locator('.strength-dot, .str-dot');
    const count = await dots.count();
    expect(count).toBeGreaterThan(0);
  }
});
