import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ShopPanel from '../ShopPanel.vue'
import { shopItemView } from '@/__tests__/fixtures'

describe('ShopPanel', () => {
  const items = [
    shopItemView({
      id: 'hpot',
      name: 'Healing potion',
      cost: 50,
      affordable: true,
      suggested: true,
      timesBought: 2,
    }),
    shopItemView({ id: 'cs', name: 'Claw Sharpening', cost: 100, affordable: false }),
  ]

  it('lists items with cost, suggestion and purchase count', () => {
    const wrapper = mount(ShopPanel, { props: { items, playerGold: 60 } })

    expect(wrapper.text()).toContain('Healing potion')
    expect(wrapper.text()).toContain('50 gold')
    expect(wrapper.text()).toContain('suggested')
    expect(wrapper.text()).toContain('×2')
    expect(wrapper.find('.is-suggested').exists()).toBe(true)
  })

  it('disables unaffordable items and explains why', () => {
    const wrapper = mount(ShopPanel, { props: { items, playerGold: 60 } })
    const buttons = wrapper.findAll('button')

    expect(buttons[0]!.attributes('disabled')).toBeUndefined()
    expect(buttons[1]!.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('40 more needed')
  })

  it('emits buy with the item id', async () => {
    const wrapper = mount(ShopPanel, { props: { items, playerGold: 60 } })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('buy')).toEqual([['hpot']])
  })

  it('disables everything while busy', () => {
    const wrapper = mount(ShopPanel, { props: { items, playerGold: 999, disabled: true } })

    expect(wrapper.findAll('button').every((b) => b.attributes('disabled') !== undefined)).toBe(
      true,
    )
  })
})
