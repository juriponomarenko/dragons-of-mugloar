import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, gameApi } from '@/api/client'
import type { AdView, GameView, Reputation, ShopItemView, TurnView } from '@/api/types'

const LAST_GAME_KEY = 'mugloar.lastGameId'

export const AUTOPLAY_MIN_DELAY = 100
export const AUTOPLAY_MAX_DELAY = 2000

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function readLastGameId(): string | null {
  try {
    return localStorage.getItem(LAST_GAME_KEY)
  } catch {
    return null
  }
}

function writeLastGameId(gameId: string | null): void {
  try {
    if (gameId) {
      localStorage.setItem(LAST_GAME_KEY, gameId)
    } else {
      localStorage.removeItem(LAST_GAME_KEY)
    }
  } catch {
    // storage may be unavailable (private mode); remembering the game is a convenience only
  }
}

/**
 * All state for the game currently on screen. Every mutation goes through an action so the
 * components stay dumb, and every action funnels errors into `error` for a single banner.
 */
export const useGameStore = defineStore('game', () => {
  const game = ref<GameView | null>(null)
  const ads = ref<AdView[]>([])
  const shop = ref<ShopItemView[]>([])
  const history = ref<TurnView[]>([])
  const reputation = ref<Reputation | null>(null)
  const lastTurn = ref<TurnView | null>(null)
  const error = ref<string | null>(null)
  const errorDetails = ref<string[]>([])
  const loading = reactive({ game: false, board: false, action: false })
  const autoplay = reactive({ running: false, stopRequested: false, delayMs: 500 })
  const lastGameId = ref<string | null>(readLastGameId())

  const gameId = computed(() => game.value?.gameId ?? null)
  const isGameOver = computed(() => game.value?.gameOver ?? false)
  const busy = computed(() => loading.action || loading.game || autoplay.running)
  const recommendedAd = computed(() => ads.value.find((ad) => ad.recommended) ?? null)
  const eligibleAds = computed(() => ads.value.filter((ad) => ad.eligible))
  const suggestedItem = computed(() => shop.value.find((item) => item.suggested) ?? null)
  const automatedTurns = computed(() => history.value.filter((t) => t.automated).length)

  function clearError(): void {
    error.value = null
    errorDetails.value = []
  }

  function captureError(cause: unknown): void {
    if (cause instanceof ApiError) {
      error.value = cause.message
      errorDetails.value = cause.errors
    } else if (cause instanceof Error) {
      error.value = cause.message
      errorDetails.value = []
    } else {
      error.value = 'Something went wrong'
      errorDetails.value = []
    }
  }

  function reset(): void {
    game.value = null
    ads.value = []
    shop.value = []
    history.value = []
    reputation.value = null
    lastTurn.value = null
    clearError()
  }

  function applyTurn(turn: TurnView): void {
    game.value = turn.state
    lastTurn.value = turn
    history.value = [...history.value, turn]
  }

  async function refreshBoard(): Promise<void> {
    const id = gameId.value
    if (!id || isGameOver.value) {
      return
    }
    loading.board = true
    try {
      const [nextAds, nextShop] = await Promise.all([gameApi.getAds(id), gameApi.getShop(id)])
      ads.value = nextAds
      shop.value = nextShop
    } catch (cause) {
      captureError(cause)
    } finally {
      loading.board = false
    }
  }

  /** Starts a brand-new game and loads its board. Returns the new game id or null on failure. */
  async function startGame(): Promise<string | null> {
    reset()
    loading.game = true
    try {
      game.value = await gameApi.startGame()
      lastGameId.value = game.value.gameId
      writeLastGameId(game.value.gameId)
      await refreshBoard()
      return game.value.gameId
    } catch (cause) {
      captureError(cause)
      return null
    } finally {
      loading.game = false
    }
  }

  /** Loads an existing game (e.g. after a page refresh). Returns false if it is unknown. */
  async function loadGame(id: string): Promise<boolean> {
    if (gameId.value === id && game.value) {
      // Already on screen (e.g. navigated away and back): just pick up any board changes.
      await refreshBoard()
      return true
    }
    reset()
    loading.game = true
    try {
      const [state, turns] = await Promise.all([gameApi.getGame(id), gameApi.getHistory(id)])
      game.value = state
      history.value = turns
      lastGameId.value = id
      writeLastGameId(id)
      await refreshBoard()
      return true
    } catch (cause) {
      captureError(cause)
      if (cause instanceof ApiError && cause.status === 404 && lastGameId.value === id) {
        lastGameId.value = null
        writeLastGameId(null)
      }
      return false
    } finally {
      loading.game = false
    }
  }

  /** Runs one game action, records its turn and refreshes the board. Returns the turn or null. */
  async function performAction(
    action: (id: string) => Promise<TurnView>,
    { refresh = true }: { refresh?: boolean } = {},
  ): Promise<TurnView | null> {
    const id = gameId.value
    if (!id || loading.action) {
      return null
    }
    clearError()
    loading.action = true
    try {
      const turn = await action(id)
      applyTurn(turn)
      if (refresh) {
        await refreshBoard()
      }
      return turn
    } catch (cause) {
      captureError(cause)
      // The board may have moved on (expired ad, server-side game over); show the latest.
      if (cause instanceof ApiError && (cause.status === 422 || cause.status === 409)) {
        await resync(id)
      }
      return null
    } finally {
      loading.action = false
    }
  }

  async function resync(id: string): Promise<void> {
    try {
      game.value = await gameApi.getGame(id)
    } catch {
      // keep what we have; the original error is already on screen
    }
    await refreshBoard()
  }

  const solveAd = (adId: string) => performAction((id) => gameApi.solveAd(id, adId))
  const buyItem = (itemId: string) => performAction((id) => gameApi.buyItem(id, itemId))
  const autoplayStep = () => performAction((id) => gameApi.autoplayStep(id))

  async function investigate(): Promise<void> {
    const id = gameId.value
    if (!id) {
      return
    }
    clearError()
    try {
      reputation.value = await gameApi.investigate(id)
    } catch (cause) {
      captureError(cause)
    }
  }

  /** Lets the backend strategy play turn by turn, with a pause between turns so the player can follow. */
  async function runAutoplay(): Promise<void> {
    if (autoplay.running || !game.value || isGameOver.value) {
      return
    }
    autoplay.running = true
    autoplay.stopRequested = false
    try {
      while (!autoplay.stopRequested && game.value && !game.value.gameOver) {
        const turn = await performAction((id) => gameApi.autoplayStep(id), { refresh: false })
        if (!turn || turn.state.gameOver || autoplay.stopRequested) {
          break
        }
        await sleep(autoplay.delayMs)
      }
    } finally {
      autoplay.running = false
      autoplay.stopRequested = false
      await refreshBoard()
    }
  }

  function stopAutoplay(): void {
    if (autoplay.running) {
      autoplay.stopRequested = true
    }
  }

  function setAutoplayDelay(ms: number): void {
    autoplay.delayMs = Math.min(AUTOPLAY_MAX_DELAY, Math.max(AUTOPLAY_MIN_DELAY, Math.round(ms)))
  }

  /** Hands the whole rest of the game to the backend in one request. */
  async function finishGame(): Promise<void> {
    const id = gameId.value
    if (!id || busy.value || isGameOver.value) {
      return
    }
    clearError()
    loading.action = true
    try {
      const outcome = await gameApi.autoplay(id)
      game.value = outcome.state
      history.value = [...history.value, ...outcome.turns]
      lastTurn.value = outcome.turns[outcome.turns.length - 1] ?? lastTurn.value
      if (!outcome.gameOver) {
        error.value = outcome.stoppedBecause
      }
    } catch (cause) {
      captureError(cause)
    } finally {
      loading.action = false
      await refreshBoard()
    }
  }

  return {
    // state
    game,
    ads,
    shop,
    history,
    reputation,
    lastTurn,
    error,
    errorDetails,
    loading,
    autoplay,
    lastGameId,
    // getters
    gameId,
    isGameOver,
    busy,
    recommendedAd,
    eligibleAds,
    suggestedItem,
    automatedTurns,
    // actions
    startGame,
    loadGame,
    refreshBoard,
    solveAd,
    buyItem,
    investigate,
    autoplayStep,
    runAutoplay,
    stopAutoplay,
    setAutoplayDelay,
    finishGame,
    clearError,
    reset,
  }
})
