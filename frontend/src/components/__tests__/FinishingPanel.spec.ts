import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import FinishingPanel from '../FinishingPanel.vue'
import { DRAGON_LINES, LINE_INTERVAL_MS } from '@/utils/dragonLines'

describe('FinishingPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('explains what the server is doing and announces it once', () => {
    const wrapper = mount(FinishingPanel)

    expect(wrapper.text()).toContain('finishing the game for you')
    expect(wrapper.find('[role="status"]').text()).toContain('on the server')
    expect(wrapper.findAll('ol li')).toHaveLength(4)
    expect(DRAGON_LINES).toHaveLength(10)
    expect(wrapper.text()).toContain(DRAGON_LINES[0]!)
  })

  it('counts the elapsed time and rotates through every line', async () => {
    const wrapper = mount(FinishingPanel)
    expect(wrapper.text()).toContain('Running for 0 s')

    await vi.advanceTimersByTimeAsync(LINE_INTERVAL_MS)
    expect(wrapper.text()).toContain(DRAGON_LINES[1]!)
    expect(wrapper.text()).not.toContain(DRAGON_LINES[0]!)

    await vi.advanceTimersByTimeAsync(LINE_INTERVAL_MS * (DRAGON_LINES.length - 1))
    expect(wrapper.text()).toContain(DRAGON_LINES[0]!)
    expect(wrapper.text()).toContain('Running for 40 s')
  })

  it('stops its timers when it leaves the screen', () => {
    const wrapper = mount(FinishingPanel)
    expect(vi.getTimerCount()).toBe(2)

    wrapper.unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
