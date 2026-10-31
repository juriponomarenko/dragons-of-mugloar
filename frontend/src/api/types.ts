/**
 * Shapes returned by the backend REST API (see backend `web/dto`).
 */

export interface GameView {
  gameId: string
  lives: number
  gold: number
  level: number
  score: number
  highScore: number
  turn: number
  gameOver: boolean
  purchases: Record<string, number>
  createdAt: string
}

export type RiskLevel = 'SAFE' | 'MODERATE' | 'DANGEROUS'

export interface AdView {
  adId: string
  message: string
  reward: number
  expiresIn: number
  probability: string
  successRate: number
  expectedValue: number
  score: number
  risk: RiskLevel
  eligible: boolean
  recommended: boolean
  encryption: string
  notes: string[]
}

export interface ShopItemView {
  id: string
  name: string
  cost: number
  affordable: boolean
  timesBought: number
  suggested: boolean
}

export type ActionType = 'SOLVE' | 'BUY' | 'INVESTIGATE'

export interface TurnView {
  turn: number
  action: ActionType
  targetId: string
  description: string
  success: boolean
  message: string
  reason: string
  automated: boolean
  state: GameView
  at: string
}

export interface AutoplayView {
  state: GameView
  turns: TurnView[]
  gameOver: boolean
  stoppedBecause: string
}

export interface Reputation {
  people: number
  state: number
  underworld: number
}

/** An investigation costs a turn in the game, so it comes back with the turn it was recorded as. */
export interface InvestigationView {
  reputation: Reputation
  turn: TurnView
}

/** RFC 9457 problem document produced by the backend for every error. */
export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  instance?: string
  errors?: string[]
  upstreamStatus?: number
}
