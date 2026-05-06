import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type NotificationTone = 'success' | 'error' | 'warning' | 'info'
export type NotificationAnchor = 'global' | 'right-panel'

export interface AppNotification {
  id: string
  tone: NotificationTone
  title: string
  message?: string
  anchor: NotificationAnchor
  createdAt: number
}

interface PushNotificationInput {
  tone?: NotificationTone
  title: string
  message?: string
  anchor?: NotificationAnchor
  durationMs?: number
}

let sequence = 0

export const useNotificationStore = defineStore('notifications', () => {
  const notifications = ref<AppNotification[]>([])
  const timers = new Map<string, ReturnType<typeof setTimeout>>()

  const rightPanelNotifications = computed(() =>
    notifications.value.filter((notification) => notification.anchor === 'right-panel')
  )

  function dismiss(id: string) {
    const timer = timers.get(id)
    if (timer) {
      clearTimeout(timer)
      timers.delete(id)
    }
    notifications.value = notifications.value.filter((notification) => notification.id !== id)
  }

  function push(input: PushNotificationInput) {
    const id = `notice-${Date.now()}-${++sequence}`
    const notification: AppNotification = {
      id,
      tone: input.tone ?? 'info',
      title: input.title,
      message: input.message,
      anchor: input.anchor ?? 'global',
      createdAt: Date.now(),
    }

    notifications.value = [
      notification,
      ...notifications.value.filter((item) => item.id !== id),
    ].slice(0, 5)

    const durationMs = input.durationMs ?? 3600
    if (durationMs > 0) {
      const timer = setTimeout(() => dismiss(id), durationMs)
      ;(timer as unknown as { unref?: () => void }).unref?.()
      timers.set(id, timer)
    }

    return id
  }

  function notificationsFor(anchor: NotificationAnchor) {
    return notifications.value.filter((notification) => notification.anchor === anchor)
  }

  function clear(anchor?: NotificationAnchor) {
    const ids = notifications.value
      .filter((notification) => !anchor || notification.anchor === anchor)
      .map((notification) => notification.id)
    ids.forEach(dismiss)
  }

  return {
    notifications,
    rightPanelNotifications,
    push,
    dismiss,
    clear,
    notificationsFor,
  }
})
