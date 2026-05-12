import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ArtifactViewer from './ArtifactViewer.vue'
import type { ArtifactDto } from '../../api/types'

vi.mock('./MarkdownContent.vue', () => ({
  default: {
    name: 'MarkdownContent',
    props: ['content'],
    template: '<pre class="mocked-markdown">{{ content }}</pre>',
  },
}))

function makeArtifact(overrides: Partial<ArtifactDto> = {}): ArtifactDto {
  return {
    id: 'artifact-000000000001',
    workItemId: 'work-1',
    workflowInstanceId: 'workflow-1',
    workflowNodeInstanceId: 'node-1',
    artifactType: 'MARKDOWN',
    title: 'FE2002 详细设计 (LLD).md',
    content: '# FE2002 仪表盘数据可视化\n\n正文。',
    createdAt: '2026-05-12T10:30:00Z',
    ...overrides,
  }
}

describe('ArtifactViewer.vue', () => {
  it('renders artifact file metadata and document title separately', () => {
    const wrapper = mount(ArtifactViewer, {
      props: {
        artifact: makeArtifact(),
      },
    })

    expect(wrapper.text()).toContain('FE2002 详细设计 (LLD).md')
    expect(wrapper.text()).toContain('文档标题')
    expect(wrapper.text()).toContain('FE2002 仪表盘数据可视化')
    expect(wrapper.text()).toContain('来源节点')
    expect(wrapper.text()).toContain('node-1')
    expect(wrapper.text()).toContain('产物 ID')
    expect(wrapper.find('.mocked-markdown').text()).toContain('# FE2002 仪表盘数据可视化')
  })
})
