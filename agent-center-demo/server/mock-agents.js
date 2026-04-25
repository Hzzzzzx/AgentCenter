const { getServiceByName, getArtifact, createDeployment, createBuild, incrementDeployments } = require('./db');

function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }

class MockAgent {
  constructor(name, io, executionId) { this.name = name; this.io = io; this.executionId = executionId; }

  async execute(step) {
    this.io.emit('step_update', { step: step.name, status: 'running', message: step.message });
    await sleep(step.delay || 1000);
    this.io.emit('step_update', { step: step.name, status: 'completed', message: step.successMessage || step.message });
    return { success: true };
  }
}

class BuilderAgent extends MockAgent {
  async build(service, version) {
    return this.execute({ name: 'build', message: `正在构建 ${service}:${version}...`, delay: 2000, successMessage: `构建完成: ${service}:${version}` });
  }
}

class DeployerAgent extends MockAgent {
  async deploy(service, environment) {
    return this.execute({ name: 'deploy', message: `正在部署到 ${environment}...`, delay: 2500, successMessage: `部署成功: ${service} -> ${environment}` });
  }
}

class MonitorAgent extends MockAgent {
  async checkHealth(service) {
    return this.execute({ name: 'health_check', message: `正在检查 ${service} 健康状态...`, delay: 1500, successMessage: `健康检查通过: ${service}` });
  }
  async verify(service, env) {
    return this.execute({ name: 'verify', message: `正在验证部署结果...`, delay: 1000, successMessage: `验证通过: ${service} 在 ${env} 运行正常` });
  }
}

class NotifierAgent extends MockAgent {
  async notify(message) {
    return this.execute({ name: 'notify', message: `正在发送通知...`, delay: 800, successMessage: `通知已发送: ${message}` });
  }
}

class RequirementAgent extends MockAgent {
  async analyze(title) {
    return this.execute({ name: 'analyze', message: `正在分析需求: ${title}...`, delay: 1500, successMessage: `需求分析完成` });
  }
  async createInTracker(title) {
    return this.execute({ name: 'create', message: `正在创建需求工单...`, delay: 1000, successMessage: `需求工单已创建` });
  }
}

class TesterAgent extends MockAgent {
  async prepare(service) {
    return this.execute({ name: 'prepare', message: `正在准备测试环境...`, delay: 1200, successMessage: `测试环境就绪` });
  }
  async runSuite(service) {
    return this.execute({ name: 'test', message: `正在运行 ${service} 测试套件...`, delay: 3000, successMessage: `测试执行完成` });
  }
  async report(service) {
    return this.execute({ name: 'report', message: `正在生成测试报告...`, delay: 800, successMessage: `测试报告已生成` });
  }
}

async function runDeployment(io, executionId, service, version, environment) {
  io.emit('execution_start', { service, version, environment, totalSteps: 4, type: 'deploy' });
  const builder = new BuilderAgent('Builder', io, executionId);
  const deployer = new DeployerAgent('Deployer', io, executionId);
  const monitor = new MonitorAgent('Monitor', io, executionId);
  const notifier = new NotifierAgent('Notifier', io, executionId);
  await builder.build(service, version);
  createBuild(service, version, 'SUCCESS');
  await deployer.deploy(service, environment);
  await monitor.verify(service, environment);
  await notifier.notify(`部署完成: ${service}:${version} -> ${environment}`);
  io.emit('execution_complete', { success: true, service, version, environment, duration: '约 6 秒' });
}

async function runHealthCheck(io, executionId, service, environment) {
  io.emit('execution_start', { service, environment, totalSteps: 1, type: 'health_check' });
  const monitor = new MonitorAgent('Monitor', io, executionId);
  await monitor.checkHealth(service);
  io.emit('execution_complete', { success: true, service, environment, message: `${service} 健康状况正常` });
}

async function runRollback(io, executionId, service) {
  io.emit('execution_start', { service, totalSteps: 2, type: 'rollback' });
  const deployer = new DeployerAgent('Deployer', io, executionId);
  const monitor = new MonitorAgent('Monitor', io, executionId);
  await deployer.deploy(service, '回滚中...');
  await monitor.verify(service, 'production');
  io.emit('execution_complete', { success: true, service, message: `已回滚到上一个稳定版本` });
}

async function runCreateRequirement(io, executionId, title) {
  io.emit('execution_start', { title, totalSteps: 2, type: 'create_requirement' });
  const agent = new RequirementAgent('Requirement', io, executionId);
  await agent.analyze(title);
  await agent.createInTracker(title);
  io.emit('execution_complete', { success: true, title, message: `需求已创建: ${title}` });
}

async function runBuild(io, executionId, service) {
  io.emit('execution_start', { service, totalSteps: 1, type: 'build' });
  const builder = new BuilderAgent('Builder', io, executionId);
  await builder.build(service, 'latest');
  createBuild(service, 'latest', 'SUCCESS');
  io.emit('execution_complete', { success: true, service, message: `${service} 构建成功` });
}

async function runTests(io, executionId, service) {
  io.emit('execution_start', { service, totalSteps: 3, type: 'test' });
  const tester = new TesterAgent('Tester', io, executionId);
  await tester.prepare(service || 'all');
  await tester.runSuite(service || 'all');
  await tester.report(service || 'all');
  io.emit('execution_complete', { success: true, service: service || 'all', message: `测试执行完成，报告已生成` });
}

module.exports = { runDeployment, runHealthCheck, runRollback, runCreateRequirement, runBuild, runTests };
