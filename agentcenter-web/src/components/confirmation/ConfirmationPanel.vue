<script setup lang="ts">
import { onMounted } from 'vue'
import { useConfirmationStore } from '../../stores/confirmations'
import ConfirmationCard from './ConfirmationCard.vue'

const store = useConfirmationStore()

const emit = defineEmits<{
  handle: [id: string]
}>()

onMounted(() => {
  store.loadPending()
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
      <ConfirmationCard
        v-for="item in store.pendingConfirmations"
        :key="item.id"
        :confirmation="item"
        @handle="emit('handle', $event)"
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
