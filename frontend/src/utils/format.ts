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

/** Elapsed time as "12 s" or "2 min 05 s". */
export function duration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const minutes = Math.floor(total / 60)
  const secs = total % 60
  return minutes ? `${minutes} min ${secs.toString().padStart(2, '0')} s` : `${secs} s`
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
