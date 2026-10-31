<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { DRAGON_LINES, LINE_INTERVAL_MS } from '@/utils/dragonLines'
import { duration } from '@/utils/format'

/**
 * Takes the place of the message board while the server plays the rest of the game in one
 * request. Nothing arrives until that run is over, so the panel says what the server is doing,
 * how long it has been at it, and keeps the player company with a line about dragons.
 */
const elapsedMs = ref(0)
const lineIndex = ref(0)
const startedAt = Date.now()
let ticker: ReturnType<typeof setInterval> | undefined
let rotator: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  ticker = setInterval(() => {
    elapsedMs.value = Date.now() - startedAt
  }, 1000)
  rotator = setInterval(() => {
    lineIndex.value = (lineIndex.value + 1) % DRAGON_LINES.length
  }, LINE_INTERVAL_MS)
})

onBeforeUnmount(() => {
  clearInterval(ticker)
  clearInterval(rotator)
})
</script>

<template>
  <section class="finishing card">
    <!-- Announced once; the ticking clock and the rotating line stay out of the live region. -->
    <p class="visually-hidden" role="status">
      The advisor is playing the rest of the game on the server.
    </p>
    <header class="finishing__head">
      <span class="finishing__spinner" aria-hidden="true"></span>
      <div>
        <h2 class="finishing__title">The advisor is finishing the game for you</h2>
        <p class="muted finishing__elapsed">Running for {{ duration(elapsedMs) }}</p>
      </div>
    </header>

    <p class="finishing__text">
      The server plays the rest of the game the way Autoplay does, without the pauses, and sends
      everything back at once when it is done. On repeat until your dragon runs out of lives or the
      server's turn cap is reached:
    </p>
    <ol class="finishing__steps">
      <li>Fetch the message board and the shop</li>
      <li>Score every ad and check what the gold can buy</li>
      <li>
        Buy a potion or the next upgrade when it pays off, otherwise solve the best eligible ad
      </li>
      <li>Record the turn and start over</li>
    </ol>
    <p class="muted finishing__note">
      A strong dragon trips the game's rate limit after about 300 turns, and the server waits it out
      rather than giving up, so a good run can take a few minutes. The board, the history and the
      final score arrive together.
    </p>

    <Transition name="line" mode="out-in">
      <p :key="lineIndex" class="finishing__line">{{ DRAGON_LINES[lineIndex] }}</p>
    </Transition>
  </section>
</template>

<style scoped>
.finishing {
  display: grid;
  gap: 14px;
  padding: 20px;
}

.finishing__head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.finishing__spinner {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 4px solid var(--accent-soft);
  border-top-color: var(--accent);
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.finishing__title {
  font-size: 1.2rem;
}

.finishing__elapsed {
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
}

.finishing__text {
  max-width: 70ch;
}

.finishing__steps {
  margin: 0;
  padding-left: 22px;
  display: grid;
  gap: 4px;
}

.finishing__note {
  font-size: 0.9rem;
  max-width: 70ch;
}

.finishing__line {
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--accent);
  background: var(--surface-2);
  font-style: italic;
}

.line-enter-active,
.line-leave-active {
  transition:
    opacity 0.25s,
    transform 0.25s;
}

.line-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.line-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
