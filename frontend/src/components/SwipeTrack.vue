<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { panelId, tabId } from './tabViews'

/**
 * The views side by side in a horizontally scrolling track that snaps to one view at a time.
 * The browser provides the swipe, the momentum and the snap; this component only keeps the
 * model and the scroll position in step: a tab change scrolls the track, and a swipe that has
 * settled on a view updates the model. Views that are off screen are inert, so keyboard focus
 * and screen readers stay on the one that is visible.
 */
const props = defineProps<{
  /** View ids, in order; each has a slot of the same name. */
  views: string[]
  /** The sticky header the new view is brought up to when the page had been scrolled down. */
  header?: HTMLElement | null
}>()
const active = defineModel<string>({ required: true })

/** How long the scroll position has to stay still before a swipe counts as settled. */
const SETTLE_MS = 80

/** The longest a smooth scroll to a tab is waited for before its current position is accepted. */
const TRAVEL_MS = 1500

const track = ref<HTMLElement | null>(null)
const activeIndex = computed(() => Math.max(0, props.views.indexOf(active.value)))

let settle: ReturnType<typeof setTimeout> | undefined
let lastWidth = 0

/** The view a smooth scroll is on its way to, so the positions it passes through are not mistaken for a swipe. */
let travellingTo: number | null = null
let travellingSince = 0

function prefersReducedMotion(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

/** The view the track is currently resting on. */
function restingIndex(): number {
  const el = track.value
  if (!el || !el.clientWidth) {
    return activeIndex.value
  }
  return Math.max(0, Math.min(props.views.length - 1, Math.round(el.scrollLeft / el.clientWidth)))
}

function snapTo(index: number, behavior: ScrollBehavior): void {
  const el = track.value
  if (!el || typeof el.scrollTo !== 'function') {
    return
  }
  if (behavior === 'smooth') {
    travellingTo = index
    travellingSince = Date.now()
  }
  el.scrollTo({ left: index * el.clientWidth, behavior })
}

/** A finger on the track takes over from a smooth scroll; wherever it lets go is where we are. */
function takeOver(): void {
  travellingTo = null
}

/** When the page is scrolled past the top of the track, bring the new view up under the header. */
function revealTop(): void {
  const el = track.value
  if (!el) {
    return
  }
  const headerBottom = props.header?.getBoundingClientRect().bottom ?? 0
  const gap = el.getBoundingClientRect().top - headerBottom
  if (gap < 0) {
    window.scrollBy({ top: gap, behavior: 'auto' })
  }
}

function onSettled(): void {
  const index = restingIndex()
  if (travellingTo !== null && index !== travellingTo && Date.now() - travellingSince < TRAVEL_MS) {
    // A tab's smooth scroll paused on its way (a busy frame); give it time to arrive.
    settle = setTimeout(onSettled, SETTLE_MS)
    return
  }
  travellingTo = null
  const view = props.views[index]
  if (view && view !== active.value) {
    active.value = view
    revealTop()
  }
}

function onScroll(): void {
  clearTimeout(settle)
  settle = setTimeout(onSettled, SETTLE_MS)
}

function onResize(): void {
  const width = track.value?.clientWidth ?? 0
  if (width && width !== lastWidth) {
    lastWidth = width
    snapTo(activeIndex.value, 'auto')
  }
}

watch(activeIndex, (index) => {
  // A change that came from a swipe already has the track in place; only a tab needs scrolling.
  if (index !== restingIndex()) {
    snapTo(index, prefersReducedMotion() ? 'auto' : 'smooth')
    revealTop()
  }
})

onMounted(() => {
  lastWidth = track.value?.clientWidth ?? 0
  snapTo(activeIndex.value, 'auto')
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  clearTimeout(settle)
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div ref="track" class="track" @scroll.passive="onScroll" @pointerdown.passive="takeOver">
    <section
      v-for="id in views"
      :id="panelId(id)"
      :key="id"
      class="track__view"
      :class="{ 'is-active': id === active }"
      role="tabpanel"
      :aria-labelledby="tabId(id)"
      :inert="id !== active ? true : undefined"
    >
      <slot :name="id" />
    </section>
  </div>
</template>

<style scoped>
.track {
  display: flex;
  align-items: flex-start;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
}

.track::-webkit-scrollbar {
  display: none;
}

.track__view {
  flex: 0 0 100%;
  min-width: 0;
  scroll-snap-align: start;
  scroll-snap-stop: always;
}

/* A tall view that is off screen must not leave a long empty run under a short one. */
.track__view:not(.is-active) {
  max-height: 100vh;
  overflow: hidden;
}
</style>
