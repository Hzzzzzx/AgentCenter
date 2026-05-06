<script setup lang="ts">
import type { WorkItemDto } from '../../api/types'
import WorkItemCard from './WorkItemCard.vue'

defineProps<{
  items: WorkItemDto[]
}>()

const emit = defineEmits<{
  select: [id: string]
}>()
</script>

<template>
  <div class="work-item-list">
    <div v-if="items.length === 0" class="work-item-list__empty">
      暂无工作项
    </div>
    <div v-else class="work-item-list__grid">
      <WorkItemCard
        v-for="item in items"
        :key="item.id"
        :work-item="item"
        @click="emit('select', item.id)"
      />
    </div>
  </div>
</template>

<style scoped>
.work-item-list__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  color: var(--text-secondary);
  font-size: 14px;
}

.work-item-list__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
</style>
