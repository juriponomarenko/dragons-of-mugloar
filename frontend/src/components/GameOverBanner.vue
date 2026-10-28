<script setup lang="ts">
import type { GameView } from '@/api/types'
import { gold } from '@/utils/format'

const TARGET = 1000

defineProps<{ game: GameView; turns: number; busy?: boolean }>()
const emit = defineEmits<{ newGame: [] }>()
</script>

<template>
  <section class="over card" :class="game.score >= TARGET ? 'is-win' : 'is-loss'" role="status">
    <div>
      <h2 class="over__title">
        {{ game.score >= TARGET ? 'Victory! ' : 'Game over. ' }}
        Your dragon finished with {{ gold(game.score) }} points.
      </h2>
      <p class="over__text">
        {{ turns }} actions, level {{ game.level }}, {{ gold(game.gold) }} gold left.
        <span v-if="game.score >= TARGET">That clears the {{ gold(TARGET) }}-point target.</span>
        <span v-else
          >{{ gold(TARGET - game.score) }} short of the {{ gold(TARGET) }}-point target.</span
        >
      </p>
    </div>
    <button class="btn btn-primary" type="button" :disabled="busy" @click="emit('newGame')">
      Start a new game
    </button>
  </section>
</template>

<style scoped>
.over {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
}

.over.is-win {
  border-color: var(--success);
  background: var(--success-soft);
}

.over.is-loss {
  border-color: var(--warning);
  background: var(--warning-soft);
}

.over__title {
  font-size: 1.2rem;
  margin-bottom: 4px;
}

.over__text {
  font-size: 0.95rem;
}
</style>
