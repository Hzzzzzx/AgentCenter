const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const path = require('path');

const { parseIntent } = require('./intent-parser');
const {
  getStats, getBubbleStats, getAllServices, getDeployments, getServiceByName,
  getRequirements, getSprint, getCodeReviews, getBuilds, getTests, getAlerts,
  createRequirement, createTask,
} = require('./db');
const { runDeployment, runHealthCheck, runRollback, runCreateRequirement, runBuild, runTests } = require('./mock-agents');
const { getInteractionResponse, getStorySequence } = require('./mock-events');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*', methods: ['GET', 'POST'] } });

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '../client')));

const executions = new Map();

// Demo state for reset functionality
let activeRole = 'management';
let messages = [];

function resetDemoState() {
  activeRole = 'management';
  messages = [];
  if (typeof resetStoryState === 'function') {
    resetStoryState();
  }
}

io.on('connection', (socket) => {
  console.log('客户端连接:', socket.id);

  socket.on('user_message', async (data) => {
    const { message } = data;
    const executionId = 'exec_' + Date.now();
    const intent = parseIntent(message);

    switch (intent.intent) {
      case 'create_requirement': return handleCreateRequirement(socket, executionId, intent.entities);
      case 'query_requirements': return handleQueryRequirements(socket);
      case 'query_sprint': return handleQuerySprint(socket);
      case 'query_reviews': return handleQueryReviews(socket);
      case 'create_task': return handleCreateTask(socket, intent.entities);
      case 'build': return handleBuild(socket, executionId, intent.entities);
      case 'query_builds': return handleQueryBuilds(socket);
      case 'run_tests': return handleRunTests(socket, executionId, intent.entities);
      case 'query_tests': return handleQueryTests(socket);
      case 'deploy': return handleDeploy(socket, executionId, intent.entities);
      case 'query_services': return handleQueryServices(socket);
      case 'query_deployments': return handleQueryDeployments(socket);
      case 'health_check': return handleHealthCheck(socket, executionId, intent.entities);
      case 'rollback': return handleRollback(socket, executionId, intent.entities);
      case 'query_alerts': return handleQueryAlerts(socket);
      case 'help': return handleHelp(socket);
      case 'overview': return handleOverview(socket);
      default:
        socket.emit('bot_response', {
          message: '抱歉，我没有理解你的意思。\n\n试试说：\n- "创建需求：用户登录支持二维码"\n- "Sprint 进度"\n- "代码审查"\n- "构建 identity-domain"\n- "跑一下回归测试"\n- "部署 identity-domain v2.4.0 到 test"\n- "总览"',
          type: 'text'
        });
    }
  });

  socket.on('confirm_deploy', async (data) => {
    const { service, version, environment } = data;
    const executionId = 'exec_' + Date.now();
    socket.emit('bot_response', { message: `开始部署 ${service}:${version} 到 ${environment}...`, type: 'execution_start' });
    await runDeployment(io, executionId, service, version, environment);
  });

  socket.on('get_stats', () => { socket.emit('stats_update', getBubbleStats()); });

  socket.on('demo_interaction', (data) => {
    const { interactionId } = data;
    const response = getInteractionResponse(interactionId);

    if (interactionId && interactionId.startsWith('story:start:')) {
      const storyId = interactionId.replace('story:start:', '');
      const events = getStorySequence(storyId);
      socket.emit('demo_interaction_response', { interactionId, response, events });
    } else {
      socket.emit('demo_interaction_response', { interactionId, response });
    }
  });

  socket.on('story_advance', (data) => {
    const { storyId, step, role } = data;
    const events = getStorySequence(storyId);
    if (events && events.length > 0) {
      socket.emit('story_event', events[0]);
    }
  });

  socket.on('reset', () => {
    resetDemoState();
    socket.emit('demo_reset', { status: 'ok', activeRole });
  });
});

