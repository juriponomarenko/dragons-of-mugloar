import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ViewTabs from '../ViewTabs.vue'
import { VIEW_ICONS, type TabView } from '../tabViews'

const views: TabView[] = [
  { id: 'board', label: 'Board', icon: VIEW_ICONS.board, badge: 7 },
  { id: 'shop', label: 'Shop', icon: VIEW_ICONS.shop, badge: 'dot' },
  { id: 'advisor', label: 'Advisor', icon: VIEW_ICONS.advisor, badge: 'live' },
]

function mountTabs(modelValue = 'board') {
  return mount(ViewTabs, { props: { views, modelValue }, attachTo: document.body })
}

function lastSelected(wrapper: ReturnType<typeof mountTabs>): unknown[] | undefined {
  const events = wrapper.emitted('update:modelValue') ?? []
  return events[events.length - 1]
}

describe('ViewTabs', () => {
  it('renders a tab per view with the active one selected and the badges', () => {
    const wrapper = mountTabs('shop')
    const tabs = wrapper.findAll('[role="tab"]')

    expect(wrapper.find('[role="tablist"]').exists()).toBe(true)
    expect(tabs).toHaveLength(3)
    expect(tabs[1]!.attributes('aria-selected')).toBe('true')
    expect(tabs[1]!.attributes('tabindex')).toBe('0')
    expect(tabs[0]!.attributes('aria-selected')).toBe('false')
    expect(tabs[0]!.attributes('tabindex')).toBe('-1')
    expect(tabs[0]!.attributes('aria-controls')).toBe('view-panel-board')
    expect(tabs[0]!.find('.tab__count').text()).toBe('7')
    expect(tabs[1]!.find('.tab__dot').classes()).not.toContain('is-live')
    expect(tabs[2]!.find('.tab__dot').classes()).toContain('is-live')
    wrapper.unmount()
  })

  it('selects a view on click', async () => {
    const wrapper = mountTabs()

    await wrapper.findAll('[role="tab"]')[2]!.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['advisor']])
    wrapper.unmount()
  })

  it('moves between tabs with the keyboard and follows with focus', async () => {
    const wrapper = mountTabs('shop')
    const tablist = wrapper.find('[role="tablist"]')

    await tablist.trigger('keydown', { key: 'ArrowRight' })
    expect(lastSelected(wrapper)).toEqual(['advisor'])
    expect(document.activeElement?.id).toBe('view-tab-advisor')

    await wrapper.setProps({ modelValue: 'advisor' })
    await tablist.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)

    await tablist.trigger('keydown', { key: 'Home' })
    expect(lastSelected(wrapper)).toEqual(['board'])
    expect(document.activeElement?.id).toBe('view-tab-board')
    wrapper.unmount()
  })
})
