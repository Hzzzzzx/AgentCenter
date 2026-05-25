import { ButtonV2 } from "@opencode-ai/ui/v2/components/button-v2.jsx"
import { Icon as IconV2 } from "@opencode-ai/ui/v2/components/icon.jsx"
import { For, Show, createMemo, createSignal } from "solid-js"
import { useNavigate } from "@solidjs/router"

type ResourceTab = "skills" | "mcp"
type Health = "normal" | "warning" | "failed" | "disabled"

type SkillResource = {
  id: string
  name: string
  version: string
  status: Health
  source: "project" | "builtin" | "upload"
  path: string
  workflows: number
  updated: string
  summary: string
}

type McpResource = {
  id: string
  name: string
  transport: "stdio" | "http" | "sse"
  status: Health
  desired: "running" | "stopped"
  tools: number
  scope: "project" | "workspace"
  updated: string
  summary: string
}

type ResourceRow = SkillResource | McpResource

const skillResources = [
  {
    id: "skill-prd",
    name: "prd-design",
    version: "1.4.2",
    status: "normal",
    source: "project",
    path: ".opencode/skills/prd-design",
    workflows: 4,
    updated: "今天 10:12",
    summary: "需求澄清、PRD 输出、验收标准收敛",
  },
  {
    id: "skill-review",
    name: "code-review",
    version: "0.9.8",
    status: "normal",
    source: "builtin",
    path: "builtin://code-review",
    workflows: 2,
    updated: "昨天 18:40",
    summary: "代码风险、测试缺口、行为回归检查",
  },
  {
    id: "skill-release",
    name: "release-risk-check",
    version: "0.3.1",
    status: "warning",
    source: "upload",
    path: ".opencode/skills/release-risk-check",
    workflows: 1,
    updated: "5 月 24 日",
    summary: "发布前风险巡检，等待补全验收模板",
  },
  {
    id: "skill-legacy",
    name: "legacy-upload-demo",
    version: "0.1.0",
    status: "failed",
    source: "upload",
    path: ".opencode/skills/legacy-upload-demo",
    workflows: 0,
    updated: "5 月 23 日",
    summary: "示例包缺少 SKILL.md，保持禁用",
  },
] satisfies SkillResource[]

const mcpResources = [
  {
    id: "mcp-gitlab",
    name: "gitlab-internal",
    transport: "stdio",
    status: "normal",
    desired: "running",
    tools: 12,
    scope: "project",
    updated: "今天 09:58",
    summary: "代码库、Merge Request、CI 状态读取",
  },
  {
    id: "mcp-browser",
    name: "playwright-browser",
    transport: "sse",
    status: "normal",
    desired: "running",
    tools: 7,
    scope: "workspace",
    updated: "今天 09:44",
    summary: "本地 Web 验证、截图和交互回放",
  },
  {
    id: "mcp-artifact",
    name: "artifact-store",
    transport: "http",
    status: "warning",
    desired: "running",
    tools: 5,
    scope: "project",
    updated: "昨天 20:11",
    summary: "产物索引、报告上传、预览元数据",
  },
  {
    id: "mcp-design-docs",
    name: "design-docs",
    transport: "http",
    status: "disabled",
    desired: "stopped",
    tools: 0,
    scope: "project",
    updated: "5 月 22 日",
    summary: "文档库连接，等待配置密钥引用",
  },
] satisfies McpResource[]

const tabLabel: Record<ResourceTab, string> = {
  skills: "Skill",
  mcp: "MCP",
}

const statusLabel: Record<Health, string> = {
  normal: "正常",
  warning: "待处理",
  failed: "异常",
  disabled: "停用",
}

const statusClass: Record<Health, string> = {
  normal: "bg-icon-success-base text-text-strong",
  warning: "bg-surface-warning-base text-text-warning-base",
  failed: "bg-surface-error-base text-text-danger-base",
  disabled: "bg-surface-base text-text-weak",
}

