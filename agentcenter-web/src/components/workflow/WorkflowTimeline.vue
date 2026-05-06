<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowInstanceDto, WorkflowDefinitionDto, WorkflowNodeStatus } from '../../api/types'

const props = defineProps<{
  instance: WorkflowInstanceDto | null
  definition: WorkflowDefinitionDto | null
}>()

interface TimelineNode {
  id: string
  name: string
  skillName: string
  status: WorkflowNodeStatus
  isCurrent: boolean
  orderNo: number
}

const nodes = computed<TimelineNode[]>(() => {
  if (!props.definition) return []

  return props.definition.nodes.map((defNode) => {
    const instanceNode = props.instance?.nodes.find(
      (n) => n.nodeDefinitionId === defNode.id
    )
    return {
      id: defNode.id,
      name: defNode.name,
      skillName: defNode.skillName,
      status: instanceNode?.status ?? 'PENDING',
      isCurrent: instanceNode?.id === props.instance?.currentNodeInstanceId,
      orderNo: defNode.orderNo,
    }
  }).sort((a, b) => a.orderNo - b.orderNo)
})

const statusColors: Record<WorkflowNodeStatus, string> = {
  PENDING: '#94a3b8',
  RUNNING: '#3b82f6',
  WAITING_CONFIRMATION: '#f59e0b',
  FAILED: '#ef4444',
  COMPLETED: '#10b981',
  SKIPPED: '#94a3b8',
}

const statusLabels: Record<WorkflowNodeStatus, string> = {
  PENDING: '待执行',
  RUNNING: '执行中',
  WAITING_CONFIRMATION: '待确认',
  FAILED: '失败',
  COMPLETED: '已完成',
  SKIPPED: '已跳过',
}
</script>

<template>
  <div class="workflow-timeline">
    <div v-if="!definition" class="workflow-timeline__empty">
      暂无工作流
    </div>
    <div v-else class="workflow-timeline__nodes">
      <div
        v-for="(node, index) in nodes"
        :key="node.id"
        class="workflow-timeline__node"
        :class="{
          'workflow-timeline__node--current': node.isCurrent,
          'workflow-timeline__node--skipped': node.status === 'SKIPPED',
        }"
      >
        <div class="workflow-timeline__connector">
          <div
            class="workflow-timeline__dot"
            :class="{ 'workflow-timeline__dot--pulse': node.status === 'RUNNING' }"
            :style="{ backgroundColor: statusColors[node.status] }"
          />
          <div
            v-if="index < nodes.length - 1"
            class="workflow-timeline__line"
            :style="{
              backgroundColor: node.status === 'COMPLETED' ? statusColors.COMPLETED : '#e2e8f0'
            }"
          />
        </div>
        <div class="workflow-timeline__content">
          <div class="workflow-timeline__name">
            {{ node.name }}
            <span
              v-if="node.isCurrent"
              class="workflow-timeline__current-badge"
            >
              当前
            </span>
          </div>
          <div class="workflow-timeline__skill">{{ node.skillName }}</div>
          <div
            class="workflow-timeline__status"
            :style="{ color: statusColors[node.status] }"
          >
            {{ statusLabels[node.status] }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.workflow-timeline {
  padding: 8px 0;
}

.workflow-timeline__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: var(--text-secondary);
  font-size: 13px;
}

.workflow-timeline__nodes {
  display: flex;
  flex-direction: column;
}

.workflow-timeline__node {
  display: flex;
  gap: 12px;
  min-height: 60px;
}

.workflow-timeline__connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 16px;
  flex-shrink: 0;
}

.workflow-timeline__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 4px;
}

.workflow-timeline__dot--pulse {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4); }
  50% { opacity: 0.8; box-shadow: 0 0 0 6px rgba(59, 130, 246, 0); }
}

.workflow-timeline__line {
  width: 2px;
  flex: 1;
  margin: 2px 0;
}

.workflow-timeline__content {
  flex: 1;
  padding-bottom: 16px;
}

.workflow-timeline__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.workflow-timeline__current-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background-color: rgba(59, 130, 246, 0.12);
  color: var(--accent-blue);
  font-weight: 600;
}

.workflow-timeline__skill {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.workflow-timeline__status {
  font-size: 11px;
  margin-top: 2px;
}

.workflow-timeline__node--skipped .workflow-timeline__name,
.workflow-timeline__node--skipped .workflow-timeline__skill {
  text-decoration: line-through;
  opacity: 0.5;
}
</style>
