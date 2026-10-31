<script setup lang="ts">
import { ref } from 'vue'
import { panelId, tabId, type TabView } from './tabViews'

/**
 * The tab bar above the swipeable views on a phone: an icon and a label per view, the active
 * one underlined. Keyboard users move between tabs with the arrow keys (roving tabindex).
 */
const props = defineProps<{ views: TabView[] }>()
const active = defineModel<string>({ required: true })

const root = ref<HTMLElement | null>(null)

function select(index: number): void {
  const view = props.views[index]
  if (!view) {
    return
  }
  active.value = view.id
  root.value?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[index]?.focus()
}

function onKeydown(event: KeyboardEvent): void {
  const index = props.views.findIndex((view) => view.id === active.value)
  const last = props.views.length - 1
  const next: Record<string, number> = {
    ArrowRight: Math.min(last, index + 1),
    ArrowLeft: Math.max(0, index - 1),
    Home: 0,
    End: last,
  }
  const target = next[event.key]
  if (target === undefined || target === index) {
    return
  }
  event.preventDefault()
  select(target)
}
</script>

<template>
  <div ref="root" class="tabs" role="tablist" aria-label="Game views" @keydown="onKeydown">
    <button
      v-for="(view, index) in views"
      :id="tabId(view.id)"
      :key="view.id"
      class="tab"
      :class="{ 'is-active': view.id === active }"
      type="button"
      role="tab"
      :aria-selected="view.id === active ? 'true' : 'false'"
      :aria-controls="panelId(view.id)"
      :tabindex="view.id === active ? 0 : -1"
      @click="select(index)"
    >
      <span class="tab__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <path
            :d="view.icon"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <span v-if="typeof view.badge === 'number'" class="tab__count">{{ view.badge }}</span>
        <span
          v-else-if="view.badge"
          class="tab__dot"
          :class="{ 'is-live': view.badge === 'live' }"
        ></span>
      </span>
      <span class="tab__label">{{ view.label }}</span>
      <span v-if="typeof view.badge === 'number'" class="visually-hidden">
        , {{ view.badge }} to pick from
      </span>
      <span v-else-if="view.badge === 'live'" class="visually-hidden">, running</span>
    </button>
  </div>
</template>

<style scoped>
.tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px 6px;
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: var(--muted);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  transition:
    color 0.15s,
    border-color 0.15s;
}

.tab.is-active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.tab:focus-visible {
  outline-offset: -3px;
}

.tab__icon {
  position: relative;
  display: inline-flex;
  width: 28px;
  height: 24px;
  align-items: center;
  justify-content: center;
}

.tab__count {
  position: absolute;
  top: -6px;
  right: -10px;
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 0.65rem;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.tab__dot {
  position: absolute;
  top: -2px;
  right: -4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
}

.tab__dot.is-live {
  background: var(--success);
  animation: tab-pulse 1s infinite;
}

@keyframes tab-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}
</style>
