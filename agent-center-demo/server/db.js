const { getMockDomains, PERSON_HANDLES } = require('./registry');

const PERSON = {
  productA: '产品负责人 A',
  productF: '产品负责人 F',
  productG: '产品负责人 G',
  devB: '研发工程师 B',
  devC: '研发工程师 C',
  opsD: '运维工程师 D',
  oncallE: '值班工程师 E',
  architectH: '架构师 H',
  securityI: '安全工程师 I',
  opsJ: '运维工程师 J',
};

const db = {
  requirements: [
    { id: 'REQ-001', title: '用户登录支持二维码扫码', status: 'IN_PROGRESS', priority: 'P0', creator: PERSON.productA, assignee: PERSON.devB, created_at: '2026-03-28' },
    { id: 'REQ-002', title: '订单列表增加导出功能', status: 'APPROVED', priority: 'P1', creator: PERSON.productF, assignee: null, created_at: '2026-03-30' },
    { id: 'REQ-003', title: '支付超时自动取消订单', status: 'DONE', priority: 'P0', creator: PERSON.productA, assignee: PERSON.devC, created_at: '2026-03-25' },
    { id: 'REQ-004', title: '消息推送增加企微渠道', status: 'IN_PROGRESS', priority: 'P2', creator: PERSON.productG, assignee: PERSON.opsD, created_at: '2026-03-29' },
    { id: 'REQ-005', title: 'API 网关限流策略优化', status: 'DRAFT', priority: 'P1', creator: PERSON.architectH, assignee: null, created_at: '2026-04-01' },
    { id: 'REQ-006', title: '前端首页加载性能优化', status: 'APPROVED', priority: 'P1', creator: PERSON.productA, assignee: null, created_at: '2026-04-01' },
    { id: 'REQ-007', title: '用户注册短信验证码防刷', status: 'DONE', priority: 'P0', creator: PERSON.securityI, assignee: PERSON.devB, created_at: '2026-03-20' },
    { id: 'REQ-008', title: '部署流水线增加人工审批节点', status: 'REJECTED', priority: 'P3', creator: PERSON.opsJ, assignee: null, created_at: '2026-03-27' },
  ],

  sprint: {
    name: 'Sprint 2026-W14',
    start: '2026-03-31',
    end: '2026-04-11',
    tasks: [
      { id: 'TASK-101', title: '二维码登录-前端组件开发', status: 'IN_REVIEW', assignee: PERSON.devB, priority: 'P0', due_date: '2026-04-03', requirement_id: 'REQ-001' },
      { id: 'TASK-102', title: '二维码登录-后端接口实现', status: 'DONE', assignee: PERSON.devB, priority: 'P0', due_date: '2026-04-02', requirement_id: 'REQ-001' },
      { id: 'TASK-103', title: '订单导出-Excel生成模块', status: 'IN_PROGRESS', assignee: PERSON.devC, priority: 'P1', due_date: '2026-04-05', requirement_id: 'REQ-002' },
      { id: 'TASK-104', title: '企微推送-SDK集成', status: 'IN_PROGRESS', assignee: PERSON.opsD, priority: 'P2', due_date: '2026-04-08', requirement_id: 'REQ-004' },
      { id: 'TASK-105', title: '企微推送-消息模板配置', status: 'TODO', assignee: PERSON.opsD, priority: 'P2', due_date: '2026-04-09', requirement_id: 'REQ-004' },
      { id: 'TASK-106', title: '网关限流-策略引擎开发', status: 'TODO', assignee: null, priority: 'P1', due_date: '2026-04-10', requirement_id: 'REQ-005' },
      { id: 'TASK-107', title: '首页性能-懒加载优化', status: 'TODO', assignee: null, priority: 'P1', due_date: '2026-04-11', requirement_id: 'REQ-006' },
      { id: 'TASK-108', title: '二维码登录-联调测试', status: 'TODO', assignee: PERSON.devB, priority: 'P0', due_date: '2026-04-04', requirement_id: 'REQ-001' },
    ],
  },

  codeReviews: [
    { id: 'MR-201', title: 'feat: 二维码登录前端组件', author: PERSON.devB, reviewer: PERSON.architectH, status: 'PENDING', created_at: '2026-04-02 09:00' },
    { id: 'MR-202', title: 'fix: 支付超时取消逻辑修复', author: PERSON.devC, reviewer: PERSON.devB, status: 'APPROVED', created_at: '2026-04-01 16:00' },
    { id: 'MR-203', title: 'chore: 升级依赖版本', author: PERSON.opsD, reviewer: PERSON.devC, status: 'CHANGES_REQUESTED', created_at: '2026-04-01 14:00' },
  ],

  services: getMockDomains(),

  artifacts: [
    { id: 1, name: 'identity-domain', version: 'v2.4.0', type: 'CONTAINER_IMAGE', location: 'registry.internal/identity-domain:v2.4.0', digest: 'sha256:abc123' },
    { id: 2, name: 'identity-domain', version: 'v2.3.1', type: 'CONTAINER_IMAGE', location: 'registry.internal/identity-domain:v2.3.1', digest: 'sha256:def456' },
    { id: 3, name: 'integration-gateway', version: 'v1.8.5', type: 'CONTAINER_IMAGE', location: 'registry.internal/integration-gateway:v1.8.5', digest: 'sha256:ghi789' },
    { id: 4, name: 'billing-domain', version: 'v2.1.0', type: 'CONTAINER_IMAGE', location: 'registry.internal/billing-domain:v2.1.0', digest: 'sha256:jkl012' },
    { id: 5, name: 'order-domain', version: 'v3.2.0', type: 'CONTAINER_IMAGE', location: 'registry.internal/order-domain:v3.2.0', digest: 'sha256:mno345' },
  ],

  builds: [
    { id: 'BLD-301', service: 'identity-domain', version: 'v2.4.0', status: 'SUCCESS', duration: '2m 15s', triggered_by: PERSON.devB, built_at: '2026-04-02 10:00' },
    { id: 'BLD-302', service: 'integration-gateway', version: 'v1.8.5', status: 'SUCCESS', duration: '1m 48s', triggered_by: PERSON.devC, built_at: '2026-04-02 09:30' },
    { id: 'BLD-303', service: 'billing-domain', version: 'v2.1.0', status: 'FAILED', duration: '3m 02s', triggered_by: PERSON.opsD, built_at: '2026-04-02 09:00' },
    { id: 'BLD-304', service: 'order-domain', version: 'v3.2.0', status: 'SUCCESS', duration: '2m 30s', triggered_by: PERSON.devC, built_at: '2026-04-01 16:00' },
  ],

  tests: [
    { id: 'T-401', suite: 'identity-domain 单元测试', total: 128, passed: 126, failed: 2, skipped: 0, status: 'FAILED', ran_at: '2026-04-02 10:15' },
    { id: 'T-402', suite: 'integration-gateway 集成测试', total: 56, passed: 56, failed: 0, skipped: 0, status: 'PASSED', ran_at: '2026-04-02 09:45' },
    { id: 'T-403', suite: 'billing-domain 回归测试', total: 84, passed: 84, failed: 0, skipped: 0, status: 'PASSED', ran_at: '2026-04-02 09:10' },
    { id: 'T-404', suite: 'E2E 冒烟测试', total: 22, passed: 21, failed: 1, skipped: 0, status: 'FAILED', ran_at: '2026-04-02 08:00' },
  ],

  deployments: [
    { id: 1, service_id: 1, artifact_id: 1, environment: 'test', status: 'SUCCESS', triggered_by: PERSON_HANDLES[PERSON.devB], deployed_at: '2026-04-02 10:30' },
    { id: 2, service_id: 2, artifact_id: 3, environment: 'test', status: 'SUCCESS', triggered_by: PERSON_HANDLES[PERSON.devC], deployed_at: '2026-04-02 09:50' },
    { id: 3, service_id: 3, artifact_id: 4, environment: 'test', status: 'SUCCESS', triggered_by: PERSON_HANDLES[PERSON.opsD], deployed_at: '2026-04-02 09:20' },
    { id: 4, service_id: 1, artifact_id: 2, environment: 'production', status: 'SUCCESS', triggered_by: PERSON_HANDLES[PERSON.devB], deployed_at: '2026-04-01 18:00' },
    { id: 5, service_id: 4, artifact_id: 5, environment: 'test', status: 'FAILED', triggered_by: PERSON_HANDLES[PERSON.oncallE], deployed_at: '2026-04-01 16:30' },
  ],

  alerts: [
    { id: 'ALT-501', service: 'order-domain', level: 'WARNING', message: '接口响应时间 > 2s', triggered_at: '2026-04-02 11:00' },
    { id: 'ALT-502', service: 'identity-domain', level: 'INFO', message: 'CPU 使用率 > 80%', triggered_at: '2026-04-02 10:45' },
  ],

  nextId: { requirement: 9, task: 109, build: 305, deployment: 6, alert: 503 },
};

