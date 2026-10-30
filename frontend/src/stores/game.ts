import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, gameApi } from '@/api/client'
import type { AdView, GameView, Reputation, ShopItemView, TurnView } from '@/api/types'

const LAST_GAME_KEY = 'mugloar.lastGameId'

export const AUTOPLAY_MIN_DELAY = 100
export const AUTOPLAY_MAX_DELAY = 2000

/** How the game named in the URL came to be (or not be) on screen. */
export type LoadStatus = 'idle' | 'loading' | 'ready' | 'not-found' | 'failed'

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

/** A rejected action means the game moved on without us (expired ad, server-side game over). */
function isStaleStateError(cause: unknown): boolean {
  return cause instanceof ApiError && (cause.status === 422 || cause.status === 409)
}

/**
 * The request may or may not have reached the game: a dropped connection or a gateway error
 * says nothing about whether the backend went on and took the turn.
 */
function isUncertainOutcome(cause: unknown): boolean {
  return (
    cause instanceof ApiError &&
    (cause.isNetworkError || cause.status === 502 || cause.status === 503 || cause.status === 504)
  )
}

/**
 * All state for the game currently on screen. Every mutation goes through an action so the
 * components stay dumb, and every action funnels errors into `error` for a single banner.
 *
 * Every switch of game bumps a generation counter, and every response is checked against the
 * generation it was requested in, so a slow answer about a game that is no longer on screen is
 * dropped instead of overwriting the one that is.
 */
