<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TurnView } from '@/api/types'
import { gold, outcome, time } from '@/utils/format'

const props = defineProps<{ turns: TurnView[] }>()

const PAGE = 20
const showAll = ref(false)

const newestFirst = computed(() => [...props.turns].reverse())
const visible = computed(() =>
  showAll.value ? newestFirst.value : newestFirst.value.slice(0, PAGE),
)
const successes = computed(
  () => props.turns.filter((t) => t.action === 'SOLVE' && t.success).length,
)
const failures = computed(
  () => props.turns.filter((t) => t.action === 'SOLVE' && !t.success).length,
)
</script>

<template>
  <section class="history card">
    <header class="history__header">
      <h2 class="card-title">History</h2>
      <p v-if="turns.length" class="muted history__summary">
        {{ turns.length }} actions · {{ successes }} solved · {{ failures }} failed
      </p>
    </header>

    <p v-if="!turns.length" class="muted">Nothing has happened yet. Pick an ad to get started.</p>

    <ol v-else class="history__list">
      <li
        v-for="turn in visible"
        :key="`${turn.turn}-${turn.targetId}-${turn.at}`"
        class="history__item"
      >
        <div class="history__turn mono">#{{ turn.turn }}</div>
        <div class="history__body">
          <div class="history__line">
            <span
              class="badge"
              :class="
                turn.action !== 'SOLVE'
                  ? 'badge-muted'
                  : turn.success
                    ? 'badge-success'
                    : 'badge-danger'
              "
            >
              {{ outcome(turn) }}
            </span>
            <span v-if="turn.automated" class="badge badge-accent" title="Chosen by the advisor"
              >advisor</span
            >
            <span class="history__description">{{ turn.description }}</span>
          </div>
          <p v-if="turn.message" class="history__message muted">{{ turn.message }}</p>
          <p v-if="turn.reason" class="history__reason muted">Why: {{ turn.reason }}</p>
        </div>
        <div class="history__state muted">
          <span>♥ {{ turn.state.lives }}</span>
          <span>{{ gold(turn.state.gold) }} g</span>
          <span>{{ gold(turn.state.score) }} pts</span>
          <span class="history__time">{{ time(turn.at) }}</span>
        </div>
      </li>
    </ol>

    <button
      v-if="newestFirst.length > PAGE"
      class="btn btn-sm history__more"
      type="button"
      @click="showAll = !showAll"
    >
      {{ showAll ? 'Show latest only' : `Show all ${newestFirst.length}` }}
    </button>
  </section>
</template>

<style scoped>
.history {
  margin-top: 16px;
}

.history__header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}

.history__summary {
  font-size: 0.85rem;
}

.history__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.history__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 6px 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
}

.history__turn {
  color: var(--muted);
  font-size: 0.85rem;
  padding-top: 2px;
}

.history__line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.history__description {
  font-weight: 600;
}

.history__message,
.history__reason {
  font-size: 0.85rem;
  margin-top: 4px;
}

.history__state {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
}

.history__time {
  margin-left: auto;
}

.history__more {
  margin-top: 12px;
}

@media (min-width: 900px) {
  .history__item {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .history__state {
    grid-column: auto;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
  }

  .history__time {
    margin-left: 0;
  }
}
</style>
