<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { gameApi } from '@/api/client'
import type { GameView } from '@/api/types'
import { useGameStore } from '@/stores/game'
import ErrorBanner from '@/components/ErrorBanner.vue'
import { gold } from '@/utils/format'

const router = useRouter()
const store = useGameStore()
const recentGames = ref<GameView[]>([])

onMounted(async () => {
  try {
    recentGames.value = (await gameApi.listGames()).slice(0, 8)
  } catch {
    // the list is a convenience; the page works without it
  }
})

async function startNewGame() {
  const id = await store.startGame()
  if (id) {
    await router.push({ name: 'play', params: { gameId: id } })
  }
}
</script>

<template>
  <section class="hero card">
    <h1 class="hero__title">Train your dragon. Pick your battles.</h1>
    <p class="hero__text">
      Take on tasks from the message board, earn gold, buy upgrades and keep your dragon alive. The
      advisor ranks every ad by its expected value and risk, and the autoplayer can take over
      whenever you like.
    </p>
    <ErrorBanner :message="store.error" :details="store.errorDetails" @dismiss="store.clearError" />
    <div class="hero__actions">
      <button
        class="btn btn-primary"
        type="button"
        :disabled="store.loading.game"
        @click="startNewGame"
      >
        {{ store.loading.game ? 'Starting…' : 'Start a new game' }}
      </button>
      <RouterLink
        v-if="store.lastGameId"
        class="btn"
        :to="{ name: 'play', params: { gameId: store.lastGameId } }"
      >
        Resume last game
      </RouterLink>
    </div>
  </section>

  <section v-if="recentGames.length" class="recent">
    <h2 class="card-title">Games on this server</h2>
    <ul class="recent__list">
      <li v-for="g in recentGames" :key="g.gameId" class="recent__item card">
        <RouterLink :to="{ name: 'play', params: { gameId: g.gameId } }" class="recent__link">
          <span class="mono">{{ g.gameId }}</span>
          <span class="badge" :class="g.gameOver ? 'badge-muted' : 'badge-success'">
            {{ g.gameOver ? 'finished' : 'in progress' }}
          </span>
        </RouterLink>
        <div class="recent__stats muted">
          score {{ gold(g.score) }} · turn {{ g.turn }} · lives {{ g.lives }}
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.hero {
  padding: 32px 24px;
  margin-bottom: 24px;
}

.hero__title {
  font-size: clamp(1.6rem, 4vw, 2.4rem);
  margin-bottom: 12px;
}

.hero__text {
  max-width: 60ch;
  color: var(--muted);
  margin-bottom: 20px;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.recent__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
}

.recent__item {
  padding: 12px 16px;
}

.recent__link {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: var(--text);
  font-weight: 600;
}

.recent__stats {
  font-size: 0.85rem;
  margin-top: 4px;
}
</style>
