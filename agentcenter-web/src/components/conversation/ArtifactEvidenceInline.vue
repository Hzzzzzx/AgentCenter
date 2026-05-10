<script setup lang="ts">
import type { ArtifactEvidencePart } from './projection/types'

const props = defineProps<{
  part: ArtifactEvidencePart
}>()

const emit = defineEmits<{
  'open-artifact': [artifactId: string]
}>()

function handleClick(): void {
  if (props.part.artifactId) {
    emit('open-artifact', props.part.artifactId)
  }
}
</script>

<template>
  <div
    class="artifact-evidence"
    :class="{ 'artifact-evidence--clickable': !!part.artifactId }"
    :role="part.artifactId ? 'button' : undefined"
    :tabindex="part.artifactId ? 0 : undefined"
    @click="handleClick"
    @keydown.enter="handleClick"
    @keydown.space.prevent="handleClick"
  >
    <span class="artifact-evidence__icon">F</span>
    <div class="artifact-evidence__content">
      <strong class="artifact-evidence__title">{{ part.title }}</strong>
      <span v-if="part.filePath" class="artifact-evidence__path">{{ part.filePath }}</span>
    </div>
    <span v-if="part.diffAvailable" class="artifact-evidence__diff-badge">diff</span>
  </div>
</template>

<style scoped>
.artifact-evidence {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-card, var(--bg-card));
  font-size: 12px;
  font-weight: 850;
}

.artifact-evidence--clickable {
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.artifact-evidence--clickable:hover {
  background: var(--surface-hover);
  border-color: var(--accent-blue);
}

.artifact-evidence--clickable:focus-visible {
  outline: 2px solid var(--accent-blue);
  outline-offset: 2px;
}

.artifact-evidence__icon {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--brand-soft);
  color: var(--accent-blue);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 900;
}

.artifact-evidence__content {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.artifact-evidence__title {
  color: var(--text-primary);
  font-weight: 950;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.artifact-evidence__path {
  color: var(--text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.artifact-evidence__diff-badge {
  min-height: 20px;
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--success-soft);
  color: var(--success);
  font-size: 10px;
  font-weight: 900;
  text-transform: uppercase;
}
</style>
