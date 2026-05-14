<script setup lang="ts">
interface Props {
  systemStatus?: 'normal' | 'degraded' | 'down'
  toolConnections?: number
  agentsOnline?: number
  runtimeConnected?: boolean
  runtimeConnectionLabel?: string
  resourceLabel?: string
  resourceSyncLabel?: string
  resourceReloadRequired?: boolean
  resourceTooltip?: string
  agentStateDot?: string
  agentStateText?: string
  agentStateReason?: string
}

withDefaults(defineProps<Props>(), {
  systemStatus: 'normal',
  toolConnections: 0,
  agentsOnline: 0,
  runtimeConnected: false,
  runtimeConnectionLabel: '连接中',
  resourceLabel: '',
  resourceSyncLabel: '',
  resourceReloadRequired: false,
  resourceTooltip: '',
  agentStateDot: '',
  agentStateText: '',
  agentStateReason: '',
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
      <span
        v-if="agentStateText"
        class="status-bar__pill status-bar__pill--agent"
      >
        <span v-if="agentStateDot" class="status-bar__agent-dot">{{ agentStateDot }}</span>
        {{ agentStateText }}
        <small v-if="agentStateReason">{{ agentStateReason }}</small>
      </span>
      <span
        class="status-bar__pill status-bar__pill--connection"
        :class="{ 'status-bar__pill--online': runtimeConnected }"
      >
        <span class="status-bar__dot" />
        {{ runtimeConnectionLabel }}
      </span>
      <span
        v-if="resourceLabel"
        class="status-bar__pill status-bar__pill--resource"
        :class="{ 'status-bar__pill--reload': resourceReloadRequired }"
        :title="resourceTooltip"
      >
        {{ resourceLabel }}
        <small v-if="resourceSyncLabel">{{ resourceSyncLabel }}</small>
      </span>
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

.status-bar__pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 18px;
  max-width: 190px;
  padding: 0 7px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.status-bar__pill small {
  min-width: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 750;
  text-overflow: ellipsis;
}

.status-bar__dot {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--text-muted);
}

.status-bar__pill--online {
  border-color: color-mix(in srgb, var(--success) 22%, var(--border-color));
  background: var(--success-soft);
  color: var(--success);
}

.status-bar__pill--online .status-bar__dot {
  background: var(--success);
}

.status-bar__pill--reload {
  border-color: var(--warning-border, var(--border-color));
  background: var(--warning-soft, var(--bg-card));
  color: var(--warning, var(--text-secondary));
}

.status-bar__pill--agent {
  max-width: min(360px, 36vw);
  border-color: color-mix(in srgb, var(--primary) 22%, var(--border-color));
  background: color-mix(in srgb, var(--primary) 10%, var(--bg-card));
  color: var(--primary);
}

.status-bar__agent-dot {
  flex: 0 0 auto;
  font-size: 10px;
  line-height: 1;
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
