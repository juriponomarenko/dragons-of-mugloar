import { describe, expect, it } from 'vitest'
import { compact, duration, gold, percent, seconds, time, turns } from '../format'

describe('format helpers', () => {
  it('formats rates as whole percentages', () => {
    expect(percent(0.95)).toBe('95%')
    expect(percent(0)).toBe('0%')
    expect(percent(1)).toBe('100%')
    expect(percent(0.333)).toBe('33%')
  })

  it('groups gold in thousands with a thin space', () => {
    expect(gold(0)).toBe('0')
    expect(gold(999)).toBe('999')
    expect(gold(12345)).toBe('12 345')
    expect(gold(1234567.6)).toBe('1 234 568')
  })

  it('keeps at most one decimal', () => {
    expect(compact(47.5)).toBe('47.5')
    expect(compact(47.04)).toBe('47')
    expect(compact(-12.26)).toBe('-12.3')
  })

  it('formats a pause in seconds', () => {
    expect(seconds(500)).toBe('0.5 s')
    expect(seconds(2000)).toBe('2 s')
    expect(seconds(1250)).toBe('1.3 s')
  })

  it('formats an elapsed time in seconds and minutes', () => {
    expect(duration(0)).toBe('0 s')
    expect(duration(12_400)).toBe('12 s')
    expect(duration(125_000)).toBe('2 min 05 s')
    expect(duration(-5)).toBe('0 s')
  })

  it('pluralises turns', () => {
    expect(turns(1)).toBe('1 turn')
    expect(turns(0)).toBe('0 turns')
    expect(turns(7)).toBe('7 turns')
  })

  it('formats times and tolerates garbage', () => {
    expect(time('2026-10-27T10:00:01Z')).toMatch(/\d{1,2}:\d{2}:\d{2}/)
    expect(time('not a date')).toBe('')
  })
})
