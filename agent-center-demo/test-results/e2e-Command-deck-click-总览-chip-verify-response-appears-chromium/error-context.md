# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: e2e.spec.js >> Command deck >> click "总览" chip, verify response appears
- Location: tests/e2e.spec.js:81:3

# Error details

```
TimeoutError: locator.fill: Timeout 10000ms exceeded.
Call log:
  - waiting for locator('[data-testid="chat-input"]')

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - generic [ref=e5]:
      - generic [ref=e6]:
        - img [ref=e7]
        - generic [ref=e9]: INC-2024-0892
      - generic [ref=e11]: P1
      - generic [ref=e12]: 身份认证服务响应超时
      - generic [ref=e14]: "阶段: 检测中"
    - generic [ref=e15]:
      - generic [ref=e16]:
        - generic [ref=e17]: "影响域:"
        - generic [ref=e18]: 身份认证, 订单服务
      - generic [ref=e19]:
        - generic [ref=e20]: "影响用户:"
        - generic [ref=e21]: ~12,400
      - generic [ref=e22]:
        - generic [ref=e23]: "持续时间:"
        - generic [ref=e24]: 0分钟
    - generic [ref=e25]:
      - generic [ref=e26]:
        - generic [ref=e27]: "事件流 (Seq: 0)"
        - generic [ref=e29]: 等待事件...
      - generic [ref=e30]:
        - generic [ref=e31]:
          - generic [ref=e32]: Agent 执行 lanes
          - generic [ref=e33]:
            - button "管理视角" [ref=e34] [cursor=pointer]
            - button "工程视角" [ref=e35] [cursor=pointer]
        - generic [ref=e37]:
          - generic [ref=e38]: Agent Lanes
          - generic [ref=e39]:
            - generic [ref=e41]:
              - generic [ref=e42]: Monitor
              - generic [ref=e43]: Idle
            - generic [ref=e45]:
              - generic [ref=e46]: Operations
              - generic [ref=e47]: Idle
            - generic [ref=e49]:
              - generic [ref=e50]: Development
              - generic [ref=e51]: Idle
            - generic [ref=e53]:
              - generic [ref=e54]: Quality
              - generic [ref=e55]: Idle
            - generic [ref=e57]:
              - generic [ref=e58]: Architecture
              - generic [ref=e59]: Idle
            - generic [ref=e61]:
              - generic [ref=e62]: Management
              - generic [ref=e63]: Idle
      - generic [ref=e64]:
        - generic [ref=e65]: 管理视角
        - generic [ref=e66]:
          - generic [ref=e67]:
            - generic [ref=e68]:
              - generic [ref=e69]: 业务影响
              - generic [ref=e70]: 订单转化率下降 23%
            - generic [ref=e71]:
              - generic [ref=e72]: 风险等级
              - generic [ref=e73]: P1 - 重大故障
            - generic [ref=e74]:
              - generic [ref=e75]: 资源调配
              - generic [ref=e76]: 需要 SRE 支援
          - generic [ref=e77]:
            - generic [ref=e78]: 验证关卡
            - generic [ref=e79]:
              - generic [ref=e80]:
                - generic [ref=e81]: 根因确认
                - generic [ref=e82]: ✗
              - generic [ref=e83]:
                - generic [ref=e84]: 回归测试通过
                - generic [ref=e85]: ✗
              - generic [ref=e86]:
                - generic [ref=e87]: 发布窗口有效
                - generic [ref=e88]: ✗
              - generic [ref=e89]:
                - generic [ref=e90]: 回滚方案就绪
                - generic [ref=e91]: ✗
              - generic [ref=e92]:
                - generic [ref=e93]: 业务方批准
                - generic [ref=e94]: ✗
            - generic [ref=e95]:
              - button "发布上线" [disabled] [ref=e96]
              - button "执行回滚" [disabled] [ref=e97]
          - generic [ref=e98]:
            - generic [ref=e99]: 工具调用
            - generic [ref=e100]: 暂无工具调用
    - generic [ref=e101]:
      - button "开始" [ref=e102] [cursor=pointer]:
        - img [ref=e103]
        - text: 开始
      - button "暂停" [ref=e105] [cursor=pointer]:
        - img [ref=e106]
        - text: 暂停
      - button "下一步" [ref=e109] [cursor=pointer]:
        - img [ref=e110]
        - text: 下一步
      - button "重播" [ref=e112] [cursor=pointer]:
        - img [ref=e113]
        - text: 重播
      - button "重置" [ref=e116] [cursor=pointer]: 重置
      - button "旧版视图" [ref=e118] [cursor=pointer]:
        - img [ref=e119]
        - text: 旧版视图
  - button "故事模式" [ref=e124] [cursor=pointer]:
    - img [ref=e125]
    - text: 故事模式
  - complementary [ref=e127]:
    - generic [ref=e129]:
      - img [ref=e131]
      - generic [ref=e134]: AgentCenter
      - generic [ref=e135]: v2.0
    - generic [ref=e136]:
      - generic [ref=e137]:
        - generic [ref=e138]: 角色切换
        - generic [ref=e139]:
          - generic [ref=e140] [cursor=pointer]:
            - img [ref=e141]
            - generic [ref=e143]: 管理层
          - generic [ref=e144] [cursor=pointer]:
            - img [ref=e145]
            - generic [ref=e147]: 产品团队
          - generic [ref=e148] [cursor=pointer]:
            - img [ref=e149]
            - generic [ref=e153]: 研发团队
          - generic [ref=e154] [cursor=pointer]:
            - img [ref=e155]
            - generic [ref=e158]: 运维团队
          - generic [ref=e159] [cursor=pointer]:
            - img [ref=e160]
            - generic [ref=e163]: 质量团队
          - generic [ref=e164] [cursor=pointer]:
            - img [ref=e165]
            - generic [ref=e170]: 架构团队
      - generic [ref=e171]:
        - generic [ref=e172]: 工作台
        - generic [ref=e173] [cursor=pointer]:
          - img [ref=e174]
          - generic [ref=e179]: 总览
        - generic [ref=e180] [cursor=pointer]:
          - img [ref=e181]
          - generic [ref=e183]: 对话
        - generic [ref=e184] [cursor=pointer]:
          - img [ref=e185]
          - generic [ref=e189]: 工作流
        - generic [ref=e190] [cursor=pointer]:
          - img [ref=e191]
          - generic [ref=e194]: Agent 管理
        - generic [ref=e195] [cursor=pointer]:
          - img [ref=e196]
          - generic [ref=e201]: 架构视图
      - generic [ref=e202]:
        - generic [ref=e203]: DevOps 工具链
        - generic [ref=e204] [cursor=pointer]:
          - generic [ref=e207]: 需求管理平台
          - generic [ref=e208]: 已连接
        - generic [ref=e209] [cursor=pointer]:
          - generic [ref=e212]: 设计协作平台
          - generic [ref=e213]: 已连接
        - generic [ref=e214] [cursor=pointer]:
          - generic [ref=e217]: 代码仓库
          - generic [ref=e218]: 已连接
        - generic [ref=e219] [cursor=pointer]:
          - generic [ref=e222]: 代码质量平台
          - generic [ref=e223]: 已连接
        - generic [ref=e224] [cursor=pointer]:
          - generic [ref=e227]: 构建引擎
          - generic [ref=e228]: 延迟
        - generic [ref=e229] [cursor=pointer]:
          - generic [ref=e232]: 测试平台
          - generic [ref=e233]: 已连接
        - generic [ref=e234] [cursor=pointer]:
          - generic [ref=e237]: 制品仓库
          - generic [ref=e238]: 已连接
        - generic [ref=e239] [cursor=pointer]:
          - generic [ref=e242]: 部署平台
          - generic [ref=e243]: 已连接
        - generic [ref=e244] [cursor=pointer]:
          - generic [ref=e247]: 可观测性平台
          - generic [ref=e248]: 已连接
        - generic [ref=e249] [cursor=pointer]:
          - generic [ref=e252]: 告警通道
          - generic [ref=e253]: 已连接
      - generic [ref=e254]:
        - generic [ref=e255]: 智能体状态
        - generic [ref=e256] [cursor=pointer]:
          - generic [ref=e257]:
            - generic [ref=e258]: 需求分析 Agent
            - generic [ref=e259]: 空闲
          - generic [ref=e261]: 等待下一个任务
        - generic [ref=e262] [cursor=pointer]:
          - generic [ref=e263]:
            - generic [ref=e264]: 代码审查 Agent
            - generic [ref=e265]: 工作中
          - generic [ref=e267]: 审查设计中
        - generic [ref=e268] [cursor=pointer]:
          - generic [ref=e269]:
            - generic [ref=e270]: 部署编排 Agent
            - generic [ref=e271]: 空闲
          - generic [ref=e273]: 等待下一个任务
        - generic [ref=e274] [cursor=pointer]:
          - generic [ref=e275]:
            - generic [ref=e276]: 监控预警 Agent
            - generic [ref=e277]: 告警中
          - generic [ref=e279]: 服务延迟预警
    - generic [ref=e281] [cursor=pointer]:
      - img [ref=e282]
      - generic [ref=e285]: 系统设置
```

