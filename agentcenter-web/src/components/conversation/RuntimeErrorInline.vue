<script setup lang="ts">
import type { ErrorPart } from './projection/types'

const props = defineProps<{
  part: ErrorPart
}>()
</script>

<template>
  <div class="runtime-error" role="alert">
    <div class="runtime-error__head">
      <span class="runtime-error__icon">E</span>
      <strong class="runtime-error__message">{{ part.message }}</strong>
    </div>
    <details v-if="part.detail" :open="part.defaultExpanded" class="runtime-error__details">
      <summary>查看详情</summary>
      <pre class="runtime-error__detail">{{ part.detail }}</pre>
    </details>
  </div>
</template>

<style scoped>
.runtime-error {
  margin-top: 8px;
  border: 1px solid var(--error);
  border-radius: 8px;
  background: var(--error-soft);
  overflow: hidden;
}

.runtime-error__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 10px;
}

.runtime-error__icon {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--error);
  color: var(--on-error, #fff);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 900;
}

.runtime-error__message {
  color: var(--error);
  font-size: 13px;
  font-weight: 950;
  line-height: 1.5;
}

.runtime-error__details {
  border-top: 1px solid color-mix(in srgb, var(--error) 24%, transparent);
}

.runtime-error__details summary {
  min-height: 30px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  color: var(--error);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.runtime-error__detail {
  margin: 0;
  padding: 8px 10px;
  border-top: 1px solid color-mix(in srgb, var(--error) 16%, transparent);
  background: var(--bg-primary, var(--bg-card));
  color: var(--error);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
