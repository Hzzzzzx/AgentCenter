const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const evidenceDir = path.resolve(__dirname, '../../.sisyphus/evidence');
const staticUrl = 'file://' + path.resolve(__dirname, '../../docs/prototype/homepage.html');
fs.mkdirSync(evidenceDir, { recursive: true });

const SHELL_TEST_IDS = [
  'workbench-shell',
  'workbench-titlebar',
  'top-metrics-panel',
  'workbench-main',
  'workbench-left',
  'workbench-center',
  'workbench-right',
  'bottom-history-panel',
  'footer-status',
];

async function waitForReactHomepage(page) {
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

async function expectLightBody(page) {
  const colors = await page.evaluate(() => {
    const bg = getComputedStyle(document.body).backgroundColor;
    const fg = getComputedStyle(document.body).color;
    const rgb = bg.match(/\d+/g).map(Number).slice(0, 3);
    const luminance = rgb.map((v) => v / 255).reduce((sum, v) => sum + v, 0) / 3;
    return { bg, fg, luminance };
  });
  expect(colors.bg).not.toBe('rgb(15, 23, 42)');
  expect(colors.fg).not.toBe('rgb(241, 245, 249)');
  expect(colors.luminance).toBeGreaterThan(0.85);
}

async function expectThreeColumnWorkbench(page) {
  const main = page.getByTestId('workbench-main');
  await expect(main).toBeAttached();
  const directChildren = await main.evaluate((el) => Array.from(el.children).map((child) => child.getAttribute('data-testid')));
  expect(directChildren).toEqual(['workbench-left', 'workbench-center', 'workbench-right']);

  const [left, center, right] = await Promise.all([
    page.getByTestId('workbench-left').boundingBox(),
    page.getByTestId('workbench-center').boundingBox(),
    page.getByTestId('workbench-right').boundingBox(),
  ]);
  expect(center.width).toBeGreaterThan(left.width);
  expect(center.width).toBeGreaterThan(right.width);
}

async function expectPanelRailsAlignWithCenter(page) {
  const [center, topRail, bottomRail] = await Promise.all([
    page.getByTestId('workbench-center').boundingBox(),
    page.getByTestId('top-metrics-rail').boundingBox(),
    page.getByTestId('bottom-history-rail').boundingBox(),
  ]);
  expect(Math.abs(topRail.x - center.x)).toBeLessThanOrEqual(1);
  expect(Math.abs(bottomRail.x - center.x)).toBeLessThanOrEqual(1);
  expect(Math.abs(topRail.width - center.width)).toBeLessThanOrEqual(1);
  expect(Math.abs(bottomRail.width - center.width)).toBeLessThanOrEqual(1);
}

async function expectSidebarsSpanPanelRows(page) {
  await page.waitForFunction(() => {
    const box = (testId) => document.querySelector(`[data-testid="${testId}"]`)?.getBoundingClientRect();
    const left = box('workbench-left');
    const right = box('workbench-right');
    const topPanel = box('top-metrics-panel');
    const bottomPanel = box('bottom-history-panel');
    if (!left || !right || !topPanel || !bottomPanel) return false;
    const bottom = bottomPanel.y + bottomPanel.height;
    return Math.abs(left.y - topPanel.y) <= 1
      && Math.abs(right.y - topPanel.y) <= 1
      && Math.abs((left.y + left.height) - bottom) <= 1
      && Math.abs((right.y + right.height) - bottom) <= 1;
  }, null, { timeout: 2000 });
  const [left, right, topPanel, bottomPanel] = await Promise.all([
    page.getByTestId('workbench-left').boundingBox(),
    page.getByTestId('workbench-right').boundingBox(),
    page.getByTestId('top-metrics-panel').boundingBox(),
    page.getByTestId('bottom-history-panel').boundingBox(),
  ]);
  expect(Math.abs(left.y - topPanel.y)).toBeLessThanOrEqual(1);
  expect(Math.abs(right.y - topPanel.y)).toBeLessThanOrEqual(1);
  expect(Math.abs((left.y + left.height) - (bottomPanel.y + bottomPanel.height))).toBeLessThanOrEqual(1);
  expect(Math.abs((right.y + right.height) - (bottomPanel.y + bottomPanel.height))).toBeLessThanOrEqual(1);
}

async function expectNoHorizontalOverflow(page) {
  const overflow = await page.evaluate(() => document.body.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
}

async function expectViewportFit(page) {
  const fit = await page.evaluate(() => ({
    bodyScrollHeight: document.body.scrollHeight,
    docScrollHeight: document.documentElement.scrollHeight,
    bodyScrollWidth: document.body.scrollWidth,
    viewportHeight: window.innerHeight,
    viewportWidth: window.innerWidth,
    scrollY: window.scrollY,
  }));
  expect(fit.bodyScrollWidth).toBeLessThanOrEqual(fit.viewportWidth);
  expect(fit.bodyScrollHeight).toBeLessThanOrEqual(fit.viewportHeight);
  expect(fit.docScrollHeight).toBeLessThanOrEqual(fit.viewportHeight);
  expect(fit.scrollY).toBe(0);
}

async function expectNoFocusableAriaHiddenDescendants(page) {
  const offenders = await page.evaluate(() => {
    const focusableSelector = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';
    return Array.from(document.querySelectorAll('[aria-hidden="true"]'))
      .flatMap((hidden) => Array.from(hidden.querySelectorAll(focusableSelector)))
      .filter((el) => !el.disabled)
      .map((el) => el.outerHTML.slice(0, 120));
  });
  expect(offenders).toEqual([]);
}

async function panelHeight(page, testId) {
  return page.getByTestId(testId).evaluate((el) => el.getBoundingClientRect().height);
}

async function waitForPanelHeight(page, testId, min, max) {
  await page.waitForFunction(
    ({ selector, minHeight, maxHeight }) => {
      const el = document.querySelector(selector);
      if (!el) return false;
      const height = el.getBoundingClientRect().height;
      return height >= minHeight && height <= maxHeight;
    },
    { selector: `[data-testid="${testId}"]`, minHeight: min, maxHeight: max },
  );
}

test.describe('shell contract', () => {
  test.beforeEach(async ({ page }) => {
    await waitForReactHomepage(page);
  });

  for (const testId of SHELL_TEST_IDS) {
    test(`[${testId}] element exists`, async ({ page }) => {
      await expect(page.getByTestId(testId)).toBeAttached();
    });
  }

  test('workbench-shell uses CSS Grid', async ({ page }) => {
    await expect(page.getByTestId('workbench-shell')).toHaveCSS('display', 'grid');
  });

  test('workbench-shell defines independent side and center tracks', async ({ page }) => {
    const columns = await page.getByTestId('workbench-shell').evaluate((el) => getComputedStyle(el).gridTemplateColumns);
    expect(columns.split(' ')).toHaveLength(7);
    await expect(page.getByTestId('workbench-main')).toHaveCSS('display', 'contents');
  });

  test('body has light theme defaults', async ({ page }) => {
    await expectLightBody(page);
  });
});

test.describe('static prototype layout', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(staticUrl, { waitUntil: 'domcontentloaded' });
  });

  test('static prototype opens in collapsed three-column workbench layout', async ({ page }) => {
    await expectThreeColumnWorkbench(page);
    await expect(page.getByTestId('top-metrics-panel')).toHaveClass(/is-collapsed/);
    await expect(page.getByTestId('bottom-history-panel')).toHaveClass(/is-collapsed/);
    await expectPanelRailsAlignWithCenter(page);
    await expectSidebarsSpanPanelRows(page);
    expect(await panelHeight(page, 'top-metrics-panel')).toBeLessThanOrEqual(44);
    expect(await panelHeight(page, 'bottom-history-panel')).toBeLessThanOrEqual(56);
    await expect(page.getByText('今日部署').first()).toBeHidden();
    await expectViewportFit(page);
  });

  test('static prototype keeps required workbench text visible', async ({ page }) => {
    await expect(page.getByText('对话工作台 · AI 智能中枢')).toBeVisible();
    await expect(page.getByText('指标概览').first()).toBeVisible();
    await expect(page.getByText('最近活动').first()).toBeVisible();
    await expect(page.getByText('执行记录').first()).toBeVisible();
  });
});

