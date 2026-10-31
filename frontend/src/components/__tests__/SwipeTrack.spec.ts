import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SwipeTrack from '../SwipeTrack.vue'

const WIDTH = 300

/** jsdom has no layout and no element scrolling, so the track gets a width and a scroll position. */
function mountTrack(modelValue = 'a') {
  const wrapper = mount(SwipeTrack, {
    props: { views: ['a', 'b', 'c'], modelValue },
    slots: { a: '<p>first</p>', b: '<p>second</p>', c: '<p>third</p>' },
  })
  const track = wrapper.element as HTMLElement
  Object.defineProperty(track, 'clientWidth', { value: WIDTH, configurable: true })
  Object.defineProperty(track, 'scrollLeft', { value: 0, writable: true, configurable: true })
  const scrollTo = vi.fn<(options?: ScrollToOptions) => void>()
  track.scrollTo = scrollTo as HTMLElement['scrollTo']
  return { wrapper, track, scrollTo }
}

function isInert(el: Element): boolean {
  return el.hasAttribute('inert') || (el as HTMLElement & { inert?: boolean }).inert === true
}

describe('SwipeTrack', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders a panel per view and keeps the off-screen ones inert', async () => {
    const { wrapper } = mountTrack('b')
    const panels = wrapper.findAll('[role="tabpanel"]')

    expect(panels).toHaveLength(3)
    expect(panels[1]!.attributes('aria-labelledby')).toBe('view-tab-b')
    expect(panels[1]!.text()).toBe('second')
    expect(isInert(panels[0]!.element)).toBe(true)
    expect(isInert(panels[1]!.element)).toBe(false)
    expect(isInert(panels[2]!.element)).toBe(true)

    await wrapper.setProps({ modelValue: 'c' })
    expect(isInert(panels[1]!.element)).toBe(true)
    expect(isInert(panels[2]!.element)).toBe(false)
  })

  it('scrolls to the view a tab selected', async () => {
    const { wrapper, scrollTo } = mountTrack()

    await wrapper.setProps({ modelValue: 'c' })

    expect(scrollTo).toHaveBeenLastCalledWith({ left: 2 * WIDTH, behavior: 'smooth' })
  })

  it('updates the model once a swipe has settled, without scrolling again', async () => {
    const { wrapper, track, scrollTo } = mountTrack()
    const scrollsBefore = scrollTo.mock.calls.length

    track.scrollLeft = 150 // mid-swipe
    await wrapper.trigger('scroll')
    track.scrollLeft = WIDTH // landed on the second view
    await wrapper.trigger('scroll')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await vi.advanceTimersByTimeAsync(100)
    expect(wrapper.emitted('update:modelValue')).toEqual([['b']])

    await wrapper.setProps({ modelValue: 'b' })
    expect(scrollTo.mock.calls).toHaveLength(scrollsBefore)
  })

  it('is not fooled by the positions a smooth scroll to a tab passes through', async () => {
    const { wrapper, track } = mountTrack()
    await wrapper.setProps({ modelValue: 'c' })

    track.scrollLeft = 100 // a busy frame left the animation hanging near the first view
    await wrapper.trigger('scroll')
    await vi.advanceTimersByTimeAsync(300)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    track.scrollLeft = 2 * WIDTH // arrived
    await wrapper.trigger('scroll')
    await vi.advanceTimersByTimeAsync(100)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('accepts where an interrupted smooth scroll came to rest', async () => {
    const { wrapper, track } = mountTrack()
    await wrapper.setProps({ modelValue: 'c' })

    track.scrollLeft = WIDTH // a finger stopped it on the second view
    await wrapper.trigger('pointerdown')
    await wrapper.trigger('scroll')
    await vi.advanceTimersByTimeAsync(100)

    expect(wrapper.emitted('update:modelValue')).toEqual([['b']])
  })

  it('gives up waiting for a smooth scroll that never arrives', async () => {
    const { wrapper, track } = mountTrack()
    await wrapper.setProps({ modelValue: 'c' })

    track.scrollLeft = WIDTH
    await wrapper.trigger('scroll')
    await vi.advanceTimersByTimeAsync(1700)

    expect(wrapper.emitted('update:modelValue')).toEqual([['b']])
  })

  it('re-snaps to the active view when the width changes', async () => {
    const { wrapper, track, scrollTo } = mountTrack('b')
    const scrollsBefore = scrollTo.mock.calls.length

    Object.defineProperty(track, 'clientWidth', { value: 400, configurable: true })
    window.dispatchEvent(new Event('resize'))

    expect(scrollTo).toHaveBeenLastCalledWith({ left: 400, behavior: 'auto' })
    expect(scrollTo.mock.calls).toHaveLength(scrollsBefore + 1)
    wrapper.unmount()
  })
})
