import { describe, expect, it, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import RunSummaryPanel from './RunSummaryPanel.vue'

describe('RunSummaryPanel.vue', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('expands from the rail, renders file-backed results, and opens artifacts', async () => {
    vi.useFakeTimers()
    const wrapper = mount(RunSummaryPanel, {
      props: {
        visible: true,
        artifactOpen: false,
        todoItems: [
          { id: 'node-1', label: '需求整理 (PRD)', status: 'COMPLETED', meta: 'prd-design' },
          { id: 'node-2', label: '方案设计 (HLD)', status: 'RUNNING', meta: 'hld-design' },
        ],
        results: [
          {
            id: 'artifact-1',
            title: 'FE2001 需求整理 (PRD).md',
            source: 'workflow-node',
            filePath: '/runtime/FE2001 需求整理 (PRD).md',
          },
        ],
        sources: [
          { id: 'skill:hld-design', label: 'hld-design', meta: 'Skill' },
          { id: 'mcp:figma', label: 'figma', meta: 'MCP' },
        ],
      },
    })

    expect(wrapper.find('.run-summary__rail').exists()).toBe(true)
    expect(wrapper.find('.run-summary__panel').exists()).toBe(false)

    await wrapper.trigger('mouseenter')
    vi.advanceTimersByTime(180)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('任务进展')
    expect(wrapper.text()).not.toContain('To do')
    expect(wrapper.text()).toContain('方案设计 (HLD)')
    expect(wrapper.text()).toContain('FE2001 需求整理 (PRD).md')
    expect(wrapper.text()).toContain('hld-design')
    expect(wrapper.text()).toContain('figma')

    await wrapper.find('.run-summary__result').trigger('click')
    expect(wrapper.emitted('open-artifact')).toEqual([['artifact-1']])
  })

  it('delays collapse so users can move to the panel and pin it', async () => {
    vi.useFakeTimers()
    const wrapper = mount(RunSummaryPanel, {
      props: {
        visible: true,
        artifactOpen: false,
        todoItems: [{ id: 'node-1', label: '方案设计 (HLD)', status: 'RUNNING', meta: 'hld-design' }],
        results: [],
        sources: [],
      },
    })

    await wrapper.trigger('mouseenter')
    vi.advanceTimersByTime(180)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.run-summary__panel').exists()).toBe(true)

    await wrapper.trigger('mouseleave')
    vi.advanceTimersByTime(500)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.run-summary__panel').exists()).toBe(true)

    vi.advanceTimersByTime(220)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.run-summary__panel').exists()).toBe(false)

    await wrapper.trigger('mouseenter')
    vi.advanceTimersByTime(180)
    await wrapper.vm.$nextTick()
    const outlinePath = wrapper.find('.run-summary__pin path').attributes('d')
    expect(wrapper.find('.run-summary__pin path').attributes('fill')).toBe('currentColor')
    await wrapper.find('.run-summary__pin').trigger('click')
    expect(wrapper.find('.run-summary__pin').text()).toBe('')
    expect(wrapper.find('.run-summary__pin svg').exists()).toBe(true)
    expect(wrapper.find('.run-summary__pin svg').classes()).toContain('is-pinned')
    expect(wrapper.find('.run-summary__pin path').attributes('fill')).toBe('currentColor')
    expect(wrapper.find('.run-summary__pin path').attributes('d')).not.toBe(outlinePath)
    await wrapper.trigger('mouseleave')
    vi.advanceTimersByTime(720)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.run-summary__panel').exists()).toBe(true)
  })

  it('collapses to a rail while an artifact preview is open', async () => {
    vi.useFakeTimers()
    const wrapper = mount(RunSummaryPanel, {
      props: {
        visible: true,
        artifactOpen: true,
        todoItems: [],
        results: [],
        sources: [],
      },
    })

    expect(wrapper.find('.run-summary__rail').exists()).toBe(true)
    expect(wrapper.find('.run-summary__panel').exists()).toBe(false)

    await wrapper.trigger('mouseenter')
    vi.advanceTimersByTime(180)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.run-summary__panel').exists()).toBe(false)
  })
})