test.describe('static prototype light theme', () => {
  test('static body background is light', async ({ page }) => {
    await page.goto(staticUrl, { waitUntil: 'domcontentloaded' });
    await expectLightBody(page);
  });
});

test.describe('react workbench layout', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await waitForReactHomepage(page);
  });

  test('react demo renders the white workbench with center conversation focus', async ({ page }) => {
    await expectThreeColumnWorkbench(page);
    await expectPanelRailsAlignWithCenter(page);
    await expect(page.getByText('对话工作台').first()).toBeVisible();
    await expect(page.getByRole('tab', { name: '上下文详情' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tab', { name: '主动预警' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '智能体协作' })).toBeVisible();
    await expect(page.getByText('指标概览').first()).toBeVisible();
    await expect(page.getByText('构建成功率').first()).toBeHidden();
    await expectViewportFit(page);
  });

  test('collapsed metric labels stay generic across roles', async ({ page }) => {
    await page.getByTestId('top-metrics-toggle').click();
    await page.locator('[data-interaction-id="click:sidebar:role:development"]').click();
    await expect(page.getByText('今日部署').first()).toBeVisible();
    await expect(page.getByText('构建成功率').first()).toBeVisible();
    await expect(page.getByText('活跃告警').first()).toBeVisible();
    await expect(page.getByText('Sprint 进度').first()).toBeVisible();
  });

  test('center column grows on wide desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await waitForReactHomepage(page);
    await expectThreeColumnWorkbench(page);
    await expectPanelRailsAlignWithCenter(page);
    await expectSidebarsSpanPanelRows(page);
    await expectViewportFit(page);
  });

  test('conversation workbench accepts input and runs scenario dialogue', async ({ page }) => {
    await expect(page.getByTestId('scenario-rail')).toBeVisible();
    await page.getByTestId('chat-input').fill('帮我检查今天发布风险');
    await page.getByTestId('chat-send-btn').click();
    await expect(page.getByText('帮我检查今天发布风险')).toBeVisible();
    await expect(page.getByText('正在巡检发布风险')).toBeVisible();
    await expect(page.getByText('拉取发布清单')).toBeVisible();

    await page.getByTestId('scenario-chip-requirement-to-design').click();
    await expect(page.getByText('正在执行需求转设计编排流程')).toBeVisible();
    await expect(page.locator('.execution-step').filter({ hasText: '生成设计方案' }).last()).toBeVisible();
  });

  test('left sidebar has default-open conversation list and collapsible groups', async ({ page }) => {
    await expect(page.getByTestId('conversation-list')).toBeVisible();
    await expect(page.getByText('登录优化风险巡检')).toBeVisible();
    await expect(page.getByText('发布门禁检查')).toBeVisible();

    const toolsToggle = page.getByTestId('sidebar-section-toggle-tools');
    await expect(toolsToggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByText('持续集成平台')).toBeVisible();

    await toolsToggle.click();
    await expect(toolsToggle).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByTestId('sidebar-section-body-tools')).toBeHidden();

    await toolsToggle.click();
    await expect(toolsToggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByText('持续集成平台')).toBeVisible();
  });

  test('agent collaboration tab shows richer orchestration detail', async ({ page }) => {
    await page.getByRole('tab', { name: '智能体协作' }).click();
    await expect(page.getByTestId('agent-collaboration')).toBeVisible();
    await expect(page.getByTestId('collab-summary-grid')).toContainText('当前负责人');
    await expect(page.getByTestId('agent-roster')).toContainText('方案设计 Agent');
    await expect(page.getByText('交接队列')).toBeVisible();
    await expect(page.getByText('已沉淀产出')).toBeVisible();
    await expect(page.getByText('当前风险')).toBeVisible();
  });
});

