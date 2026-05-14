<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { RuntimeStatusProjection } from './projection/runtimeStatusProjector'

const props = defineProps<{
  status: RuntimeStatusProjection
}>()

const expanded = ref(false)

watch(
  () => props.status.eventId ?? `${props.status.tone}:${props.status.label}`,
  () => {
    expanded.value = props.status.tone === 'blocked' || props.status.tone === 'error'
  },
  { immediate: true }
)

const canExpand = computed(() => props.status.expandable && Boolean(props.status.detail))

function toggleExpanded() {
  if (!canExpand.value) return
  expanded.value = !expanded.value
}
</script>

<template>
  <section
    class="runtime-status-bar"
    :class="`runtime-status-bar--${status.tone}`"
    aria-live="polite"
  >
    <button
      type="button"
      class="runtime-status-bar__summary"
      :disabled="!canExpand"
      :aria-expanded="canExpand ? expanded : undefined"
      @click="toggleExpanded"
    >
      <span class="runtime-status-bar__left">
        <span class="runtime-status-bar__dot" aria-hidden="true" />
        <span class="runtime-status-bar__text">
          <strong>{{ status.label }}</strong>
          <span v-if="status.detail">{{ status.detail }}</span>
        </span>
      </span>
      <span class="runtime-status-bar__right">
        <span class="runtime-status-bar__badge">{{ status.badge }}</span>
        <span v-if="canExpand" class="runtime-status-bar__toggle">
          {{ expanded ? '收起' : '详情' }}
        </span>
      </span>
    </button>

    <div v-if="canExpand && expanded" class="runtime-status-bar__detail">
      <dl>
        <div>
          <dt>状态</dt>
          <dd>{{ status.label }}</dd>
        </div>
        <div v-if="status.rawEventType">
          <dt>来源</dt>
          <dd>{{ status.rawEventType }}</dd>
        </div>
      </dl>
      <p>{{ status.detail }}</p>
    </div>
  </section>
</template>

<style scoped>
.runtime-status-bar {
  width: min(920px, 100%);
  margin: 0 auto 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-primary);
  overflow: hidden;
}

.runtime-status-bar__summary {
  width: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 10px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  text-align: left;
}

.runtime-status-bar__summary:not(:disabled) {
  cursor: pointer;
}

.runtime-status-bar__summary:disabled {
  cursor: default;
}

.runtime-status-bar__left,
.runtime-status-bar__right {
  display: flex;
  align-items: center;
  min-width: 0;
}

.runtime-status-bar__left {
  gap: 9px;
}

.runtime-status-bar__right {
  flex: 0 0 auto;
  gap: 7px;
}

.runtime-status-bar__dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--text-muted);
}

.runtime-status-bar__text {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.runtime-status-bar__text strong {
  flex: 0 0 auto;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 950;
  line-height: 1.35;
}

.runtime-status-bar__text span {
  min-width: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-status-bar__badge,
.runtime-status-bar__toggle {
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

.runtime-status-bar__badge {
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-muted);
}

.runtime-status-bar__toggle {
  color: var(--accent-blue);
}

.runtime-status-bar__detail {
  padding: 9px 10px 10px 27px;
  border-top: 1px solid color-mix(in srgb, var(--border-color) 72%, transparent);
  background: color-mix(in srgb, var(--bg-card) 82%, var(--bg-primary));
}

.runtime-status-bar__detail dl {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin: 0 0 6px;
}

.runtime-status-bar__detail div {
  min-width: 0;
}

.runtime-status-bar__detail dt {
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 900;
}

.runtime-status-bar__detail dd {
  margin: 2px 0 0;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 750;
}

.runtime-status-bar__detail p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
}

.runtime-status-bar--ok .runtime-status-bar__dot,
.runtime-status-bar--ok .runtime-status-bar__badge {
  background: var(--success);
  border-color: var(--success);
  color: var(--on-success, #fff);
}

.runtime-status-bar--running .runtime-status-bar__dot,
.runtime-status-bar--running .runtime-status-bar__badge {
  background: var(--accent-blue);
  border-color: var(--accent-blue);
  color: var(--on-brand, #fff);
}

.runtime-status-bar--warning .runtime-status-bar__dot,
.runtime-status-bar--warning .runtime-status-bar__badge,
.runtime-status-bar--offline .runtime-status-bar__dot,
.runtime-status-bar--offline .runtime-status-bar__badge {
  background: var(--warning);
  border-color: var(--warning);
  color: #111827;
}

.runtime-status-bar--error .runtime-status-bar__dot,
.runtime-status-bar--error .runtime-status-bar__badge,
.runtime-status-bar--blocked .runtime-status-bar__dot,
.runtime-status-bar--blocked .runtime-status-bar__badge {
  background: var(--error);
  border-color: var(--error);
  color: var(--on-error, #fff);
}

.runtime-status-bar--warning,
.runtime-status-bar--offline {
  border-color: color-mix(in srgb, var(--warning) 40%, var(--border-color));
  background: color-mix(in srgb, var(--warning) 8%, var(--bg-card));
}

.runtime-status-bar--error,
.runtime-status-bar--blocked {
  border-color: color-mix(in srgb, var(--error) 42%, var(--border-color));
  background: color-mix(in srgb, var(--error) 7%, var(--bg-card));
}

@media (max-width: 760px) {
  .runtime-status-bar__summary {
    align-items: flex-start;
  }

  .runtime-status-bar__text {
    display: grid;
    gap: 2px;
  }

  .runtime-status-bar__text span {
    white-space: normal;
  }
}
</style>
