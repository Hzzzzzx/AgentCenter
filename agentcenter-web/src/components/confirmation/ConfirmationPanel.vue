<script setup lang="ts">
import { onMounted } from 'vue'
import { useConfirmationStore } from '../../stores/confirmations'
import { useWorkItemStore } from '../../stores/workItems'
import ConfirmationCard from './ConfirmationCard.vue'

const store = useConfirmationStore()
const workItemStore = useWorkItemStore()

const emit = defineEmits<{
  handle: [id: string]
  resolved: [id: string]
  rejected: [id: string]
  changed: [workItemId?: string | null]
}>()

onMounted(() => {
  store.loadPending()
  if (workItemStore.items.length === 0) {
    workItemStore.loadItems()
  }
})

function workItemFor(id: string | null) {
  if (!id) return null
  return workItemStore.items.find((item) => item.id === id) ?? null
}

async function refreshAfterDecision(workItemId?: string | null) {
  emit('changed', workItemId)
  await Promise.all([
    store.loadPending(),
    workItemId ? workItemStore.refreshItem(workItemId) : workItemStore.loadItems(),
  ])
}

async function handleResolved(id: string) {
  const workItemId = store.pendingConfirmations.find((item) => item.id === id)?.workItemId
  emit('resolved', id)
  await refreshAfterDecision(workItemId)
}

async function handleRejected(id: string) {
  const workItemId = store.pendingConfirmations.find((item) => item.id === id)?.workItemId
  emit('rejected', id)
  await refreshAfterDecision(workItemId)
}
</script>

<template>
  <div class="confirmation-panel">
    <div v-if="store.loading" class="confirmation-panel__loading">
      加载中...
    </div>
    <div v-else-if="store.pendingConfirmations.length === 0" class="confirmation-panel__empty">
      无待确认事项
    </div>
    <div v-else class="confirmation-panel__list">
      <ConfirmationCard
        v-for="item in store.pendingConfirmations"
        :key="item.id"
        :confirmation="item"
        :work-item="workItemFor(item.workItemId)"
        @handle="emit('handle', $event)"
        @resolved="handleResolved"
        @rejected="handleRejected"
      />
    </div>
  </div>
</template>

<style scoped>
.confirmation-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.confirmation-panel__loading,
.confirmation-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-secondary);
  font-size: 13px;
}

.confirmation-panel__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}
</style>
