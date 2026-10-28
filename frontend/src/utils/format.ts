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

/** "1 turn" / "3 turns". */
export function turns(count: number): string {
  return `${count} ${count === 1 ? 'turn' : 'turns'}`
}

/** Short time of day for history entries. */
export function time(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
