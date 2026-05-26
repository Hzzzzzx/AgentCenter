import "./styles.css"

type ViewId = "home" | "board" | "workflow"
type PanelTab = "confirmations" | "details" | "artifact"
type Status = "normal" | "running" | "waiting" | "blocked" | "done"

type WorkItem = {
  id: string
  type: "FE" | "US" | "TASK" | "BUG" | "VULN"
  title: string
  status: Status
  priority: "低" | "中" | "高" | "紧急"
  owner: string
  node: string
  updated: string
  progress: number
}

const workItems: WorkItem[] = [
  {
    id: "FE-1201",
    type: "FE",
    title: "登录页视觉与权限提示优化",
    status: "running",
    priority: "高",
    owner: "张敏",
    node: "代码生成",
    updated: "10 分钟",
    progress: 62,
  },
  {
    id: "US-1188",
    type: "US",
    title: "项目空间支持按迭代筛选任务",
    status: "waiting",
    priority: "中",
    owner: "李然",
    node: "等待确认",
    updated: "26 分钟",
    progress: 48,
  },
  {
    id: "TASK-771",
    type: "TASK",
    title: "补齐工作项会话回归验证清单",
    status: "normal",
    priority: "中",
    owner: "王一",
    node: "需求澄清",
    updated: "1 小时",
    progress: 18,
  },
  {
    id: "BUG-219",
    type: "BUG",
    title: "Skill 上传后刷新状态不同步",
    status: "blocked",
    priority: "高",
    owner: "平台组",
    node: "问题定位",
    updated: "2 小时",
    progress: 34,
  },
  {
    id: "VULN-037",
    type: "VULN",
    title: "MCP 密钥引用展示脱敏",
    status: "done",
    priority: "紧急",
    owner: "安全组",
    node: "已完成",
    updated: "昨天",
    progress: 100,
  },
]

const sessions = [
  { title: "通用会话：迭代范围确认", meta: "自由讨论 · 平台上下文", time: "刚刚" },
  { title: "任务会话：FE-1201", meta: "任务上下文 · Agent Runtime", time: "10 分钟" },
  { title: "任务会话：BUG-219", meta: "任务上下文 · 诊断中", time: "2 小时" },
]

const confirmations = [
  { id: "CONF-1024", title: "确认是否允许生成登录页修改补丁", source: "FE-1201 / 代码生成" },
  { id: "CONF-1025", title: "确认 US-1188 是否进入本迭代", source: "US-1188 / 需求澄清" },
]

const state = {
  view: "home" as ViewId,
  panelTab: "confirmations" as PanelTab,
  selectedId: workItems[0].id,
}

function selectedItem() {
  return workItems.find((item) => item.id === state.selectedId) ?? workItems[0]
}

function statusText(status: Status) {
  const map: Record<Status, string> = {
    normal: "待处理",
    running: "处理中",
    waiting: "待确认",
    blocked: "已阻塞",
    done: "已完成",
  }
  return map[status]
}

function render() {
  const app = document.querySelector<HTMLDivElement>("#app")
  if (!app) return
  app.innerHTML = `
    <div class="app-shell">
      ${titleBar()}
      ${leftSidebar()}
      <main class="center-workbench">${centerView()}</main>
      ${rightPanel()}
      ${statusBar()}
    </div>
  `
  bindEvents(app)
}

function titleBar() {
  return `
    <header class="title-bar">
      <div class="title-bar__brand">
        <div class="title-bar__logo" aria-hidden="true">${iconSpark()}</div>
        <div class="title-bar__name"><span>AI DevOps</span><strong>v2.0</strong></div>
      </div>
      <div class="title-bar__context" aria-label="当前项目空间迭代">
        <span>当前项目</span>
        <strong>CloudReq Platform</strong>
        <select aria-label="选择当前迭代">
          <option>迭代 2026.05</option>
          <option>迭代 2026.06</option>
        </select>
      </div>
      <label class="title-bar__search">
        ${iconSearch()}
        <input type="search" placeholder="搜索 FE、US、Task、Work、缺陷、漏洞..." />
      </label>
      <div class="title-bar__actions">
        <button class="ghost-button" type="button">浅色</button>
        <div class="title-bar__user"><span>张</span><div><strong>产品负责人 A</strong><em>技术负责人</em></div></div>
      </div>
    </header>
  `
}

