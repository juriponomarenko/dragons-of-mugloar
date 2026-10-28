import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, gameApi } from '../client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('gameApi', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts a game with POST and parses the body', async () => {
    fetchMock.mockImplementation(async () => jsonResponse({ gameId: 'g1', lives: 3 }, 201))

    const game = await gameApi.startGame()

    expect(game.gameId).toBe('g1')
    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe('/api/games')
    expect(init?.method).toBe('POST')
  })

  it('builds the solve and buy URLs from validated ids', async () => {
    fetchMock.mockImplementation(async () => jsonResponse({ turn: 1 }))

    await gameApi.solveAd('g1', 'ad-1')
    await gameApi.buyItem('g1', 'hpot')

    expect(fetchMock.mock.calls[0]![0]).toBe('/api/games/g1/ads/ad-1/solve')
    expect(fetchMock.mock.calls[1]![0]).toBe('/api/games/g1/shop/hpot/buy')
  })

  it('sends the optional autoplay limit as JSON', async () => {
    fetchMock.mockImplementation(async () => jsonResponse({ turns: [] }))

    await gameApi.autoplay('g1', 10)
    await gameApi.autoplay('g1')

    const withBody = fetchMock.mock.calls[0]![1]!
    expect(withBody.body).toBe('{"maxTurns":10}')
    expect((withBody.headers as Record<string, string>)['Content-Type']).toBe('application/json')
    expect(fetchMock.mock.calls[1]![1]!.body).toBeUndefined()
  })

  it('refuses malformed ids before touching the network', async () => {
    await expect(gameApi.getGame('bad id!')).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
    })
    await expect(gameApi.solveAd('g1', '../etc')).rejects.toBeInstanceOf(ApiError)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('turns a problem document into an ApiError', async () => {
    fetchMock.mockImplementation(async () =>
      jsonResponse(
        {
          status: 422,
          title: 'Action not possible',
          detail: 'Not enough gold',
          errors: ['gold: too low'],
        },
        422,
      ),
    )

    const error = await gameApi.buyItem('g1', 'hpot').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(422)
    expect(apiError.title).toBe('Action not possible')
    expect(apiError.message).toBe('Not enough gold')
    expect(apiError.errors).toEqual(['gold: too low'])
    expect(apiError.isNetworkError).toBe(false)
  })

  it('falls back to the HTTP status when the error body is not JSON', async () => {
    fetchMock.mockImplementation(
      async () =>
        new Response('<html>Bad Gateway</html>', { status: 502, statusText: 'Bad Gateway' }),
    )

    const error = (await gameApi.getAds('g1').catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(502)
    expect(error.title).toBe('Bad Gateway')
    expect(error.message).toContain('502')
  })

  it('wraps network failures as status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    const error = (await gameApi.getShop('g1').catch((e: unknown) => e)) as ApiError

    expect(error.isNetworkError).toBe(true)
    expect(error.message).toContain('Failed to fetch')
  })
})
