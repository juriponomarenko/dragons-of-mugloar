<script setup lang="ts">
import type { Reputation } from '@/api/types'
import { compact } from '@/utils/format'

defineProps<{ reputation: Reputation | null; disabled?: boolean }>()
const emit = defineEmits<{ investigate: [] }>()

const factions: { key: keyof Reputation; label: string }[] = [
  { key: 'people', label: 'People' },
  { key: 'state', label: 'State' },
  { key: 'underworld', label: 'Underworld' },
]

function tone(value: number): string {
  if (value > 0) return 'is-positive'
  if (value < 0) return 'is-negative'
  return ''
}
</script>

<template>
  <section class="reputation card">
    <h2 class="card-title">Reputation</h2>
    <dl v-if="reputation" class="reputation__list">
      <div v-for="f in factions" :key="f.key" class="reputation__row">
        <dt>{{ f.label }}</dt>
        <dd :class="tone(reputation[f.key])">
          {{ reputation[f.key] > 0 ? '+' : '' }}{{ compact(reputation[f.key]) }}
        </dd>
      </div>
    </dl>
    <p v-else class="muted reputation__hint">
      Who likes you depends on which jobs you take. Shady work pleases the underworld and upsets the
      state.
    </p>
    <button
      class="btn btn-sm reputation__btn"
      type="button"
      :disabled="disabled"
      @click="emit('investigate')"
    >
      {{ reputation ? 'Investigate again' : 'Investigate' }}
    </button>
  </section>
</template>

<style scoped>
.reputation__list {
  margin: 0 0 12px;
  display: grid;
  gap: 6px;
}

.reputation__row {
  display: flex;
  justify-content: space-between;
}

.reputation__row dt {
  color: var(--muted);
}

.reputation__row dd {
  margin: 0;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.is-positive {
  color: var(--success);
}

.is-negative {
  color: var(--danger);
}

.reputation__hint {
  font-size: 0.85rem;
  margin-bottom: 12px;
}
</style>
