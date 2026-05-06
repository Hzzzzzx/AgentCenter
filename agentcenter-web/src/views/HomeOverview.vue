<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useWorkItemStore } from '../stores/workItems'
import { workItemApi } from '../api/workItems'
import { useWorkflowStore } from '../stores/workflows'
import type { StartWorkflowResponse, WorkItemDto, WorkflowInstanceDto, WorkflowNodeStatus, WorkflowStatus, WorkItemType } from '../api/types'

const store = useWorkItemStore()
const workflowStore = useWorkflowStore()
const selectedType = ref<WorkItemType | 'ALL'>('ALL')
const startingIds = ref<Set<string>>(new Set())
const startErrors = ref<Record<string, string>>({})

const emit = defineEmits<{
  'select-work-item': [id: string]
  'start-workflow': [workItemId: string, response: StartWorkflowResponse]
}>()

const typeOrder: WorkItemType[] = ['FE', 'US', 'TASK', 'WORK', 'BUG', 'VULN']

const typeConfig: Record<WorkItemType, { label: string; title: string; color: string; tint: string }> = {
  FE: { label: 'FE', title: 'FE', color: '#6366f1', tint: '#eef2ff' },
  US: { label: 'US', title: 'US', color: '#10b981', tint: '#ecfdf5' },
  TASK: { label: 'TASK', title: 'TASK', color: '#f59e0b', tint: '#fffbeb' },
  WORK: { label: 'WORK', title: 'WORK', color: '#0ea5e9', tint: '#f0f9ff' },
  BUG: { label: '缺陷', title: '缺陷', color: '#ef4444', tint: '#fef2f2' },
  VULN: { label: '漏洞', title: '漏洞', color: '#991b1b', tint: '#fff1f2' },
}

type StatStage = {
  label: string
  status: WorkflowNodeStatus
  pendingConfirmationCount?: number
}

type StatChipKind = 'running' | 'waiting' | 'blocked' | 'pending' | 'done'

type TypeStatCard = {
  type: WorkItemType
  label: string
  color: string
  tint: string
  total: number
  nodeSummary: string
  progressLabel: string
  completionRate: number
  chips: Array<{ label: string; kind: StatChipKind }>
}

type NodeDistributionBucket = {
  label: string
  priority: number
}

const nodeStatusClass: Record<WorkflowNodeStatus, string> = {
  PENDING: '',
  RUNNING: 'home-overview__node--active',
  WAITING_CONFIRMATION: 'home-overview__node--waiting',
  COMPLETED: 'home-overview__node--done',
  FAILED: 'home-overview__node--failed',
  SKIPPED: 'home-overview__node--skipped',
}

