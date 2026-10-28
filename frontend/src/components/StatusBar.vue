<script setup lang="ts">
import { computed } from 'vue'
import type { GameView } from '@/api/types'
import { gold } from '@/utils/format'

const props = defineProps<{ game: GameView; loading?: boolean }>()

const hearts = computed(() => Math.min(props.game.lives, 5))
const livesClass = computed(() => {
  if (props.game.lives <= 1) return 'is-critical'
  if (props.game.lives <= 2) return 'is-low'
  return ''
})
</script>

<template>
  <div class="status card" role="status" aria-live="polite" :class="{ 'is-loading': loading }">
    <div class="stat stat--lives" :class="livesClass">
      <span class="stat__label">Lives</span>
      <span class="stat__value">
        <span class="hearts" aria-hidden="true">
          <span v-for="i in hearts" :key="i">♥</span>
          <span v-if="game.lives > 5" class="hearts__more">+{{ game.lives - 5 }}</span>
        </span>
        <span class="stat__number">{{ game.lives }}</span>
      </span>
    </div>
    <div class="stat stat--gold">
      <span class="stat__label">Gold</span>
      <span class="stat__value">{{ gold(game.gold) }}</span>
    </div>
    <div class="stat stat--score">
      <span class="stat__label">Score</span>
      <span class="stat__value">{{ gold(game.score) }}</span>
    </div>
    <div class="stat">
      <span class="stat__label">Level</span>
      <span class="stat__value">{{ game.level }}</span>
    </div>
    <div class="stat">
      <span class="stat__label">Turn</span>
      <span class="stat__value">{{ game.turn }}</span>
    </div>
    <div class="stat stat--id">
      <span class="stat__label">Game</span>
      <span class="stat__value mono">{{ game.gameId }}</span>
    </div>
  </div>
</template>

<style scoped>
.status {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 12px;
  padding: 12px 16px;
  position: sticky;
  top: 8px;
  z-index: 5;
  transition: opacity 0.2s;
}

.status.is-loading {
  opacity: 0.75;
}

.stat {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat__label {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--muted);
}

.stat__value {
  font-size: 1.25rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  display: flex;
  align-items: baseline;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stat--gold .stat__value {
  color: var(--gold);
}

.stat--score .stat__value {
  color: var(--accent);
}

.stat--lives .hearts {
  color: var(--danger);
  letter-spacing: 1px;
}

.stat--lives.is-low .stat__number {
  color: var(--warning);
}

.stat--lives.is-critical .stat__number {
  color: var(--danger);
}

.hearts__more {
  font-size: 0.85rem;
  color: var(--muted);
  margin-left: 2px;
}

.stat--id .stat__value {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--muted);
}

@media (min-width: 640px) {
  .status {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}
</style>
