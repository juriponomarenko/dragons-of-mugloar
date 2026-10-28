<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AdView } from '@/api/types'
import AdCard from './AdCard.vue'

const props = defineProps<{ ads: AdView[]; loading?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ solve: [adId: string]; refresh: [] }>()

export type SortKey = 'score' | 'reward' | 'successRate' | 'expiresIn'

const sortOptions: { value: SortKey; label: string }[] = [
  { value: 'score', label: "Advisor's score" },
  { value: 'reward', label: 'Reward' },
  { value: 'successRate', label: 'Success chance' },
  { value: 'expiresIn', label: 'Expiring soonest' },
]

const sortBy = ref<SortKey>('score')
const hideRisky = ref(false)

const visibleAds = computed(() => {
  const list = props.ads.filter((ad) => !hideRisky.value || ad.eligible)
  const key = sortBy.value
  return [...list].sort((a, b) =>
    key === 'expiresIn' ? a.expiresIn - b.expiresIn : b[key] - a[key],
  )
})

const hiddenCount = computed(() => props.ads.length - visibleAds.value.length)
</script>

<template>
  <section class="board">
    <header class="board__header">
      <div>
        <h2 class="board__title">Message board</h2>
        <p class="muted board__subtitle">
          {{ ads.length }} ads · {{ ads.filter((a) => a.eligible).length }} within your risk budget
        </p>
      </div>
      <div class="board__controls">
        <label class="board__control">
          <span class="visually-hidden">Sort ads by</span>
          <select v-model="sortBy" class="board__select" aria-label="Sort ads by">
            <option v-for="o in sortOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </label>
        <label class="board__control board__checkbox">
          <input v-model="hideRisky" type="checkbox" />
          <span>Hide risky</span>
        </label>
        <button
          class="btn btn-sm"
          type="button"
          :disabled="loading || disabled"
          aria-label="Refresh the message board"
          @click="emit('refresh')"
        >
          ↻ Refresh
        </button>
      </div>
    </header>

    <p v-if="loading && !ads.length" class="muted board__empty" role="status">Loading ads…</p>
    <p v-else-if="!ads.length" class="muted board__empty">
      The board is empty right now. Refresh to check again.
    </p>

    <TransitionGroup v-else name="ads" tag="div" class="board__list">
      <AdCard
        v-for="ad in visibleAds"
        :key="ad.adId"
        :ad="ad"
        :disabled="disabled"
        @solve="emit('solve', $event)"
      />
    </TransitionGroup>

    <p v-if="hiddenCount > 0" class="muted board__hidden">
      {{ hiddenCount }} risky {{ hiddenCount === 1 ? 'ad' : 'ads' }} hidden.
    </p>
  </section>
</template>

<style scoped>
.board__header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 12px;
}

.board__title {
  font-size: 1.25rem;
}

.board__subtitle {
  font-size: 0.85rem;
}

.board__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.board__select {
  min-height: 32px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
}

.board__checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.9rem;
}

.board__list {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr);
}

@media (min-width: 1100px) {
  .board__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.board__empty,
.board__hidden {
  padding: 16px 0;
  font-size: 0.9rem;
}

.ads-enter-active,
.ads-leave-active {
  transition:
    opacity 0.25s,
    transform 0.25s;
}

.ads-enter-from,
.ads-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.ads-move {
  transition: transform 0.25s;
}
</style>
