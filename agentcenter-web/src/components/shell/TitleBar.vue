<script setup lang="ts">
interface Props {
  searchValue?: string
}

const props = withDefaults(defineProps<Props>(), {
  searchValue: '',
})

const emit = defineEmits<{
  'update:searchValue': [value: string]
}>()
</script>

<template>
  <header class="title-bar">
    <div class="title-bar__brand">
      <div class="title-bar__logo" aria-hidden="true">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
          <path d="M12 2l1.8 5.2L19 9l-5.2 1.8L12 16l-1.8-5.2L5 9l5.2-1.8L12 2z" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>
          <path d="M18.5 14l.9 2.6L22 17.5l-2.6.9-.9 2.6-.9-2.6-2.6-.9 2.6-.9.9-2.6z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
        </svg>
      </div>
      <div class="title-bar__name">
        <span>AI DevOps</span>
        <strong>v2.0</strong>
      </div>
    </div>

    <div class="title-bar__scopes" aria-label="项目、空间、迭代筛选">
      <label class="title-bar__scope">
        <span>项目</span>
        <select aria-label="选择项目">
          <option>AgentCenter</option>
          <option>TianYuan</option>
          <option>平台接入</option>
        </select>
      </label>
      <label class="title-bar__scope">
        <span>空间</span>
        <select aria-label="选择空间">
          <option>研发中台</option>
          <option>平台工程</option>
          <option>安全治理</option>
        </select>
      </label>
      <label class="title-bar__scope">
        <span>迭代</span>
        <select aria-label="选择迭代">
          <option>Sprint 14</option>
          <option>Sprint 15</option>
          <option>长期演进</option>
        </select>
      </label>
    </div>

    <div class="title-bar__search">
      <svg class="title-bar__search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none">
        <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="2"/>
        <path d="M20 20l-4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <input
        type="search"
        placeholder="搜索 FE、US、Task、Work、缺陷、漏洞..."
        :value="props.searchValue"
        @input="emit('update:searchValue', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <div class="title-bar__actions">
      <button class="title-bar__icon-btn" title="通知" aria-label="通知">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M13.7 21a2 2 0 01-3.4 0" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <span class="title-bar__badge">3</span>
      </button>
      <button class="title-bar__icon-btn" title="设置" aria-label="设置">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/>
          <path d="M19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 01-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 01-4 0v-.1a1.7 1.7 0 00-1-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 01-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.8 1.7 1.7 0 00-1.5-1H3a2 2 0 010-4h.1a1.7 1.7 0 001.5-1 1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 012.8-2.8l.1.1a1.7 1.7 0 001.8.3 1.7 1.7 0 001-1.5V3a2 2 0 014 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.8-.3l.1-.1a2 2 0 012.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.8 1.7 1.7 0 001.5 1h.1a2 2 0 010 4h-.1a1.7 1.7 0 00-1.5 1z" stroke="currentColor" stroke-width="2"/>
        </svg>
      </button>
      <div class="title-bar__user">
        <span class="title-bar__avatar">张</span>
        <span class="title-bar__user-text">
          <strong>产品负责人 A</strong>
          <em>技术负责人</em>
        </span>
      </div>
    </div>
  </header>
</template>

<style scoped>
.title-bar {
  display: grid;
  grid-template-columns: max-content max-content minmax(260px, 1fr) max-content;
  align-items: center;
  gap: 16px;
  height: var(--titlebar-height);
  padding: 0 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}

.title-bar__brand,
.title-bar__scopes,
.title-bar__actions,
.title-bar__user {
  display: flex;
  align-items: center;
}

.title-bar__brand {
  gap: 10px;
  min-width: 160px;
}

.title-bar__logo {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: #ffffff;
  background: linear-gradient(135deg, #3b82f6, #7c3aed);
}

.title-bar__name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.title-bar__name strong {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  color: var(--accent-blue);
  background: var(--bg-tertiary);
}

.title-bar__scopes {
  gap: 8px;
}

.title-bar__scope {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 7px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.title-bar__scope span {
  color: var(--text-muted);
}

.title-bar__scope select {
  min-width: 80px;
  max-width: 108px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  outline: 0;
}

.title-bar__search {
  position: relative;
  max-width: 480px;
  width: 100%;
  justify-self: center;
}

.title-bar__search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-muted);
  pointer-events: none;
}

.title-bar__search input {
  width: 100%;
  height: 36px;
  padding: 0 14px 0 44px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 13px;
  outline: 0;
}

.title-bar__search input:focus {
  border-color: var(--accent-blue);
  background: var(--bg-secondary);
  box-shadow: 0 0 0 3px var(--glow-blue);
}

.title-bar__actions {
  gap: 8px;
}

.title-bar__icon-btn {
  position: relative;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.title-bar__icon-btn:hover {
  color: var(--text-primary);
  background: var(--bg-tertiary);
}

.title-bar__badge {
  position: absolute;
  top: 3px;
  right: 3px;
  display: grid;
  place-items: center;
  min-width: 14px;
  height: 14px;
  padding: 0 2px;
  border-radius: 50%;
  background: var(--error);
  color: #ffffff;
  font-size: 9px;
  font-weight: 600;
}

.title-bar__user {
  gap: 8px;
  padding-left: 4px;
}

.title-bar__avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: linear-gradient(135deg, #3b82f6, #7c3aed);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
}

.title-bar__user-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.title-bar__user-text strong {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
}

.title-bar__user-text em {
  font-style: normal;
  font-size: 10px;
  color: var(--text-muted);
}

@media (max-width: 1180px) {
  .title-bar {
    grid-template-columns: max-content minmax(220px, 1fr) max-content;
  }

  .title-bar__scopes {
    display: none;
  }
}
</style>
