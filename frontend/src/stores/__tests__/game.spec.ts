import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError, gameApi } from '@/api/client'
import { adView, gameView, shopItemView, turnView } from '@/__tests__/fixtures'
import { useGameStore } from '../game'

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return {
    ...actual,
    gameApi: {
      startGame: vi.fn<typeof actual.gameApi.startGame>(),
      listGames: vi.fn<typeof actual.gameApi.listGames>(),
      getGame: vi.fn<typeof actual.gameApi.getGame>(),
      getAds: vi.fn<typeof actual.gameApi.getAds>(),
      solveAd: vi.fn<typeof actual.gameApi.solveAd>(),
      getShop: vi.fn<typeof actual.gameApi.getShop>(),
      buyItem: vi.fn<typeof actual.gameApi.buyItem>(),
      investigate: vi.fn<typeof actual.gameApi.investigate>(),
      autoplayStep: vi.fn<typeof actual.gameApi.autoplayStep>(),
      autoplay: vi.fn<typeof actual.gameApi.autoplay>(),
      getHistory: vi.fn<typeof actual.gameApi.getHistory>(),
    },
  }
})

const api = vi.mocked(gameApi)

describe('game store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    api.getAds.mockResolvedValue([
      adView({ adId: 'a1', recommended: true }),
      adView({ adId: 'a2', eligible: false }),
    ])
    api.getShop.mockResolvedValue([
      shopItemView({ id: 'hpot', suggested: true }),
      shopItemView({ id: 'cs', cost: 100, affordable: false }),
    ])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts a game, loads the board and remembers the id', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    const store = useGameStore()

    const id = await store.startGame()

    expect(id).toBe('g1')
    expect(store.game?.gameId).toBe('g1')
    expect(store.ads).toHaveLength(2)
    expect(store.shop).toHaveLength(2)
    expect(store.recommendedAd?.adId).toBe('a1')
    expect(store.eligibleAds.map((a) => a.adId)).toEqual(['a1'])
    expect(store.suggestedItem?.id).toBe('hpot')
    expect(store.lastGameId).toBe('g1')
    expect(localStorage.getItem('mugloar.lastGameId')).toBe('g1')
    expect(store.loading.game).toBe(false)
  })

  it('reports a failed start without leaving a half-loaded game', async () => {
    api.startGame.mockRejectedValue(new ApiError(503, 'Game API error', 'upstream down'))
    const store = useGameStore()

    const id = await store.startGame()

    expect(id).toBeNull()
    expect(store.game).toBeNull()
    expect(store.error).toBe('upstream down')
  })

  it('loads an existing game with its history', async () => {
    api.getGame.mockResolvedValue(gameView({ gameId: 'g2', turn: 3 }))
    api.getHistory.mockResolvedValue([turnView(), turnView({ turn: 2 })])
    const store = useGameStore()

    expect(await store.loadGame('g2')).toBe(true)

    expect(store.game?.turn).toBe(3)
    expect(store.history).toHaveLength(2)
    expect(api.getAds).toHaveBeenCalledWith('g2')
  })

  it('forgets a remembered game the server no longer knows', async () => {
    localStorage.setItem('mugloar.lastGameId', 'gone')
    setActivePinia(createPinia())
    api.getGame.mockRejectedValue(new ApiError(404, 'Game not found', 'unknown'))
    api.getHistory.mockRejectedValue(new ApiError(404, 'Game not found', 'unknown'))
    const store = useGameStore()
    expect(store.lastGameId).toBe('gone')

    expect(await store.loadGame('gone')).toBe(false)

    expect(store.lastGameId).toBeNull()
    expect(localStorage.getItem('mugloar.lastGameId')).toBeNull()
    expect(store.error).toBe('unknown')
    expect(store.loadStatus).toBe('not-found')
  })

  it('tells a backend outage apart from an unknown game', async () => {
    localStorage.setItem('mugloar.lastGameId', 'g9')
    setActivePinia(createPinia())
    api.getGame.mockRejectedValue(new ApiError(503, 'Game API error', 'upstream down'))
    api.getHistory.mockRejectedValue(new ApiError(503, 'Game API error', 'upstream down'))
    const store = useGameStore()

    expect(await store.loadGame('g9')).toBe(false)

    expect(store.loadStatus).toBe('failed')
    expect(store.error).toBe('upstream down')
    expect(store.lastGameId).toBe('g9')
  })

  it('reloads the state and history when the same game is opened again', async () => {
    api.getGame.mockResolvedValue(gameView({ gameId: 'g2', turn: 3 }))
    api.getHistory.mockResolvedValue([turnView()])
    const store = useGameStore()
    await store.loadGame('g2')
    api.getGame.mockResolvedValue(gameView({ gameId: 'g2', turn: 7, score: 300 }))
    api.getHistory.mockResolvedValue([turnView(), turnView({ turn: 2 })])

    expect(await store.loadGame('g2')).toBe(true)

    expect(store.game?.turn).toBe(7)
    expect(store.history).toHaveLength(2)
  })

  it('drops a late answer about a game that is no longer on screen', async () => {
    api.startGame.mockResolvedValueOnce(gameView({ gameId: 'A' }))
    let finishA: (value: Awaited<ReturnType<typeof gameApi.autoplay>>) => void = () => {}
    api.autoplay.mockReturnValueOnce(new Promise((resolve) => (finishA = resolve)))
    const store = useGameStore()
    await store.startGame()
    const pendingFinish = store.finishGame()

    api.startGame.mockResolvedValueOnce(gameView({ gameId: 'B' }))
    await store.startGame()
    finishA({
      state: gameView({ gameId: 'A', gameOver: true, score: 999 }),
      turns: [turnView({ automated: true })],
      gameOver: true,
      stoppedBecause: 'Game over',
    })
    await pendingFinish

    expect(store.game?.gameId).toBe('B')
    expect(store.history).toHaveLength(0)
    expect(store.isGameOver).toBe(false)
    // the late request must not leave the new game locked
    expect(store.busy).toBe(false)
    api.solveAd.mockResolvedValue(turnView({ state: gameView({ gameId: 'B', turn: 1 }) }))
    expect(await store.solveAd('a1')).not.toBeNull()
  })

  it('does not hand back the id of a start that was abandoned for another game', async () => {
    api.startGame.mockResolvedValueOnce(gameView({ gameId: 'A' }))
    let boardOfA: (ads: ReturnType<typeof adView>[]) => void = () => {}
    api.getAds.mockReturnValueOnce(new Promise((resolve) => (boardOfA = resolve)))
    const store = useGameStore()
    const first = store.startGame()
    await vi.waitFor(() => expect(api.getAds).toHaveBeenCalledWith('A'))

    api.startGame.mockResolvedValueOnce(gameView({ gameId: 'B' }))
    expect(await store.startGame()).toBe('B')
    boardOfA([adView()])

    expect(await first).toBeNull()
    expect(store.game?.gameId).toBe('B')
  })

  it('holds actions while the same game is being reloaded and ignores an older answer', async () => {
    api.getGame.mockResolvedValue(gameView({ gameId: 'g2', turn: 3 }))
    api.getHistory.mockResolvedValue([turnView(), turnView({ turn: 2 }), turnView({ turn: 3 })])
    const store = useGameStore()
    await store.loadGame('g2')
    let answer: (state: ReturnType<typeof gameView>) => void = () => {}
    api.getGame.mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
    api.getHistory.mockResolvedValueOnce([turnView()])

    const resume = store.loadGame('g2')
    await vi.waitFor(() => expect(store.busy).toBe(true))
    expect(await store.solveAd('a1')).toBeNull()
    expect(api.solveAd).not.toHaveBeenCalled()
    answer(gameView({ gameId: 'g2', turn: 1 }))
    await resume

    expect(store.game?.turn).toBe(3)
    expect(store.history).toHaveLength(3)
    expect(store.busy).toBe(false)
  })

  it('does not double the turns a resume already listed while a server-side finish was running', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    let finish: (value: Awaited<ReturnType<typeof gameApi.autoplay>>) => void = () => {}
    api.autoplay.mockReturnValueOnce(new Promise((resolve) => (finish = resolve)))
    const store = useGameStore()
    await store.startGame()
    const pendingFinish = store.finishGame()

    // the player navigated away and back while the server was still playing
    const first = turnView({ turn: 1, automated: true })
    api.getGame.mockResolvedValue(gameView({ gameId: 'g1', turn: 1 }))
    api.getHistory.mockResolvedValue([first])
    await store.loadGame('g1')
    expect(store.history).toHaveLength(1)

    finish({
      state: gameView({ gameId: 'g1', turn: 2, gameOver: true, lives: 0 }),
      turns: [first, turnView({ turn: 2, automated: true })],
      gameOver: true,
      stoppedBecause: 'Game over',
    })
    await pendingFinish

    expect(store.history.map((t) => t.turn)).toEqual([1, 2])
    expect(store.automatedTurns).toBe(2)
    expect(store.isGameOver).toBe(true)
  })

  it('drops a resumed game the backend has forgotten', async () => {
    api.getGame.mockResolvedValue(gameView({ gameId: 'g2', turn: 3 }))
    api.getHistory.mockResolvedValue([turnView()])
    const store = useGameStore()
    await store.loadGame('g2')
    expect(store.lastGameId).toBe('g2')

    // the backend restarted in between: it keeps games in memory
    const gone = new ApiError(404, 'Game not found', 'unknown')
    api.getGame.mockRejectedValue(gone)
    api.getHistory.mockRejectedValue(gone)

    expect(await store.loadGame('g2')).toBe(false)

    expect(store.game).toBeNull()
    expect(store.loadStatus).toBe('not-found')
    expect(store.lastGameId).toBeNull()
    expect(store.error).toBe('unknown')
    expect(store.busy).toBe(false)
  })

  it('drops the game when an action finds the backend has forgotten it', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    api.solveAd.mockRejectedValue(new ApiError(404, 'Game not found', 'unknown'))
    const store = useGameStore()
    await store.startGame()

    await store.solveAd('a1')

    expect(store.game).toBeNull()
    expect(store.loadStatus).toBe('not-found')
    expect(store.lastGameId).toBeNull()
  })

  it('reloads everything after an action whose outcome is unknown', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    api.solveAd.mockRejectedValue(
      new ApiError(0, 'Backend unreachable', 'Could not reach the backend'),
    )
    api.getGame.mockResolvedValue(gameView({ gameId: 'g1', turn: 1, gold: 50, score: 50 }))
    api.getHistory.mockResolvedValue([turnView()])
    const store = useGameStore()
    await store.startGame()

    await store.solveAd('a1')

    expect(store.error).toBe('Could not reach the backend')
    expect(store.game?.score).toBe(50)
    expect(store.history).toHaveLength(1)
  })

  it('solving an ad records the turn, updates the state and refreshes the board', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    const turn = turnView({ state: gameView({ gold: 50, score: 50, turn: 1 }) })
    api.solveAd.mockResolvedValue(turn)
    const store = useGameStore()
    await store.startGame()
    api.getAds.mockClear()

    const result = await store.solveAd('a1')

    expect(result).toEqual(turn)
    expect(api.solveAd).toHaveBeenCalledWith('g1', 'a1')
    expect(store.game?.gold).toBe(50)
    expect(store.history).toEqual([turn])
    expect(store.lastTurn).toEqual(turn)
    expect(api.getAds).toHaveBeenCalledTimes(1)
  })

  it('does not refresh the board once the game is over', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1', lives: 1 }))
    api.solveAd.mockResolvedValue(
      turnView({ success: false, state: gameView({ lives: 0, gameOver: true }) }),
    )
    const store = useGameStore()
    await store.startGame()
    api.getAds.mockClear()

    await store.solveAd('a1')

    expect(store.isGameOver).toBe(true)
    expect(api.getAds).not.toHaveBeenCalled()
  })

  it('surfaces a rejected action and resyncs with the server', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    api.buyItem.mockRejectedValue(
      new ApiError(422, 'Action not possible', 'Not enough gold', ['gold: short']),
    )
    api.getGame.mockResolvedValue(gameView({ gameId: 'g1', gold: 7 }))
    const store = useGameStore()
    await store.startGame()

    const result = await store.buyItem('cs')

    expect(result).toBeNull()
    expect(store.error).toBe('Not enough gold')
    expect(store.errorDetails).toEqual(['gold: short'])
    expect(store.game?.gold).toBe(7)
    expect(store.loading.action).toBe(false)
  })

  it('ignores actions while another one is in flight', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    let release: (turn: ReturnType<typeof turnView>) => void = () => {}
    api.solveAd.mockReturnValue(new Promise((resolve) => (release = resolve)))
    const store = useGameStore()
    await store.startGame()

    const first = store.solveAd('a1')
    const second = await store.solveAd('a2')

    expect(second).toBeNull()
    expect(api.solveAd).toHaveBeenCalledTimes(1)
    release(turnView())
    await first
  })

  it('runs autoplay turn by turn until the game ends', async () => {
    vi.useFakeTimers()
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    api.autoplayStep
      .mockResolvedValueOnce(turnView({ turn: 1, automated: true }))
      .mockResolvedValueOnce(turnView({ turn: 2, automated: true }))
      .mockResolvedValueOnce(
        turnView({
          turn: 3,
          automated: true,
          state: gameView({ lives: 0, gameOver: true, score: 1200 }),
        }),
      )
    const store = useGameStore()
    await store.startGame()
    store.setAutoplayDelay(100)
    api.getAds.mockClear()

    const run = store.runAutoplay()
    expect(store.autoplay.running).toBe(true)
    await vi.advanceTimersByTimeAsync(1000)
    await run

    expect(api.autoplayStep).toHaveBeenCalledTimes(3)
    // the board is refreshed after each step while the game is alive; not once it is over
    expect(api.getAds).toHaveBeenCalledTimes(2)
    expect(store.autoplay.running).toBe(false)
    expect(store.isGameOver).toBe(true)
    expect(store.game?.score).toBe(1200)
    expect(store.automatedTurns).toBe(3)
  })

  it('stops autoplay when asked and when a step fails', async () => {
    vi.useFakeTimers()
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    api.autoplayStep.mockResolvedValue(turnView({ automated: true }))
    const store = useGameStore()
    await store.startGame()
    store.setAutoplayDelay(100)

    const run = store.runAutoplay()
    await vi.advanceTimersByTimeAsync(250)
    store.stopAutoplay()
    await vi.advanceTimersByTimeAsync(500)
    await run
    const callsAfterStop = api.autoplayStep.mock.calls.length

    expect(store.autoplay.running).toBe(false)
    expect(callsAfterStop).toBeGreaterThanOrEqual(2)
    expect(callsAfterStop).toBeLessThanOrEqual(4)

    api.autoplayStep.mockRejectedValue(new ApiError(502, 'Game API error', 'upstream exploded'))
    api.getGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    const second = store.runAutoplay()
    await vi.advanceTimersByTimeAsync(500)
    await second

    expect(store.error).toBe('upstream exploded')
    expect(store.autoplay.running).toBe(false)
  })

  it('clamps the autoplay delay to the allowed range', () => {
    const store = useGameStore()

    store.setAutoplayDelay(5)
    expect(store.autoplay.delayMs).toBe(100)
    store.setAutoplayDelay(99999)
    expect(store.autoplay.delayMs).toBe(2000)
    store.setAutoplayDelay(750.4)
    expect(store.autoplay.delayMs).toBe(750)
  })

  it('finishes the game on the server and appends its turns', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    api.autoplay.mockResolvedValue({
      state: gameView({ gameOver: true, lives: 0, score: 3000 }),
      turns: [turnView({ turn: 1, automated: true }), turnView({ turn: 2, automated: true })],
      gameOver: true,
      stoppedBecause: 'Game over',
    })
    const store = useGameStore()
    await store.startGame()

    await store.finishGame()

    expect(api.autoplay).toHaveBeenCalledWith('g1')
    expect(store.history).toHaveLength(2)
    expect(store.game?.score).toBe(3000)
    expect(store.isGameOver).toBe(true)
    expect(store.error).toBeNull()
  })

  it('flags a server-side finish while it runs and clears it after', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    let finish: (value: Awaited<ReturnType<typeof gameApi.autoplay>>) => void = () => {}
    api.autoplay.mockReturnValueOnce(new Promise((resolve) => (finish = resolve)))
    const store = useGameStore()
    await store.startGame()

    const pending = store.finishGame()
    await vi.waitFor(() => expect(store.loading.finish).toBe(true))
    expect(store.busy).toBe(true)
    finish({
      state: gameView({ gameId: 'g1', turn: 1, gameOver: true, lives: 0 }),
      turns: [turnView({ automated: true })],
      gameOver: true,
      stoppedBecause: 'Game over',
    })
    await pending

    expect(store.loading.finish).toBe(false)
    expect(store.busy).toBe(false)
  })

  it('shows why a server-side run stopped early', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    api.autoplay.mockResolvedValue({
      state: gameView({ turn: 5 }),
      turns: [],
      gameOver: false,
      stoppedBecause: 'Stopped after repeated upstream errors',
    })
    const store = useGameStore()
    await store.startGame()

    await store.finishGame()

    expect(store.notice).toBe('Stopped after repeated upstream errors')
    expect(store.error).toBeNull()
  })

  it('resyncs the game when a server-side finish is rejected', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    api.autoplay.mockRejectedValue(new ApiError(409, 'Game over', 'the game is over'))
    api.getGame.mockResolvedValue(gameView({ gameId: 'g1', lives: 0, gameOver: true, score: 800 }))
    const store = useGameStore()
    await store.startGame()

    await store.finishGame()

    expect(store.error).toBe('the game is over')
    expect(store.isGameOver).toBe(true)
    expect(store.game?.score).toBe(800)
    expect(store.loading.finish).toBe(false)
  })

  it('shows the final state when the board reports the game as over', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    const store = useGameStore()
    await store.startGame()
    const over = new ApiError(409, 'Game over', 'the game server no longer knows it')
    api.getAds.mockRejectedValue(over)
    api.getShop.mockRejectedValue(over)
    api.getGame.mockResolvedValue(gameView({ gameId: 'g1', lives: 0, gameOver: true, score: 420 }))

    await store.refreshBoard()

    expect(store.isGameOver).toBe(true)
    expect(store.game?.score).toBe(420)
    expect(store.error).toContain('no longer knows')
  })

  it('investigating reputation records the turn it costs and refreshes the board', async () => {
    api.startGame.mockResolvedValue(gameView({ gameId: 'g1' }))
    const turn = turnView({
      action: 'INVESTIGATE',
      targetId: 'reputation',
      description: 'Reputation investigation',
      message: 'People 1, state -2, underworld 0',
      state: gameView({ gameId: 'g1', turn: 1 }),
    })
    api.investigate.mockResolvedValue({ reputation: { people: 1, state: -2, underworld: 0 }, turn })
    const store = useGameStore()
    await store.startGame()
    api.getAds.mockClear()

    await store.investigate()

    expect(store.reputation).toEqual({ people: 1, state: -2, underworld: 0 })
    expect(store.game?.turn).toBe(1)
    expect(store.history).toEqual([turn])
    expect(api.getAds).toHaveBeenCalledTimes(1)
  })
})
