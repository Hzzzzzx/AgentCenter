#!/usr/bin/env node
import http from 'node:http';
import net from 'node:net';
import path from 'node:path';
import { existsSync } from 'node:fs';
import { spawn, spawnSync } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';

const DIRECTORY_HEADER = 'x-opencode-directory';
const DEFAULT_PORT = 4789;
const DEFAULT_HOST = '127.0.0.1';
const READY_TIMEOUT_MS = 15_000;

const args = parseArgs(process.argv.slice(2));
const config = {
  host: args.host || process.env.AGENTCENTER_OPENCODE_BRIDGE_HOST || DEFAULT_HOST,
  port: Number(args.port || process.env.AGENTCENTER_OPENCODE_BRIDGE_PORT || DEFAULT_PORT),
  cwd: path.resolve(args.cwd || process.env.AGENTCENTER_OPENCODE_CWD || process.cwd()),
  mock: Boolean(args.mock || process.env.OPENCODE_BRIDGE_MOCK === '1'),
  opencodeCommand: args.opencode || process.env.OPENCODE_BIN || '',
};

const state = {
  sessions: new Map(),
  clients: new Map(),
  opencode: {
    baseUrl: '',
    child: null,
    cwd: '',
    command: '',
    eventStreams: new Map(),
  },
};

const server = http.createServer(async (req, res) => {
  setCorsHeaders(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = new URL(req.url || '/', `http://${req.headers.host || `${config.host}:${config.port}`}`);

  try {
    if (req.method === 'GET' && url.pathname === '/') {
      sendHtml(res, 200, renderHomePage());
      return;
    }

    if (req.method === 'GET' && url.pathname === '/health') {
      sendJson(res, 200, {
        ok: true,
        service: 'agentcenter-opencode-bridge',
        mock: config.mock,
        cwd: config.cwd,
        opencodeAvailable: config.mock ? false : Boolean(resolveOpenCodeCommand({ quiet: true })),
        sessions: state.sessions.size,
      });
      return;
    }

    if (req.method === 'GET' && url.pathname === '/api/agentcenter/events') {
      openEventsStream(req, res, url.searchParams.get('sessionId'));
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/opencode/session') {
      const body = await readJson(req);
      const session = await createBridgeSession(body);
      sendJson(res, 200, {
        ok: true,
        sessionId: session.id,
        opencodeSessionId: session.opencodeSessionId,
        mode: session.mode,
        eventsUrl: `/api/agentcenter/events?sessionId=${encodeURIComponent(session.id)}`,
      });
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/opencode/message') {
      const body = await readJson(req);
      const session = requireSession(body.sessionId);
      await sendMessage(session, String(body.message || ''));
      sendJson(res, 202, { ok: true, accepted: true, sessionId: session.id });
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/opencode/permission') {
      const body = await readJson(req);
      const session = requireSession(body.sessionId);
      await replyPermission(session, String(body.permissionId || ''), String(body.reply || 'once'));
      sendJson(res, 202, { ok: true, accepted: true, sessionId: session.id });
      return;
    }

    sendJson(res, 404, { ok: false, error: 'not_found' });
  } catch (error) {
    sendJson(res, 500, { ok: false, error: error.message || String(error) });
  }
});

server.listen(config.port, config.host, () => {
  console.log(`[agentcenter-opencode-bridge] listening on http://${config.host}:${config.port}`);
  console.log(`[agentcenter-opencode-bridge] cwd=${config.cwd}`);
  if (config.mock) {
    console.log('[agentcenter-opencode-bridge] mock mode enabled');
  }
});

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

function parseArgs(argv) {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--mock') {
      parsed.mock = true;
      continue;
    }
    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      parsed[key] = argv[index + 1];
      index += 1;
    }
  }
  return parsed;
}

function setCorsHeaders(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

function sendJson(res, status, payload) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}

function sendHtml(res, status, html) {
  res.writeHead(status, { 'content-type': 'text/html; charset=utf-8' });
  res.end(html);
}

