import type { AdView, GameView, ShopItemView, TurnView } from '@/api/types'

export function gameView(overrides: Partial<GameView> = {}): GameView {
  return {
    gameId: 'game1',
    lives: 3,
    gold: 0,
    level: 0,
    score: 0,
    highScore: 0,
    turn: 0,
    gameOver: false,
    purchases: {},
    createdAt: '2026-10-27T10:00:00Z',
    ...overrides,
  }
}

export function adView(overrides: Partial<AdView> = {}): AdView {
  return {
    adId: 'ad1',
    message: 'Help Thais Fairchild to reach an agreement',
    reward: 50,
    expiresIn: 5,
    probability: 'Piece of cake',
    successRate: 0.95,
    expectedValue: 47.5,
    score: 45,
    risk: 'SAFE',
    eligible: true,
    recommended: false,
    encryption: 'none',
    notes: [],
    ...overrides,
  }
}

export function shopItemView(overrides: Partial<ShopItemView> = {}): ShopItemView {
  return {
    id: 'hpot',
    name: 'Healing potion',
    cost: 50,
    affordable: true,
    timesBought: 0,
    suggested: false,
    ...overrides,
  }
}

export function turnView(overrides: Partial<TurnView> = {}): TurnView {
  return {
    turn: 1,
    action: 'SOLVE',
    targetId: 'ad1',
    description: 'Help Thais Fairchild to reach an agreement',
    success: true,
    message: 'You successfully solved the mission!',
    reason: '',
    automated: false,
    state: gameView({ turn: 1, gold: 50, score: 50 }),
    at: '2026-10-27T10:00:01Z',
    ...overrides,
  }
}
