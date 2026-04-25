# 统一对象模型 (Unified Domain Model)

> 生成时间：2026-04-02
> 状态：设计讨论中
> 关联文档：[架构总览](./ARCHITECTURE-OVERVIEW.md) | [环境与晋升](./ENVIRONMENT-AND-PROMOTION.md)

---

## 一、设计原则

```
所有 DevOps 对象都有一个全局唯一 ID，可以跨系统关联。
外部系统通过适配器映射到统一模型，平台不直接依赖任何外部系统。
```

---

## 二、对象关系总览

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ 工单     │────▶│ 代码库   │────▶│ 制品     │────▶│ 部署     │────▶│ 运行时   │
│ WorkItem │     │ CodeRepo │     │ Artifact │     │Deployment│     │ Runtime  │
│          │     │          │     │          │     │          │     │          │
│ 需求     │     │ 分支     │     │ 镜像     │     │ 发布记录 │     │ 服务实例 │
│ 任务     │     │ 合并请求 │     │ 包       │     │ 策略     │     │ 指标     │
│ 缺陷     │     │ 提交     │     │ 构建物   │     │ 审批     │     │ 告警     │
└──────────┘     └──────────┘     └──────────┘     └──────────┘     └──────────┘
     │                │                │                │                │
     └────────────────┴────────────────┴────────────────┴────────────────┘
                                  全局可追溯链路
```

---

## 三、8 个核心对象详细定义

### 3.1 WorkItem (工单)

```yaml
WorkItem:
  id: String!                         # 全局唯一: WI-{seq}
  externalRefs:                       # 外部系统引用 (双向映射)
    - system: String                  # 来源系统标识
      externalId: String              # 外部系统中的 ID
  type: Enum                          # EPIC | STORY | TASK | BUG
  title: String
  description: String
  status: Enum                        # BACKLOG | IN_PROGRESS | IN_REVIEW | DONE | CLOSED
  priority: Enum                      # P0 | P1 | P2 | P3
  owner: User
  assignee: User
  labels: [String]
  iteration: Iteration                # 所属迭代
  links:
    codeChanges: [CodeChange]          # 关联的代码变更
    deployments: [Deployment]          # 关联的部署
  timestamps:
    createdAt: DateTime
    updatedAt: DateTime
    dueDate: DateTime?
```

### 3.2 CodeRepo (代码库)

```yaml
CodeRepo:
  id: String!
  name: String
  url: String
  defaultBranch: String
  branches: [Branch]

Branch:
  name: String
  codeChanges: [CodeChange]

CodeChange:                            # 合并请求 / 变更集
  id: String!
  title: String
  description: String
  sourceBranch: String
  targetBranch: String
  status: Enum                        # OPEN | MERGED | CLOSED
  author: User
  reviewers: [User]
  commits: [Commit]
  linkedWorkItems: [WorkItem]
  linkedArtifacts: [Artifact]
  timestamps:
    createdAt: DateTime
    mergedAt: DateTime?

Commit:
  hash: String
  message: String
  author: User
  timestamp: DateTime
  filesChanged: [String]
```

### 3.3 Artifact (制品)

```yaml
Artifact:
  id: String!
  name: String                         # backend:v2.3.1
  version: String
  type: Enum                           # CONTAINER_IMAGE | BINARY | PACKAGE | CHART | BUNDLE
  location: String                     # 存储地址
  digest: String                       # SHA256 校验
  size: Long
  provenance:                          # 来源证明
    codeRepo: CodeRepo
    commitHash: String
    buildJob: BuildJob
    buildTime: DateTime
  scanResult:                          # 安全扫描
    vulnerabilities: [Vulnerability]
    passed: Boolean
  promotionHistory: [Promotion]         # 晋升记录

BuildJob:
  id: String!
  trigger: Enum                        # PUSH | SCHEDULE | MANUAL
  status: Enum                         # RUNNING | SUCCESS | FAILED
  stages: [BuildStage]
  logs: String
  duration: Long
  artifacts: [Artifact]
```

### 3.4 Deployment (部署)

```yaml
Deployment:
  id: String!
  artifact: Artifact!
  target: DeploymentTarget!
  service: Service!
  strategy: Enum                       # ROLLING | BLUE_GREEN | CANARY | RECREATE
  strategyConfig: JSON
  status: Enum                         # PENDING | APPROVED | IN_PROGRESS | SUCCESS | FAILED | ROLLED_BACK
  stages: [DeploymentStage]
  result:
    success: Boolean
    duration: Long
    error: String?
  rollback: RollbackInfo?
  approval: ApprovalInfo?
  triggeredBy: User
  timestamps:
    createdAt: DateTime
    startedAt: DateTime
    completedAt: DateTime?