const workflowStatusLabels: Record<WorkflowStatus, string> = {
  PENDING: '待处理',
  RUNNING: '处理中',
  BLOCKED: '已阻塞',
  FAILED: '异常',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

type FlowNode = {
  label: string
  status: WorkflowNodeStatus
  kind: 'start' | 'skill' | 'end'
  dynamicNodeCount?: number
  recoveryCount?: number
  pendingConfirmationCount?: number
  latestSummary?: string | null
}

const defaultStageLabels: Record<WorkItemType, string[]> = {
  FE: ['需求', '方案', '实施', '验证', '归档'],
  US: ['故事', '验收', '拆分', '评审', '归档'],
  TASK: ['理解', '计划', '执行', '验证', '总结'],
  WORK: ['分析', 'Runbook', '执行', '校验', '报告'],
  BUG: ['复现', '根因', '修复', '回归', '关闭'],
  VULN: ['分级', '影响', '修复', '验证', '归档'],
}

onMounted(() => {
  store.loadItems()
  workflowStore.loadDefinitions()
})

const typeStatCards = computed<TypeStatCard[]>(() =>
  typeOrder.map((type) => buildTypeStatCard(type, store.items.filter((item) => item.type === type)))
)

const filteredItems = computed(() => {
  const items = selectedType.value === 'ALL'
    ? store.items
    : store.items.filter((item) => item.type === selectedType.value)
  return [...items].sort((a, b) => itemUpdatedAtValue(b) - itemUpdatedAtValue(a))
})

function itemUpdatedAtValue(item: WorkItemDto) {
  return Date.parse(item.updatedAt ?? item.createdAt ?? '') || 0
}

function workflowFor(item: WorkItemDto): WorkflowInstanceDto | null {
  const cached = workflowStore.instancesByWorkItemId[item.id]
  if (cached) return cached
  if (item.workflowSummary) {
    return {
      id: item.workflowSummary.instanceId,
      workItemId: item.id,
      workflowDefinitionId: '',
      status: item.workflowSummary.status,
      currentNodeInstanceId: item.workflowSummary.currentNodeInstanceId,
      nodes: item.workflowSummary.nodes.map((n) => ({
        id: n.id,
        nodeDefinitionId: '',
        status: n.status,
        inputArtifactId: null,
        outputArtifactId: null,
        agentSessionId: null,
        startedAt: null,
        completedAt: null,
        errorMessage: null,
        definitionName: n.definitionName,
        skillName: n.skillName,
      })),
      startedAt: null,
      completedAt: null,
    } as WorkflowInstanceDto & { nodes: typeof item.workflowSummary.nodes }
  }
  return null
}

function buildTypeStatCard(type: WorkItemType, items: WorkItemDto[]): TypeStatCard {
  const config = typeConfig[type]
  let runningCount = 0
  let waitingCount = 0
  let blockedCount = 0
  let unstartedCount = 0
  let completedCount = 0
  let completedNodeCount = 0
  let totalNodeCount = 0
  const nodeDistribution = new Map<string, { count: number; priority: number }>()

  for (const item of items) {
    const wf = workflowFor(item)
    const stages = statStagesFor(item)
    const hasRunning = stages.some((stage) => stage.status === 'RUNNING')
    const hasWaiting = stages.some((stage) =>
      stage.status === 'WAITING_CONFIRMATION' || (stage.pendingConfirmationCount ?? 0) > 0
    )
    const hasFailed = stages.some((stage) => stage.status === 'FAILED')
      || wf?.status === 'FAILED'
      || wf?.status === 'BLOCKED'
    const isCompleted = wf?.status === 'COMPLETED' || item.status === 'DONE'
    const isUnstarted = !wf && !item.currentWorkflowInstanceId

    if (hasRunning) runningCount += 1
    if (hasWaiting) waitingCount += 1
    if (hasFailed) blockedCount += 1
    if (isUnstarted) unstartedCount += 1
    if (isCompleted) completedCount += 1

    stages.forEach((stage) => {
      if (isNodeComplete(stage.status)) completedNodeCount += 1
      totalNodeCount += 1
    })

    addNodeDistribution(nodeDistribution, currentNodeBucketFor(item, stages, wf))
  }

  const chips = buildStatChips({
    total: items.length,
    runningCount,
    waitingCount,
    blockedCount,
    unstartedCount,
    completedCount,
  })
  const completionRate = totalNodeCount > 0 ? Math.round((completedNodeCount / totalNodeCount) * 100) : 0

  return {
    type,
    label: config.label,
    color: config.color,
    tint: config.tint,
    total: items.length,
    nodeSummary: nodeDistributionSummary(nodeDistribution, items.length),
    progressLabel: `${completionRate}% 节点完成`,
    completionRate,
    chips,
  }
}

function statStagesFor(item: WorkItemDto): StatStage[] {
  const summary = item.workflowSummary
  if (summary?.stages?.length) {
    return summary.stages.map((stage) => ({
      label: stage.name ?? stage.skillName ?? '阶段',
      status: stage.status,
      pendingConfirmationCount: stage.pendingConfirmationCount,
    }))
  }
  if (summary?.nodes.length) {
    return summary.nodes.map((node) => ({
      label: node.definitionName ?? node.skillName ?? '阶段',
      status: node.status,
    }))
  }
  const wf = workflowFor(item)
  if (wf?.nodes.length) {
    return wf.nodes.map((node) => ({
      label: (node as { definitionName?: string | null; skillName?: string | null }).definitionName
        ?? (node as { definitionName?: string | null; skillName?: string | null }).skillName
        ?? '阶段',
      status: node.status,
    }))
  }
  return defaultStageNamesFor(item).map((label) => ({
    label,
    status: 'PENDING',
  }))
}

function buildStatChips(counts: {
  total: number
  runningCount: number
  waitingCount: number
  blockedCount: number
  unstartedCount: number
  completedCount: number
}): Array<{ label: string; kind: StatChipKind }> {
  if (counts.total === 0) {
    return [{ label: '暂无事项', kind: 'pending' }]
  }

  const chips: Array<{ label: string; kind: StatChipKind }> = []
  if (counts.waitingCount > 0) chips.push({ label: `${counts.waitingCount} 待确认`, kind: 'waiting' })
  if (counts.runningCount > 0) chips.push({ label: `${counts.runningCount} 进行中`, kind: 'running' })
  if (counts.blockedCount > 0) chips.push({ label: `${counts.blockedCount} 异常`, kind: 'blocked' })
  if (counts.unstartedCount > 0) chips.push({ label: `${counts.unstartedCount} 未开始`, kind: 'pending' })
  if (counts.completedCount > 0 && chips.length < 2) {
    chips.push({ label: `${counts.completedCount} 已完成`, kind: 'done' })
  }
  if (chips.length === 0 && counts.completedCount > 0) {
    chips.push({ label: `${counts.completedCount} 已完成`, kind: 'done' })
  }
  return chips.slice(0, 2)
}

function currentNodeBucketFor(
  item: WorkItemDto,
  stages: StatStage[],
  wf: WorkflowInstanceDto | null
): NodeDistributionBucket {
  const waitingStage = stages.find((stage) =>
    stage.status === 'WAITING_CONFIRMATION' || (stage.pendingConfirmationCount ?? 0) > 0
  )
  if (waitingStage) return { label: waitingStage.label, priority: 0 }

  const runningStage = stages.find((stage) => stage.status === 'RUNNING')
  if (runningStage) return { label: runningStage.label, priority: 1 }

  const failedStage = stages.find((stage) => stage.status === 'FAILED')
  if (failedStage) return { label: failedStage.label, priority: 2 }

  if (wf?.status === 'COMPLETED' || item.status === 'DONE') {
    return { label: '已完成', priority: 5 }
  }

  if (!wf && !item.currentWorkflowInstanceId) {
    return { label: '未开始', priority: 4 }
  }

  const nextStage = stages.find((stage) => !isNodeComplete(stage.status))
  if (nextStage) return { label: nextStage.label, priority: 3 }

  if (wf?.status === 'FAILED' || wf?.status === 'BLOCKED') {
    return { label: '异常', priority: 2 }
  }

  return { label: workflowStatusLabels[wf?.status ?? 'PENDING'] ?? '待处理', priority: 3 }
}

function addNodeDistribution(
  distribution: Map<string, { count: number; priority: number }>,
  bucket: NodeDistributionBucket
) {
  const current = distribution.get(bucket.label)
  distribution.set(bucket.label, {
    count: (current?.count ?? 0) + 1,
    priority: Math.min(current?.priority ?? bucket.priority, bucket.priority),
  })
}

function nodeDistributionSummary(
  distribution: Map<string, { count: number; priority: number }>,
  total: number
): string {
  if (total === 0) return '等待创建事项'
  return [...distribution.entries()]
    .sort((a, b) => a[1].priority - b[1].priority || b[1].count - a[1].count || a[0].localeCompare(b[0], 'zh-CN'))
    .slice(0, 3)
    .map(([label, item]) => `${label} ${item.count}`)
    .join(' · ')
}

function buildFlowWithAnchors(skillNodes: FlowNode[], wfStatus?: WorkflowStatus | null): FlowNode[] {
  const hasStarted = Boolean(wfStatus)
  const allSkillsCompleted = skillNodes.length > 0 && skillNodes.every((node) =>
    ['COMPLETED', 'SKIPPED'].includes(node.status)
  )
  return [
    { label: '开始', status: hasStarted ? 'COMPLETED' : 'RUNNING', kind: 'start' },
    ...skillNodes,
    {
      label: '结束',
      status: wfStatus === 'COMPLETED' || allSkillsCompleted ? 'COMPLETED' : 'PENDING',
      kind: 'end',
    },
  ]
}

function flowNodes(item: WorkItemDto): FlowNode[] {
  const wf = workflowFor(item)
  const summary = item.workflowSummary
  if (summary?.stages?.length) {
    const stages = summary.stages.map((stage) => ({
      label: stage.name ?? stage.skillName ?? '阶段',
      status: stage.status,
      kind: 'skill' as const,
      dynamicNodeCount: stage.dynamicNodeCount,
      recoveryCount: stage.recoveryCount,
      pendingConfirmationCount: stage.pendingConfirmationCount,
      latestSummary: stage.latestSummary,
    }))
    return buildFlowWithAnchors(stages, summary.status)
  }
  if (wf && wf.nodes.length > 0) {
    const stages = wf.nodes.map((n) => ({
      label: (n as { definitionName?: string | null; skillName?: string | null }).definitionName
        ?? (n as { definitionName?: string | null; skillName?: string | null }).skillName
        ?? '阶段',
      status: n.status as WorkflowNodeStatus,
      kind: 'skill' as const,
    }))
    return buildFlowWithAnchors(stages, wf.status)
  }
  return buildFlowWithAnchors(
    defaultStageNamesFor(item).map((label) => ({
      label,
      status: 'PENDING' as WorkflowNodeStatus,
      kind: 'skill' as const,
    })),
    null,
  )
}

function defaultStageNamesFor(item: WorkItemDto): string[] {
  const definition = workflowStore.definitions.find(
    (candidate) => candidate.workItemType === item.type && candidate.status === 'ENABLED' && candidate.isDefault
  ) ?? workflowStore.definitions.find(
    (candidate) => candidate.workItemType === item.type && candidate.status === 'ENABLED'
  )
  if (definition?.nodes.length) {
    return definition.nodes.map((node) => node.name)
  }
  return defaultStageLabels[item.type]
}

function isNodeComplete(status: WorkflowNodeStatus) {
  return status === 'COMPLETED' || status === 'SKIPPED'
}

function isConnectorDone(left: FlowNode, right: FlowNode) {
  return isNodeComplete(left.status) && isNodeComplete(right.status)
}

function stageMeta(node: FlowNode): string {
  const parts: string[] = []
  if (node.dynamicNodeCount) parts.push(`${node.dynamicNodeCount} 动态`)
  if (node.recoveryCount) parts.push(`${node.recoveryCount} 修复`)
  if (node.pendingConfirmationCount) parts.push(`${node.pendingConfirmationCount} 确认`)
  return parts.join(' · ')
}

function launchLabel(item: WorkItemDto): string {
  if (startingIds.value.has(item.id)) return '启动中'
  const wf = workflowFor(item)
  if (!wf && !item.currentWorkflowInstanceId) return '开始处理'
  if (!wf) return '开始处理'
  if (hasStageStatus(item, 'WAITING_CONFIRMATION')) return '待确认'
  if (hasStageStatus(item, 'RUNNING')) return '处理中'
  return workflowStatusLabels[wf.status] ?? '开始处理'
}

function launchDisabled(item: WorkItemDto): boolean {
  if (startingIds.value.has(item.id)) return true
  const wf = workflowFor(item)
  if (!wf) return false
  return wf.status !== 'FAILED' && wf.status !== 'CANCELLED'
}

function hasStageStatus(item: WorkItemDto, status: WorkflowNodeStatus) {
  return item.workflowSummary?.stages?.some((stage) => stage.status === status)
    || item.workflowSummary?.nodes?.some((node) => node.status === status)
    || false
}

function handleSelectItem(id: string) {
  emit('select-work-item', id)
}

async function handleStartWorkflow(workItemId: string) {
  if (startingIds.value.has(workItemId)) return
  startingIds.value = new Set([...startingIds.value, workItemId])
  const { [workItemId]: _removed, ...restErrors } = startErrors.value
  startErrors.value = restErrors
  try {
    const response = await workItemApi.startWorkflow(workItemId, { mode: 'AUTO' })
    if (response.workflowInstance) {
      workflowStore.upsertInstance(response.workflowInstance)
    }
    emit('start-workflow', workItemId, response)
    await store.loadItems()
  } catch (error) {
    const message = error instanceof Error ? error.message : '启动工作流失败'
    startErrors.value = { ...startErrors.value, [workItemId]: message }
  } finally {
    const next = new Set(startingIds.value)
    next.delete(workItemId)
    startingIds.value = next
  }
}
</script>

<template>
  <section class="home-overview" aria-label="首页概览">
    <div class="home-overview__panel">
      <div class="home-overview__stats" aria-label="任务分类指标">
        <button
          v-for="card in typeStatCards"
          :key="card.type"
          class="home-overview__stat"
          :class="{ 'home-overview__stat--active': selectedType === card.type }"
          :style="{ '--stat-color': card.color, '--stat-tint': card.tint }"
          @click="selectedType = selectedType === card.type ? 'ALL' : card.type"
        >
          <span class="home-overview__stat-head">
            <span class="home-overview__stat-mark">{{ card.label }}</span>
            <span class="home-overview__stat-progress">{{ card.progressLabel }}</span>
          </span>
          <span class="home-overview__stat-main">
            <strong>{{ card.total }}</strong>
            <span>事项</span>
          </span>
          <span class="home-overview__stat-chips">
            <span
              v-for="chip in card.chips"
              :key="chip.label"
              class="home-overview__stat-chip"
              :class="`home-overview__stat-chip--${chip.kind}`"
            >
              {{ chip.label }}
            </span>
          </span>
          <span class="home-overview__stat-node" :title="card.nodeSummary">
            <span>节点分布</span>
            <em>{{ card.nodeSummary }}</em>
          </span>
          <span class="home-overview__stat-track" aria-hidden="true">
            <span :style="{ width: `${card.completionRate}%` }"></span>
          </span>
        </button>
      </div>

      <div class="home-overview__toolbar">
        <h3>工作项列表</h3>
        <span>{{ selectedType === 'ALL' ? '全部类型' : typeConfig[selectedType].label }}</span>
      </div>

      <div v-if="store.loading" class="home-overview__loading">加载中...</div>
      <div v-else class="home-overview__content">
        <div v-if="filteredItems.length === 0" class="home-overview__empty">暂无工作项</div>
        <div
          v-for="item in filteredItems"
          v-else
          :key="item.id"
          class="work-item-card home-overview__row"
          role="button"
          tabindex="0"
          @click="handleSelectItem(item.id)"
          @keydown.enter="handleSelectItem(item.id)"
          @keydown.space.prevent="handleSelectItem(item.id)"
        >
          <div class="home-overview__row-main">
            <span
              class="home-overview__tag"
              :style="{ backgroundColor: typeConfig[item.type].color + '18', color: typeConfig[item.type].color }"
            >
              {{ item.code }}
            </span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.description || '' }}</p>
          </div>
          <div class="home-overview__row-side">
            <span class="home-overview__priority" :class="`is-${item.priority.toLowerCase()}`">
              {{ item.priority === 'URGENT' ? 'Urgent' : item.priority[0] + item.priority.slice(1).toLowerCase() }}
            </span>
            <button
              class="home-overview__launch"
              :disabled="launchDisabled(item)"
              @click.stop="handleStartWorkflow(item.id)"
            >
              {{ launchLabel(item) }}
            </button>
          </div>
          <div class="home-overview__flow" aria-label="工作流进展">
            <template v-for="(node, nIndex) in flowNodes(item)" :key="`${item.id}-${nIndex}`">
              <span
                class="home-overview__stage"
                :title="[node.label, stageMeta(node)].filter(Boolean).join(' · ')"
              >
                <span
                  class="home-overview__node"
                  :class="nodeStatusClass[node.status]"
                ></span>
              </span>
              <span
                v-if="nIndex < flowNodes(item).length - 1"
                class="home-overview__node-line"
                :class="{ 'home-overview__node-line--done': isConnectorDone(node, flowNodes(item)[nIndex + 1]) }"
              ></span>
            </template>
            <em>{{ launchLabel(item) }}</em>
          </div>
          <div v-if="startErrors[item.id]" class="home-overview__error">
            {{ startErrors[item.id] }}
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home-overview {
  display: flex;
  height: 100%;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  background: var(--bg-primary);
}