export const useGameStore = defineStore('game', () => {
  const game = ref<GameView | null>(null)
  const ads = ref<AdView[]>([])
  const shop = ref<ShopItemView[]>([])
  const history = ref<TurnView[]>([])
  const reputation = ref<Reputation | null>(null)
  const lastTurn = ref<TurnView | null>(null)
  const error = ref<string | null>(null)
  /** Something worth knowing that is not a failure, e.g. why a server-side run stopped early. */
  const notice = ref<string | null>(null)
  const errorDetails = ref<string[]>([])
  const loading = reactive({ game: false, board: false, action: false })
  const loadStatus = ref<LoadStatus>('idle')
  const autoplay = reactive({ running: false, stopRequested: false, delayMs: 500 })
  const lastGameId = ref<string | null>(readLastGameId())

  let generation = 0
  const isCurrent = (requested: number) => requested === generation

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

  function clearNotice(): void {
    notice.value = null
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

  /** Forgets the game on screen; any response still in flight for it will be dropped. */
  function reset(): void {
    generation++
    // a request of the old generation skips its own cleanup, so the flags are cleared here
    loading.action = false
    loading.board = false
    game.value = null
    ads.value = []
    shop.value = []
    history.value = []
    reputation.value = null
    lastTurn.value = null
    loadStatus.value = 'idle'
    clearError()
    clearNotice()
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
    const requested = generation
    loading.board = true
    try {
      const [nextAds, nextShop] = await Promise.all([gameApi.getAds(id), gameApi.getShop(id)])
      if (isCurrent(requested)) {
        ads.value = nextAds
        shop.value = nextShop
      }
    } catch (cause) {
      if (isCurrent(requested)) {
        captureError(cause)
        if (isStaleStateError(cause)) {
          // the game ended behind our back, or the game server forgot it: show its final state
          await reloadState(id, requested)
        }
      }
    } finally {
      loading.board = false
    }
  }

  /** Reloads the state and the history from the server, then the board. */
  async function reloadFromServer(id: string, requested: number): Promise<void> {
    try {
      const [state, turns] = await Promise.all([gameApi.getGame(id), gameApi.getHistory(id)])
      if (isCurrent(requested)) {
        game.value = state
        history.value = turns
      }
    } catch {
      // keep what we have; the original error is already on screen
    }
    if (isCurrent(requested)) {
      await refreshBoard()
    }
  }

  /** Starts a brand-new game and loads its board. Returns the new game id or null on failure. */
  async function startGame(): Promise<string | null> {
    reset()
    const requested = generation
    loading.game = true
    loadStatus.value = 'loading'
    try {
      const started = await gameApi.startGame()
      if (!isCurrent(requested)) {
        return null
      }
      game.value = started
      loadStatus.value = 'ready'
      lastGameId.value = started.gameId
      writeLastGameId(started.gameId)
      await refreshBoard()
      return started.gameId
    } catch (cause) {
      if (isCurrent(requested)) {
        captureError(cause)
        loadStatus.value = 'failed'
      }
      return null
    } finally {
      if (isCurrent(requested)) {
        loading.game = false
      }
    }
  }

  /**
   * Loads an existing game (e.g. after a page refresh). Returns false when it could not be
   * loaded; `loadStatus` tells whether the game is unknown or the server could not be reached.
   */
  async function loadGame(id: string): Promise<boolean> {
    if (gameId.value === id && game.value) {
      // Already on screen (e.g. navigated away and back): the server may have moved on meanwhile.
      await reloadFromServer(id, generation)
      return true
    }
    reset()
    const requested = generation
    loading.game = true
    loadStatus.value = 'loading'
    try {
      const [state, turns] = await Promise.all([gameApi.getGame(id), gameApi.getHistory(id)])
      if (!isCurrent(requested)) {
        return false
      }
      game.value = state
      history.value = turns
      loadStatus.value = 'ready'
      lastGameId.value = id
      writeLastGameId(id)
      await refreshBoard()
      return true
    } catch (cause) {
      if (!isCurrent(requested)) {
        return false
      }
      captureError(cause)
      const notFound = cause instanceof ApiError && cause.status === 404
      loadStatus.value = notFound ? 'not-found' : 'failed'
      if (notFound && lastGameId.value === id) {
        lastGameId.value = null
        writeLastGameId(null)
      }
      return false
    } finally {
      if (isCurrent(requested)) {
        loading.game = false
      }
    }
  }

  /**
   * Runs one request that changes the game, applies its result and refreshes the board. Single
   * actions and the server-side finish share it: same guard, same error capture, same recovery.
   */
  async function mutate<T>(
    request: (id: string) => Promise<T>,
    apply: (result: T) => void,
  ): Promise<T | null> {
    const id = gameId.value
    if (!id || loading.action) {
      return null
    }
    const requested = generation
    clearError()
    loading.action = true
    try {
      const result = await request(id)
      if (!isCurrent(requested)) {
        return null
      }
      apply(result)
      await refreshBoard()
      return result
    } catch (cause) {
      if (isCurrent(requested)) {
        captureError(cause)
        await recoverFrom(cause, id, requested)
      }
      return null
    } finally {
      if (isCurrent(requested)) {
        loading.action = false
      }
    }
  }

  /** Runs one game action and records its turn. Returns the turn or null. */
  const performAction = (action: (id: string) => Promise<TurnView>) => mutate(action, applyTurn)

  /** Brings the screen back in line with the server after an action that may have changed it. */
  async function recoverFrom(cause: unknown, id: string, requested: number): Promise<void> {
    if (isUncertainOutcome(cause)) {
      await reloadFromServer(id, requested)
    } else if (isStaleStateError(cause)) {
      await reloadState(id, requested)
      if (isCurrent(requested)) {
        await refreshBoard()
      }
    }
  }

  /** Reloads lives, gold, score and turn alone; the board is the caller's business. */
  async function reloadState(id: string, requested: number): Promise<void> {
    try {
      const state = await gameApi.getGame(id)
      if (isCurrent(requested)) {
        game.value = state
      }
    } catch {
      // keep what we have; the original error is already on screen
    }
  }

  const solveAd = (adId: string) => performAction((id) => gameApi.solveAd(id, adId))
  const buyItem = (itemId: string) => performAction((id) => gameApi.buyItem(id, itemId))
  const autoplayStep = () => performAction((id) => gameApi.autoplayStep(id))

  async function investigate(): Promise<void> {
    const id = gameId.value
    if (!id) {
      return
    }
    const requested = generation
    clearError()
    try {
      const result = await gameApi.investigate(id)
      if (isCurrent(requested)) {
        reputation.value = result
      }
    } catch (cause) {
      if (isCurrent(requested)) {
        captureError(cause)
      }
    }
  }

  /**
   * Lets the backend strategy play turn by turn, with a pause between turns so the player can
   * follow. The board is refreshed after every step, so the card the advisor just solved
   * disappears and the next recommendation is visible before the next step is taken.
   */
  async function runAutoplay(): Promise<void> {
    if (autoplay.running || !game.value || isGameOver.value) {
      return
    }
    const requested = generation
    autoplay.running = true
    autoplay.stopRequested = false
    let boardIsStale = false
    try {
      while (
        isCurrent(requested) &&
        !autoplay.stopRequested &&
        game.value &&
        !game.value.gameOver
      ) {
        const turn = await performAction((id) => gameApi.autoplayStep(id))
        if (!turn) {
          // the step failed before the board could be refreshed, or the game changed under us
          boardIsStale = isCurrent(requested)
          break
        }
        if (turn.state.gameOver || autoplay.stopRequested) {
          break
        }
        await sleep(autoplay.delayMs)
      }
    } finally {
      autoplay.running = false
      autoplay.stopRequested = false
      if (boardIsStale) {
        await refreshBoard()
      }
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
    if (busy.value || isGameOver.value) {
      return
    }
    clearNotice()
    await mutate(
      (id) => gameApi.autoplay(id),
      (outcome) => {
        game.value = outcome.state
        history.value = [...history.value, ...outcome.turns]
        lastTurn.value = outcome.turns[outcome.turns.length - 1] ?? lastTurn.value
        if (!outcome.gameOver) {
          // Not a failure: the run stopped early (turn cap, upstream trouble) and the game goes on.
          notice.value = outcome.stoppedBecause
        }
      },
    )
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
    notice,
    loading,
    loadStatus,
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
    clearNotice,
    reset,
  }
})
