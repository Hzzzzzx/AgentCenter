<script setup lang="ts">
import { computed } from 'vue'
import { useNotificationStore, type NotificationAnchor, type NotificationTone } from '../../stores/notifications'

const props = withDefaults(defineProps<{
  anchor?: NotificationAnchor
}>(), {
  anchor: 'global',
})

const notificationStore = useNotificationStore()
const visibleNotifications = computed(() => notificationStore.notificationsFor(props.anchor))

const toneLabels: Record<NotificationTone, string> = {
  success: '成功',
  error: '异常',
  warning: '提醒',
  info: '提示',
}
</script>

<template>
  <TransitionGroup
    v-if="visibleNotifications.length > 0"
    name="notification-bubble"
    tag="div"
    class="notification-bubbles"
    :class="`notification-bubbles--${anchor}`"
    role="status"
    aria-live="polite"
  >
    <div
      v-for="notification in visibleNotifications"
      :key="notification.id"
      class="notification-bubble"
      :class="`notification-bubble--${notification.tone}`"
    >
      <span class="notification-bubble__tone">{{ toneLabels[notification.tone] }}</span>
      <span class="notification-bubble__body">
        <strong>{{ notification.title }}</strong>
        <em v-if="notification.message">{{ notification.message }}</em>
      </span>
      <button
        class="notification-bubble__close"
        type="button"
        aria-label="关闭通知"
        @click="notificationStore.dismiss(notification.id)"
      >
        x
      </button>
    </div>
  </TransitionGroup>
</template>

<style scoped>
.notification-bubbles {
  position: absolute;
  z-index: 60;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.notification-bubbles--right-panel {
  top: 36px;
  right: 12px;
  width: min(268px, calc(100% - 24px));
}

.notification-bubble {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
  min-height: 44px;
  padding: 9px 10px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.16);
  color: var(--text-primary);
  pointer-events: auto;
  backdrop-filter: blur(14px);
}

.notification-bubble::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  border-radius: 8px 0 0 8px;
  content: '';
}

.notification-bubble--success::before {
  background: #10b981;
}

.notification-bubble--error::before {
  background: #ef4444;
}

.notification-bubble--warning::before {
  background: #f59e0b;
}

.notification-bubble--info::before {
  background: var(--accent-blue);
}

.notification-bubble__tone {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 7px;
  border-radius: 6px;
  background: #eff6ff;
  color: var(--accent-blue);
  font-size: 10px;
  font-weight: 900;
  line-height: 1;
}

.notification-bubble--success .notification-bubble__tone {
  background: #d1fae5;
  color: #047857;
}

.notification-bubble--error .notification-bubble__tone {
  background: #fee2e2;
  color: #b91c1c;
}

.notification-bubble--warning .notification-bubble__tone {
  background: #fef3c7;
  color: #b45309;
}

.notification-bubble__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.notification-bubble__body strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-bubble__body em {
  display: block;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 11px;
  font-style: normal;
  font-weight: 650;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-bubble__close {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.notification-bubble__close:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.notification-bubble-enter-active,
.notification-bubble-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.notification-bubble-enter-from,
.notification-bubble-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}

.notification-bubble-move {
  transition: transform 0.18s ease;
}
</style>
