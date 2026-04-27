/**
 * AgentCenter Demo — Source-of-Truth Registries
 *
 * Plain JS module, loadable via require() without starting the server.
 * Provides: toolchain categories, role definitions, banned-terms safety list.
 */

'use strict';

const fs = require('fs');

const TOOLCHAIN_REGISTRY = [
  { id: 'requirement_platform', name_zh: '需求管理平台', name_en: 'Requirement Management', icon: 'ClipboardList', description_zh: '需求收集、评审、排期与全生命周期追踪' },
  { id: 'design_collaboration', name_zh: '设计协作平台', name_en: 'Design Collaboration', icon: 'PenTool', description_zh: 'UI/UX 设计、原型评审与设计资产共享' },
  { id: 'code_repository', name_zh: '代码托管平台', name_en: 'Code Repository', icon: 'GitBranch', description_zh: '源码托管、分支管理与代码评审' },
  { id: 'code_quality', name_zh: '代码质量平台', name_en: 'Code Quality', icon: 'ShieldCheck', description_zh: '静态分析、代码规范扫描与技术债务治理' },
  { id: 'build_engine', name_zh: '持续集成平台', name_en: 'Build Engine', icon: 'Hammer', description_zh: '编译构建、CI 流水线与制品产出' },
  { id: 'test_platform', name_zh: '测试管理平台', name_en: 'Test Platform', icon: 'TestTube2', description_zh: '单元测试、集成测试、E2E 与回归测试管理' },
  { id: 'artifact_registry', name_zh: '制品管理平台', name_en: 'Artifact Registry', icon: 'Package', description_zh: '容器镜像、制品版本管理与安全扫描' },
  { id: 'deployment_platform', name_zh: '发布部署平台', name_en: 'Deployment Platform', icon: 'Rocket', description_zh: '环境管理、应用部署、灰度发布与回滚' },
  { id: 'observability_platform', name_zh: '监控观测平台', name_en: 'Observability Platform', icon: 'Activity', description_zh: '指标监控、链路追踪、日志聚合与告警' },
  { id: 'alert_channel', name_zh: '告警通知平台', name_en: 'Alert Channel', icon: 'Bell', description_zh: 'IM 消息推送、邮件通知与事件广播' },
  { id: 'audit_compliance', name_zh: '审计合规平台', name_en: 'Audit & Compliance', icon: 'FileCheck', description_zh: '操作审计、权限管控与合规报告' },
];

const ROLE_REGISTRY = [
  { id: 'management', name_zh: '管理层', name_en: 'Management', icon: 'Crown', description_zh: '全局视角，关注交付效率、资源分配与风险把控', value_statement_zh: '一站式了解项目进展、团队效率与关键风险' },
  { id: 'product', name_zh: '产品团队', name_en: 'Product', icon: 'Lightbulb', description_zh: '需求全生命周期管理，从提出到上线验收', value_statement_zh: '需求进度实时可见，一键追踪从提出到上线全流程' },
  { id: 'development', name_zh: '研发团队', name_en: 'Development', icon: 'Code2', description_zh: '代码编写、评审、构建与日常开发协作', value_statement_zh: '代码评审、构建状态与任务分配一目了然' },
  { id: 'ops', name_zh: '运维团队', name_en: 'Operations', icon: 'Server', description_zh: '部署管理、环境运维、监控告警处理', value_statement_zh: '部署状态实时掌握，告警响应快速精准' },
  { id: 'quality', name_zh: '质量团队', name_en: 'Quality Assurance', icon: 'CheckCircle2', description_zh: '测试执行、质量度量、回归保障', value_statement_zh: '测试覆盖率与质量趋势清晰可见，缺陷无处藏身' },
  { id: 'architecture', name_zh: '架构团队', name_en: 'Architecture', icon: 'Network', description_zh: '技术架构决策、系统设计与规范治理', value_statement_zh: '全局视角审视系统依赖关系与技术债务' },
];

