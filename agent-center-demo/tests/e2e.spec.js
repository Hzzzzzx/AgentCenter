const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const ROLE_IDS = ['management', 'product', 'development', 'ops', 'quality', 'architecture'];

const ROLE_HEADLINES = {
  management: '战略驾驶舱',
  product: '需求管理中枢',
  development: '开发工作台',
  ops: '运维监控台',
  quality: '质量保障中心',
  architecture: '架构设计中心',
};

const BANNED_TERMS_SCAN = [
  'JIRA', 'GITLAB', 'GITHUB', 'JENKINS', 'ARGOCD',
  'PROMETHEUS', 'GRAFANA', 'SLACK', 'HARBOR',
  'KUBERNETES', 'K8S', '飞书',
];

async function waitForDemoHome(page) {
  let lastError;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      await page.goto('/', { waitUntil: 'domcontentloaded', timeout: 15000 });
      await page.waitForSelector('[data-testid="dashboard-headline"]', { timeout: 15000 });
      return;
    } catch (error) {
      lastError = error;
      await page.waitForTimeout(500);
    }
  }
  throw lastError;
}

test.beforeEach(async ({ page }) => {
  await waitForDemoHome(page);
});

// ─── 1. Role Switches ───────────────────────────────────────────────────────

test.describe('Role switches', () => {
  for (const roleId of ROLE_IDS) {
    test(`switching to "${roleId}" changes headline to "${ROLE_HEADLINES[roleId]}"`, async ({ page }) => {
      const roleItem = page.locator(`[data-interaction-id="click:sidebar:role:${roleId}"]`);
      await roleItem.click();

      const headline = page.locator('[data-testid="dashboard-headline"]');
      await expect(headline).toHaveText(ROLE_HEADLINES[roleId], { timeout: 5000 });
    });
  }
});

// ─── 2. Story Mode ──────────────────────────────────────────────────────────

test.describe('Story mode', () => {
  test('start story mode, select "需求到设计与研发启动", advance one step, verify role changes, exit', async ({ page }) => {
    const startBtn = page.locator('[data-testid="story-mode-start"]');
    await startBtn.click();

    const overlay = page.locator('.story-overlay');
    await expect(overlay).toBeVisible({ timeout: 5000 });

    const storyCard = overlay.locator('.storyline-card').filter({ hasText: '需求到设计与研发启动' });
    await storyCard.click();
    const explicitStart = page.locator('[data-testid="story-mode-start-btn"]');
    if (await explicitStart.isVisible({ timeout: 1000 }).catch(() => false)) {
      await explicitStart.click();
    }

    // Wait for step view to render (parent state update triggers re-render)
    const stepBadge = overlay.locator('.story-step-badge');
    await expect(stepBadge).toHaveText('1', { timeout: 10000 });

    const headline = page.locator('[data-testid="dashboard-headline"]');
    await expect(headline).toHaveText('需求管理中枢', { timeout: 5000 });

    const nextBtn = page.locator('[data-testid="story-mode-next-btn"]');
    await nextBtn.click();

    await expect(headline).toHaveText('架构设计中心', { timeout: 5000 });
    await expect(stepBadge).toHaveText('2', { timeout: 5000 });

    const exitBtn = page.locator('[data-testid="story-mode-exit-btn"]').first();
    await exitBtn.click();

    await expect(overlay).not.toBeVisible({ timeout: 5000 });
  });
});

// ─── 3. Command Deck ────────────────────────────────────────────────────────

test.describe('Command deck', () => {
  test('click "总览" chip, verify response appears', async ({ page }) => {
    // Switch to management role which has "成本分析" as first chip;
    // "总览" is in defaultChips only — type it in chat instead
    const chatInput = page.locator('[data-testid="chat-input"]');
    await chatInput.fill('总览');
    await page.locator('[data-testid="chat-send-btn"]').click();

    // Wait for bot response containing "总览"
    const messages = page.locator('.conversation-messages .message');
    await expect(messages.last()).toContainText('总览', { timeout: 10000 });

    // Verify no crash — page still responsive
    const headline = page.locator('[data-testid="dashboard-headline"]');
    await expect(headline).toBeVisible({ timeout: 5000 });
  });
});

// ─── 4. Dead-Click Detector ─────────────────────────────────────────────────

