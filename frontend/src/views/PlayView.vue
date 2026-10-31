<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch, watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import { useGameStore } from '@/stores/game'
import { useMediaQuery } from '@/composables/useMediaQuery'
import StatusBar from '@/components/StatusBar.vue'
import AdBoard from '@/components/AdBoard.vue'
import ShopPanel from '@/components/ShopPanel.vue'
import AutoplayControls from '@/components/AutoplayControls.vue'
import FinishingPanel from '@/components/FinishingPanel.vue'
import ReputationPanel from '@/components/ReputationPanel.vue'
import HistoryLog from '@/components/HistoryLog.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import GameOverBanner from '@/components/GameOverBanner.vue'
import LastTurnToast from '@/components/LastTurnToast.vue'
import ViewTabs from '@/components/ViewTabs.vue'
import SwipeTrack from '@/components/SwipeTrack.vue'
import { VIEW_ICONS, type TabView } from '@/components/tabViews'

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

const APP_TITLE = 'Dragons of Mugloar'
watchEffect(() => {
  document.title = store.game ? `Game ${store.game.gameId} · ${APP_TITLE}` : APP_TITLE
})

onBeforeUnmount(() => {
  store.stopAutoplay()
  document.title = APP_TITLE
})

/**
 * From 900px up the board sits next to a sidebar; below that the screen is three views the
 * player swipes between or picks from a tab bar. Both read the same store, so nothing is lost
 * when a window crosses the breakpoint.
 */
const isDesktop = useMediaQuery('(min-width: 900px)')
const activeView = ref('board')
const mobileHeader = ref<HTMLElement | null>(null)
const views = computed<TabView[]>(() => [
  {
    id: 'board',
    label: 'Board',
    icon: VIEW_ICONS.board,
    badge: store.eligibleAds.length || undefined,
  },
  {
    id: 'shop',
    label: 'Shop',
    icon: VIEW_ICONS.shop,
    badge: store.suggestedItem?.affordable ? 'dot' : undefined,
  },
  {
    id: 'advisor',
    label: 'Advisor',
    icon: VIEW_ICONS.advisor,
    badge: store.autoplay.running ? 'live' : undefined,
  },
])
const viewIds = computed(() => views.value.map((view) => view.id))

async function newGame() {
  store.stopAutoplay()
  const id = await store.startGame()
  if (id) {
    await router.push({ name: 'play', params: { gameId: id } })
  }
}

function retry() {
  void store.loadGame(props.gameId)
}
</script>

<template>
  <div v-if="store.loading.game && !store.game" class="loading muted" role="status">
    Loading game {{ gameId }}…
  </div>

  <template v-else-if="store.game">
    <h1 class="visually-hidden">Game {{ store.game.gameId }}</h1>

    <StatusBar v-if="isDesktop" :game="store.game" :loading="store.loading.action" />
    <div v-else ref="mobileHeader" class="mobile-header">
      <StatusBar :game="store.game" :loading="store.loading.action" />
      <ViewTabs v-model="activeView" :views="views" />
    </div>

    <MessageBanner
      :message="store.error"
      :details="store.errorDetails"
      @dismiss="store.clearError"
    />
    <MessageBanner :message="store.notice" tone="info" @dismiss="store.clearNotice" />

    <GameOverBanner
      v-if="store.isGameOver"
      :game="store.game"
      :turns="store.history.length"
      :busy="store.loading.game"
      @new-game="newGame"
    />

    <template v-if="isDesktop">
      <div class="play-grid">
        <section class="play-main">
          <FinishingPanel v-if="store.loading.finish" />
          <AdBoard
            v-else
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
            :finishing="store.loading.finish"
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
    </template>

    <SwipeTrack
      v-else
      v-model="activeView"
      :views="viewIds"
      :header="mobileHeader"
      class="mobile-track"
    >
      <template #board>
        <FinishingPanel v-if="store.loading.finish" />
        <AdBoard
          v-else
          :ads="store.ads"
          :loading="store.loading.board"
          :disabled="store.busy || store.isGameOver"
          @solve="store.solveAd"
          @refresh="store.refreshBoard"
        />
      </template>
      <template #shop>
        <ShopPanel
          :items="store.shop"
          :player-gold="store.game.gold"
          :disabled="store.busy || store.isGameOver"
          @buy="store.buyItem"
        />
      </template>
      <template #advisor>
        <div class="mobile-stack">
          <AutoplayControls
            :running="store.autoplay.running"
            :busy="store.busy"
            :game-over="store.isGameOver"
            :finishing="store.loading.finish"
            :delay-ms="store.autoplay.delayMs"
            :automated-turns="store.automatedTurns"
            @step="store.autoplayStep"
            @run="store.runAutoplay"
            @stop="store.stopAutoplay"
            @finish="store.finishGame"
            @update:delay-ms="store.setAutoplayDelay"
          />
          <ReputationPanel
            :reputation="store.reputation"
            :disabled="store.busy || store.isGameOver"
            @investigate="store.investigate"
          />
          <HistoryLog :turns="store.history" />
        </div>
      </template>
    </SwipeTrack>

    <LastTurnToast :turn="store.lastTurn" />
  </template>

  <div v-else-if="store.loadStatus === 'not-found'" class="card not-found">
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

  <div v-else class="card not-found">
    <h2>Could not load the game</h2>
    <p class="muted">
      Game <span class="mono">{{ gameId }}</span> may well exist, but the backend did not answer.
    </p>
    <MessageBanner
      :message="store.error"
      :details="store.errorDetails"
      @dismiss="store.clearError"
    />
    <div class="not-found__actions">
      <button class="btn btn-primary" type="button" :disabled="store.loading.game" @click="retry">
        Try again
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

/* Desktop (900px and up) */
.play-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  align-items: start;
  margin-top: 16px;
}

.play-side {
  display: grid;
  gap: 16px;
  align-content: start;
  /* The status bar is sticky too (8px from the top, about 73px tall), so the sidebar
     sticks below it instead of sliding underneath. */
  position: sticky;
  top: 96px;
}

/* Phone: a sticky status bar and tab bar, then the swipeable views */
.mobile-header {
  position: sticky;
  top: 0;
  z-index: 6;
  margin-top: -8px;
  padding: 8px 0;
  background: var(--bg);
}

.mobile-track {
  margin-top: 8px;
}

.mobile-stack {
  display: grid;
  gap: 16px;
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