test.describe('collapsed panels toggle', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await waitForReactHomepage(page);
  });

  test('react top and bottom panels expand and collapse without overflow', async ({ page }) => {
    const topToggle = page.getByTestId('top-metrics-toggle');
    const bottomToggle = page.getByTestId('bottom-history-toggle');

    expect(await panelHeight(page, 'top-metrics-panel')).toBeLessThanOrEqual(44);
    expect(await panelHeight(page, 'bottom-history-panel')).toBeLessThanOrEqual(56);
    const sidebarsBefore = await Promise.all([
      page.getByTestId('workbench-left').boundingBox(),
      page.getByTestId('workbench-right').boundingBox(),
    ]);
    await expect(topToggle).toHaveAttribute('aria-expanded', 'false');
    await expect(bottomToggle).toHaveAttribute('aria-expanded', 'false');

    await topToggle.click();
    await expect(topToggle).toHaveAttribute('aria-expanded', 'true');
    await waitForPanelHeight(page, 'top-metrics-panel', 150, 180);

    await bottomToggle.click();
    await expect(bottomToggle).toHaveAttribute('aria-expanded', 'true');
    await waitForPanelHeight(page, 'bottom-history-panel', 280, 320);
    await expect(page.getByTestId('bottom-history-content')).toBeVisible();
    await expect(page.getByText('全流程管线')).toBeVisible();
    await expect(page.getByText('12 条更新，最新 10:32')).toBeVisible();
    await expect(page.getByText('接口契约检查通过')).toBeVisible();
    await expectSidebarsSpanPanelRows(page);
    const sidebarsExpanded = await Promise.all([
      page.getByTestId('workbench-left').boundingBox(),
      page.getByTestId('workbench-right').boundingBox(),
    ]);
    expect(Math.abs(sidebarsExpanded[0].height - sidebarsBefore[0].height)).toBeLessThanOrEqual(1);
    expect(Math.abs(sidebarsExpanded[1].height - sidebarsBefore[1].height)).toBeLessThanOrEqual(1);

    await topToggle.click();
    await bottomToggle.click();
    await waitForPanelHeight(page, 'top-metrics-panel', 30, 44);
    await waitForPanelHeight(page, 'bottom-history-panel', 36, 56);
    await expectViewportFit(page);
  });

  test('static top and bottom panels expand and collapse', async ({ page }) => {
    await page.goto(staticUrl, { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('今日部署').first()).toBeHidden();
    await page.getByTestId('top-metrics-toggle').click();
    await page.getByTestId('bottom-history-toggle').click();
    await waitForPanelHeight(page, 'top-metrics-panel', 150, 180);
    await waitForPanelHeight(page, 'bottom-history-panel', 280, 320);
    await expect(page.locator('.metrics-detail').getByText('今日部署')).toBeVisible();
    await expect(page.getByText('REQ-2024-0892 已完成设计评审')).toBeVisible();
    await page.getByTestId('top-metrics-toggle').click();
    await page.getByTestId('bottom-history-toggle').click();
    await waitForPanelHeight(page, 'top-metrics-panel', 30, 44);
    await waitForPanelHeight(page, 'bottom-history-panel', 36, 56);
  });

  test('static prototype supports typed conversation demo', async ({ page }) => {
    await page.goto(staticUrl, { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('scenario-rail')).toBeVisible();
    await page.getByTestId('chat-input').fill('跟进 payment-service 的告警');
    await page.getByTestId('chat-send-btn').click();
    await expect(page.getByText('跟进 payment-service 的告警')).toBeVisible();
    await expect(page.getByText('正在跟进告警处置')).toBeVisible();
    await expect(page.getByText('关联最近变更 — 进行中')).toBeVisible();
  });

  test('static prototype sidebar groups collapse and collaboration detail is rich', async ({ page }) => {
    await page.goto(staticUrl, { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('conversation-list')).toBeVisible();
    await expect(page.getByText('登录优化风险巡检')).toBeVisible();

    const workbenchToggle = page.getByTestId('sidebar-section-toggle-workbench');
    await workbenchToggle.click();
    await expect(workbenchToggle).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByTestId('sidebar-section-body-workbench')).toBeHidden();

    await page.getByRole('tab', { name: '智能体协作' }).click();
    await expect(page.getByTestId('collab-summary-grid')).toContainText('预计完成');
    await expect(page.getByTestId('agent-roster')).toContainText('风险检查 Agent');
    await expect(page.locator('.collab-output-title').filter({ hasText: '接口草案' })).toBeVisible();
  });

  test('left and right sidebars collapse independently', async ({ page }) => {
    const leftToggle = page.getByTestId('left-sidebar-toggle');
    const rightToggle = page.getByTestId('right-sidebar-toggle');
    const centerBefore = await page.getByTestId('workbench-center').boundingBox();

    await leftToggle.click();
    await expect(leftToggle).toHaveAttribute('aria-expanded', 'false');
    const leftCollapsedCenter = await page.getByTestId('workbench-center').boundingBox();
    expect(leftCollapsedCenter.width).toBeGreaterThan(centerBefore.width);

    await rightToggle.click();
    await expect(rightToggle).toHaveAttribute('aria-expanded', 'false');
    const bothCollapsedCenter = await page.getByTestId('workbench-center').boundingBox();
    expect(bothCollapsedCenter.width).toBeGreaterThan(leftCollapsedCenter.width);

    await leftToggle.click();
    await rightToggle.click();
    await expect(leftToggle).toHaveAttribute('aria-expanded', 'true');
    await expect(rightToggle).toHaveAttribute('aria-expanded', 'true');
  });

  test('top navigation bar collapses and expands', async ({ page }) => {
    const topNavToggle = page.getByTestId('top-nav-toggle');
    await expect(topNavToggle).toHaveAttribute('aria-expanded', 'true');
    await waitForPanelHeight(page, 'workbench-titlebar', 50, 60);

    await topNavToggle.click();
    await expect(topNavToggle).toHaveAttribute('aria-expanded', 'false');
    await waitForPanelHeight(page, 'workbench-titlebar', 28, 36);
    await expect(page.getByTestId('search-input')).toBeHidden();
    await expect(page.getByTestId('left-sidebar-toggle')).toBeVisible();
    await expect(page.getByTestId('right-sidebar-toggle')).toBeVisible();

    await topNavToggle.click();
    await expect(topNavToggle).toHaveAttribute('aria-expanded', 'true');
    await waitForPanelHeight(page, 'workbench-titlebar', 50, 60);
    await expect(page.getByTestId('search-input')).toBeVisible();
  });

  test('static top navigation bar collapses and expands', async ({ page }) => {
    await page.goto(staticUrl, { waitUntil: 'domcontentloaded' });
    const topNavToggle = page.getByTestId('top-nav-toggle');
    await expect(topNavToggle).toHaveAttribute('aria-expanded', 'true');
    await waitForPanelHeight(page, 'workbench-titlebar', 50, 60);

    await topNavToggle.click();
    await expect(topNavToggle).toHaveAttribute('aria-expanded', 'false');
    await waitForPanelHeight(page, 'workbench-titlebar', 28, 36);
    await expect(page.locator('.titlebar-center')).toBeHidden();

    await topNavToggle.click();
    await expect(topNavToggle).toHaveAttribute('aria-expanded', 'true');
    await waitForPanelHeight(page, 'workbench-titlebar', 50, 60);
  });
});

