import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdCard from '../AdCard.vue'
import { adView } from '@/__tests__/fixtures'

describe('AdCard', () => {
  it('shows the ad text, reward, probability and stats', () => {
    const wrapper = mount(AdCard, {
      props: { ad: adView({ reward: 1250, expiresIn: 1, notes: ['May hurt reputation'] }) },
    })

    expect(wrapper.text()).toContain('Help Thais Fairchild')
    expect(wrapper.text()).toContain('1 250')
    expect(wrapper.text()).toContain('Piece of cake')
    expect(wrapper.text()).toContain('95%')
    expect(wrapper.text()).toContain('47.5')
    expect(wrapper.text()).toContain('1 turn')
    expect(wrapper.text()).toContain('May hurt reputation')
    expect(wrapper.find('.ad__meter-fill').attributes('style')).toContain('width: 95%')
  })

  it('highlights the recommended ad and dims ineligible ones', () => {
    const recommended = mount(AdCard, { props: { ad: adView({ recommended: true }) } })
    const ineligible = mount(AdCard, {
      props: { ad: adView({ eligible: false, risk: 'DANGEROUS' }) },
    })

    expect(recommended.classes()).toContain('is-recommended')
    expect(recommended.text()).toContain('Recommended')
    expect(recommended.find('button').classes()).toContain('btn-primary')
    expect(ineligible.classes()).toContain('is-ineligible')
    expect(ineligible.find('.badge-danger').exists()).toBe(true)
  })

  it('marks decoded ads', () => {
    const wrapper = mount(AdCard, { props: { ad: adView({ encryption: 'base64' }) } })

    expect(wrapper.text()).toContain('decoded')
  })

  it('emits solve with the ad id and respects disabled', async () => {
    const wrapper = mount(AdCard, { props: { ad: adView({ adId: 'xyz' }) } })

    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('solve')).toEqual([['xyz']])

    await wrapper.setProps({ disabled: true })
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })
})