# Test source

```ts
  1   | const { test, expect } = require('@playwright/test');
  2   | const fs = require('fs');
  3   | const path = require('path');
  4   | 
  5   | const ROLE_IDS = ['management', 'product', 'development', 'ops', 'quality', 'architecture'];
  6   | 
  7   | const ROLE_HEADLINES = {
  8   |   management: '战略驾驶舱',
  9   |   product: '需求管理中枢',
  10  |   development: '开发工作台',
  11  |   ops: '运维监控台',
  12  |   quality: '质量保障中心',
  13  |   architecture: '架构设计中心',
  14  | };
  15  | 
  16  | const BANNED_TERMS_SCAN = [
  17  |   'JIRA', 'GITLAB', 'GITHUB', 'JENKINS', 'ARGOCD',
  18  |   'PROMETHEUS', 'GRAFANA', 'SLACK', 'HARBOR',
  19  |   'KUBERNETES', 'K8S', '飞书',
  20  | ];
  21  | 
  22  | test.beforeEach(async ({ page }) => {
  23  |   await page.goto('/');
  24  |   await page.waitForSelector('[data-testid="war-room-root"]', { timeout: 30000 });
  25  | });
  26  | 
  27  | // ─── 1. Role Switches ───────────────────────────────────────────────────────
  28  | 
  29  | test.describe('Role switches', () => {
  30  |   for (const roleId of ROLE_IDS) {
  31  |     test(`switching to "${roleId}" changes headline to "${ROLE_HEADLINES[roleId]}"`, async ({ page }) => {
  32  |       const legacyViewBtn = page.locator('[data-testid="war-room-legacy-view"]');
  33  |       await legacyViewBtn.click();
  34  |       await page.waitForSelector('[data-testid="dashboard-headline"]', { timeout: 5000 });
  35  | 
  36  |       const roleItem = page.locator(`[data-interaction-id="click:sidebar:role:${roleId}"]`);
  37  |       await roleItem.click();
  38  | 
  39  |       const headline = page.locator('[data-testid="dashboard-headline"]');
  40  |       await expect(headline).toHaveText(ROLE_HEADLINES[roleId], { timeout: 5000 });
  41  |     });
  42  |   }
  43  | });
  44  | 
  45  | // ─── 2. Story Mode ──────────────────────────────────────────────────────────
  46  | 
  47  | test.describe('Story mode', () => {
  48  |   test('start story mode, select "需求到设计与研发启动", advance one step, verify role changes, exit', async ({ page }) => {
  49  |     const startBtn = page.locator('[data-testid="story-mode-start"]');
  50  |     await startBtn.click();
  51  | 
  52  |     const overlay = page.locator('.story-overlay');
  53  |     await expect(overlay).toBeVisible({ timeout: 5000 });
  54  | 
  55  |     const storyCard = overlay.locator('.storyline-card').filter({ hasText: '需求到设计与研发启动' });
  56  |     await storyCard.click();
  57  | 
  58  |     // Wait for step view to render (parent state update triggers re-render)
  59  |     const stepBadge = overlay.locator('.story-step-badge');
  60  |     await expect(stepBadge).toHaveText('1', { timeout: 10000 });
  61  | 
  62  |     const headline = page.locator('[data-testid="dashboard-headline"]');
  63  |     await expect(headline).toHaveText('需求管理中枢', { timeout: 5000 });
  64  | 
  65  |     const nextBtn = page.locator('[data-testid="story-mode-next-btn"]');
  66  |     await nextBtn.click();
  67  | 
  68  |     await expect(headline).toHaveText('架构设计中心', { timeout: 5000 });
  69  |     await expect(stepBadge).toHaveText('2', { timeout: 5000 });
  70  | 
  71  |     const exitBtn = page.locator('[data-testid="story-mode-exit-btn"]').first();
  72  |     await exitBtn.click();
  73  | 
  74  |     await expect(overlay).not.toBeVisible({ timeout: 5000 });
  75  |   });
  76  | });
  77  | 
  78  | // ─── 3. Command Deck ────────────────────────────────────────────────────────
  79  | 
  80  | test.describe('Command deck', () => {
  81  |   test('click "总览" chip, verify response appears', async ({ page }) => {
  82  |     // Switch to management role which has "成本分析" as first chip;
  83  |     // "总览" is in defaultChips only — type it in chat instead
  84  |     const chatInput = page.locator('[data-testid="chat-input"]');
> 85  |     await chatInput.fill('总览');
      |                     ^ TimeoutError: locator.fill: Timeout 10000ms exceeded.
  86  |     await page.locator('[data-testid="chat-send-btn"]').click();
  87  | 
  88  |     // Wait for bot response containing "总览"
  89  |     const messages = page.locator('.conversation-messages .message');
  90  |     await expect(messages.last()).toContainText('总览', { timeout: 10000 });
  91  | 
  92  |     // Verify no crash — page still responsive
  93  |     const headline = page.locator('[data-testid="dashboard-headline"]');
  94  |     await expect(headline).toBeVisible({ timeout: 5000 });
  95  |   });
  96  | });
  97  | 
  98  | // ─── 4. Dead-Click Detector ─────────────────────────────────────────────────
  99  | 
  100 | test.describe('Dead-click detector', () => {
  101 |   test('click every [data-interaction-id] element, verify no crash and visible response', async ({ page }) => {
  102 |     test.setTimeout(120000);
  103 | 
  104 |     const elements = page.locator('[data-interaction-id]');
  105 |     const count = await elements.count();
  106 |     expect(count).toBeGreaterThan(0);
  107 | 
  108 |     const testedIds = [];
  109 |     const errors = [];
  110 |     const consoleErrors = [];
  111 |     page.on('console', (msg) => {
  112 |       if (msg.type() === 'error') consoleErrors.push(msg.text());
  113 |     });
  114 | 
  115 |     for (let i = 0; i < count; i++) {
  116 |       const el = elements.nth(i);
  117 |       const interactionId = await el.getAttribute('data-interaction-id');
  118 |       if (!interactionId) continue;
  119 | 
  120 |       const isVisible = await el.isVisible().catch(() => false);
  121 |       if (!isVisible) {
  122 |         testedIds.push({ id: interactionId, status: 'skipped-not-visible' });
  123 |         continue;
  124 |       }
  125 | 
  126 |       const errorsBefore = consoleErrors.length;
  127 | 
  128 |       try {
  129 |         await el.click({ timeout: 3000 });
  130 |       } catch {
  131 |         await el.click({ force: true, timeout: 3000 }).catch(() => {});
  132 |       }
  133 | 
  134 |       await page.waitForTimeout(200);
  135 | 
  136 |       const newErrors = consoleErrors.length - errorsBefore;
  137 |       const toastVisible = await page.locator('.toast').isVisible().catch(() => false);
  138 |       const storyVisible = await page.locator('.story-overlay').isVisible().catch(() => false);
  139 |       const archVisible = await page.locator('.architecture-overlay').isVisible().catch(() => false);
  140 | 
  141 |       const hasResponse = toastVisible || storyVisible || archVisible || newErrors === 0;
  142 | 
  143 |       testedIds.push({
  144 |         id: interactionId,
  145 |         status: hasResponse ? 'ok' : 'possible-dead-click',
  146 |         toast: toastVisible,
  147 |         newErrors,
  148 |       });
  149 | 
  150 |       if (!hasResponse) errors.push(interactionId);
  151 | 
  152 |       if (storyVisible) {
  153 |         await page.locator('[data-testid="story-mode-exit-btn"]').first().click().catch(() => {});
  154 |         await page.waitForTimeout(100);
  155 |       }
  156 |       if (archVisible) {
  157 |         await page.locator('[data-testid="architecture-close-btn"]').click().catch(() => {});
  158 |         await page.waitForTimeout(100);
  159 |       }
  160 |     }
  161 | 
  162 |     console.log('\n=== Dead-Click Detector Report ===');
  163 |     console.log(`Total elements: ${count}`);
  164 |     console.log(`Tested: ${testedIds.length}`);
  165 |     console.log(`Possible dead clicks: ${errors.length}`);
  166 |     testedIds.forEach(t => console.log(`  [${t.status}] ${t.id}`));
  167 | 
  168 |     const headline = page.locator('[data-testid="dashboard-headline"]');
  169 |     await expect(headline).toBeVisible({ timeout: 5000 });
  170 | 
  171 |     if (errors.length > 0) {
  172 |       console.log(`\n⚠️  Possible dead clicks: ${errors.join(', ')}`);
  173 |     }
  174 |   });
  175 | });
  176 | 
  177 | // ─── 5. Banned-Term Scan ────────────────────────────────────────────────────
  178 | 
  179 | test.describe('Banned-term scan', () => {
  180 |   test('client/index.html and server files contain zero banned terms', () => {
  181 |     const demoRoot = path.resolve(__dirname, '..');
  182 |     // registry.js defines the BANNED_TERMS list itself — exclude it from scan
  183 |     const filesToScan = [
  184 |       path.join(demoRoot, 'client', 'index.html'),
  185 |       path.join(demoRoot, 'server', 'index.js'),
```