function handleCreateRequirement(socket, executionId, entities) {
  const title = entities.title || '未命名需求';
  socket.emit('bot_response', { message: `正在创建需求: ${title}...`, type: 'execution_start' });
  runCreateRequirement(io, executionId, title).then(() => {
    const id = createRequirement(title, 'P2', 'system');
    socket.emit('bot_response', { message: `需求已创建成功！\n\n📋 ID: ${id}\n📌 标题: ${title}\n⚡ 优先级: P2\n📊 状态: 草稿`, type: 'text' });
    socket.emit('stats_update', getBubbleStats());
  });
}

function handleQueryRequirements(socket) {
  const reqs = getRequirements();
  let response = `📋 当前需求列表 (${reqs.length} 个)：\n\n`;
  const statusIcons = { DRAFT: '📝', APPROVED: '✅', IN_PROGRESS: '🔨', DONE: '🎉', REJECTED: '❌' };
  reqs.forEach(r => {
    response += `${statusIcons[r.status] || '❓'} [${r.id}] ${r.title}\n   优先级: ${r.priority} | 状态: ${r.status} | 负责人: ${r.assignee || '未分配'}\n\n`;
  });
  const done = reqs.filter(r => r.status === 'DONE').length;
  response += `📊 完成率: ${Math.round(done / reqs.length * 100)}% (${done}/${reqs.length})`;
  socket.emit('bot_response', { message: response, type: 'text' });
}

function handleQuerySprint(socket) {
  const sprint = getSprint();
  const tasks = sprint.tasks;
  let response = `📋 ${sprint.name} (${sprint.start} ~ ${sprint.end})\n\n`;
  response += `📊 进度: ${sprint.name} ${getBubbleStats().sprint.progress}%\n\n`;
  const statusIcons = { TODO: '⬜', IN_PROGRESS: '🔵', IN_REVIEW: '🟡', DONE: '✅' };
  tasks.forEach(t => {
    const delayed = t.status !== 'DONE' && t.due_date < '2026-04-03' ? ' ⚠️延期' : '';
    response += `${statusIcons[t.status] || '⬜'} [${t.id}] ${t.title}\n   ${t.assignee || '未分配'} | 截止: ${t.due_date}${delayed}\n\n`;
  });
  socket.emit('bot_response', { message: response, type: 'text' });
}

function handleQueryReviews(socket) {
  const reviews = getCodeReviews();
  let response = `🔍 代码审查列表：\n\n`;
  const statusIcons = { PENDING: '🟡待审查', APPROVED: '✅已通过', REJECTED: '❌已拒绝', CHANGES_REQUESTED: '🔄需修改' };
  reviews.forEach(r => {
    response += `${statusIcons[r.status]} [${r.id}] ${r.title}\n   作者: ${r.author} | 审查人: ${r.reviewer} | ${r.created_at}\n\n`;
  });
  socket.emit('bot_response', { message: response, type: 'text' });
}

function handleCreateTask(socket, entities) {
  const title = entities.title || '新任务';
  const assignee = entities.assignee || '未指定';
  const id = createTask(title, assignee, 'P2', null);
  socket.emit('bot_response', { message: `任务已创建！\n\n📋 ID: ${id}\n📌 标题: ${title}\n👤 指派: ${assignee}\n📊 状态: 待办`, type: 'text' });
  socket.emit('stats_update', getBubbleStats());
}

function handleBuild(socket, executionId, entities) {
  const service = entities.service || 'unknown';
  socket.emit('bot_response', { message: `开始构建 ${service}...`, type: 'execution_start' });
  runBuild(io, executionId, service).then(() => {
    socket.emit('bot_response', { message: `构建完成！${service} 镜像已推送到仓库。`, type: 'text' });
    socket.emit('stats_update', getBubbleStats());
  });
}