// Vendor/product names: stored uppercase for case-insensitive scanning
// Chinese product/person names: stored as-is (no uppercase transformation)
// Concrete service names: abstract away internal architecture
const BANNED_TERMS = [
  'JIRA', 'GITLAB', 'GITHUB', 'JENKINS', 'ARGOCD',
  'PROMETHEUS', 'GRAFANA', 'ALERTMANAGER', 'SLACK', 'CONFLUENCE',
  'HARBOR', 'KUBERNETES', 'K8S',
  '飞书', '禅道',
  'USER-SERVICE', 'PAYMENT-SERVICE', 'PAYMENT-SVC',
  'ORDER-SVC', 'AUTH-SERVICE', 'FRONTEND-WEB', 'BACKEND-API',
  'API-GATEWAY', 'NOTIFICATION',
  'HARBOR.EXAMPLE.COM',
  'ZHANGSAN', 'LISI', 'WANGWU', 'ZHAOLIU',
  '张三', '李四', '王五', '赵六',
  '产品经理A', '产品经理B', '产品经理C',
  '架构师D', '安全工程师E', '运维F',
];

const _dedupedBannedTerms = [...new Set(BANNED_TERMS)];

function isBannedTerm(term) {
  if (!term || typeof term !== 'string') return false;
  const upper = term.toUpperCase().trim();
  return _dedupedBannedTerms.some(function (b) {
    return b.toUpperCase() === upper;
  });
}

function scanForBannedTerms(filePath) {
  var content;
  try {
    content = fs.readFileSync(filePath, 'utf-8');
  } catch (e) {
    return [{ term: '__READ_ERROR__', line: 0, error: e.message }];
  }

  var lines = content.split('\n');
  var violations = [];

  lines.forEach(function (line, idx) {
    var lineNum = idx + 1;
    var upperLine = line.toUpperCase();
    _dedupedBannedTerms.forEach(function (banned) {
      if (upperLine.indexOf(banned.toUpperCase()) !== -1) {
        violations.push({ term: banned, line: lineNum });
      }
    });
  });

  return violations;
}

function getToolchainRegistry() {
  return TOOLCHAIN_REGISTRY.map(function (t) { return Object.assign({}, t); });
}

function getRoleRegistry() {
  return ROLE_REGISTRY.map(function (r) { return Object.assign({}, r); });
}

function getBannedTerms() {
  return _dedupedBannedTerms.slice();
}

/**
 * Abstract domain IDs used throughout the demo.
 * Replaces all concrete vendor/product/service names.
 */
const MOCK_DOMAINS = [
  { id: 1, name: 'identity-domain', display_name: '身份域', type: 'BACKEND', status: 'HEALTHY', env: 'test' },
  { id: 2, name: 'integration-gateway', display_name: '集成网关', type: 'BACKEND', status: 'HEALTHY', env: 'test' },
  { id: 3, name: 'billing-domain', display_name: '账务域', type: 'BACKEND', status: 'HEALTHY', env: 'test' },
  { id: 4, name: 'order-domain', display_name: '订单域', type: 'BACKEND', status: 'DEGRADED', env: 'test' },
  { id: 5, name: 'customer-portal', display_name: '客户门户', type: 'FRONTEND', status: 'HEALTHY', env: 'test' },
];

/** Flat list of abstract service names for intent matching */
function getServiceNames() {
  return MOCK_DOMAINS.map(function (d) { return d.name; });
}

/** Get full domain list (shallow copies) */
function getMockDomains() {
  return MOCK_DOMAINS.map(function (d) { return Object.assign({}, d); });
}

/**
 * Anonymized person labels used throughout the demo.
 * Maps abstract labels to display names.
 */
const PERSON_LABELS = {
  '产品负责人 A': '产品负责人 A',
  '产品负责人 F': '产品负责人 F',
  '产品负责人 G': '产品负责人 G',
  '研发工程师 B': '研发工程师 B',
  '研发工程师 C': '研发工程师 C',
  '运维工程师 D': '运维工程师 D',
  '值班工程师 E': '值班工程师 E',
  '架构师 H': '架构师 H',
  '安全工程师 I': '安全工程师 I',
  '运维工程师 J': '运维工程师 J',
};

/** English login handles mapped from person labels */
const PERSON_HANDLES = {
  '研发工程师 B': 'dev-engineer-b',
  '研发工程师 C': 'dev-engineer-c',
  '运维工程师 D': 'ops-engineer-d',
  '值班工程师 E': 'oncall-engineer-e',
};

module.exports = {
  getToolchainRegistry,
  getRoleRegistry,
  getBannedTerms,
  isBannedTerm,
  scanForBannedTerms,
  getServiceNames,
  getMockDomains,
  PERSON_LABELS,
  PERSON_HANDLES,
};