function leftSidebar() {
  return `
    <aside class="left-sidebar">
      <div class="left-sidebar__topbar">
        <button class="icon-button" type="button" aria-label="收起侧栏">${iconPanel()}</button>
      </div>
      <nav class="left-sidebar__nav" aria-label="固定工作台入口">
        ${navButton("home", "首页", iconGrid())}
        ${navButton("board", "任务看板", iconColumns())}
        ${navButton("workflow", "任务编排", iconWorkflow())}
      </nav>
      <section class="left-sidebar__sessions" aria-label="会话列表">
        <div class="section-toggle">会话列表 <span>⌄</span></div>
        <div class="session-group-title">通用会话 <button type="button">+</button></div>
        ${sessions
          .slice(0, 1)
          .map(
            (session) => `
              <button class="session-item" type="button">
                <span><strong>${session.title}</strong><em>${session.time}</em></span>
                <small>${session.meta}</small>
              </button>
            `,
          )
          .join("")}
        <div class="session-group-title">任务会话 <small>2</small></div>
        ${sessions
          .slice(1)
          .map(
            (session) => `
              <button class="session-item" type="button">
                <span><strong>${session.title}</strong><em>${session.time}</em></span>
                <small>${session.meta}</small>
              </button>
            `,
          )
          .join("")}
      </section>
      <div class="left-sidebar__footer">
        <button class="settings-button" type="button">${iconSettings()}<span>设置</span></button>
        <div class="settings-menu">
          <button type="button">项目管理</button>
          <button type="button">Skill 管理</button>
          <button type="button">MCP 管理</button>
        </div>
      </div>
    </aside>
  `
}

function navButton(id: ViewId, label: string, icon: string) {
  return `
    <button class="nav-item ${state.view === id ? "nav-item--active" : ""}" data-view="${id}" type="button">
      ${icon}<span>${label}</span>
    </button>
  `
}

function centerView() {
  if (state.view === "board") return boardView()
  if (state.view === "workflow") return workflowView()
  return homeView()
}

function homeView() {
  return `
    <section class="home-overview">
      <div class="page-heading">
        <div>
          <p>首页</p>
          <h1>项目工作台</h1>
        </div>
        <div class="demo-badge">展示数据</div>
      </div>
      <div class="type-grid">
        ${(["FE", "US", "TASK", "BUG", "VULN"] as const)
          .map((type) => {
            const items = workItems.filter((item) => item.type === type)
            return `
              <article class="type-card">
                <div class="type-card__head"><strong>${type}</strong><span>${items.length}</span></div>
                <div class="type-card__summary">${items[0]?.node ?? "暂无节点"}</div>
                <div class="progress"><i style="width:${items[0]?.progress ?? 0}%"></i></div>
                <div class="type-card__chips"><span>处理中 ${items.filter((item) => item.status === "running").length}</span><span>待确认 ${items.filter((item) => item.status === "waiting").length}</span></div>
              </article>
            `
          })
          .join("")}
      </div>
      <section class="work-list">
        <div class="list-toolbar">
          <strong>近期工作项</strong>
          <div><button type="button">全部类型</button><button type="button">全部状态</button></div>
        </div>
        ${workItems.map(workItemRow).join("")}
      </section>
    </section>
  `
}

