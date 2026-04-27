'use strict';

var _eventId = 1000;

function createMockEvent(overrides) {
  var defaults = {
    id: 'EVT-' + String(_eventId++).padStart(4, '0'),
    type: 'pipeline.updated',
    timestamp: new Date().toISOString(),
    actor: 'Orchestrator',
    target: 'identity-domain',
    severity: 'INFO',
    status: 'completed',
    message: '',
    relatedRole: 'development',
    nextActions: [],
  };
  var keys = Object.keys(defaults);
  var result = {};
  for (var i = 0; i < keys.length; i++) {
    var k = keys[i];
    result[k] = (overrides && overrides.hasOwnProperty(k)) ? overrides[k] : defaults[k];
  }
  return result;
}

var INTERACTION_RESPONSES = {
  'click:metric:deployment': { type: 'drawer', title_zh: '部署指标', data: { metric: 'deployment', period: 'today' } },
  'click:tool:build_engine': { type: 'drawer', title_zh: '持续集成平台', data: { toolId: 'build_engine' } },
  'click:tool:test_platform': { type: 'drawer', title_zh: '测试管理平台', data: { toolId: 'test_platform' } },
  'click:agent:ops': { type: 'drawer', title_zh: '运维智能体', data: { roleId: 'ops' } },
  'click:alert:critical': { type: 'modal', title_zh: '严重告警', data: { severity: 'critical' } },
  'click:alert:warning': { type: 'drawer', title_zh: '警告告警', data: { severity: 'warning' } },
  'story:start:incident': { type: 'modal', title_zh: '故障处置故事', data: { storyId: 'incident' } },
  'story:start:release': { type: 'modal', title_zh: '发布流程故事', data: { storyId: 'release' } },
  'story:start:requirement': { type: 'modal', title_zh: '需求流程故事', data: { storyId: 'requirement' } },
  'role:switch:management': { type: 'toast', title_zh: '角色已切换', data: { roleId: 'management' } },
  'role:switch:product': { type: 'toast', title_zh: '角色已切换', data: { roleId: 'product' } },
  'role:switch:development': { type: 'toast', title_zh: '角色已切换', data: { roleId: 'development' } },
  'role:switch:ops': { type: 'toast', title_zh: '角色已切换', data: { roleId: 'ops' } },
  'role:switch:quality': { type: 'toast', title_zh: '角色已切换', data: { roleId: 'quality' } },
  'role:switch:architecture': { type: 'toast', title_zh: '角色已切换', data: { roleId: 'architecture' } },
  'click:sidebar:architecture': { type: 'drawer', title_zh: '架构视图', data: { view: 'architecture' } },
  'click:search': { type: 'drawer', title_zh: '搜索结果', data: { view: 'search' } },
  'click:message_feed': { type: 'drawer', title_zh: '通知列表', data: { view: 'message_feed' } },
};

var STORY_SEQUENCES = {
  incident: [
    { step: 1, role: 'ops', event: { type: 'incident.escalated', message: '故障已冒泡到运维团队', nextActions: ['检查服务', '启动故障处置'] } },
    { step: 2, role: 'development', event: { type: 'tool.invoked', message: '研发开始排查', nextActions: ['查看日志', '分析根因'] } },
    { step: 3, role: 'quality', event: { type: 'approval.required', message: '测试团队待命', nextActions: ['执行验证', '确认修复'] } },
    { step: 4, role: 'management', event: { type: 'pipeline.updated', message: '故障已恢复', nextActions: ['查看报告', '复盘总结'] } },
  ],
  release: [
    { step: 1, role: 'development', event: { type: 'tool.invoked', message: '研发提交发布申请', nextActions: ['检查门禁', '等待审批'] } },
    { step: 2, role: 'ops', event: { type: 'approval.required', message: '等待运维审批', nextActions: ['审批通过', '开始部署'] } },
    { step: 3, role: 'ops', event: { type: 'pipeline.updated', message: '部署执行中', nextActions: ['监控进度', '处理异常'] } },
    { step: 4, role: 'management', event: { type: 'pipeline.updated', message: '发布完成', nextActions: ['确认结果', '通知相关方'] } },
  ],
  requirement: [
    { step: 1, role: 'product', event: { type: 'tool.invoked', message: '产品经理提交需求', nextActions: ['评审需求', '分配研发'] } },
    { step: 2, role: 'architecture', event: { type: 'approval.required', message: '架构师评审方案', nextActions: ['设计方案', '技术评审'] } },
    { step: 3, role: 'development', event: { type: 'tool.invoked', message: '研发开始实现', nextActions: ['开发任务', '提交代码'] } },
    { step: 4, role: 'quality', event: { type: 'pipeline.updated', message: '测试验证完成', nextActions: ['验收通过', '上线部署'] } },
  ],
};

var _storyState = {};

function _ensureStoryState(storyId) {
  if (!_storyState[storyId]) {
    _storyState[storyId] = { currentStep: 0 };
  }
  return _storyState[storyId];
}

function getStorySequence(storyId) {
  var seq = STORY_SEQUENCES[storyId];
  if (!seq) return [];

  var state = _ensureStoryState(storyId);
  var stepDef = seq[state.currentStep];

  if (!stepDef) {
    _storyState[storyId].currentStep = 0;
    stepDef = seq[0];
  }

  state.currentStep = (state.currentStep + 1) % seq.length;

  var evt = stepDef.event;
  return [createMockEvent({
    type: evt.type,
    message: evt.message,
    relatedRole: stepDef.role,
    nextActions: evt.nextActions,
    target: storyId + '-flow',
    status: 'running',
  })];
}

function resetStoryState() {
  _storyState = {};
  _eventId = 1000;
}

function getInteractionResponse(interactionId) {
  if (interactionId && INTERACTION_RESPONSES[interactionId]) {
    var resp = INTERACTION_RESPONSES[interactionId];
    return {
      type: resp.type,
      title_zh: resp.title_zh,
      data: resp.data,
    };
  }
  return { type: 'toast', title_zh: '演示交互', data: {} };
}

module.exports = {
  createMockEvent: createMockEvent,
  getInteractionResponse: getInteractionResponse,
  getStorySequence: getStorySequence,
  resetStoryState: resetStoryState,
};
