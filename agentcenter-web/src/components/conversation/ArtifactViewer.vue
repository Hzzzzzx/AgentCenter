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
</script>

<template>
  <div class="artifact-viewer">
    <div v-if="!artifact" class="artifact-viewer__empty">
      暂无产物
    </div>
    <template v-else>
      <div class="artifact-viewer__header">
        <span class="artifact-viewer__title">{{ artifact.title }}</span>
        <span class="artifact-viewer__type">{{ artifact.artifactType }}</span>
      </div>
      <div class="artifact-viewer__body">
        <pre v-if="isJson" class="artifact-viewer__json">{{ renderedJson }}</pre>
        <MarkdownContent
          v-else-if="isMarkdownLike"
          class="artifact-viewer__markdown"
          :content="artifact.content"
        />
        <pre v-else class="artifact-viewer__plain">{{ artifact.content }}</pre>
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
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 8px;
}

.artifact-viewer__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.artifact-viewer__type {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  background-color: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
  font-weight: 600;
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