function renderHomePage() {
  const homepagePath = path.join(config.cwd, 'docs/prototype/homepage.html');
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>AgentCenter OpenCode Bridge</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #f6f8fb; color: #0f172a; }
    main { max-width: 760px; margin: 56px auto; background: white; border: 1px solid #d9e2ec; border-radius: 12px; padding: 28px; }
    h1 { margin: 0 0 10px; font-size: 24px; }
    p { color: #475569; line-height: 1.7; }
    code, pre { background: #f1f5f9; border: 1px solid #d9e2ec; border-radius: 8px; }
    code { padding: 2px 6px; }
    pre { padding: 12px; overflow: auto; }
    a { color: #2563eb; font-weight: 650; }
    .ok { display: inline-flex; align-items: center; gap: 8px; color: #047857; font-weight: 700; }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; }
  </style>
</head>
<body>
  <main>
    <div class="ok"><span class="dot"></span>AgentCenter OpenCode Bridge is running</div>
    <h1>这是本地桥接服务，不是高保真页面</h1>
    <p>请打开静态高保真页面，然后在任务详情里点击“进入会话”，对话输入会通过这个 bridge 发送到本机 OpenCode。</p>
    <p>当前桥接模式：<code>${config.mock ? 'mock' : 'opencode'}</code></p>
    <p>健康检查：<a href="/health">/health</a></p>
    <p>高保真页面地址：</p>
    <pre>file://${homepagePath}</pre>
  </main>
</body>
</html>`;
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 1024 * 1024) throw new Error('request body too large');
    chunks.push(chunk);
  }
  const text = Buffer.concat(chunks).toString('utf8').trim();
  return text ? JSON.parse(text) : {};
}

function requireSession(sessionId) {
  const session = state.sessions.get(sessionId);
  if (!session) throw new Error(`unknown bridge session: ${sessionId || '(empty)'}`);
  return session;
}

async function createBridgeSession(input = {}) {
  const task = input.task || {};
  const id = `acs_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const cwd = path.resolve(input.cwd || config.cwd);
  const title = [task.id, task.title].filter(Boolean).join(' · ') || input.title || 'AgentCenter 对话';
  const session = {
    id,
    mode: config.mock ? 'mock' : 'opencode',
    cwd,
    task,
    model: input.model || '',
    title,
    opencodeSessionId: '',
    userMessageIds: new Set(),
    seenTextParts: new Set(),
    seenReasoningParts: new Set(),
    runningTools: new Set(),
    events: [],
  };
  state.sessions.set(id, session);

  if (config.mock) {
    session.opencodeSessionId = `mock_${id}`;
    emit(session, {
      type: 'status',
      status: 'waiting_user',
      label: 'Mock OpenCode 会话已就绪',
      taskId: task.id,
    });
    return session;
  }

  await ensureOpenCodeServer(cwd);
  const created = await createOpenCodeSession(cwd, title);
  session.opencodeSessionId = created.id;
  session.opencodeRaw = created.raw;
  startOpenCodeEventStream(session);
  emit(session, {
    type: 'status',
    status: 'waiting_user',
    label: 'OpenCode 会话已创建',
    taskId: task.id,
    opencodeSessionId: session.opencodeSessionId,
  });
  return session;
}

async function sendMessage(session, message) {
  if (!message.trim()) throw new Error('message is empty');
  if (session.mode === 'mock') {
    runMockTurn(session, message);
    return;
  }

  const promptText = buildPromptText(session, message);
  const body = {
    agent: 'build',
    parts: [{ type: 'text', text: promptText }],
  };
  const model = parseModelRef(session.model);
  if (model) body.model = model;

  const response = await fetch(`${state.opencode.baseUrl}/session/${session.opencodeSessionId}/prompt_async`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      [DIRECTORY_HEADER]: session.cwd,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const text = await response.text().catch(() => '');
    throw new Error(`OpenCode prompt_async returned ${response.status}: ${text}`);
  }

  emit(session, { type: 'status', status: 'running', label: '已发送到 OpenCode', taskId: session.task.id });
}

async function replyPermission(session, permissionId, reply) {
  if (!permissionId) throw new Error('permissionId is required');
  if (session.mode === 'mock') {
    emit(session, { type: 'status', status: 'running', label: '已确认 mock permission', taskId: session.task.id });
    return;
  }
  const response = await fetch(`${state.opencode.baseUrl}/permission/${permissionId}/reply`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      [DIRECTORY_HEADER]: session.cwd,
    },
    body: JSON.stringify({ reply }),
  });
  if (!response.ok) {
    const text = await response.text().catch(() => '');
    throw new Error(`OpenCode permission reply returned ${response.status}: ${text}`);
  }
}

function buildPromptText(session, message) {
  const task = session.task || {};
  const sections = [
    '你正在通过 AgentCenter 高保真原型与本机 OpenCode 会话协作。',
    '请把输出写成适合企业研发工作台展示的自然语言，并在需要用户确认、权限或遇到异常时明确说明阻塞点。',
  ];
  if (task.id) {
    sections.push(`当前事项上下文:\n- ID: ${task.id}\n- 类型: ${task.type || ''}\n- 标题: ${task.title || ''}\n- 状态: ${task.status || ''}\n- 工作流节点: ${task.stageName || ''}\n- 优先级: ${task.priority || ''}\n- 描述: ${task.desc || ''}`);
  }
  sections.push(`用户输入:\n${message}`);
  return sections.join('\n\n');
}

function parseModelRef(model) {
  const trimmed = String(model || '').trim();
  if (!trimmed) return null;
  const [providerID, modelID] = trimmed.includes('/') ? trimmed.split('/', 2) : ['openai', trimmed];
  if (!modelID) return null;
  return { providerID, modelID };
}

async function ensureOpenCodeServer(cwd) {
  if (state.opencode.child && state.opencode.baseUrl && state.opencode.cwd === cwd) return;

  const command = resolveOpenCodeCommand();
  const port = await findAvailablePort();
  const baseUrl = `http://${DEFAULT_HOST}:${port}`;
  const child = spawn(command, [
    'serve',
    '--hostname',
    DEFAULT_HOST,
    '--port',
    String(port),
    '--print-logs',
    '--log-level',
    'WARN',
  ], {
    cwd,
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  child.stdout.on('data', chunk => process.stdout.write(`[opencode] ${chunk}`));
  child.stderr.on('data', chunk => process.stderr.write(`[opencode] ${chunk}`));
  child.on('exit', code => {
    if (state.opencode.child === child) {
      state.opencode.child = null;
      for (const session of state.sessions.values()) {
        if (session.mode === 'opencode') {
          emit(session, { type: 'status', status: 'failed', label: `OpenCode serve exited (${code})`, taskId: session.task.id });
        }
      }
    }
  });

  state.opencode = {
    baseUrl,
    child,
    cwd,
    command,
    eventStreams: new Map(),
  };

  await waitForServerReady(baseUrl, cwd);
}

function resolveOpenCodeCommand({ quiet = false } = {}) {
  const candidates = [
    config.opencodeCommand,
    'opencode',
    'opencode-bin',
    '/opt/homebrew/bin/opencode',
    '/usr/local/bin/opencode',
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (candidate.includes('/')) {
      if (existsSync(candidate)) return candidate;
      continue;
    }
    const result = spawnSync(candidate, ['--version'], { stdio: 'ignore' });
    if (!result.error) return candidate;
  }

  if (quiet) return '';
  throw new Error('Cannot find opencode executable. Install opencode or pass --opencode /path/to/opencode.');
}

async function findAvailablePort() {
  return new Promise((resolve, reject) => {
    const probe = net.createServer();
    probe.once('error', reject);
    probe.listen(0, DEFAULT_HOST, () => {
      const address = probe.address();
      probe.close(() => resolve(address.port));
    });
  });
}

async function waitForServerReady(baseUrl, cwd) {
  const deadline = Date.now() + READY_TIMEOUT_MS;
  let lastError = '';
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${baseUrl}/path`, { headers: { [DIRECTORY_HEADER]: cwd } });
      if (response.ok) return;
      lastError = `HTTP ${response.status}`;
    } catch (error) {
      lastError = error.message || String(error);
    }
    await delay(250);
  }
  throw new Error(`OpenCode serve did not become ready: ${lastError}`);
}

async function createOpenCodeSession(cwd, title) {
  const response = await fetch(`${state.opencode.baseUrl}/session`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      [DIRECTORY_HEADER]: cwd,
    },
    body: JSON.stringify({
      title,
      permission: [{ permission: 'edit', pattern: '*', action: 'ask' }],
    }),
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`create OpenCode session returned ${response.status}: ${text}`);
  }
  const raw = JSON.parse(text);
  if (!raw.id) throw new Error(`OpenCode session response did not include id: ${text}`);
  return { id: raw.id, raw };
}

function startOpenCodeEventStream(session) {
  if (state.opencode.eventStreams.has(session.id)) return;
  const controller = new AbortController();
  state.opencode.eventStreams.set(session.id, controller);
  readOpenCodeEvents(session, controller.signal).catch(error => {
    if (!controller.signal.aborted) {
      emit(session, {
        type: 'status',
        status: 'failed',
        label: `OpenCode SSE 断开: ${error.message || error}`,
        taskId: session.task.id,
      });
    }
  });
}

async function readOpenCodeEvents(session, signal) {
  const response = await fetch(`${state.opencode.baseUrl}/event`, {
    headers: { [DIRECTORY_HEADER]: session.cwd },
    signal,
  });
  if (!response.ok) throw new Error(`OpenCode SSE returned ${response.status}`);
  if (!response.body) throw new Error('OpenCode SSE response has no body');

  const decoder = new TextDecoder();
  const reader = response.body.getReader();
  let buffer = '';
  while (!signal.aborted) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      handleSseBlock(session, block);
      boundary = buffer.indexOf('\n\n');
    }
  }
}

function handleSseBlock(session, block) {
  const data = block
    .split(/\r?\n/)
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trimStart())
    .join('\n');
  if (!data || data === '[DONE]') return;
  try {
    const raw = JSON.parse(data);
    for (const event of normalizeOpenCodeEvent(session, raw)) {
      emit(session, event);
    }
  } catch (error) {
    emit(session, {
      type: 'debug',
      label: `忽略非 JSON OpenCode 事件: ${String(data).slice(0, 120)}`,
    });
  }
}

function normalizeOpenCodeEvent(session, raw) {
  const eventType = raw.type;
  const properties = raw.properties || raw.data || {};
  if (!eventType || extractSessionId(properties) !== session.opencodeSessionId) return [];

  if (eventType === 'message.updated') {
    const info = properties.info || properties;
    if (info.role === 'user' && info.id) session.userMessageIds.add(info.id);
    return [];
  }

  if (eventType === 'message.part.updated' || eventType === 'message.part.delta') {
    return normalizeMessagePart(session, properties);
  }

  if (eventType === 'session.status') {
    const rawStatus = properties.status?.type || properties.status || properties.type || 'unknown';
    const status = rawStatus === 'busy' ? 'running' : rawStatus;
    return [{ type: 'status', status, label: `OpenCode ${status}`, taskId: session.task.id }];
  }

  if (eventType === 'session.idle') {
    return [{ type: 'status', status: 'waiting_user', label: 'OpenCode 等待用户输入', taskId: session.task.id }];
  }

  if (eventType === 'permission.asked' || eventType === 'permission.updated') {
    const permissionId = properties.id || '';
    const permission = properties.permission || properties.type || 'opencode_permission';
    const skillName = properties.tool?.tool || properties.tool?.name || permission;
    return [
      {
        type: 'permission_required',
        permissionId,
        skillName,
        title: properties.title || `OpenCode permission: ${permission}`,
        taskId: session.task.id,
      },
      {
        type: 'alert',
        level: 'warning',
        title: `${session.task.id || 'OpenCode'} · ${skillName} 等待确认`,
        meta: 'opencode skill 执行过程中需要用户确认权限或下一步。',
        taskId: session.task.id,
      },
    ];
  }

  if (eventType === 'session.error') {
    const reason = properties.error?.data?.message || properties.error?.name || properties.message || 'unknown OpenCode session error';
    return [
      { type: 'status', status: 'failed', label: reason, taskId: session.task.id },
      {
        type: 'alert',
        level: 'critical',
        title: `${session.task.id || 'OpenCode'} · 会话异常`,
        meta: reason,
        taskId: session.task.id,
      },
    ];
  }

  return [];
}

function normalizeMessagePart(session, properties) {
  const part = properties.part || properties;
  if (part.messageID && session.userMessageIds.has(part.messageID)) return [];
  if (part.message_id && session.userMessageIds.has(part.message_id)) return [];

  const delta = typeof properties.delta === 'string' ? properties.delta : '';
  if (part.type === 'text') {
    const text = delta || onceByPartId(session.seenTextParts, part, part.text);
    return text ? [{ type: 'assistant_delta', text, taskId: session.task.id }] : [];
  }
  if (part.type === 'reasoning') {
    const text = delta || onceByPartId(session.seenReasoningParts, part, part.text);
    return text ? [{ type: 'reasoning_delta', text, taskId: session.task.id }] : [];
  }
  if (part.type === 'tool') {
    return normalizeToolPart(session, part);
  }
  return [];
}

function onceByPartId(seen, part, value) {
  if (!value) return '';
  if (!part.id) return String(value);
  if (seen.has(part.id)) return '';
  seen.add(part.id);
  return String(value);
}

function normalizeToolPart(session, part) {
  const callId = part.callID || part.call_id || part.id || `tool_${Date.now()}`;
  const skillName = part.tool || part.name || 'unknown';
  const state = part.state || {};
  const status = state.status || 'running';
  const events = [];
  if ((status === 'running' || status === 'completed' || status === 'error') && !session.runningTools.has(callId)) {
    session.runningTools.add(callId);
    events.push({ type: 'skill_started', skillName, toolCallId: callId, taskId: session.task.id });
  }
  if (status === 'completed' || status === 'error') {
    const output = status === 'error'
      ? stringifyValue(state.error || part.error || '')
      : stringifyValue(state.output || state.result || part.output || '');
    events.push({
      type: 'skill_completed',
      skillName,
      toolCallId: callId,
      isError: status === 'error',
      output,
      taskId: session.task.id,
    });
    if (status === 'error') {
      events.push({
        type: 'alert',
        level: 'critical',
        title: `${session.task.id || 'OpenCode'} · ${skillName} 执行失败`,
        meta: output || 'opencode skill 执行失败，需要人工处理。',
        taskId: session.task.id,
      });
    }
    session.runningTools.delete(callId);
  }
  return events;
}

function stringifyValue(value) {
  if (value == null) return '';
  return typeof value === 'string' ? value : JSON.stringify(value);
}

function extractSessionId(value) {
  return value.sessionID
    || value.session_id
    || value.info?.sessionID
    || value.info?.session_id
    || value.info?.id
    || value.part?.sessionID
    || value.part?.session_id
    || '';
}

function openEventsStream(req, res, sessionId) {
  if (!sessionId) {
    sendJson(res, 400, { ok: false, error: 'sessionId is required' });
    return;
  }

  res.writeHead(200, {
    'content-type': 'text/event-stream; charset=utf-8',
    'cache-control': 'no-cache, no-transform',
    connection: 'keep-alive',
    'x-accel-buffering': 'no',
  });
  res.write('\n');

  const clients = state.clients.get(sessionId) || new Set();
  clients.add(res);
  state.clients.set(sessionId, clients);

  const session = state.sessions.get(sessionId);
  if (session) {
    for (const event of session.events.slice(-50)) {
      writeSse(res, event);
    }
  }

  const heartbeat = setInterval(() => res.write(': keepalive\n\n'), 15_000);
  req.on('close', () => {
    clearInterval(heartbeat);
    clients.delete(res);
  });
}

function emit(session, event) {
  const normalized = {
    id: `evt_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    createdAt: new Date().toISOString(),
    ...event,
  };
  session.events.push(normalized);
  const clients = state.clients.get(session.id);
  if (clients) {
    for (const client of clients) writeSse(client, normalized);
  }
}

function writeSse(res, event) {
  res.write(`event: ${event.type || 'message'}\n`);
  res.write(`data: ${JSON.stringify(event)}\n\n`);
}

function runMockTurn(session, message) {
  const task = session.task || {};
  const taskId = task.id || 'GENERAL';
  const chunks = [
    `已接入本机 OpenCode mock 会话，当前事项 ${taskId}。`,
    '我会按当前任务上下文读取目标、状态和工作流节点，随后给出可执行建议。',
    `\n\n针对“${message.slice(0, 80)}”，建议先确认影响范围，再推进到下一节点。`,
  ];
  const steps = [
    [100, { type: 'status', status: 'running', label: 'OpenCode mock 正在运行', taskId }],
    [250, { type: 'skill_started', skillName: 'build', taskId, toolCallId: `mock-build-${Date.now()}` }],
    [600, { type: 'assistant_delta', text: chunks[0], taskId }],
    [900, { type: 'assistant_delta', text: chunks[1], taskId }],
    [1200, { type: 'skill_completed', skillName: 'build', taskId, toolCallId: `mock-build-${Date.now()}`, output: '已生成事项处理建议', isError: false }],
    [1450, { type: 'assistant_delta', text: chunks[2], taskId }],
  ];
  if (/阻塞|确认|权限|失败|异常|处理/.test(message)) {
    steps.push([1700, {
      type: 'alert',
      level: 'warning',
      title: `${taskId} · build skill 等待用户确认`,
      meta: 'mock skill 执行到高风险节点，需要用户确认后继续。',
      taskId,
    }]);
    steps.push([1800, {
      type: 'permission_required',
      permissionId: `mock-perm-${Date.now()}`,
      skillName: 'build',
      title: '需要确认是否继续推进该节点',
      taskId,
    }]);
  } else {
    steps.push([1700, { type: 'status', status: 'waiting_user', label: 'OpenCode mock 等待继续输入', taskId }]);
  }
  for (const [timeout, event] of steps) {
    setTimeout(() => emit(session, event), timeout);
  }
}

function shutdown() {
  for (const controller of state.opencode.eventStreams.values()) {
    controller.abort();
  }
  if (state.opencode.child) {
    state.opencode.child.kill();
  }
  server.close(() => process.exit(0));
}
