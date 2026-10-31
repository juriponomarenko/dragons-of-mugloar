import type { TurnView } from '@/api/types'

/** Formats a 0..1 rate as a whole percentage, e.g. 0.95 -> "95%". */
export function percent(rate: number): string {
  return `${Math.round(rate * 100)}%`
}

/** Formats gold amounts with a thin separator for readability, e.g. 12345 -> "12 345". */
export function gold(amount: number): string {
  return Math.round(amount)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
}

/** Formats a value with at most one decimal, dropping ".0". */
export function compact(value: number): string {
  const rounded = Math.round(value * 10) / 10
  return Number.isInteger(rounded) ? rounded.toString() : rounded.toFixed(1)
}

/** Milliseconds as seconds with at most one decimal, e.g. 500 -> "0.5 s", 2000 -> "2 s". */
export function seconds(ms: number): string {
  return `${compact(ms / 1000)} s`
}

/** "1 turn" / "3 turns". */
export function turns(count: number): string {
  return `${count} ${count === 1 ? 'turn' : 'turns'}`
}

/** What a turn did, in the past tense: "solved", "failed", "bought" or "investigated". */
export function outcome(turn: Pick<TurnView, 'action' | 'success'>): string {
  switch (turn.action) {
    case 'BUY':
      return 'bought'
    case 'INVESTIGATE':
      return 'investigated'
    default:
      return turn.success ? 'solved' : 'failed'
  }
}

/** Short time of day for history entries. */
export function time(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
