import type {
  AdView,
  AutoplayView,
  GameView,
  ProblemDetail,
  Reputation,
  ShopItemView,
  TurnView,
} from './types'

/** Thrown for every failed request; carries the backend's problem document when there is one. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly errors: string[]

  constructor(status: number, title: string, detail: string, errors: string[] = []) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.errors = errors
  }

  /** No usable answer came back (network down, CORS, timeout, or a body cut off mid-way). */
  get isNetworkError(): boolean {
    return this.status === 0
  }
}

const BASE_URL = '/api/games'

/** Only ids matching the backend's pattern are ever put into a URL. */
const ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/

function requireId(value: string, what: string): string {
  if (!ID_PATTERN.test(value)) {
    throw new ApiError(400, 'Invalid request', `${what} '${value}' is not a valid identifier`)
  }
  return value
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(BASE_URL + path, {
      ...init,
      headers: { Accept: 'application/json', ...init.headers },
    })
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause)
    throw new ApiError(0, 'Backend unreachable', `Could not reach the backend: ${message}`)
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  try {
    return (await response.json()) as T
  } catch (cause) {
    // The headers arrived but the body did not: the backend may well have acted on the request.
    const message = cause instanceof Error ? cause.message : String(cause)
    throw new ApiError(0, 'Backend unreachable', `Could not read the backend's answer: ${message}`)
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: Partial<ProblemDetail> = {}
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // not a JSON body; fall back to the HTTP status text
  }
  return new ApiError(
    response.status,
    problem.title ?? response.statusText ?? 'Request failed',
    problem.detail ?? `Request failed with HTTP ${response.status}`,
    problem.errors ?? [],
  )
}

function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    ...(body !== undefined
      ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
      : {}),
  })
}

/**
 * Typed client for the backend. Exposed as a plain object so tests can mock individual calls.
 */
export const gameApi = {
  startGame: async () => post<GameView>(''),
  listGames: async () => request<GameView[]>(''),
  getGame: async (gameId: string) => request<GameView>(`/${requireId(gameId, 'Game id')}`),
  getAds: async (gameId: string) => request<AdView[]>(`/${requireId(gameId, 'Game id')}/ads`),
  solveAd: async (gameId: string, adId: string) =>
    post<TurnView>(`/${requireId(gameId, 'Game id')}/ads/${requireId(adId, 'Ad id')}/solve`),
  getShop: async (gameId: string) =>
    request<ShopItemView[]>(`/${requireId(gameId, 'Game id')}/shop`),
  buyItem: async (gameId: string, itemId: string) =>
    post<TurnView>(`/${requireId(gameId, 'Game id')}/shop/${requireId(itemId, 'Item id')}/buy`),
  investigate: async (gameId: string) =>
    post<Reputation>(`/${requireId(gameId, 'Game id')}/reputation`),
  autoplayStep: async (gameId: string) =>
    post<TurnView>(`/${requireId(gameId, 'Game id')}/autoplay/step`),
  autoplay: async (gameId: string, maxTurns?: number) =>
    post<AutoplayView>(
      `/${requireId(gameId, 'Game id')}/autoplay`,
      maxTurns !== undefined ? { maxTurns } : undefined,
    ),
  getHistory: async (gameId: string) =>
    request<TurnView[]>(`/${requireId(gameId, 'Game id')}/history`),
}

export type GameApi = typeof gameApi
