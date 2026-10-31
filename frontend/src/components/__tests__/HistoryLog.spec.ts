import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import HistoryLog from '../HistoryLog.vue'
import { turnView } from '@/__tests__/fixtures'

describe('HistoryLog', () => {
  it('shows newest first with outcome, reason and state', () => {
    const turns = [
      turnView({ turn: 1, description: 'First job' }),
      turnView({
        turn: 2,
        action: 'BUY',
        description: 'Healing potion',
        message: 'Bought Healing potion',
      }),
      turnView({
        turn: 3,
        success: false,
        automated: true,
        reason: 'Best eligible ad',
        description: 'Third job',
      }),
    ]
    const wrapper = mount(HistoryLog, { props: { turns } })
    const items = wrapper.findAll('.history__item')

    expect(items).toHaveLength(3)
    expect(items[0]!.text()).toContain('#3')
    expect(items[0]!.text()).toContain('failed')
    expect(items[0]!.text()).toContain('advisor')
    expect(items[0]!.text()).toContain('Why: Best eligible ad')
    expect(items[1]!.text()).toContain('bought')
    expect(items[2]!.text()).toContain('solved')
    expect(wrapper.text()).toContain('3 actions · 1 solved · 1 failed')
  })

  it('shows an investigation as a neutral turn that is neither solved nor failed', () => {
    const turns = [
      turnView({
        action: 'INVESTIGATE',
        description: 'Reputation investigation',
        message: 'People 1, state -2, underworld 0',
      }),
    ]
    const wrapper = mount(HistoryLog, { props: { turns } })

    expect(wrapper.find('.badge').text()).toBe('investigated')
    expect(wrapper.find('.badge').classes()).toContain('badge-muted')
    expect(wrapper.text()).toContain('People 1, state -2, underworld 0')
    expect(wrapper.text()).toContain('0 solved · 0 failed')
  })

  it('collapses long histories', async () => {
    const turns = Array.from({ length: 25 }, (_, i) =>
      turnView({ turn: i + 1, at: `2026-10-27T10:00:${String(i).padStart(2, '0')}Z` }),
    )
    const wrapper = mount(HistoryLog, { props: { turns } })

    expect(wrapper.findAll('.history__item')).toHaveLength(20)
    await wrapper.find('.history__more').trigger('click')
    expect(wrapper.findAll('.history__item')).toHaveLength(25)
  })

  it('has an empty state', () => {
    expect(mount(HistoryLog, { props: { turns: [] } }).text()).toContain('Nothing has happened yet')
  })
})