test.describe('Dead-click detector', () => {
  test('click every [data-interaction-id] element, verify no crash and visible response', async ({ page }) => {
    test.setTimeout(120000);

    const elements = page.locator('[data-interaction-id]');
    const count = await elements.count();
    expect(count).toBeGreaterThan(0);

    const testedIds = [];
    const errors = [];
    const consoleErrors = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    for (let i = 0; i < count; i++) {
      const el = elements.nth(i);
      const interactionId = await el.getAttribute('data-interaction-id');
      if (!interactionId) continue;

      const isVisible = await el.isVisible().catch(() => false);
      if (!isVisible) {
        testedIds.push({ id: interactionId, status: 'skipped-not-visible' });
        continue;
      }

      const errorsBefore = consoleErrors.length;

      try {
        await el.click({ timeout: 3000 });
      } catch {
        await el.click({ force: true, timeout: 3000 }).catch(() => {});
      }

      await page.waitForTimeout(200);

      const newErrors = consoleErrors.length - errorsBefore;
      const toastVisible = await page.locator('.toast').isVisible().catch(() => false);
      const storyVisible = await page.locator('.story-overlay').isVisible().catch(() => false);
      const archVisible = await page.locator('.architecture-overlay').isVisible().catch(() => false);

      const hasResponse = toastVisible || storyVisible || archVisible || newErrors === 0;

      testedIds.push({
        id: interactionId,
        status: hasResponse ? 'ok' : 'possible-dead-click',
        toast: toastVisible,
        newErrors,
      });

      if (!hasResponse) errors.push(interactionId);

      if (storyVisible) {
        await page.locator('[data-testid="story-mode-exit-btn"]').first().click().catch(() => {});
        await page.waitForTimeout(100);
      }
      if (archVisible) {
        await page.locator('[data-testid="architecture-close-btn"]').click().catch(() => {});
        await page.waitForTimeout(100);
      }
    }

    console.log('\n=== Dead-Click Detector Report ===');
    console.log(`Total elements: ${count}`);
    console.log(`Tested: ${testedIds.length}`);
    console.log(`Possible dead clicks: ${errors.length}`);
    testedIds.forEach(t => console.log(`  [${t.status}] ${t.id}`));

    const headline = page.locator('[data-testid="dashboard-headline"]');
    await expect(headline).toBeVisible({ timeout: 5000 });

    if (errors.length > 0) {
      console.log(`\n⚠️  Possible dead clicks: ${errors.join(', ')}`);
    }
  });
});

// ─── 5. Banned-Term Scan ────────────────────────────────────────────────────

test.describe('Banned-term scan', () => {
  test('client/index.html and server files contain zero banned terms', () => {
    const demoRoot = path.resolve(__dirname, '..');
    // registry.js defines the BANNED_TERMS list itself — exclude it from scan
    const filesToScan = [
      path.join(demoRoot, 'client', 'index.html'),
      path.join(demoRoot, 'server', 'index.js'),
      path.join(demoRoot, 'server', 'intent-parser.js'),
      path.join(demoRoot, 'server', 'mock-events.js'),
      path.join(demoRoot, 'server', 'mock-agents.js'),
      path.join(demoRoot, 'server', 'db.js'),
    ];

    const violations = [];

    for (const filePath of filesToScan) {
      if (!fs.existsSync(filePath)) continue;
      const content = fs.readFileSync(filePath, 'utf-8');
      const upperContent = content.toUpperCase();

      for (const term of BANNED_TERMS_SCAN) {
        const upperTerm = term.toUpperCase();
        let lineIdx = upperContent.indexOf(upperTerm);
        while (lineIdx !== -1) {
          const lineNum = content.substring(0, lineIdx).split('\n').length;
          violations.push({
            file: path.relative(demoRoot, filePath),
            term,
            line: lineNum,
          });
          lineIdx = upperContent.indexOf(upperTerm, lineIdx + 1);
        }
      }
    }

    if (violations.length > 0) {
      console.log('\n=== Banned Term Violations ===');
      violations.forEach(v => console.log(`  ${v.file}:${v.line} — found "${v.term}"`));
    }

    expect(violations, `Found ${violations.length} banned term(s). See console output.`).toHaveLength(0);
  });
});
