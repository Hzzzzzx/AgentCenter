import { describe, expect, it } from 'vitest'
import {
  isRuntimeConnectionDiagnostic,
  projectRuntimeStatus,
  type RuntimeStatusEvent,
} from './runtimeStatusProjector'

function makeEvent(overrides: Partial<RuntimeStatusEvent> & { id: string }): RuntimeStatusEvent {
  return {
    eventType: 'STATUS',
    payloadJson: null,
    seqNo: null,
    createdAt: '2026-05-10T10:00:00.000Z',
    ...overrides,
  }
}

describe('runtimeStatusProjector', () => {
  it('prioritizes pending exception interactions over event health', () => {
    const status = projectRuntimeStatus({
      events: [],
      connected: true,
      running: false,
      pendingExceptionCount: 2,
    })

    expect(status.tone).toBe('blocked')
    expect(status.label).toBe('需要处理 2 个异常')
    expect(status.badge).toBe('待处理')
  })

  it('shows task runtime errors as bottom status errors', () => {
    const status = projectRuntimeStatus({
      events: [
        makeEvent({
          id: 'ev-timeout',
          eventType: 'ERROR',
          payloadJson: JSON.stringify({
            kind: 'error',
            title: 'Runtime 响应超时',
            summary: 'Agent Runtime 已接收请求，但没有返回可用输出。',
            rawEventType: 'session.messages.timeout',
          }),
        }),
      ],
      connected: true,
      running: false,
      pendingExceptionCount: 0,
    })

    expect(status.tone).toBe('error')
    expect(status.label).toBe('Runtime 响应超时')
    expect(status.rawEventType).toBe('session.messages.timeout')
  })

  it('classifies browser and OpenCode stream diagnostics as connection diagnostics', () => {
    expect(isRuntimeConnectionDiagnostic(makeEvent({
      id: 'ev-browser',
      eventType: 'ERROR',
      payloadJson: JSON.stringify({ kind: 'runtime_connection', rawEventType: 'browser.sse.error' }),
    }))).toBe(true)

    expect(isRuntimeConnectionDiagnostic(makeEvent({
      id: 'ev-runtime',
      eventType: 'ERROR',
      payloadJson: JSON.stringify({ rawEventType: 'event.stream.error' }),
    }))).toBe(true)
  })

  it('does not keep a recovered browser SSE error in the live bar', () => {
    const status = projectRuntimeStatus({
      events: [
        makeEvent({
          id: 'ev-browser',
          eventType: 'ERROR',
          payloadJson: JSON.stringify({
            kind: 'runtime_connection',
            title: '事件流连接异常',
            rawEventType: 'browser.sse.error',
          }),
        }),
      ],
      connected: true,
      running: false,
      pendingExceptionCount: 0,
    })

    expect(status.tone).toBe('ok')
    expect(status.label).toBe('Runtime 已连接')
  })

  it('shows retry diagnostics while Runtime output polling is retrying', () => {
    const status = projectRuntimeStatus({
      events: [
        makeEvent({
          id: 'ev-retry',
          eventType: 'PROCESS_TRACE',
          payloadJson: JSON.stringify({
            kind: 'retry',
            status: 'retrying',
            title: '读取 Runtime 输出失败，正在自动重试',
            summary: '读取 Agent Runtime 输出时失败，正在第 1 次自动重试。',
          }),
        }),
      ],
      connected: true,
      running: true,
      pendingExceptionCount: 0,
    })

    expect(status.tone).toBe('warning')
    expect(status.badge).toBe('重试中')
  })
})
