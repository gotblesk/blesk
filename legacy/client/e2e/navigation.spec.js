const { test, expect } = require('@playwright/test');

// These tests require a running server and valid credentials
// Skip in CI unless TEST_USERNAME and TEST_PASSWORD are set
test.describe('Navigation', () => {
  test.skip(!process.env.TEST_USERNAME, 'Requires TEST_USERNAME env var');

  test('top nav tabs are visible', async ({ page }) => {
    // This would need proper Electron setup with auth
    // Placeholder for when auth is automated
  });

  test('sidebar shows chat list', async ({ page }) => {
    // Placeholder
  });

  test('Ctrl+K opens spotlight search', async ({ page }) => {
    // Placeholder
  });
});
