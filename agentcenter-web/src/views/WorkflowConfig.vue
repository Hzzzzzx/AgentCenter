<script setup lang="ts">
import { onMounted } from 'vue'
import { useWorkflowStore } from '../stores/workflows'

const workflowStore = useWorkflowStore()

onMounted(() => {
  workflowStore.loadDefinitions()
})
</script>

<template>
  <div class="workflow-config">
    <div class="workflow-config__header">
      <h3 class="workflow-config__title">工作流定义</h3>
    </div>

    <div v-if="workflowStore.loading" class="workflow-config__loading">加载中...</div>
    <div v-else-if="workflowStore.definitions.length === 0" class="workflow-config__empty">
      暂无工作流定义
    </div>
    <div v-else class="workflow-config__list">
      <div
        v-for="def in workflowStore.definitions"
        :key="def.id"
        class="workflow-definition"
      >
        <div class="workflow-definition__header">
          <span class="workflow-definition__name">{{ def.name }}</span>
          <span class="workflow-definition__type">{{ def.workItemType }}</span>
          <span v-if="def.isDefault" class="workflow-definition__default">默认</span>
        </div>
        <table class="workflow-definition__table">
          <thead>
            <tr>
              <th>序号</th>
              <th>节点名称</th>
              <th>技能</th>
              <th>输出类型</th>
              <th>需确认</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="node in def.nodes" :key="node.id">
              <td>{{ node.orderNo }}</td>
              <td>{{ node.name }}</td>
              <td>{{ node.skillName }}</td>
              <td>{{ node.outputArtifactType }}</td>
              <td>{{ node.requiredConfirmation ? '是' : '否' }}</td>
            </tr>
          </tbody>
        </table>
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
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
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

.workflow-definition__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.workflow-definition__table th {
  text-align: left;
  padding: 8px 16px;
  font-weight: 500;
  color: var(--text-secondary);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  background-color: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
}

.workflow-definition__table td {
  padding: 8px 16px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
}

.workflow-definition__table tr:last-child td {
  border-bottom: none;
}
</style>
