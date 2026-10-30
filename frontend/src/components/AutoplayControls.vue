<script setup lang="ts">
import { AUTOPLAY_MAX_DELAY, AUTOPLAY_MIN_DELAY } from '@/stores/game'
import { seconds } from '@/utils/format'

defineProps<{ running: boolean; busy: boolean; gameOver: boolean; automatedTurns: number }>()

/** Pause between autoplay turns, owned by the parent (the store clamps it). */
const delayMs = defineModel<number>('delayMs', { required: true })

const emit = defineEmits<{ step: []; run: []; stop: []; finish: [] }>()

const minDelay = AUTOPLAY_MIN_DELAY
const maxDelay = AUTOPLAY_MAX_DELAY

function onDelayInput(event: Event) {
  delayMs.value = Number((event.target as HTMLInputElement).value)
}
</script>

<template>
  <section class="autoplay card">
    <h2 class="card-title">Advisor</h2>
    <p class="muted autoplay__hint">
      Let the strategy play for you: one turn at a time, continuously while you watch, or straight
      to the end.
    </p>
    <div class="autoplay__buttons">
      <button class="btn" type="button" :disabled="busy || gameOver" @click="emit('step')">
        Play one turn
      </button>
      <button
        v-if="!running"
        class="btn btn-primary"
        type="button"
        :disabled="busy || gameOver"
        @click="emit('run')"
      >
        ▶ Autoplay
      </button>
      <button v-else class="btn btn-danger" type="button" @click="emit('stop')">■ Stop</button>
      <button
        class="btn"
        type="button"
        :disabled="busy || gameOver"
        title="Plays the whole game on the server and returns the result"
        @click="emit('finish')"
      >
        ⏭ Finish game
      </button>
    </div>
    <label class="autoplay__speed">
      <span
        >Pause between turns: <strong>{{ seconds(delayMs) }}</strong></span
      >
      <input
        type="range"
        :min="minDelay"
        :max="maxDelay"
        step="100"
        :value="delayMs"
        aria-label="Pause between autoplay turns"
        :aria-valuetext="seconds(delayMs)"
        @input="onDelayInput"
      />
    </label>
    <p v-if="running" class="autoplay__status" role="status">
      <span class="autoplay__dot" aria-hidden="true"></span> Autoplay running…
    </p>
    <p v-else-if="automatedTurns" class="muted autoplay__status">
      {{ automatedTurns }} {{ automatedTurns === 1 ? 'turn' : 'turns' }} played by the advisor so
      far.
    </p>
  </section>
</template>

<style scoped>
.autoplay__hint {
  font-size: 0.85rem;
  margin-bottom: 12px;
}

.autoplay__buttons {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.autoplay__speed {
  display: grid;
  gap: 4px;
  font-size: 0.85rem;
}

.autoplay__speed input {
  width: 100%;
}

.autoplay__status {
  margin-top: 10px;
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  gap: 8px;
}

.autoplay__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--success);
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}
</style>
