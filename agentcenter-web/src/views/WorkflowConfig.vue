<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useWorkflowStore } from '../stores/workflows'
import type { WorkflowNodeDefinitionDto } from '../api/types'

const workflowStore = useWorkflowStore()

const enabledDefinitions = computed(() =>
  workflowStore.definitions.filter((definition) => definition.status === 'ENABLED')
)

onMounted(() => {
  workflowStore.loadDefinitions()
})

function recommendedSkills(node: WorkflowNodeDefinitionDto): string {
  if (node.recommendedSkillNamesJson) {
    try {
      const parsed = JSON.parse(node.recommendedSkillNamesJson)
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed.join('、')
      }
    } catch {
      return node.skillName
    }
  }
  return node.skillName
}

function inputPolicyLabel(policy: string): string {
  const labels: Record<string, string> = {
    WORK_ITEM_ONLY: '工作项',
    PREVIOUS_ARTIFACT: '上游产物',
    MERGED_CONTEXT: '合并上下文',
  }
  return labels[policy] ?? policy
}
</script>

<template>
  <div class="workflow-config">
    <div class="workflow-config__header">
      <div>
        <h3 class="workflow-config__title">编排策略</h3>
        <p class="workflow-config__subtitle">按任务类型维护稳定阶段、推荐 Skill 和确认门禁</p>
      </div>
    </div>

    <div v-if="workflowStore.loading" class="workflow-config__loading">加载中...</div>
    <div v-else-if="enabledDefinitions.length === 0" class="workflow-config__empty">
      暂无编排策略
    </div>
    <div v-else class="workflow-config__list">
      <div
        v-for="def in enabledDefinitions"
        :key="def.id"
        class="workflow-definition"
      >
        <div class="workflow-definition__header">
          <span class="workflow-definition__name">{{ def.name }}</span>
          <span class="workflow-definition__type">{{ def.workItemType }}</span>
          <span v-if="def.isDefault" class="workflow-definition__default">默认</span>
          <span class="workflow-definition__version">v{{ def.versionNo }}</span>
        </div>
        <div class="workflow-definition__stages">
          <div
            v-for="node in def.nodes"
            :key="node.id"
            class="workflow-stage"
          >
            <div class="workflow-stage__order">{{ node.orderNo }}</div>
            <div class="workflow-stage__main">
              <div class="workflow-stage__topline">
                <strong>{{ node.name }}</strong>
                <span>{{ node.stageKey || node.nodeKey }}</span>
              </div>
              <p>{{ node.stageGoal || node.name }}</p>
              <div class="workflow-stage__meta">
                <span>Skill：{{ recommendedSkills(node) }}</span>
                <span>输入：{{ inputPolicyLabel(node.inputPolicy) }}</span>
                <span>输出：{{ node.outputArtifactType }}</span>
                <span>{{ node.requiredConfirmation ? '需确认' : '自动推进' }}</span>
                <span>{{ node.allowDynamicActions !== false ? '允许动态动作' : '固定阶段' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.workflow-config {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px;
  overflow-y: auto;
}

.workflow-config__header {
  margin-bottom: 16px;
}

.workflow-config__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.workflow-config__subtitle {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.workflow-config__loading,
.workflow-config__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-secondary);
  font-size: 14px;
}

.workflow-config__list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workflow-definition {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.workflow-definition__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
}

.workflow-definition__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.workflow-definition__type {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: rgba(59, 130, 246, 0.1);
  color: var(--accent-blue);
}

.workflow-definition__default {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: rgba(16, 185, 129, 0.1);
  color: var(--success);
}

.workflow-definition__version {
  margin-left: auto;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
}

.workflow-definition__stages {
  display: flex;
  flex-direction: column;
}

.workflow-stage {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
}

.workflow-stage:last-child {
  border-bottom: none;
}

.workflow-stage__order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.workflow-stage__main {
  min-width: 0;
}

.workflow-stage__topline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-stage__topline strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 800;
}

.workflow-stage__topline span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
}

.workflow-stage__main p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.workflow-stage__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.workflow-stage__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
}
</style>
