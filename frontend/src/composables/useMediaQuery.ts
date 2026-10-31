import { getCurrentScope, onScopeDispose, ref, type Ref } from 'vue'

/**
 * Whether a CSS media query matches, kept current as the viewport changes. Without `matchMedia`
 * (an old browser, a test runner) it reports `fallback` and never changes.
 */
export function useMediaQuery(query: string, fallback = true): Ref<boolean> {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return ref(fallback)
  }
  const list = window.matchMedia(query)
  const matches = ref(list.matches)
  const update = (event: MediaQueryListEvent) => {
    matches.value = event.matches
  }
  list.addEventListener('change', update)
  if (getCurrentScope()) {
    onScopeDispose(() => list.removeEventListener('change', update))
  }
  return matches
}