function boardView() {
  const columns: Array<{ title: string; statuses: Status[] }> = [
    { title: "待处理", statuses: ["normal"] },
    { title: "处理中", statuses: ["running"] },
    { title: "待确认 / 阻塞", statuses: ["waiting", "blocked"] },
    { title: "已完成", statuses: ["done"] },
  ]
  return `
    <section class="board-view">
      <div class="page-heading"><div><p>任务看板</p><h1>工作项流转</h1></div><button type="button">新建工作项</button></div>
      <div class="board-columns">
        ${columns
          .map(
            (column) => `
              <section class="board-column">
                <header>${column.title}<span>${workItems.filter((item) => column.statuses.includes(item.status)).length}</span></header>
                ${workItems
                  .filter((item) => column.statuses.includes(item.status))
                  .map((item) => `<button class="board-card" data-work-item="${item.id}" type="button"><strong>${item.id}</strong><span>${item.title}</span><em>${item.node}</em></button>`)
                  .join("")}
              </section>
            `,
          )
          .join("")}
      </div>
    </section>
  `
}

function workflowView() {
  return `
    <section class="workflow-view">
      <div class="page-heading"><div><p>任务编排</p><h1>工作流模板</h1></div><button type="button">新建模板</button></div>
      <div class="workflow-card">
        <div class="workflow-line"><span>需求澄清</span><i></i><span>设计评审</span><i></i><span>代码生成</span><i></i><span>验证回归</span><i></i><span>交付确认</span></div>
        <p>这一页只复刻 1026 的展示入口。真实工作流逻辑等你后面明确后再接。</p>
      </div>
    </section>
  `
}

function workItemRow(item: WorkItem) {
  return `
    <button class="work-row ${state.selectedId === item.id ? "work-row--active" : ""}" data-work-item="${item.id}" type="button">
      <span class="type-dot">${item.type}</span>
      <span class="work-row__main"><strong>${item.title}</strong><small>${item.id} · ${item.owner} · ${item.updated}</small></span>
      <span class="status-pill status-pill--${item.status}">${statusText(item.status)}</span>
      <span class="work-row__node">${item.node}</span>
    </button>
  `
}

function rightPanel() {
  return `
    <aside class="right-panel">
      <div class="right-panel__tabs">
        ${panelTab("confirmations", `待确认${confirmations.length > 0 ? `<b>${confirmations.length}</b>` : ""}`)}
        ${panelTab("details", "详情")}
        ${panelTab("artifact", "产物预览")}
      </div>
      <div class="right-panel__body">${rightPanelBody()}</div>
    </aside>
  `
}

function panelTab(id: PanelTab, label: string) {
  return `<button class="${state.panelTab === id ? "right-panel__tab--active" : ""}" data-panel="${id}" type="button">${label}</button>`
}

function rightPanelBody() {
  const item = selectedItem()
  if (state.panelTab === "details") {
    return `
      <section class="detail-panel">
        <h2>${item.id}</h2>
        <p>${item.title}</p>
        ${detailRow("类型", item.type)}
        ${detailRow("优先级", item.priority)}
        ${detailRow("负责人", item.owner)}
        ${detailRow("当前节点", item.node)}
        ${detailRow("状态", statusText(item.status))}
        <button class="primary-action" type="button">进入会话</button>
      </section>
    `
  }
  if (state.panelTab === "artifact") {
    return `
      <section class="artifact-panel">
        <h2>产物预览</h2>
        <div class="artifact-preview">
          <strong>review-report.md</strong>
          <p>这里后续承载 OpenCode 文件预览 / diff / review 能力。</p>
          <pre>## 验证摘要\n- 类型检查通过\n- 待补充浏览器截图\n- 等待人工确认</pre>
        </div>
      </section>
    `
  }
  return `
    <section class="confirmation-panel">
      <h2>待确认</h2>
      ${confirmations
        .map(
          (item) => `
            <article class="confirmation-card">
              <strong>${item.title}</strong>
              <p>${item.source}</p>
              <div><button type="button">通过</button><button type="button">驳回</button></div>
            </article>
          `,
        )
        .join("")}
    </section>
  `
}

