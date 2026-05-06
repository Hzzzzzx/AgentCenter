import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownContent from './MarkdownContent.vue'

function mountMarkdown(content: string) {
  return mount(MarkdownContent, {
    props: {
      content,
      renderMermaid: false,
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