function getBubbleStats() {
  const totalReqs = db.requirements.length;
  const doneReqs = db.requirements.filter(r => r.status === 'DONE').length;
  const tasks = db.sprint.tasks;
  const delayedTasks = tasks.filter(t => t.status !== 'DONE' && t.due_date < '2026-04-03').length;
  const totalBuilds = db.builds.length;
  const successBuilds = db.builds.filter(b => b.status === 'SUCCESS').length;
  const totalTests = db.tests.length;
  const passedTests = db.tests.filter(t => t.status === 'PASSED').length;
  const totalDeps = db.deployments.length;
  const successDeps = db.deployments.filter(d => d.status === 'SUCCESS').length;
  const healthyServices = db.services.filter(s => s.status === 'HEALTHY').length;
  return {
    requirements: { total: totalReqs, done: doneReqs, in_progress: db.requirements.filter(r => r.status === 'IN_PROGRESS').length, completion_rate: Math.round(doneReqs / totalReqs * 100) },
    sprint: { name: db.sprint.name, total: tasks.length, done: tasks.filter(t => t.status === 'DONE').length, in_progress: tasks.filter(t => t.status === 'IN_PROGRESS').length, delayed: delayedTasks, progress: Math.round(tasks.filter(t => t.status === 'DONE').length / tasks.length * 100) },
    development: { in_progress: tasks.filter(t => t.status === 'IN_PROGRESS').length, code_reviews_pending: db.codeReviews.filter(r => r.status === 'PENDING').length, todo: tasks.filter(t => t.status === 'TODO').length },
    builds: { today: totalBuilds, success_rate: totalBuilds > 0 ? Math.round(successBuilds / totalBuilds * 100) : 0 },
    tests: { total_suites: totalTests, pass_rate: totalTests > 0 ? Math.round(passedTests / totalTests * 100) : 0, failed: totalTests - passedTests },
    deployments: { today: totalDeps, total: 156, success_rate: totalDeps > 0 ? Math.round(successDeps / totalDeps * 100) : 0 },
    monitoring: { active_alerts: db.alerts.length, healthy_services: healthyServices, total_services: db.services.length, sla: 99.8 },
  };
}