function detailRow(label: string, value: string) {
  return `<div class="detail-row"><span>${label}</span><strong>${value}</strong></div>`
}

function statusBar() {
  return `
    <footer class="status-bar">
      <span class="status-dot"></span>
      <span>系统正常</span>
      <span>工具连接 12</span>
      <span>Agents 在线 3</span>
      <span>展示数据 · OpenCode 能力待接入</span>
    </footer>
  `
}

function bindEvents(root: HTMLElement) {
  root.querySelectorAll<HTMLButtonElement>("[data-view]").forEach((button) => {
    button.addEventListener("click", () => {
      state.view = button.dataset.view as ViewId
      render()
    })
  })
  root.querySelectorAll<HTMLButtonElement>("[data-panel]").forEach((button) => {
    button.addEventListener("click", () => {
      state.panelTab = button.dataset.panel as PanelTab
      render()
    })
  })
  root.querySelectorAll<HTMLButtonElement>("[data-work-item]").forEach((button) => {
    button.addEventListener("click", () => {
      state.selectedId = button.dataset.workItem ?? state.selectedId
      state.panelTab = "details"
      render()
    })
  })
}

function iconSpark() {
  return `<svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M12 2l1.8 5.2L19 9l-5.2 1.8L12 16l-1.8-5.2L5 9l5.2-1.8L12 2z" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/><path d="M18.5 14l.9 2.6L22 17.5l-2.6.9-.9 2.6-.9-2.6-2.6-.9 2.6-.9.9-2.6z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>`
}

function iconSearch() {
  return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="2"/><path d="M20 20l-4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`
}

function iconPanel() {
  return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none"><rect x="4" y="5" width="16" height="14" rx="2.5" stroke="currentColor" stroke-width="1.8"/><path d="M9 5v14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`
}

function iconGrid() {
  return `<svg width="24" height="24" viewBox="0 0 24 24" fill="none"><rect x="3" y="3" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="2"/><rect x="14" y="3" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="2"/><rect x="3" y="14" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="2"/><rect x="14" y="14" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="2"/></svg>`
}

function iconColumns() {
  return `<svg width="24" height="24" viewBox="0 0 24 24" fill="none"><rect x="4" y="4" width="4" height="16" rx="1" stroke="currentColor" stroke-width="2"/><rect x="10" y="4" width="4" height="16" rx="1" stroke="currentColor" stroke-width="2"/><rect x="16" y="4" width="4" height="16" rx="1" stroke="currentColor" stroke-width="2"/></svg>`
}

function iconWorkflow() {
  return `<svg width="24" height="24" viewBox="0 0 24 24" fill="none"><circle cx="6" cy="6" r="2.5" stroke="currentColor" stroke-width="2"/><circle cx="18" cy="6" r="2.5" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="18" r="2.5" stroke="currentColor" stroke-width="2"/><path d="M8.4 7.6l2 2.6M15.6 7.6l-2 2.6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`
}

function iconSettings() {
  return `<svg width="22" height="22" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/><path d="M19 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 01-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3A1.7 1.7 0 0014 21h-4a1.7 1.7 0 00-.8-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 01-2.8-2.8l.1-.1A1.7 1.7 0 005 15a1.7 1.7 0 00-1.5-1H3a2 2 0 010-4h.5A1.7 1.7 0 005 9a1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 012.8-2.8l.1.1A1.7 1.7 0 009 5a1.7 1.7 0 001-1.5V3a2 2 0 014 0v.5A1.7 1.7 0 0015 5a1.7 1.7 0 001.8-.3l.1-.1a2 2 0 012.8 2.8l-.1.1A1.7 1.7 0 0019 9a1.7 1.7 0 001.5 1H21a2 2 0 010 4h-.5A1.7 1.7 0 0019 15z" stroke="currentColor" stroke-width="2"/></svg>`
}

render()
