<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useWorkItemStore } from '../stores/workItems'
import { workItemApi } from '../api/workItems'
import { useWorkflowStore } from '../stores/workflows'
import type { StartWorkflowResponse, WorkItemDto, WorkItemStatus, WorkItemType } from '../api/types'

const store = useWorkItemStore()
const workflowStore = useWorkflowStore()
const selectedType = ref<WorkItemType | 'ALL'>('ALL')

const emit = defineEmits<{
  'select-work-item': [id: string]
  'start-workflow': [workItemId: string, response: StartWorkflowResponse]
}>()

const typeConfig: Record<WorkItemType, { label: string; title: string; color: string; detail: string }> = {
  FE: { label: 'FE', title: 'FE', color: '#6366f1', detail: '3 个开发中 · 2 个待评审' },
  US: { label: 'US', title: 'US', color: '#10b981', detail: '4 个处理中 · 2 个待拆解' },
  TASK: { label: 'TASK', title: 'TASK', color: '#f59e0b', detail: '6 个处理中 · 5 个待办' },
  WORK: { label: 'WORK', title: 'WORK', color: '#0ea5e9', detail: '2 个开发中 · 3 个阻塞' },
  BUG: { label: '缺陷', title: '缺陷', color: '#ef4444', detail: '2 个修复中 · 1 个待验证' },
  VULN: { label: '漏洞', title: '漏洞', color: '#991b1b', detail: '1 个处理中 · 1 个待复核' },
}

const statusLabels: Record<WorkItemStatus, string> = {
  BACKLOG: '待办',
  TODO: '计划',
  IN_PROGRESS: '开发',
  IN_REVIEW: '评审',
  DONE: '完成',
}

const workflowStages = ['需求分析', '设计建模', '代码开发', '代码检查', '测试验证', '部署上线']

onMounted(() => {
  store.loadItems()
  workflowStore.loadDefinitions()
})

const typeStats = computed(() => {
  const counts: Record<WorkItemType, number> = {
    FE: 0,
    US: 0,
    TASK: 0,
    WORK: 0,
    BUG: 0,
    VULN: 0,
  }
  for (const item of store.items) {
    counts[item.type] += 1
  }
  return counts
})

const filteredItems = computed(() => {
  if (selectedType.value === 'ALL') return store.items
  return store.items.filter((item) => item.type === selectedType.value)
})

function workflowIndex(item: WorkItemDto) {
  if (item.status === 'BACKLOG') return 0
  if (item.status === 'TODO') return 1
  if (item.status === 'IN_PROGRESS') return 2
  if (item.status === 'IN_REVIEW') return 4
  return 5
}

function handleSelectItem(id: string) {
  emit('select-work-item', id)
}

async function handleStartWorkflow(workItemId: string) {
  const response = await workItemApi.startWorkflow(workItemId, { mode: 'AUTO' })
  emit('start-workflow', workItemId, response)
}
</script>

<template>
  <section class="home-overview" aria-label="首页概览">
    <div class="home-overview__panel">
      <header class="home-overview__header">
        <div>
          <h2>首页概览 · 任务全景</h2>
          <p>按 FE、US、Task、Work、缺陷、漏洞聚合当前企业研发事项</p>
        </div>
      </header>

      <div class="home-overview__stats" aria-label="任务分类指标">
        <button
          v-for="(info, type) in typeConfig"
          :key="type"
          class="home-overview__stat"
          :class="{ 'home-overview__stat--active': selectedType === type }"
          @click="selectedType = selectedType === type ? 'ALL' : type"
        >
          <span class="home-overview__stat-label">{{ info.label }}</span>
          <strong>{{ typeStats[type] }}</strong>
          <em>{{ info.detail }}</em>
        </button>
      </div>

      <div class="home-overview__toolbar">
        <h3>工作项列表</h3>
        <span>{{ selectedType === 'ALL' ? '全部类型' : typeConfig[selectedType].label }}</span>
      </div>

      <div v-if="store.loading" class="home-overview__loading">加载中...</div>
      <div v-else class="home-overview__content">
        <div v-if="filteredItems.length === 0" class="home-overview__empty">暂无工作项</div>
        <button
          v-for="item in filteredItems"
          v-else
          :key="item.id"
          class="work-item-card home-overview__row"
          @click="handleSelectItem(item.id)"
        >
          <div class="home-overview__row-main">
            <span
              class="home-overview__tag"
              :style="{ backgroundColor: typeConfig[item.type].color + '18', color: typeConfig[item.type].color }"
            >
              {{ item.code }}
            </span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.description || '点击卡片后创建会话并注入任务上下文' }}</p>
          </div>
          <div class="home-overview__row-side">
            <span class="home-overview__priority" :class="`is-${item.priority.toLowerCase()}`">
              {{ item.priority === 'URGENT' ? 'Urgent' : item.priority[0] + item.priority.slice(1).toLowerCase() }}
            </span>
            <button class="home-overview__launch" @click.stop="handleStartWorkflow(item.id)">
              开始处理
            </button>
          </div>
          <div class="home-overview__flow" aria-label="工作流进展">
            <span
              v-for="(stage, index) in workflowStages"
              :key="stage"
              class="home-overview__node"
              :class="{
                'home-overview__node--done': index < workflowIndex(item),
                'home-overview__node--active': index === workflowIndex(item),
              }"
            ></span>
            <em>{{ statusLabels[item.status] }}</em>
          </div>
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home-overview {
  height: 100%;
  padding: 18px 22px;
  overflow: hidden;
  background: var(--bg-primary);
}

.home-overview__panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.home-overview__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 80px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--border-color);
}

.home-overview__header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 900;
}

.home-overview__header p {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 650;
}

.home-overview__stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(128px, 1fr));
  gap: 12px;
  padding: 22px;
  overflow-x: auto;
}

.home-overview__stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 128px;
  min-height: 118px;
  padding: 18px 18px 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-primary);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}

.home-overview__stat:hover,
.home-overview__stat--active {
  border-color: var(--border-color-hover);
  background: rgba(59, 130, 246, 0.06);
}

.home-overview__stat-label {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 900;
}

.home-overview__stat strong {
  margin-top: 8px;
  color: var(--text-primary);
  font-size: 32px;
  font-weight: 950;
  line-height: 1;
}

.home-overview__stat em {
  margin-top: 10px;
  color: var(--text-secondary);
  font-style: normal;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.4;
}

.home-overview__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px 12px;
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
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  padding: 0 22px 22px;
  overflow: auto;
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

.home-overview__flow {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(6, minmax(22px, 1fr)) max-content;
  align-items: center;
  gap: 0;
  max-width: 620px;
}

.home-overview__node {
  position: relative;
  height: 24px;
}

.home-overview__node::before {
  content: '';
  position: absolute;
  left: 18px;
  right: 0;
  top: 11px;
  height: 2px;
  background: var(--border-color);
}

.home-overview__node:last-of-type::before {
  display: none;
}

.home-overview__node::after {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  width: 12px;
  height: 12px;
  border: 3px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-card);
}

.home-overview__node--done::after {
  border-color: var(--success);
  background: var(--success);
}

.home-overview__node--active::after {
  border-color: rgba(59, 130, 246, 0.3);
  background: var(--accent-blue);
  box-shadow: 0 0 0 7px var(--glow-blue);
}

.home-overview__flow em {
  color: var(--text-secondary);
  font-style: normal;
  font-size: 14px;
  font-weight: 800;
}

@media (max-width: 1360px) {
  .home-overview__stats {
    grid-template-columns: repeat(3, minmax(128px, 1fr));
  }
}
</style>