.home-overview__panel {
  container-type: inline-size;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 0;
}

.home-overview__stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(132px, 1fr));
  gap: 8px;
  padding: 10px 18px 8px;
  overflow-x: auto;
  overflow-y: hidden;
  border-bottom: 1px solid var(--border-color);
  overscroll-behavior-x: contain;
  scrollbar-gutter: stable;
}

.home-overview__stats::-webkit-scrollbar {
  height: 8px;
}

.home-overview__stats::-webkit-scrollbar-track {
  background: transparent;
}

.home-overview__stats::-webkit-scrollbar-thumb {
  border: 2px solid var(--bg-card);
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.32);
}

.home-overview__stat {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-rows: 22px 38px 18px 3px;
  grid-template-areas:
    "head head"
    "main chips"
    "node node"
    "track track";
  gap: 4px 8px;
  min-width: 0;
  min-height: 92px;
  padding: 9px 11px 8px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.home-overview__stat::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--stat-color);
  content: '';
  opacity: 0.82;
}

.home-overview__stat:hover {
  border-color: rgba(100, 116, 139, 0.45);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.home-overview__stat--active {
  border-color: var(--stat-color);
  background: linear-gradient(180deg, #ffffff 0%, var(--stat-tint) 100%);
  box-shadow: inset 0 0 0 1px var(--stat-color), 0 12px 26px rgba(15, 23, 42, 0.08);
}

.home-overview__stat-head,
.home-overview__stat-main,
.home-overview__stat-chips,
.home-overview__stat-node,
.home-overview__stat-track {
  position: relative;
  z-index: 1;
}

.home-overview__stat-head {
  grid-area: head;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.home-overview__stat-mark {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 88px;
  min-height: 20px;
  padding: 0 7px;
  overflow: hidden;
  border-radius: 7px;
  background: var(--stat-tint);
  color: var(--stat-color);
  font-size: 12px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-overview__stat-progress {
  min-width: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 9px;
  font-weight: 900;
  line-height: 1.2;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-overview__stat-main {
  grid-area: main;
  display: flex;
  align-items: flex-end;
  gap: 5px;
  min-width: 0;
}

.home-overview__stat-main strong {
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 950;
  line-height: 0.95;
}

.home-overview__stat-main span {
  margin-bottom: 2px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 850;
}

.home-overview__stat-chips {
  grid-area: chips;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-content: center;
  align-self: center;
  min-width: 0;
  min-height: 22px;
}

.home-overview__stat-chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-height: 20px;
  padding: 0 6px;
  overflow: hidden;
  border-radius: 6px;
  background: #eef2f7;
  color: #475569;
  font-size: 10px;
  font-weight: 850;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-overview__stat-chip--running {
  background: #dbeafe;
  color: #1d4ed8;
}

.home-overview__stat-chip--waiting {
  background: #fef3c7;
  color: #b45309;
}

.home-overview__stat-chip--blocked {
  background: #fee2e2;
  color: #b91c1c;
}

.home-overview__stat-chip--pending {
  background: #f1f5f9;
  color: #475569;
}

.home-overview__stat-chip--done {
  background: #d1fae5;
  color: #047857;
}

.home-overview__stat-node {
  grid-area: node;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.home-overview__stat-node > span {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: 9px;
  font-weight: 900;
}

.home-overview__stat-node em {
  display: block;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 11px;
  font-style: normal;
  font-weight: 760;
  line-height: 1.35;
  overflow-wrap: anywhere;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-overview__stat-track {
  grid-area: track;
  align-self: end;
  height: 3px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.home-overview__stat-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--stat-color);
}

.home-overview__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 18px 8px;
}

.home-overview__toolbar h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 900;
}

.home-overview__toolbar span {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 750;
}

.home-overview__loading,
.home-overview__empty {
  display: grid;
  place-items: center;
  min-height: 220px;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 750;
}

.home-overview__content {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  padding: 0 18px 18px;
  overflow-x: hidden;
  overflow-y: scroll;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.home-overview__content::-webkit-scrollbar {
  width: 10px;
}

.home-overview__content::-webkit-scrollbar-track {
  background: transparent;
}

.home-overview__content::-webkit-scrollbar-thumb {
  border: 3px solid var(--bg-card);
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.35);
}

.home-overview__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) max-content;
  gap: 12px 16px;
  width: 100%;
  padding: 16px 18px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-primary);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}

.home-overview__row:hover {
  border-color: var(--border-color-hover);
  background: var(--bg-card-hover);
}

.home-overview__row-main {
  min-width: 0;
}

.home-overview__tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 950;
}

