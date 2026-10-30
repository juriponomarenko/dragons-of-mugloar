import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MessageBanner from '../MessageBanner.vue'

describe('MessageBanner', () => {
  it('renders nothing without a message', () => {
    const wrapper = mount(MessageBanner, { props: { message: null } })

    expect(wrapper.find('.banner').exists()).toBe(false)
  })

  it('announces errors assertively with their details', () => {
    const wrapper = mount(MessageBanner, {
      props: { message: 'Not enough gold', details: ['gold: short by 40'] },
    })

    const banner = wrapper.find('.banner')
    expect(banner.attributes('role')).toBe('alert')
    expect(banner.classes()).toContain('is-error')
    expect(banner.text()).toContain('Not enough gold')
    expect(banner.text()).toContain('gold: short by 40')
  })

  it('announces notices politely and can be dismissed', async () => {
    const wrapper = mount(MessageBanner, {
      props: { message: 'Reached the turn limit of 1000', tone: 'info' },
    })

    const banner = wrapper.find('.banner')
    expect(banner.attributes('role')).toBe('status')
    expect(banner.classes()).toContain('is-info')
    await wrapper.find('button[aria-label="Dismiss"]').trigger('click')
    expect(wrapper.emitted('dismiss')).toHaveLength(1)
  })
})
