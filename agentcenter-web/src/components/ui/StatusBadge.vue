<script setup lang="ts">
interface Props {
  status: 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE'
  size?: 'sm' | 'md'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'sm',
})

const statusColorMap: Record<string, string> = {
  BACKLOG: '#94a3b8',
  TODO: '#3b82f6',
  IN_PROGRESS: '#8b5cf6',
  IN_REVIEW: '#f59e0b',
  DONE: '#10b981',
}

const statusLabelMap: Record<string, string> = {
  BACKLOG: '待规划',
  TODO: '待办',
  IN_PROGRESS: '进行中',
  IN_REVIEW: '审核中',
  DONE: '已完成',
}

const color = statusColorMap[props.status] ?? '#94a3b8'
const label = statusLabelMap[props.status] ?? props.status
</script>

<template>
  <span
    class="status-badge"
    :class="[`status-badge--${size}`]"
    :style="{ '--badge-color': color }"
  >
    <span class="status-badge__dot" />
    <span class="status-badge__label">{{ label }}</span>
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--badge-color) 10%, transparent);
  color: var(--badge-color);
  font-weight: 500;
  white-space: nowrap;
}

.status-badge--sm {
  padding: 2px 8px;
  font-size: 11px;
  line-height: 16px;
}

.status-badge--md {
  padding: 4px 12px;
  font-size: 13px;
  line-height: 20px;
}

.status-badge__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--badge-color);
  flex-shrink: 0;
}

.status-badge__label {
  line-height: inherit;
}
</style>