function handleQueryBuilds(socket) {
  const builds = getBuilds();
  let response = `🔨 最近构建记录：\n\n`;
  builds.forEach(b => {
    const icon = b.status === 'SUCCESS' ? '✅' : '❌';
    response += `${icon} [${b.id}] ${b.service} ${b.version}\n   耗时: ${b.duration} | 触发: ${b.triggered_by} | ${b.built_at}\n\n`;
  });
  socket.emit('bot_response', { message: response, type: 'text' });
}

function handleRunTests(socket, executionId, entities) {
  const service = entities.service || 'all';
  socket.emit('bot_response', { message: `开始运行 ${service === 'all' ? '全部' : service} 测试...`, type: 'execution_start' });
  runTests(io, executionId, service).then(() => {
    socket.emit('bot_response', { message: `测试执行完成！\n\n📊 结果概要:\n✅ 通过: 186\n❌ 失败: 3\n⏭️ 跳过: 1\n📋 报告已生成`, type: 'text' });
    socket.emit('stats_update', getBubbleStats());
  });
}

function handleQueryTests(socket) {
  const tests = getTests();
  let response = `🧪 测试报告：\n\n`;
  tests.forEach(t => {
    const icon = t.status === 'PASSED' ? '✅' : '❌';
    const rate = Math.round(t.passed / t.total * 100);
    response += `${icon} ${t.suite}\n   通过: ${t.passed}/${t.total} (${rate}%) | ${t.ran_at}\n\n`;
  });
  socket.emit('bot_response', { message: response, type: 'text' });
}

function handleDeploy(socket, executionId, entities) {
  if (!entities.service) {
    socket.emit('bot_response', { message: '请告诉我你要部署哪个服务？比如："部署 identity-domain 到 test"', type: 'text' });
    return;
  }
  socket.emit('bot_response', { type: 'confirmation', data: { service: entities.service, version: entities.version, environment: entities.environment, message: '确认部署以下内容：', executionId } });
}

function handleQueryServices(socket) {
  const services = getAllServices();
  let response = `当前共有 ${services.length} 个服务运行中：\n\n`;
  services.forEach(svc => {
    const statusIcon = svc.status === 'HEALTHY' ? '✅' : '⚠️';
    response += `${statusIcon} ${svc.display_name} (${svc.name}) [${svc.env}]\n`;
  });
  socket.emit('bot_response', { message: response, type: 'text' });
}

function handleQueryDeployments(socket) {
  const deployments = getDeployments(5);
  let response = `最近 ${deployments.length} 次部署记录：\n\n`;
  deployments.forEach((dep, i) => {
    const statusIcon = dep.status === 'SUCCESS' ? '✅' : '❌';
    response += `${i + 1}. ${statusIcon} ${dep.service_display_name} ${dep.version} -> ${dep.environment}\n   ${dep.deployed_at} | 触发: ${dep.triggered_by}\n\n`;
  });
  socket.emit('bot_response', { message: response, type: 'text' });
}

async function handleHealthCheck(socket, executionId, entities) {
  if (!entities.service) {
    socket.emit('bot_response', { message: '请告诉我你要检查哪个服务？比如："检查 identity-domain"', type: 'text' });
    return;
  }
  const service = getServiceByName(entities.service);
  if (!service) { socket.emit('bot_response', { message: `未找到服务: ${entities.service}`, type: 'text' }); return; }
  socket.emit('bot_response', { message: `开始检查 ${service.display_name}...`, type: 'execution_start' });
  await runHealthCheck(io, executionId, service.name, entities.environment || 'test');
}

async function handleRollback(socket, executionId, entities) {
  if (!entities.service) { socket.emit('bot_response', { message: '请告诉我你要回滚哪个服务？', type: 'text' }); return; }
  socket.emit('bot_response', { message: `开始回滚 ${entities.service}...`, type: 'execution_start' });
  await runRollback(io, executionId, entities.service);
}

