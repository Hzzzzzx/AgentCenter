<script setup lang="ts">
import { computed } from 'vue'
import type { ArtifactDto } from '../../api/types'
import MarkdownContent from './MarkdownContent.vue'

const props = defineProps<{
  artifact: ArtifactDto | null
}>()

const renderedJson = computed(() => {
  if (!props.artifact?.content) return ''

  if (props.artifact.artifactType === 'JSON') {
    try {
      return JSON.stringify(JSON.parse(props.artifact.content), null, 2)
    } catch {
      return props.artifact.content
    }
  }

  return props.artifact.content
})

const isJson = computed(() => props.artifact?.artifactType === 'JSON')
const isMarkdownLike = computed(() =>
  props.artifact?.artifactType === 'MARKDOWN' || props.artifact?.artifactType === 'REPORT'
)
const contentSourceLabel = computed(() =>
  props.artifact?.filePath ? '实际文件' : '数据库快照'
)

const documentTitle = computed(() => {
  const content = props.artifact?.content ?? ''
  return content.match(/^#\s+(.+)$/m)?.[1]?.trim() ?? ''
})

const shortArtifactId = computed(() => {
  const id = props.artifact?.id ?? ''
  return id.length > 18 ? `${id.slice(0, 8)}...${id.slice(-6)}` : id
})

const createdAtText = computed(() => {
  const value = props.artifact?.createdAt
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
})
</script>

<template>
  <div class="artifact-viewer">
    <div v-if="!artifact" class="artifact-viewer__empty">
      暂无产物
    </div>
    <template v-else>
      <div class="artifact-viewer__header">
        <div class="artifact-viewer__header-main">
          <span class="artifact-viewer__title">{{ artifact.title }}</span>
          <span class="artifact-viewer__type">{{ artifact.artifactType }}</span>
        </div>
        <dl class="artifact-viewer__meta">
          <div v-if="documentTitle && documentTitle !== artifact.title">
            <dt>文档标题</dt>
            <dd>{{ documentTitle }}</dd>
          </div>
          <div v-if="artifact.workflowNodeInstanceId">
            <dt>来源节点</dt>
            <dd :title="artifact.workflowNodeInstanceId">{{ artifact.workflowNodeInstanceId }}</dd>
          </div>
          <div v-if="artifact.sourceType">
            <dt>来源</dt>
            <dd>{{ artifact.sourceType }}</dd>
          </div>
          <div>
            <dt>内容来源</dt>
            <dd
              :class="{
                'artifact-viewer__source-file': Boolean(artifact.filePath),
                'artifact-viewer__source-snapshot': !artifact.filePath,
              }"
            >
              {{ contentSourceLabel }}
            </dd>
          </div>
          <div v-if="artifact.filePath">
            <dt>文件路径</dt>
            <dd :title="artifact.filePath">{{ artifact.filePath }}</dd>
          </div>
          <div v-if="shortArtifactId">
            <dt>产物 ID</dt>
            <dd :title="artifact.id">{{ shortArtifactId }}</dd>
          </div>
          <div v-if="createdAtText">
            <dt>创建时间</dt>
            <dd>{{ createdAtText }}</dd>
          </div>
        </dl>
      </div>
      <div class="artifact-viewer__body">
        <pre v-if="isJson" class="artifact-viewer__json">{{ renderedJson }}</pre>
        <MarkdownContent
          v-else-if="isMarkdownLike"
          class="artifact-viewer__markdown"
          :content="artifact.content ?? ''"
        />
        <pre v-else class="artifact-viewer__plain">{{ artifact.content ?? '' }}</pre>
      </div>
    </template>
  </div>
</template>

<style scoped>
.artifact-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.artifact-viewer__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-secondary);
  font-size: 13px;
}

.artifact-viewer__header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 0 10px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 8px;
}

.artifact-viewer__header-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.artifact-viewer__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  min-width: 0;
  overflow-wrap: anywhere;
}

.artifact-viewer__type {
  flex-shrink: 0;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  background-color: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
  font-weight: 600;
}

.artifact-viewer__meta {
  display: grid;
  gap: 4px;
  margin: 0;
  font-size: 11px;
  color: var(--text-secondary);
}

.artifact-viewer__meta div {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
}

.artifact-viewer__meta dt,
.artifact-viewer__meta dd {
  margin: 0;
}

.artifact-viewer__meta dt {
  color: var(--text-secondary);
}

.artifact-viewer__meta dd {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.artifact-viewer__source-file {
  color: var(--success);
  font-weight: 700;
}

.artifact-viewer__source-snapshot {
  color: var(--warning);
  font-weight: 700;
}

.artifact-viewer__body {
  flex: 1;
  overflow-y: auto;
}

.artifact-viewer__json,
.artifact-viewer__plain {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.5;
  padding: 12px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
}

.artifact-viewer__markdown {
  font-size: 13px;
}
</style>
