import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationStore } from './notifications'

describe('useNotificationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useRealTimers()
  })

  it('pushes notifications by anchor and dismisses them', () => {
    const store = useNotificationStore()
    const id = store.push({
      anchor: 'right-panel',
      tone: 'success',
      title: '确认已通过',
      durationMs: 0,
    })

    expect(store.rightPanelNotifications).toHaveLength(1)
    expect(store.rightPanelNotifications[0].id).toBe(id)

    store.dismiss(id)
    expect(store.rightPanelNotifications).toHaveLength(0)
  })

  it('auto dismisses notifications after duration', () => {
    vi.useFakeTimers()
    const store = useNotificationStore()
    store.push({
      anchor: 'right-panel',
      title: '短提示',
      durationMs: 1000,
    })

    expect(store.rightPanelNotifications).toHaveLength(1)
    vi.advanceTimersByTime(1000)
    expect(store.rightPanelNotifications).toHaveLength(0)
  })
})
