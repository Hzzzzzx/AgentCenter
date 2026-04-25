const { getServiceNames } = require('./registry');

function parseIntent(message) {
  const msg = message.toLowerCase().trim();

  if (msg.includes('创建需求') || msg.includes('新建需求') || msg.includes('提需求')) {
    const titleMatch = message.match(/(?:创建需求|新建需求|提需求)[:：\s]*(.+)/);
    return { intent: 'create_requirement', entities: { title: titleMatch ? titleMatch[1].trim() : message } };
  }

  if ((msg.includes('需求') || msg.includes('requirement')) && (msg.includes('进度') || msg.includes('列表') || msg.includes('状态') || msg.includes('有哪些'))) {
    return { intent: 'query_requirements', entities: {} };
  }

  if (msg.includes('延期') || ((msg.includes('sprint') || msg.includes('迭代') || msg.includes('计划')) && (msg.includes('概况') || msg.includes('进度') || msg.includes('状态') || msg.includes('总结')))) {
    return { intent: 'query_sprint', entities: {} };
  }

  if (msg.includes('代码审查') || msg.includes('code review') || msg.includes('cr列表') || msg.includes('review')) {
    return { intent: 'query_reviews', entities: {} };
  }

  if ((msg.includes('分配') || msg.includes('指派')) && msg.includes('任务')) {
    return { intent: 'create_task', entities: extractTaskEntities(message) };
  }

  if (msg.includes('构建') && !msg.includes('构建状态') && !msg.includes('构建情况')) {
    return { intent: 'build', entities: extractServiceEntities(message) };
  }

  if (msg.includes('构建状态') || msg.includes('构建情况') || msg.includes('build')) {
    return { intent: 'query_builds', entities: {} };
  }

  if (msg.includes('跑') && msg.includes('测试') || msg.includes('运行测试') || msg.includes('回归测试') || msg.includes('跑一下')) {
    return { intent: 'run_tests', entities: extractServiceEntities(message) };
  }

  if (msg.includes('测试报告') || msg.includes('测试结果') || msg.includes('测试情况')) {
    return { intent: 'query_tests', entities: {} };
  }

  const deployMatch = msg.match(/部署\s*(\S+)\s*(?:v)?([\d.]+)?\s*(?:到|->)?\s*(\S+)/);
  if (deployMatch || msg.includes('部署')) {
    return { intent: 'deploy', entities: extractDeployEntities(message) };
  }

  if (msg.includes('服务') && (msg.includes('运行') || msg.includes('有哪些') || msg.includes('列表'))) {
    return { intent: 'query_services', entities: {} };
  }

  if (msg.includes('部署') && (msg.includes('记录') || msg.includes('历史') || msg.includes('最近'))) {
    return { intent: 'query_deployments', entities: {} };
  }

  if (msg.includes('健康') || msg.includes('检查') || msg.includes('有没有问题')) {
    return { intent: 'health_check', entities: extractServiceEntities(message) };
  }

  if (msg.includes('回滚') || msg.includes('rollback')) {
    return { intent: 'rollback', entities: extractServiceEntities(message) };
  }

  if (msg.includes('告警') || msg.includes('alert')) {
    return { intent: 'query_alerts', entities: {} };
  }

  if (msg.includes('帮助') || msg.includes('help') || msg.includes('怎么用') || msg.includes('你能做什么')) {
    return { intent: 'help', entities: {} };
  }

  if (msg.includes('总览') || msg.includes('overview') || msg.includes('全局')) {
    return { intent: 'overview', entities: {} };
  }

  if (msg.includes('展示系统架构') || msg.includes('系统架构') || msg.includes('架构视图')) {
    return { intent: 'show_architecture', entities: {} };
  }

  return { intent: 'unknown', entities: {} };
}

function extractDeployEntities(message) {
  const entities = {};
  const msg = message.toLowerCase();
  const services = getServiceNames();
  for (const svc of services) {
    if (msg.includes(svc) || msg.includes(svc.replace(/-/g, ''))) { entities.service = svc; break; }
  }
  if (!entities.service) {
    const m = message.match(/(\S+)-(domain|gateway|portal)/i);
    if (m) entities.service = m[1] + '-' + m[2].toLowerCase();
  }
  const vm = message.match(/v?([\d.]+)/);
  entities.version = vm ? 'v' + vm[1] : 'latest';
  const envs = ['production', 'prod', 'staging', 'test', 'dev', 'development'];
  for (const env of envs) {
    if (msg.includes(env)) { entities.environment = env === 'prod' ? 'production' : env === 'dev' ? 'development' : env; break; }
  }
  if (!entities.environment) entities.environment = 'test';
  return entities;
}

function extractServiceEntities(message) {
  const entities = {};
  const msg = message.toLowerCase();
  const services = getServiceNames();
  for (const svc of services) {
    if (msg.includes(svc) || msg.includes(svc.replace(/-/g, ''))) { entities.service = svc; break; }
  }
  if (!entities.service) {
    const m = message.match(/(\S+)-(domain|gateway|portal)/i);
    if (m) entities.service = m[1] + '-' + m[2].toLowerCase();
  }
  return entities;
}

function extractTaskEntities(message) {
  const entities = {};
  const assigneeMatch = message.match(/(?:分配|指派)(?:给|给|到|至)\s*(\S+)/);
  if (assigneeMatch) entities.assignee = assigneeMatch[1];
  const titleMatch = message.match(/任务[:：\s]*(.+?)(?:\s*(?:分配|指派)|$)/);
  if (titleMatch) entities.title = titleMatch[1].trim();
  return entities;
}

module.exports = { parseIntent };