function handleQueryAlerts(socket) {
  const alerts = getAlerts();
  let response = `🚨 活跃告警 (${alerts.length} 条)：\n\n`;
  alerts.forEach(a => {
    const icon = a.level === 'WARNING' ? '⚠️' : a.level === 'CRITICAL' ? '🔴' : 'ℹ️';
    response += `${icon} [${a.id}] ${a.service}: ${a.message}\n   ${a.triggered_at}\n\n`;
  });
  socket.emit('bot_response', { message: response, type: 'text' });
}

function handleOverview(socket) {
  const s = getBubbleStats();
  let response = `📊 AgentCenter 全局总览\n\n`;
  response += `📋 需求: ${s.requirements.completion_rate}% 完成 (${s.requirements.done}/${s.requirements.total})\n`;
  response += `📅 ${s.sprint.name}: ${s.sprint.progress}% | ${s.sprint.delayed} 个延期\n`;
  response += `🔨 开发中: ${s.development.in_progress} 个任务 | ${s.development.code_reviews_pending} 个 CR 待审\n`;
  response += `⚙️ 构建: 今日 ${s.builds.today} 次, 成功率 ${s.builds.success_rate}%\n`;
  response += `🧪 测试: 通过率 ${s.tests.pass_rate}% | ${s.tests.failed} 个套件失败\n`;
  response += `🚀 部署: 今日 ${s.deployments.today} 次, 成功率 ${s.deployments.success_rate}%\n`;
  response += `📡 监控: ${s.monitoring.healthy_services}/${s.monitoring.total_services} 健康 | SLA ${s.monitoring.sla}%\n`;
  response += `⚠️ 告警: ${s.monitoring.active_alerts} 条活跃`;
  socket.emit('bot_response', { message: response, type: 'text' });
}

function handleHelp(socket) {
  const helpText = `🤖 AgentCenter 助手 — 全流程 DevOps 智能编排

📋 **需求管理**
  "创建需求：用户登录支持二维码"
  "需求进度"

📅 **计划 / Sprint**
  "Sprint 进度" / "有没有延期"

🔨 **开发**
  "代码审查" / "分配任务给研发工程师 B"

⚙️ **构建**
  "构建 identity-domain" / "构建状态"

🧪 **测试**
  "跑一下回归测试" / "测试报告"

🚀 **部署**
  "部署 identity-domain v2.4.0 到 test"
  "查看最近部署记录"

📡 **监控**
  "检查 identity-domain" / "告警列表"

🔙 **回滚**
  "回滚 identity-domain"

📊 **总览**
  "总览" / "全局"

试试说吧！`;
  socket.emit('bot_response', { message: helpText, type: 'help' });
}

app.get('/health', (req, res) => {
  res.json({ status: 'ok', mode: 'mock', timestamp: new Date().toISOString() });
});

app.get('/api/stats', (req, res) => res.json(getBubbleStats()));
app.get('/api/services', (req, res) => res.json(getAllServices()));
app.get('/api/deployments', (req, res) => res.json(getDeployments(10)));

app.post('/reset', (req, res) => {
  resetDemoState();
  res.json({ status: 'ok', message: 'Demo state reset to initial' });
});

const PORT = process.env.PORT || 4000;
server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error(`❌ Port ${PORT} is already in use. Please stop the other process or set PORT env var.`);
    process.exit(1);
  }
  throw err;
});
server.listen(PORT, () => {
  console.log(`
╔═══════════════════════════════════════════════════════════╗
║                                                           ║
║   🤖 AgentCenter Demo 服务器已启动                         ║
║                                                           ║
║   📍 访问地址: http://localhost:${PORT}                    ║
║                                                           ║
║   全流程命令示例:                                          ║
║   • "创建需求：用户登录支持二维码"                          ║
║   • "Sprint 进度"                                         ║
║   • "代码审查"                                            ║
║   • "构建 identity-domain"                                ║
║   • "跑一下回归测试"                                       ║
║   • "部署 identity-domain v2.4.0 到 test"                ║
║   • "总览"                                                ║
║                                                           ║
╚═══════════════════════════════════════════════════════════╝
  `);
});