DeploymentTarget:
  environment: Environment!
  cluster: String
  namespace: String
  region: String

DeploymentStage:
  name: String                         # 预检查 | 资源准备 | 实例更新 | 流量切换 | 健康验证
  status: Enum
  startedAt: DateTime
  completedAt: DateTime?
  details: String
```

### 3.5 Environment (环境)

```yaml
Environment:
  id: String!
  name: String                         # dev | test | staging | production
  displayName: String
  tier: Enum                           # DEVELOPMENT | TESTING | PRE_PRODUCTION | PRODUCTION
  promotionOrder: Int                  # 晋升顺序: 1→2→3→4
  promotionPolicy: PromotionPolicy     # 晋升到此环境需要的条件
  infrastructure:
    cluster: String
    namespace: String
    region: String
    networkSegment: String
  config:
    autoDeploy: Boolean
    requiresApproval: Boolean
    approvers: [User]
    changeWindow: ChangeWindow?
  status: Enum                         # HEALTHY | DEGRADED | MAINTENANCE | OFFLINE
  services: [ServiceStatus]
```

### 3.6 Promotion (晋升)

```yaml
Promotion:
  id: String!
  artifact: Artifact!
  from: Environment!
  to: Environment!
  status: Enum                         # PENDING_CHECKS | PENDING_APPROVAL | APPROVED | EXECUTING | SUCCESS | FAILED | REJECTED
  checks: [PromotionCheck]
  approval:
    required: Boolean
    decisions: [ApprovalDecision]
  deployment: Deployment?
  triggeredBy: User
  triggeredAt: DateTime
  completedAt: DateTime?

PromotionCheck:
  type: Enum                           # TEST_COVERAGE | SECURITY_SCAN | PERFORMANCE | SMOKE_TEST | MANUAL_SIGNOFF
  name: String
  status: Enum                         # PASSED | FAILED | SKIPPED | PENDING
  result: JSON
  reportUrl: String?
```

### 3.7 Service (服务)

```yaml
Service:
  id: String!
  name: String
  displayName: String
  type: Enum                           # BACKEND | FRONTEND | DATA | MIDDLEWARE | INFRASTRUCTURE
  owner: Team
  repositories: [CodeRepo]
  runtimeSpec:
    healthCheckPath: String
    resourceRequirements: ResourceSpec
    scalingPolicy: ScalingPolicy
    dependencies: [ServiceDependency]
  environments: [ServiceEnvironmentStatus]
```

### 3.8 Incident (事件)

```yaml
Incident:
  id: String!
  title: String
  severity: Enum                       # P0 | P1 | P2 | P3
  status: Enum                         # DETECTED | INVESTIGATING | MITIGATING | RESOLVED | POST_MORTEM
  affectedServices: [Service]
  affectedEnvironments: [Environment]
  rootCause: String?
  timeline: [IncidentEvent]
  relatedDeployments: [Deployment]
  relatedAlerts: [Alert]
  resolution: String?
  timestamps:
    detectedAt: DateTime
    acknowledgedAt: DateTime?
    mitigatedAt: DateTime?
    resolvedAt: DateTime?
```

---

## 四、ID 设计规范

### 格式

```
{Type}-{System}-{Sequence}

示例:
WI-JIRA-12345            # JIRA 工单
ART-HARBOR-sha256         # 制品
DEP-ARGOCD-20260401-001  # 部署
SVC-TEAM-backend          # 服务
ENV-production            # 环境
PRM-20260401-001          # 晋升
```

### 外部引用映射

```yaml
# 每个对象通过 externalRefs 保持与外部系统的双向映射
externalRefs:
  - system: "需求管理类系统"
    externalId: "PRJ-123"
  - system: "代码托管类系统"
    externalId: "PR #456"
```

---

## 五、对象间关系矩阵

|          | WorkItem | CodeRepo | Artifact | Deployment | Environment | Service | Incident |
|----------|----------|----------|----------|------------|-------------|---------|----------|
| WorkItem | —        | 1:N      | 0:N      | 0:N        | —           | —       | —        |
| CodeRepo | N:1      | —        | 1:N      | 0:N        | —           | N:1     | —        |
| Artifact | 0:N      | N:1      | —        | 1:N        | N:M         | —       | —        |
| Deployment| 0:N     | 0:N      | 1:1      | —          | 1:1         | 1:1     | 0:N      |
| Service  | —        | 1:N      | —        | 1:N        | 1:N         | —       | 1:N      |
| Incident | —        | —        | —        | N:1        | N:M         | N:1     | —        |