export default function AgentCenterResourceCenter() {
  const navigate = useNavigate()
  const [active, setActive] = createSignal<ResourceTab>("skills")
  const [selectedSkill, setSelectedSkill] = createSignal(skillResources[0].id)
  const [selectedMcp, setSelectedMcp] = createSignal(mcpResources[0].id)
  const rows = createMemo(() => (active() === "skills" ? skillResources : mcpResources))
  const selected = createMemo<ResourceRow>(() => {
    const id = active() === "skills" ? selectedSkill() : selectedMcp()
    return rows().find((item) => item.id === id) ?? rows()[0]
  })
  const metrics = createMemo(() => {
    const list = rows()
    return {
      total: list.length,
      normal: list.filter((item) => item.status === "normal").length,
      attention: list.filter((item) => item.status === "warning" || item.status === "failed").length,
    }
  })

  function select(row: ResourceRow) {
    if (active() === "skills") {
      setSelectedSkill(row.id)
      return
    }
    setSelectedMcp(row.id)
  }

  return (
    <main class="mx-auto flex h-full w-full max-w-[1120px] flex-col gap-5 overflow-auto px-6 py-10">
      <header class="flex min-w-0 flex-wrap items-center justify-between gap-4">
        <div class="flex min-w-0 items-center gap-3">
          <div class="flex size-9 shrink-0 items-center justify-center rounded-[8px] bg-v2-background-bg-deep text-v2-icon-icon-muted">
            <IconV2 name="grid-plus" size="normal" />
          </div>
          <div class="min-w-0">
            <h1 class="truncate text-[20px] font-[590] leading-7 text-v2-text-text-base">能力中心</h1>
            <p class="truncate text-[13px] font-[440] leading-5 text-v2-text-text-muted">
              项目级 Skill 与 MCP 运行资源
            </p>
          </div>
        </div>
        <ButtonV2 variant="ghost" size="normal" onClick={() => navigate("/")}>
          工作台
        </ButtonV2>
      </header>

      <section class="grid gap-3 md:grid-cols-3" aria-label="资源概览">
        <MetricTile label={`${tabLabel[active()]} 总数`} value={metrics().total} />
        <MetricTile label="可用" value={metrics().normal} />
        <MetricTile label="需处理" value={metrics().attention} />
      </section>

      <div class="grid min-h-0 gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <section class="min-w-0 rounded-[8px] border border-border-weak-base bg-background-base">
          <div class="flex min-w-0 flex-wrap items-center justify-between gap-3 border-b border-border-weaker-base px-4 py-3">
            <div class="flex h-8 rounded-[7px] bg-v2-background-bg-deep p-0.5">
              <For each={(["skills", "mcp"] as const)}>
                {(tab) => (
                  <button
                    type="button"
                    class="flex h-7 items-center gap-1.5 rounded-[6px] px-3 text-[13px] font-[530] text-v2-text-text-muted transition-colors hover:text-v2-text-text-base"
                    classList={{
                      "bg-v2-background-bg-base text-v2-text-text-base shadow-[var(--v2-elevation-raised)]":
                        active() === tab,
                    }}
                    onClick={() => setActive(tab)}
                  >
                    <IconV2 name={tab === "skills" ? "edit" : "settings-gear"} size="small" />
                    {tabLabel[tab]}
                  </button>
                )}
              </For>
            </div>
            <div class="flex items-center gap-2">
              <ButtonV2 variant="ghost" size="normal" icon="plus" disabled={true}>
                {active() === "skills" ? "上传 Skill" : "新增 MCP"}
              </ButtonV2>
              <ButtonV2 variant="ghost" size="normal" disabled={true}>
                刷新
              </ButtonV2>
            </div>
          </div>

          <div class="min-w-0 divide-y divide-border-weaker-base">
            <For each={rows()}>
              {(row) => (
                <button
                  type="button"
                  class="flex w-full min-w-0 items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-surface-base-hover"
                  classList={{ "bg-surface-base-hover": selected().id === row.id }}
                  onClick={() => select(row)}
                >
                  <div class="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-v2-background-bg-deep text-v2-icon-icon-muted">
                    <IconV2 name={active() === "skills" ? "edit" : "settings-gear"} size="small" />
                  </div>
                  <div class="min-w-0 flex-1">
                    <div class="flex min-w-0 items-center gap-2">
                      <span class="truncate text-[14px] font-[590] text-v2-text-text-base">{row.name}</span>
                      <StatusPill status={row.status} />
                    </div>
                    <p class="mt-0.5 truncate text-[12px] font-[440] text-v2-text-text-muted">{row.summary}</p>
                  </div>
                  <ResourceMeta row={row} />
                </button>
              )}
            </For>
          </div>
        </section>

        <aside class="min-w-0 rounded-[8px] border border-border-weak-base bg-background-base p-4">
          <div class="flex min-w-0 items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="truncate text-[15px] font-[590] text-v2-text-text-base">{selected().name}</div>
              <div class="mt-1 text-[12px] font-[440] text-v2-text-text-muted">
                {active() === "skills" ? "Skill 详情" : "MCP 详情"}
              </div>
            </div>
            <StatusPill status={selected().status} />
          </div>

          <dl class="mt-4 grid gap-3 text-[12px]">
            <DetailRow label="作用范围" value={active() === "skills" ? skillScope(selected()) : mcpScope(selected())} />
            <DetailRow label="安装/连接" value={active() === "skills" ? skillPath(selected()) : mcpTransport(selected())} />
            <DetailRow label={active() === "skills" ? "工作流引用" : "工具数量"} value={resourceCount(selected())} />
            <DetailRow label="最近更新" value={selected().updated} />
          </dl>

          <div class="mt-5 rounded-[8px] bg-v2-background-bg-deep p-3">
            <div class="text-[12px] font-[590] text-v2-text-text-base">后续接入点</div>
            <ul class="mt-2 space-y-1.5 text-[12px] leading-5 text-v2-text-text-muted">
              <li>项目级 registry 作为事实源</li>
              <li>会话读取 effective resources</li>
              <li>MCP 使用 desired state 与健康探测</li>
              <li>上传和刷新写入审计事件</li>
            </ul>
          </div>
        </aside>
      </div>
    </main>
  )
}

