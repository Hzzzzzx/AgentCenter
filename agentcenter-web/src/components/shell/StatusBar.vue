<script setup lang="ts">
interface Props {
  systemStatus?: 'normal' | 'degraded' | 'down'
  toolConnections?: number
  agentsOnline?: number
}

withDefaults(defineProps<Props>(), {
  systemStatus: 'normal',
  toolConnections: 0,
  agentsOnline: 0,
})
</script>

<template>
  <footer class="status-bar">
    <div class="status-bar__left">
      <span
        class="status-bar__indicator"
        :class="{
          'status-bar__indicator--normal': systemStatus === 'normal',
          'status-bar__indicator--degraded': systemStatus === 'degraded',
          'status-bar__indicator--down': systemStatus === 'down',
        }"
      />
      <span class="status-bar__text">
        {{ systemStatus === 'normal' ? '系统正常' : systemStatus === 'degraded' ? '部分服务异常' : '系统故障' }}
      </span>
    </div>

    <div class="status-bar__center">
      <span class="status-bar__text">工具连接: {{ toolConnections }}</span>
    </div>

    <div class="status-bar__right">
      <span class="status-bar__text">智能体在线: {{ agentsOnline }}</span>
    </div>
  </footer>
</template>

<style scoped>
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--statusbar-height);
  padding: 0 20px;
  background-color: var(--bg-tertiary);
  border-top: 1px solid var(--border-color);
}

.status-bar__left,
.status-bar__center,
.status-bar__right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-bar__text {
  font-size: 11px;
  color: var(--text-secondary);
}

.status-bar__indicator {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-bar__indicator--normal {
  background-color: var(--success);
}

.status-bar__indicator--degraded {
  background-color: var(--warning);
}

.status-bar__indicator--down {
  background-color: var(--error);
}
</style>
