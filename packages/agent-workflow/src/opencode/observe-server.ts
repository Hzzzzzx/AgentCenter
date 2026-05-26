import { createServer, type Server, type ServerResponse } from "node:http"
import type { AddressInfo } from "node:net"
import { URL } from "node:url"
import { inspectWorkflowDefinition, inspectWorkflowRun } from "../core/inspect"
import { advanceRun } from "../core/scheduler"
import type { FsWorkflowStore } from "../store/fs-store"

type ObserveServer = {
  baseUrl: string
  server: Server
  store: FsWorkflowStore
}

const servers = new Map<string, ObserveServer>()

export async function ensureWorkflowObserveServer(store: FsWorkflowStore) {
  const existing = servers.get(store.directory())
  if (existing) return existing

  const server = createServer((request, response) => {
    handleRequest(store, request.url ?? "/", response).catch((error) => {
      sendJson(response, 500, { error: error instanceof Error ? error.message : String(error) })
    })
  })

  await new Promise<void>((resolve, reject) => {
    server.once("error", reject)
    server.listen(0, "127.0.0.1", () => {
      server.off("error", reject)
      resolve()
    })
  })
  server.unref?.()
  const address = server.address() as AddressInfo
  const started = { baseUrl: `http://127.0.0.1:${address.port}`, server, store }
  servers.set(store.directory(), started)
  return started
}

export async function closeWorkflowObserveServersForTests() {
  const closing = [...servers.values()].map(
    (item) =>
      new Promise<void>((resolve) => {
        item.server.close(() => resolve())
      }),
  )
  servers.clear()
  await Promise.all(closing)
}

async function handleRequest(store: FsWorkflowStore, requestUrl: string, response: ServerResponse) {
  const url = new URL(requestUrl, "http://127.0.0.1")
  if (url.pathname === "/health") {
    sendJson(response, 200, { ok: true })
    return
  }

  const runId = matchPath(url.pathname, "/runs/")
  if (runId) {
    sendHtml(response, observePage({ title: `Workflow Run ${runId}`, apiPath: `/api/runs/${runId}`, kind: "run" }))
    return
  }

  const definitionId = matchPath(url.pathname, "/definitions/")
  if (definitionId) {
    sendHtml(
      response,
      observePage({ title: `Workflow Definition ${definitionId}`, apiPath: `/api/definitions/${definitionId}`, kind: "definition" }),
    )
    return
  }

  const apiRunId = matchPath(url.pathname, "/api/runs/")
  if (apiRunId) {
    const run = await store.updateRun(apiRunId, (current) => {
      advanceRun(current)
    })
    if (!run) {
      sendJson(response, 404, { error: `Workflow run not found: ${apiRunId}` })
      return
    }
    sendJson(response, 200, { run, inspection: inspectWorkflowRun(run), refreshedAt: Date.now() })
    return
  }

  const apiDefinitionId = matchPath(url.pathname, "/api/definitions/")
  if (apiDefinitionId) {
    const definition = await store.getWorkflowDefinition(apiDefinitionId)
    if (!definition) {
      sendJson(response, 404, { error: `Workflow definition not found: ${apiDefinitionId}` })
      return
    }
    sendJson(response, 200, { definition, inspection: inspectWorkflowDefinition(definition), refreshedAt: Date.now() })
    return
  }

  sendHtml(response, observeIndex())
}

function matchPath(pathname: string, prefix: string) {
  if (!pathname.startsWith(prefix)) return
  const id = decodeURIComponent(pathname.slice(prefix.length).split("/")[0] ?? "")
  return /^[A-Za-z0-9_-]+$/.test(id) ? id : undefined
}

function sendJson(response: ServerResponse, status: number, value: unknown) {
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "access-control-allow-origin": "http://127.0.0.1",
  })
  response.end(JSON.stringify(value, null, 2))
}