function MetricTile(props: { label: string; value: number }) {
  return (
    <div class="rounded-[8px] border border-border-weak-base bg-background-base px-4 py-3">
      <div class="text-[12px] font-[440] text-v2-text-text-muted">{props.label}</div>
      <div class="mt-1 text-[24px] font-[590] leading-7 text-v2-text-text-base">{props.value}</div>
    </div>
  )
}

function StatusPill(props: { status: Health }) {
  return (
    <span
      class={`inline-flex h-5 shrink-0 items-center rounded-[999px] px-2 text-[11px] font-[590] ${statusClass[props.status]}`}
    >
      {statusLabel[props.status]}
    </span>
  )
}

function ResourceMeta(props: { row: ResourceRow }) {
  return (
    <div class="hidden shrink-0 text-right text-[12px] font-[440] text-v2-text-text-muted sm:block">
      <div>{resourceCount(props.row)}</div>
      <div class="mt-0.5">{props.row.updated}</div>
    </div>
  )
}

function DetailRow(props: { label: string; value: string }) {
  return (
    <div class="flex min-w-0 items-center justify-between gap-3 border-b border-border-weaker-base pb-2 last:border-b-0 last:pb-0">
      <dt class="shrink-0 text-v2-text-text-muted">{props.label}</dt>
      <dd class="min-w-0 truncate text-right font-[530] text-v2-text-text-base">{props.value}</dd>
    </div>
  )
}

function isSkillResource(row: ResourceRow): row is SkillResource {
  return "version" in row
}

function skillScope(row: ResourceRow) {
  if (!isSkillResource(row)) return ""
  if (row.source === "builtin") return "内置"
  if (row.source === "project") return "项目"
  return "上传"
}

function skillPath(row: ResourceRow) {
  if (!isSkillResource(row)) return ""
  return `${row.name}@${row.version}`
}

function mcpScope(row: ResourceRow) {
  if (isSkillResource(row)) return ""
  return row.scope === "project" ? "项目" : "工作区"
}

function mcpTransport(row: ResourceRow) {
  if (isSkillResource(row)) return ""
  return `${row.transport} / ${row.desired === "running" ? "期望运行" : "期望停止"}`
}

function resourceCount(row: ResourceRow) {
  if (isSkillResource(row)) return `${row.workflows} 个工作流`
  return `${row.tools} 个工具`
}