test.describe('accessibility affordances', () => {
  test.beforeEach(async ({ page }) => {
    await waitForReactHomepage(page);
  });

  test('toggles and tabs expose ARIA state and keyboard focus', async ({ page }) => {
    await expect(page.getByTestId('top-metrics-toggle')).toHaveAttribute('aria-controls', 'top-metrics-content');
    await expect(page.getByTestId('bottom-history-toggle')).toHaveAttribute('aria-controls', 'bottom-history-content');
    await expect(page.getByTestId('top-nav-toggle')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByRole('tab', { name: '上下文详情' })).toHaveAttribute('aria-selected', 'true');
    await expectNoFocusableAriaHiddenDescendants(page);

    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    const focusableFound = await page.evaluate(() => {
      const active = document.activeElement;
      return Boolean(active && (active.matches('button, input, [role="tab"]') || active.closest('button, [role="tab"]')));
    });
    expect(focusableFound).toBe(true);

    await page.getByTestId('chat-input').focus();
    await expect(page.getByTestId('chat-input')).toBeFocused();
  });
});

test.describe('desktop screenshots', () => {
  test('captures static and react desktop evidence', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(staticUrl, { waitUntil: 'domcontentloaded' });
    await expectViewportFit(page);
    await page.screenshot({ path: path.join(evidenceDir, 'task-5-static-prototype-1440.png'), fullPage: false });

    await page.setViewportSize({ width: 1440, height: 900 });
    await waitForReactHomepage(page);
    await expectViewportFit(page);
    await page.screenshot({ path: path.join(evidenceDir, 'task-5-homepage-workbench-1440.png'), fullPage: false });

    await page.setViewportSize({ width: 1920, height: 1080 });
    await waitForReactHomepage(page);
    await expectViewportFit(page);
    await page.screenshot({ path: path.join(evidenceDir, 'task-5-homepage-workbench-1920.png'), fullPage: false });

    for (const name of [
      'task-5-static-prototype-1440.png',
      'task-5-homepage-workbench-1440.png',
      'task-5-homepage-workbench-1920.png',
    ]) {
      expect(fs.statSync(path.join(evidenceDir, name)).size).toBeGreaterThan(0);
    }
  });
});