function sendHtml(response: ServerResponse, html: string) {
  response.writeHead(200, {
    "content-type": "text/html; charset=utf-8",
    "cache-control": "no-store",
  })
  response.end(html)
}

function observeIndex() {
  return observeShell({
    title: "Agent Workflow Observe",
    apiPath: "",
    body: `<section class="empty"><p class="eyebrow">Agent Workflow Observe</p><h1>没有选中的工作流</h1><p>在 OpenCode 里调用 <code>workflow_observe run_id=&lt;runId&gt;</code> 后，这里会显示实时运行面板。</p></section>`,
  })
}

function observePage(input: { title: string; apiPath: string; kind: "run" | "definition" }) {
  return observeShell({
    title: input.title,
    apiPath: input.apiPath,
    body: `<header class="hero">
  <div>
    <p class="eyebrow">Agent Workflow Observe</p>
    <h1 id="title">${escapeHtml(input.title)}</h1>
    <p id="subtitle">正在读取 ${input.kind === "run" ? "运行状态" : "工作流定义"}...</p>
  </div>
  <div class="live-box">
    <span class="live-dot"></span>
    <strong>实时观察</strong>
    <span id="last-refresh">等待刷新</span>
  </div>
</header>
<section class="focus" id="focus" aria-live="polite"></section>
<section class="summary" id="summary"></section>
<section class="workspace">
  <section class="panel flow-panel">
    <div class="panel-head">
      <h2>节点流程</h2>
      <div class="legend">
        <span><i class="legend-ready"></i>可执行</span>
        <span><i class="legend-running"></i>执行中</span>
        <span><i class="legend-waiting"></i>等你确认</span>
        <span><i class="legend-done"></i>已完成</span>
      </div>
    </div>
    <div class="flow" id="dag"></div>
  </section>
  <aside class="panel next-panel">
    <h2>下一步</h2>
    <div class="next-steps" id="next-steps"></div>
  </aside>
</section>
<section class="panel">
  <h2>诊断说明</h2>
  <div class="diagnostics" id="diagnostics"></div>
</section>
<section class="panel">
  <h2>最近事件</h2>
  <ol class="events" id="events"></ol>
</section>
<details class="panel raw-panel">
  <summary>开发调试 JSON</summary>
  <pre id="raw"></pre>
</details>`,
  })
}

