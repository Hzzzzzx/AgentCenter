import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MarkdownContent from './MarkdownContent.vue'
import { useThemeStore } from '../../stores/theme'

function mountMarkdown(content: string, renderMermaid = false) {
  setActivePinia(createPinia())
  return mount(MarkdownContent, {
    props: {
      content,
      renderMermaid,
    },
  })
}

describe('MarkdownContent.vue', () => {
  it('renders GitHub-flavored markdown blocks', () => {
    const wrapper = mountMarkdown([
      '# PRD',
      '',
      '- [x] 需求确认',
      '- [ ] 验证',
      '',
      '| 模块 | 状态 |',
      '| --- | --- |',
      '| Bridge | OK |',
      '',
      '```ts',
      'const ok = true',
      '```',
    ].join('\n'))

    expect(wrapper.find('h1').text()).toBe('PRD')
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(2)
    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.find('code.language-ts').text()).toContain('const ok = true')
  })

  it('sanitizes unsafe html and link protocols', () => {
    const wrapper = mountMarkdown([
      '<img src=x onerror="alert(1)">',
      '',
      '<script>alert(1)</script>',
      '',
      '[bad](javascript:alert(1))',
      '',
      '[good](https://example.com)',
    ].join('\n'))

    expect(wrapper.html()).not.toContain('onerror')
    expect(wrapper.html()).not.toContain('<script')
    expect(wrapper.html()).not.toContain('javascript:alert')
    expect(wrapper.find('a[href="https://example.com"]').exists()).toBe(true)
  })

  it('keeps mermaid source blocks ready for browser rendering', () => {
    const wrapper = mountMarkdown([
      '```mermaid',
      'graph TD',
      '  A-->B',
      '```',
    ].join('\n'))

    expect(wrapper.find('.markdown-content__mermaid').exists()).toBe(true)
    expect(wrapper.find('.markdown-content__mermaid code').text()).toContain('A-->B')
  })
})

describe('MarkdownContent.vue — mermaid theme re-render', () => {
  const mockRender = vi.fn().mockResolvedValue({ svg: '<svg>mock</svg>' })
  const mockParse = vi.fn().mockResolvedValue({ diagramType: 'flowchart-v2', config: {} })
  const mockInitialize = vi.fn()

  beforeEach(() => {
    vi.resetModules()
    mockRender.mockClear()
    mockParse.mockClear()
    mockInitialize.mockClear()
    mockRender.mockResolvedValue({ svg: '<svg>mock</svg>' })
    mockParse.mockResolvedValue({ diagramType: 'flowchart-v2', config: {} })
  })

  async function mountWithMermaidMock() {
    vi.doMock('mermaid', () => ({
      default: {
        initialize: mockInitialize,
        parse: mockParse,
        render: mockRender,
      },
    }))

    const { default: MarkdownContentWithMock } = await import('./MarkdownContent.vue')
    const pinia = createPinia()
    setActivePinia(pinia)

    const wrapper = mount(MarkdownContentWithMock, {
      props: {
        content: '```mermaid\ngraph TD\n  A-->B\n```',
        renderMermaid: true,
      },
    })

    await vi.waitFor(() => expect(mockRender).toHaveBeenCalled(), { timeout: 2000 })

    return { wrapper, pinia }
  }

  it('re-initializes and re-renders mermaid when theme changes', async () => {
    const { pinia } = await mountWithMermaidMock()

    const firstRenderCount = mockRender.mock.calls.length
    const firstInitCount = mockInitialize.mock.calls.length
    expect(firstInitCount).toBeGreaterThanOrEqual(1)
    expect(firstRenderCount).toBeGreaterThanOrEqual(1)

    const themeStore = useThemeStore(pinia)
    themeStore.setTheme('midnight-black')

    await vi.waitFor(() => expect(mockInitialize.mock.calls.length).toBeGreaterThan(firstInitCount), { timeout: 2000 })
    await vi.waitFor(() => expect(mockRender.mock.calls.length).toBeGreaterThan(firstRenderCount), { timeout: 2000 })

    expect(mockInitialize.mock.calls.length).toBeGreaterThan(firstInitCount)
    expect(mockRender.mock.calls.length).toBeGreaterThan(firstRenderCount)

    const lastCall = mockRender.mock.calls[mockRender.mock.calls.length - 1]
    expect(lastCall[1]).toContain('A-->B')
  })

  it('keeps text labels from rendered Mermaid SVGs', async () => {
    mockRender.mockResolvedValueOnce({
      svg: [
        '<svg viewBox="0 0 120 80" role="graphics-document">',
        '<g class="node"><rect x="10" y="10" width="100" height="40"></rect>',
        '<text x="20" y="35"><tspan>需求整理 (PRD)</tspan></text></g>',
        '</svg>',
      ].join(''),
    })
    vi.doMock('mermaid', () => ({
      default: {
        initialize: mockInitialize,
        parse: mockParse,
        render: mockRender,
      },
    }))

    const { default: MarkdownContentWithMock } = await import('./MarkdownContent.vue')
    setActivePinia(createPinia())
    const wrapper = mount(MarkdownContentWithMock, {
      props: {
        content: '```mermaid\nflowchart TD\n  A["需求整理 (PRD)"]\n```',
        renderMermaid: true,
      },
    })

    await vi.waitFor(() => expect(mockRender).toHaveBeenCalled(), { timeout: 2000 })

    expect(wrapper.find('.markdown-content__mermaid').attributes('data-mermaid-state')).toBe('rendered')
    expect(wrapper.find('svg text').text()).toContain('需求整理 (PRD)')
  })

  it('falls back to source when mermaid parse reports invalid syntax', async () => {
    mockParse.mockResolvedValueOnce(false)
    vi.doMock('mermaid', () => ({
      default: {
        initialize: mockInitialize,
        parse: mockParse,
        render: mockRender,
      },
    }))

    const { default: MarkdownContentWithMock } = await import('./MarkdownContent.vue')
    setActivePinia(createPinia())
    const wrapper = mount(MarkdownContentWithMock, {
      props: {
        content: '```mermaid\nflowchart TD\n  A -->\n```',
        renderMermaid: true,
      },
    })

    await vi.waitFor(() => expect(mockParse).toHaveBeenCalled(), { timeout: 2000 })

    expect(mockRender).not.toHaveBeenCalled()
    expect(wrapper.find('.markdown-content__mermaid').attributes('data-mermaid-state')).toBe('source')
    expect(wrapper.find('.markdown-content__mermaid code').text()).toContain('A -->')
  })

  it('does not show Mermaid error SVGs returned by the renderer', async () => {
    mockRender.mockResolvedValueOnce({
      svg: '<svg><text>Syntax error in text</text><text>mermaid version 11.14.0</text></svg>',
    })
    vi.doMock('mermaid', () => ({
      default: {
        initialize: mockInitialize,
        parse: mockParse,
        render: mockRender,
      },
    }))

    const { default: MarkdownContentWithMock } = await import('./MarkdownContent.vue')
    setActivePinia(createPinia())
    const wrapper = mount(MarkdownContentWithMock, {
      props: {
        content: '```mermaid\nflowchart TD\n  A-->B\n```',
        renderMermaid: true,
      },
    })

    await vi.waitFor(() => expect(mockRender).toHaveBeenCalled(), { timeout: 2000 })

    expect(wrapper.html()).not.toContain('Syntax error in text')
    expect(wrapper.find('.markdown-content__mermaid').attributes('data-mermaid-state')).toBe('source')
    expect(wrapper.find('.markdown-content__mermaid code').text()).toContain('A-->B')
  })
})
