<script setup lang="ts">
import { ref, watch } from 'vue'
import type { TurnView } from '@/api/types'

const props = defineProps<{ turn: TurnView | null; durationMs?: number }>()

const visible = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined

watch(
  () => props.turn,
  (turn) => {
    if (!turn) return
    visible.value = true
    clearTimeout(timer)
    timer = setTimeout(() => (visible.value = false), props.durationMs ?? 2500)
  },
)
</script>

<template>
  <Transition name="toast">
    <div
      v-if="visible && turn"
      class="toast"
      :class="turn.action === 'BUY' ? 'is-buy' : turn.success ? 'is-success' : 'is-failure'"
      role="status"
      aria-live="polite"
    >
      <strong>{{ turn.action === 'BUY' ? 'Bought' : turn.success ? 'Solved' : 'Failed' }}</strong>
      <span class="toast__text">{{ turn.message || turn.description }}</span>
    </div>
  </Transition>
</template>

<style scoped>
.toast {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: 16px;
  z-index: 20;
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: var(--shadow);
  font-size: 0.95rem;
}

.toast.is-success {
  border-color: var(--success);
}

.toast.is-failure {
  border-color: var(--danger);
}

.toast.is-buy {
  border-color: var(--gold);
}

.toast__text {
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (min-width: 640px) {
  .toast {
    left: auto;
    max-width: 420px;
  }
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.2s,
    transform 0.2s;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