function observeShell(input: { title: string; apiPath: string; body: string }) {
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(input.title)}</title>
<style>
:root {
  --bg: #f4f6f8;
  --surface: #ffffff;
  --surface-soft: #f8fafc;
  --text: #17202a;
  --muted: #627084;
  --subtle: #8a96a8;
  --line: #dce3ec;
  --line-strong: #c7d1dd;
  --accent: #0f766e;
  --accent-soft: #d9f2ed;
  --blue: #2563eb;
  --blue-soft: #dbeafe;
  --warn: #a16207;
  --warn-soft: #fef3c7;
  --danger: #b42318;
  --danger-soft: #fee4e2;
  --done: #287947;
  --done-soft: #dcfce7;
  --shadow: 0 8px 22px rgba(31, 41, 55, 0.08);
  --radius: 8px;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  background:
    linear-gradient(180deg, #eef4f8 0, #f4f6f8 260px),
    var(--bg);
  color: var(--text);
  font: 14px/1.55 ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}
main {
  width: min(1280px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 44px;
}
.hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}
.eyebrow {
  margin: 0 0 8px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}
h1 {
  margin: 0;
  max-width: 900px;
  font-size: 30px;
  line-height: 1.18;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}
h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.25;
}
#subtitle {
  max-width: 900px;
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 15px;
  overflow-wrap: anywhere;
}
.live-box {
  flex: 0 0 auto;
  display: grid;
  gap: 2px;
  min-width: 150px;
  padding: 12px 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.live-box strong {
  display: flex;
  align-items: center;
  gap: 8px;
}
.live-box span:last-child {
  color: var(--muted);
  font-size: 12px;
}
.live-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--accent);
}
.focus {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  margin: 18px 0;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-left: 4px solid var(--accent);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.focus h2 {
  margin-bottom: 6px;
  font-size: 21px;
}
.focus p {
  margin: 0;
  color: var(--muted);
}
.focus strong {
  color: var(--text);
}
.focus .focus-meta {
  min-width: 150px;
  text-align: right;
  color: var(--muted);
  font-size: 12px;
}
.summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}
.summary article,
.panel,
.node-card,
.diagnostic-card,
.step-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.summary article {
  min-height: 80px;
  padding: 14px;
}
.summary span,
small {
  color: var(--muted);
}
.summary strong {
  display: block;
  margin-top: 4px;
  font-size: 18px;
  overflow-wrap: anywhere;
}
.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 340px);
  gap: 16px;
}
.panel {
  margin: 16px 0;
  padding: 18px;
  box-shadow: 0 1px 0 rgba(31, 41, 55, 0.03);
}
.workspace .panel {
  margin: 0;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--muted);
  font-size: 12px;
}
.legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.legend i {
  width: 9px;
  height: 9px;
  border-radius: 999px;
}
.legend-ready { background: var(--blue); }
.legend-running { background: var(--warn); }
.legend-waiting { background: var(--danger); }
.legend-done { background: var(--done); }
.flow {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}
.node-card {
  position: relative;
  min-height: 158px;
  padding: 15px;
  background: var(--surface-soft);
}
.node-card.ready {
  border-color: #8bb8ff;
  background: #f4f8ff;
}
.node-card.running {
  border-color: #e7bf63;
  background: #fffaf0;
}
.node-card.waiting,
.node-card.failed {
  border-color: #f2a19a;
  background: #fff7f6;
}
.node-card.succeeded,
.node-card.skipped {
  border-color: #9bd4ad;
  background: #f3fbf5;
}
.node-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.node-title {
  min-width: 0;
}
.node-title strong {
  display: block;
  font-size: 16px;
  overflow-wrap: anywhere;
}
.node-title small {
  display: block;
  margin-top: 2px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.node-card p {
  margin: 12px 0 0;
}
.node-task {
  color: var(--muted);
}
.node-reason {
  color: var(--text);
}
.node-meta {
  display: grid;
  gap: 4px;
  margin-top: 12px;
  color: var(--muted);
  font-size: 12px;
}
.node-meta code,
code {
  background: #edf1f6;
  border-radius: 5px;
  padding: 2px 5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.pill {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border-radius: 999px;
  padding: 2px 9px;
  background: #edf1f6;
  color: var(--text);
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}
.pill-ready { background: var(--blue-soft); color: var(--blue); }
.pill-running { background: var(--warn-soft); color: var(--warn); }
.pill-waiting,
.pill-failed,
.pill-cancelled { background: var(--danger-soft); color: var(--danger); }
.pill-succeeded,
.pill-skipped { background: var(--done-soft); color: var(--done); }
.pill-pending { background: #ebeef3; color: #596579; }
.next-steps {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}
.step-card {
  padding: 12px;
  background: var(--surface-soft);
}
.step-card strong {
  display: block;
  margin-bottom: 4px;
}
.step-card p {
  margin: 0;
  color: var(--muted);
}
.diagnostics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
  margin-top: 16px;
}
.diagnostic-card {
  padding: 14px;
  background: var(--surface-soft);
}
.diagnostic-card strong {
  display: block;
  margin-bottom: 6px;
}
.diagnostic-card p {
  margin: 0;
  color: var(--muted);
}
.events {
  margin: 16px 0 0;
  padding-left: 20px;
}
.events li {
  margin: 10px 0;
}
.events strong {
  display: block;
}
.events span {
  display: block;
  color: var(--muted);
  font-size: 12px;
}
.raw-panel summary {
  cursor: pointer;
  font-weight: 800;
}
pre {
  overflow-x: auto;
  max-height: 520px;
  margin: 14px 0 0;
  padding: 16px;
  background: #111827;
  color: #f9fafb;
  border-radius: var(--radius);
}
.empty {
  max-width: 680px;
  margin: 80px auto;
  padding: 28px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
@media (max-width: 880px) {
  main { width: min(100% - 24px, 1280px); padding-top: 20px; }
  .hero,
  .focus,
  .workspace {
    grid-template-columns: 1fr;
    display: grid;
  }
  .live-box,
  .focus .focus-meta {
    text-align: left;
  }
  .panel-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
</head>
<body><main>${input.body}</main></body>
<script>
const apiPath = ${JSON.stringify(input.apiPath)};
const statusText = {
  pending: "未开始",
  ready: "可执行",
  running: "执行中",
  waiting: "等待你确认",
  succeeded: "已完成",
  skipped: "已跳过",
  failed: "失败",
  cancelled: "已取消",
  agent: "Agent 节点",
  gate: "关卡节点",
  join: "汇聚节点",
  human: "人工节点"
};
const eventText = {
  "run.created": "流程已创建",
  "node.started": "节点开始执行",
  "node.succeeded": "节点完成",
  "node.skipped": "节点跳过",
  "node.failed": "节点失败",
  "node.waiting": "节点等待确认",
  "node.answer": "收到用户回答",
  "node.retry": "节点准备重试",
  "node.result_ignored": "忽略过期结果",
  "deviation.waiting_approval": "流程变更等待批准",
  "deviation.recorded": "流程变更已记录",
  "run.cancelled": "流程已取消"
};
const state = { timer: undefined };

function text(value) {
  return value == null || value === "" ? "无" : String(value);
}

function clear(element) {
  while (element.firstChild) element.removeChild(element.firstChild);
}

function el(tag, className, value) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (value != null) node.textContent = value;
  return node;
}

function pill(status) {
  return el("span", "pill pill-" + cssStatus(status), statusText[status] || status);
}

function cssStatus(status) {
  return String(status).toLowerCase().replace(/[^a-z0-9_-]+/g, "-");
}

function summaryItem(label, value) {
  const item = el("article");
  item.append(el("span", "", label), el("strong", "", text(value)));
  return item;
}

function nodeIndex(nodes) {
  return Object.fromEntries((nodes || []).map((node) => [node.id || node.nodeId, node]));
}

function labelFor(nodeId, index) {
  return index[nodeId]?.label || nodeId;
}

function promptLine(node) {
  return String(node?.prompt || "").split("\\n").find((line) => line.trim()) || "这个节点没有记录任务说明。";
}

function displayStatus(node) {
  if (node.ready) return "ready";
  return node.status || node.type || "pending";
}

function dependencyLine(dependencies, index) {
  if (!dependencies || !dependencies.length) return "无前置依赖";
  return "依赖：" + dependencies.map((dependency) => {
    const value = typeof dependency === "string" ? { nodeId: dependency, status: "defined" } : dependency;
    return labelFor(value.nodeId, index) + " " + (statusText[value.status] || value.status);
  }).join("、");
}

function render(data) {
  document.getElementById("raw").textContent = JSON.stringify(data, null, 2);
  document.getElementById("last-refresh").textContent = data.refreshedAt
    ? new Date(data.refreshedAt).toLocaleTimeString()
    : "刚刚";
  if (data.run) renderRun(data);
  if (data.definition) renderDefinition(data);
}

function renderRun(data) {
  document.getElementById("title").textContent = data.run.title || data.run.id;
  document.getElementById("subtitle").textContent = data.run.task;
  const nodes = data.inspection.nodes;
  const definitions = nodeIndex(data.run.definition.nodes);
  renderRunFocus(data, nodes);
  renderRunSummary(data, nodes);
  renderRunNodes(data.run.definition.nodes, nodes, definitions);
  renderNextSteps(data, nodes);
  renderDiagnostics(nodes, definitions);
  renderEvents(data.run.events || []);
}

function renderDefinition(data) {
  document.getElementById("title").textContent = data.definition.name || data.definition.id;
  document.getElementById("subtitle").textContent = data.definition.description;
  renderDefinitionFocus(data);
  renderDefinitionSummary(data);
  renderDefinitionNodes(data.definition.nodes);
  renderDefinitionNextSteps(data);
  renderDefinitionDiagnostics(data.inspection);
  renderEvents([]);
}

function renderRunFocus(data, nodes) {
  const failed = nodes.filter((node) => node.status === "failed");
  const waiting = nodes.filter((node) => node.status === "waiting");
  const running = nodes.filter((node) => node.status === "running");
  const ready = nodes.filter((node) => node.ready);
  const completed = nodes.filter((node) => node.status === "succeeded" || node.status === "skipped").length;
  const focus = document.getElementById("focus");
  clear(focus);

  const message = failed.length
    ? {
        title: "先看失败节点",
        body: failed.map((node) => node.label).join("、") + " 失败了，需要查看节点摘要或错误。",
        status: "failed"
      }
    : waiting.length
      ? {
          title: "现在卡在用户确认",
          body: waiting.map((node) => node.label + "：" + (node.waitQuestion || "等待回答")).join("；"),
          status: "waiting"
        }
      : ready.length
        ? {
            title: "现在可以执行下一批节点",
            body: ready.map((node) => node.label).join("、") + " 已经满足依赖，可以让 Agent 开始执行。",
            status: "ready"
          }
        : running.length
          ? {
              title: "正在等待子 Agent 返回",
              body: running.map((node) => node.label).join("、") + " 正在执行，完成后下游会自动解锁。",
              status: "running"
            }
          : data.run.status === "succeeded"
            ? { title: "流程已经完成", body: "所有必要节点都已完成或跳过。", status: "succeeded" }
            : { title: "还在等待上游节点", body: "当前没有可执行节点，先看下面每个节点的等待原因。", status: "pending" };

  const copy = el("div");
  copy.append(el("h2", "", message.title), el("p", "", message.body));
  const meta = el("div", "focus-meta");
  meta.append(pill(message.status), el("div", "", completed + " / " + nodes.length + " 个节点已处理"));
  focus.append(copy, meta);
}

function renderDefinitionFocus(data) {
  const focus = document.getElementById("focus");
  clear(focus);
  const copy = el("div");
  copy.append(el("h2", "", "这是一个可复用工作流定义"), el("p", "", "这里展示的是节点结构和依赖关系，不是某次执行记录。"));
  const meta = el("div", "focus-meta");
  meta.append(pill("agent"), el("div", "", data.definition.nodes.length + " 个节点"));
  focus.append(copy, meta);
}

function renderRunSummary(data, nodes) {
  const summary = document.getElementById("summary");
  const completed = nodes.filter((node) => node.status === "succeeded" || node.status === "skipped").length;
  clear(summary);
  summary.append(
    summaryItem("运行状态", statusText[data.run.status] || data.run.status),
    summaryItem("进度", completed + " / " + nodes.length),
    summaryItem("可执行节点", data.inspection.readyNodeIds.join("、") || "无"),
    summaryItem("等待确认", data.inspection.waitingNodeIds.join("、") || "无"),
    summaryItem("失败节点", data.inspection.failedNodeIds.join("、") || "无"),
    summaryItem("工作流", data.run.workflowId)
  );
}

function renderDefinitionSummary(data) {
  const summary = document.getElementById("summary");
  clear(summary);
  summary.append(
    summaryItem("定义 ID", data.definition.id),
    summaryItem("版本", data.definition.version),
    summaryItem("节点数", data.definition.nodes.length),
    summaryItem("触发策略", data.definition.activation?.mode || "显式确认")
  );
}

function renderRunNodes(definitionNodes, inspectionNodes, definitions) {
  const byId = Object.fromEntries(inspectionNodes.map((node) => [node.nodeId, node]));
  const flow = document.getElementById("dag");
  clear(flow);
  definitionNodes.forEach((definition) => {
    const node = byId[definition.id] || { nodeId: definition.id, label: definition.label, status: "pending", dependencies: [] };
    flow.append(nodeCard({
      id: node.nodeId,
      label: node.label,
      status: displayStatus(node),
      task: promptLine(definition),
      reason: primaryReason(node, definitions),
      dependencies: dependencyLine(node.dependencies, definitions),
      runner: node.runner,
      taskId: node.taskId,
      waitQuestion: node.waitQuestion,
      summary: node.summary
    }));
  });
}

function renderDefinitionNodes(nodes) {
  const definitions = nodeIndex(nodes);
  const flow = document.getElementById("dag");
  clear(flow);
  nodes.forEach((node) => {
    flow.append(nodeCard({
      id: node.id,
      label: node.label,
      status: node.type,
      task: promptLine(node),
      reason: node.agent ? "由 " + node.agent + " 执行。" : "由调度器自动处理。",
      dependencies: dependencyLine(node.dependsOn || [], definitions)
    }));
  });
}

function nodeCard(input) {
  const article = el("article", "node-card " + cssStatus(input.status));
  const top = el("div", "node-top");
  const title = el("div", "node-title");
  title.append(el("strong", "", input.label), el("small", "", input.id));
  top.append(title, pill(input.status));
  article.append(top, el("p", "node-task", input.task), el("p", "node-reason", input.reason));
  const meta = el("div", "node-meta");
  meta.append(el("span", "", input.dependencies));
  if (input.runner || input.taskId) meta.append(el("span", "", "执行记录：" + [input.runner, input.taskId].filter(Boolean).join(" / ")));
  if (input.waitQuestion) meta.append(el("span", "", "等待问题：" + input.waitQuestion));
  if (input.summary) meta.append(el("span", "", "摘要：" + input.summary));
  article.append(meta);
  return article;
}

function renderNextSteps(data, nodes) {
  const target = document.getElementById("next-steps");
  clear(target);
  const failed = nodes.filter((node) => node.status === "failed");
  const waiting = nodes.filter((node) => node.status === "waiting");
  const ready = nodes.filter((node) => node.ready);
  const running = nodes.filter((node) => node.status === "running");
  const steps = failed.length
    ? failed.map((node) => ["处理失败节点", node.label + " 失败了，先查看错误摘要，再决定重试、跳过或调整流程。"])
    : waiting.length
      ? waiting.map((node) => ["回答确认问题", node.label + " 正在等你回答：" + (node.waitQuestion || "未记录问题")])
      : ready.length
        ? ready.map((node) => ["执行节点", node.label + " 已经 ready。多个 ready 节点可以并行交给子 Agent。"])
        : running.length
          ? running.map((node) => ["等待执行结果", node.label + " 正在执行，返回后页面会继续更新。"])
          : data.run.status === "succeeded"
            ? [["查看收尾结果", "流程已完成，可以回看节点摘要、事件和最终证据。"]]
            : [["检查依赖", "当前没有可执行节点，下面的诊断说明会告诉你每个节点在等谁。"]];
  steps.forEach((step) => target.append(stepCard(step[0], step[1])));
}

function renderDefinitionNextSteps(data) {
  const target = document.getElementById("next-steps");
  clear(target);
  target.append(
    stepCard("运行这个定义", "在 OpenCode 里用 workflow_run workflow_id=" + data.definition.id + " 创建一次 run。"),
    stepCard("检查依赖结构", "看左侧节点流程，确认每个节点依赖的是上游产物而不是并行兄弟节点。")
  );
}

function stepCard(title, body) {
  const card = el("article", "step-card");
  card.append(el("strong", "", title), el("p", "", body));
  return card;
}

function renderDiagnostics(nodes, definitions) {
  const target = document.getElementById("diagnostics");
  clear(target);
  nodes.forEach((node) => {
    target.append(diagnosticCard(node.label, primaryReason(node, definitions)));
  });
}

function renderDefinitionDiagnostics(nodes) {
  const target = document.getElementById("diagnostics");
  clear(target);
  nodes.forEach((node) => {
    target.append(diagnosticCard(
      node.label,
      node.missingDependencies.length
        ? "缺少依赖节点：" + node.missingDependencies.join("、")
        : node.dependencies.length
          ? "依赖 " + node.dependencies.join("、") + " 完成后才会进入这个节点。"
          : "这是入口节点。"
    ));
  });
}

function diagnosticCard(title, body) {
  const card = el("article", "diagnostic-card");
  card.append(el("strong", "", title), el("p", "", body));
  return card;
}

function primaryReason(node, definitions) {
  if (node.ready) return "前置依赖已满足，现在可以执行。";
  if (node.status === "waiting") return "正在等你回答：" + (node.waitQuestion || "未记录问题");
  if (node.status === "running") return "子 Agent 正在执行，等它返回结构化结果。";
  if (node.status === "succeeded") return "这个节点已经完成，不会重复执行。";
  if (node.status === "skipped") return "这个节点被显式跳过，下游依赖仍可继续。";
  if (node.status === "failed") return "节点失败：" + (node.summary || node.blockedReasons?.join("；") || "没有记录错误摘要");
  if (node.blockedReasons && node.blockedReasons.length) return node.blockedReasons.map((reason) => explainReason(reason, definitions)).join("；");
  return "还没轮到这个节点。";
}

function explainReason(reason, definitions) {
  if (reason === "node already succeeded") return "这个节点已经完成，不会重复执行。";
  if (reason === "node is already running") return "节点正在执行，等待子 Agent 返回。";
  if (reason === "run already succeeded") return "整个流程已经完成。";
  if (reason === "run is cancelled") return "整个流程已经取消。";
  if (reason === "run has failed") return "整个流程已失败，需先处理失败节点。";
  const waiting = /^waiting for (.+) \\((.+)\\)$/.exec(reason);
  if (waiting) return "还在等「" + labelFor(waiting[1], definitions) + "」变为完成状态，目前是" + (statusText[waiting[2]] || waiting[2]) + "。";
  const user = /^waiting for user: (.+)$/.exec(reason);
  if (user) return "正在等你回答：" + user[1];
  const failed = /^node failed: (.+)$/.exec(reason);
  if (failed) return "节点失败：" + failed[1];
  if (reason.includes("nodes are handled by the scheduler")) return "这是自动调度节点，会在依赖满足后自动处理。";
  return reason;
}

function renderEvents(events) {
  const list = document.getElementById("events");
  clear(list);
  if (!events.length) {
    list.append(el("li", "", "暂无事件。"));
    return;
  }
  events.slice(-12).reverse().forEach((event) => {
    const item = el("li");
    item.append(
      el("strong", "", eventText[event.type] || event.type),
      el("span", "", new Date(event.createdAt).toLocaleTimeString() + " · " + event.message)
    );
    list.append(item);
  });
}

async function refresh() {
  if (!apiPath) return;
  try {
    const response = await fetch(apiPath, { cache: "no-store" });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || response.statusText);
    render(data);
  } catch (error) {
    const focus = document.getElementById("focus");
    clear(focus);
    const copy = el("div");
    copy.append(el("h2", "", "观察页暂时读不到状态"), el("p", "", String(error)));
    focus.append(copy);
    document.getElementById("raw").textContent = String(error);
  }
}

refresh();
state.timer = setInterval(refresh, 1500);
</script>
</html>`
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;")
}
