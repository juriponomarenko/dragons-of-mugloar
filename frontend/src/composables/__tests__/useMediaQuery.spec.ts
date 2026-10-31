import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useMediaQuery } from '../useMediaQuery'

type Listener = (event: MediaQueryListEvent) => void

function stubMatchMedia(matches: boolean) {
  const listeners = new Set<Listener>()
  const list = {
    matches,
    addEventListener: vi.fn<(type: string, listener: Listener) => void>((_, listener) => {
      listeners.add(listener)
    }),
    removeEventListener: vi.fn<(type: string, listener: Listener) => void>((_, listener) => {
      listeners.delete(listener)
    }),
  }
  vi.stubGlobal(
    'matchMedia',
    vi.fn<() => typeof list>(() => list),
  )
  return {
    list,
    change(next: boolean) {
      listeners.forEach((listener) => listener({ matches: next } as MediaQueryListEvent))
    },
  }
}

describe('useMediaQuery', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('falls back when the browser cannot answer', () => {
    expect(useMediaQuery('(min-width: 900px)').value).toBe(true)
    expect(useMediaQuery('(min-width: 900px)', false).value).toBe(false)
  })

  it('follows the viewport and stops listening when its scope ends', () => {
    const media = stubMatchMedia(false)
    const scope = effectScope()
    const matches = scope.run(() => useMediaQuery('(min-width: 900px)'))!

    expect(window.matchMedia).toHaveBeenCalledWith('(min-width: 900px)')
    expect(matches.value).toBe(false)
    media.change(true)
    expect(matches.value).toBe(true)

    scope.stop()
    expect(media.list.removeEventListener).toHaveBeenCalledTimes(1)
    media.change(false)
    expect(matches.value).toBe(true)
  })
})
