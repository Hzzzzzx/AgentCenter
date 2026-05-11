<script setup lang="ts">
import { onMounted, ref, watch, nextTick } from 'vue'
import { useConfirmationStore } from '../../stores/confirmations'
import { useWorkItemStore } from '../../stores/workItems'
import { useWorkItemWorkflowProjectionStore } from '../../stores/workItemWorkflowProjection'
import ConfirmationCard from './ConfirmationCard.vue'

const store = useConfirmationStore()
const workItemStore = useWorkItemStore()
const workflowProjectionStore = useWorkItemWorkflowProjectionStore()

const emit = defineEmits<{
  handle: [id: string]
  resolved: [id: string]
  rejected: [id: string]
  changed: [workItemId?: string | null]
}>()

const selectedCardRef = ref<HTMLElement | null>(null)

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
    workItemId ? workflowProjectionStore.syncWorkItem(workItemId) : workItemStore.loadItems(),
  ])
}

async function handleResolved(id: string, workItemId?: string | null) {
  emit('resolved', id)
  await refreshAfterDecision(workItemId)
}

async function handleRejected(id: string, workItemId?: string | null) {
  emit('rejected', id)
  await refreshAfterDecision(workItemId)
}

watch(() => store.currentConfirmation, async (confirmation) => {
  if (confirmation) {
    await nextTick()
    selectedCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }
})
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
      <div
        v-for="item in store.pendingConfirmations"
        :key="item.id"
        :ref="el => { if (item.id === store.currentConfirmation?.id) selectedCardRef = el as HTMLElement }"
        class="confirmation-panel__card-wrapper"
        :class="{ 'confirmation-panel__card-wrapper--selected': item.id === store.currentConfirmation?.id }"
      >
        <ConfirmationCard
          :confirmation="item"
          :work-item="workItemFor(item.workItemId)"
          @handle="emit('handle', $event)"
          @resolved="(id) => handleResolved(id, item.workItemId)"
          @rejected="(id) => handleRejected(id, item.workItemId)"
        />
      </div>
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

.confirmation-panel__card-wrapper {
  border-radius: 8px;
  border: 2px solid transparent;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.confirmation-panel__card-wrapper--selected {
  border-color: var(--accent-blue);
  box-shadow: 0 0 0 2px var(--focus-ring);
}
</style>
