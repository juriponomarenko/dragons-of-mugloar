<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useGameStore } from '@/stores/game'
import StatusBar from '@/components/StatusBar.vue'
import AdBoard from '@/components/AdBoard.vue'
import ShopPanel from '@/components/ShopPanel.vue'
import AutoplayControls from '@/components/AutoplayControls.vue'
import ReputationPanel from '@/components/ReputationPanel.vue'
import HistoryLog from '@/components/HistoryLog.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import GameOverBanner from '@/components/GameOverBanner.vue'
import LastTurnToast from '@/components/LastTurnToast.vue'

const props = defineProps<{ gameId: string }>()
const router = useRouter()
const store = useGameStore()

watch(
  () => props.gameId,
  (id) => {
    void store.loadGame(id)
  },
  { immediate: true },
)

onBeforeUnmount(() => store.stopAutoplay())

async function newGame() {
  store.stopAutoplay()
  const id = await store.startGame()
  if (id) {
    await router.push({ name: 'play', params: { gameId: id } })
  }
}
</script>

<template>
  <div v-if="store.loading.game && !store.game" class="loading muted" role="status">
    Loading game {{ gameId }}…
  </div>

  <template v-else-if="store.game">
    <StatusBar :game="store.game" :loading="store.loading.action" />

    <ErrorBanner :message="store.error" :details="store.errorDetails" @dismiss="store.clearError" />
    <ErrorBanner :message="store.notice" tone="info" @dismiss="store.clearNotice" />

    <GameOverBanner
      v-if="store.isGameOver"
      :game="store.game"
      :turns="store.history.length"
      :busy="store.loading.game"
      @new-game="newGame"
    />

    <div class="play-grid">
      <section class="play-main">
        <AdBoard
          :ads="store.ads"
          :loading="store.loading.board"
          :disabled="store.busy || store.isGameOver"
          @solve="store.solveAd"
          @refresh="store.refreshBoard"
        />
      </section>

      <aside class="play-side">
        <AutoplayControls
          :running="store.autoplay.running"
          :busy="store.busy"
          :game-over="store.isGameOver"
          :delay-ms="store.autoplay.delayMs"
          :automated-turns="store.automatedTurns"
          @step="store.autoplayStep"
          @run="store.runAutoplay"
          @stop="store.stopAutoplay"
          @finish="store.finishGame"
          @update:delay-ms="store.setAutoplayDelay"
        />
        <ShopPanel
          :items="store.shop"
          :player-gold="store.game.gold"
          :disabled="store.busy || store.isGameOver"
          @buy="store.buyItem"
        />
        <ReputationPanel
          :reputation="store.reputation"
          :disabled="store.busy || store.isGameOver"
          @investigate="store.investigate"
        />
      </aside>
    </div>

    <HistoryLog :turns="store.history" />

    <LastTurnToast :turn="store.lastTurn" />
  </template>

  <div v-else class="card not-found">
    <h2>Game not found</h2>
    <p class="muted">
      This server does not know game <span class="mono">{{ gameId }}</span
      >. Games live in the backend's memory, so they are gone after a restart.
    </p>
    <div class="not-found__actions">
      <button class="btn btn-primary" type="button" :disabled="store.loading.game" @click="newGame">
        Start a new game
      </button>
      <RouterLink class="btn" to="/">Back to start</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.loading {
  padding: 48px 0;
  text-align: center;
}

.play-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr);
  margin-top: 16px;
}

.play-side {
  display: grid;
  gap: 16px;
  align-content: start;
}

@media (min-width: 900px) {
  .play-grid {
    grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
    align-items: start;
  }

  /* The status bar is sticky too (8px from the top, about 73px tall), so the sidebar
     sticks below it instead of sliding underneath. */
  .play-side {
    position: sticky;
    top: 96px;
  }
}

.not-found {
  margin-top: 24px;
}

.not-found__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
}
</style>
