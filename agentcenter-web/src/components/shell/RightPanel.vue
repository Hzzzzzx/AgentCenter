<script setup lang="ts">
import { ref } from 'vue'
import ConfirmationPanel from '../confirmation/ConfirmationPanel.vue'

interface Props {
  collapsed?: boolean
}

withDefaults(defineProps<Props>(), {
  collapsed: false,
})

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  'handle-confirmation': [id: string]
}>()

function handleConfirmation(id: string) {
  emit('handle-confirmation', id)
}

const activeTab = ref<'confirmations' | 'details'>('confirmations')

const tabs = [
  { id: 'confirmations' as const, label: '待确认' },
  { id: 'details' as const, label: '详情' },
]
</script>

<template>
  <aside class="right-panel" :class="{ 'right-panel--collapsed': collapsed }">
    <div v-if="!collapsed" class="right-panel__content">
      <div class="right-panel__tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="right-panel__tab"
          :class="{ 'right-panel__tab--active': activeTab === tab.id }"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="right-panel__body">
        <ConfirmationPanel v-if="activeTab === 'confirmations'" @handle="handleConfirmation" />
        <div v-else class="right-panel__placeholder">
          <span>暂无详情</span>
        </div>
      </div>
    </div>

    <button
      class="right-panel__toggle"
      :title="collapsed ? '展开面板' : '收起面板'"
      @click="emit('update:collapsed', !collapsed)"
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        :style="{ transform: collapsed ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }"
      >
        <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </button>
  </aside>
</template>

<style scoped>
.right-panel {
  display: flex;
  flex-direction: row-reverse;
  height: 100%;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-color);
  overflow: hidden;
}

.right-panel--collapsed {
}

.right-panel__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.right-panel__tabs {
  display: flex;
  padding: 12px 16px 0;
  gap: 4px;
  border-bottom: 1px solid var(--border-color);
}

.right-panel__tab {
  position: relative;
  height: auto;
  padding: 6px 12px;
  border: none;
  border-radius: 6px 6px 0 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s;
}

.right-panel__tab:hover {
  color: var(--text-primary);
}

.right-panel__tab--active {
  color: var(--accent-blue);
  background: var(--bg-primary);
}

.right-panel__tab--active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background-color: var(--accent-blue);
}

.right-panel__body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.right-panel__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  font-size: 13px;
}

.right-panel__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-muted);
  cursor: pointer;
  flex-shrink: 0;
  align-self: flex-start;
}

.right-panel__toggle:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}
</style>