function getStats() { return getBubbleStats(); }
function getAllServices() { return db.services.map(s => ({ ...s })); }
function getServiceByName(name) { return db.services.find(s => s.name === name) || null; }

function getRequirements() { return db.requirements.map(r => ({ ...r })); }

function getSprint() {
  return {
    ...db.sprint,
    tasks: db.sprint.tasks.map(t => {
      const req = db.requirements.find(r => r.id === t.requirement_id);
      return { ...t, requirement_title: req?.title };
    }),
  };
}

function getCodeReviews() { return db.codeReviews.map(r => ({ ...r })); }
function getBuilds() { return db.builds.map(b => ({ ...b })); }
function getTests() { return db.tests.map(t => ({ ...t })); }
function getAlerts() { return db.alerts.map(a => ({ ...a })); }

function getDeployments(limit = 10) {
  return db.deployments
    .sort((a, b) => b.id - a.id)
    .slice(0, limit)
    .map(d => {
      const service = db.services.find(s => s.id === d.service_id);
      const artifact = db.artifacts.find(a => a.id === d.artifact_id);
      return { ...d, service_name: service?.name, service_display_name: service?.display_name, version: artifact?.version };
    });
}

function getArtifact(name, version) { return db.artifacts.find(a => a.name === name && a.version === version) || null; }

function createRequirement(title, priority, creator) {
  const id = 'REQ-' + String(db.nextId.requirement++).padStart(3, '0');
  db.requirements.push({ id, title, status: 'DRAFT', priority: priority || 'P2', creator: creator || 'system', assignee: null, created_at: new Date().toISOString().substring(0, 10) });
  return id;
}

function createTask(title, assignee, priority, requirementId) {
  const id = 'TASK-' + db.nextId.task++;
  db.sprint.tasks.push({ id, title, status: 'TODO', assignee: assignee || null, priority: priority || 'P2', due_date: db.sprint.end, requirement_id: requirementId || null });
  return id;
}

function createDeployment(serviceId, artifactId, environment, triggeredBy) {
  const id = db.nextId.deployment++;
  db.deployments.push({ id, service_id: serviceId, artifact_id: artifactId, environment, status: 'SUCCESS', triggered_by: triggeredBy, deployed_at: new Date().toISOString().replace('T', ' ').substring(0, 16) });
  return id;
}

function createBuild(service, version, status) {
  const id = 'BLD-' + db.nextId.build++;
  db.builds.push({ id, service, version, status: status || 'SUCCESS', duration: Math.floor(Math.random() * 120 + 60) + 's', triggered_by: 'system', built_at: new Date().toISOString().replace('T', ' ').substring(0, 16) });
  return id;
}

function incrementDeployments() {}

module.exports = {
  db, getStats, getBubbleStats, getAllServices, getServiceByName,
  getRequirements, getSprint, getCodeReviews, getBuilds, getTests, getAlerts,
  getDeployments, getArtifact, createRequirement, createTask, createDeployment, createBuild,
  incrementDeployments,
};