.home-overview__row-main strong {
  display: block;
  margin-top: 8px;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 17px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-overview__row-main p {
  margin-top: 6px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-overview__row-side {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.home-overview__priority {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 900;
}

.home-overview__priority.is-high,
.home-overview__priority.is-urgent {
  background: #ffedd5;
  color: #ea580c;
}

.home-overview__priority.is-low {
  background: #e0f2fe;
  color: #0284c7;
}

.home-overview__launch {
  min-height: 28px;
  padding: 0 12px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--accent-blue);
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.home-overview__launch:hover {
  background: var(--accent-blue);
  color: #ffffff;
}

.home-overview__launch:disabled {
  cursor: wait;
  opacity: 0.68;
}

.home-overview__error {
  grid-column: 1 / -1;
  color: #dc2626;
  font-size: 12px;
  font-weight: 700;
}

.home-overview__flow {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 0;
  margin-top: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.home-overview__stage {
  display: inline-flex;
  align-items: center;
  min-width: 12px;
}

.home-overview__node {
  display: inline-flex;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  border: 2px solid var(--border-color);
  background: transparent;
  flex-shrink: 0;
}

.home-overview__node-line {
  width: 28px;
  height: 2px;
  flex: 0 0 28px;
  background: var(--border-color);
}

.home-overview__flow em {
  margin-left: 24px;
  color: var(--text-secondary);
  font-size: 13px;
  font-style: normal;
  font-weight: 800;
}

.home-overview__node--done {
  background: var(--success);
  border-color: var(--success);
}

.home-overview__node-line--done {
  background: var(--success);
}

.home-overview__node--active {
  background: var(--accent-blue);
  border-color: rgba(59, 130, 246, 0.3);
  animation: node-pulse 2s ease-in-out infinite;
}

@keyframes node-pulse {
  0%, 100% { box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.15); }
  50% { box-shadow: 0 0 0 8px rgba(59, 130, 246, 0.25); }
}

.home-overview__node--waiting {
  background: #f59e0b;
  border-color: #f59e0b;
}

.home-overview__node--failed {
  background: #ef4444;
  border-color: #ef4444;
}

.home-overview__node--skipped {
  background: #9ca3af;
  border-color: #9ca3af;
  opacity: 0.5;
}

</style>
