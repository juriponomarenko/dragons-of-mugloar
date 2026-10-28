import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdBoard from '../AdBoard.vue'
import { adView } from '@/__tests__/fixtures'

const ads = [
  adView({
    adId: 'rich',
    reward: 500,
    successRate: 0.1,
    score: -100,
    eligible: false,
    risk: 'DANGEROUS',
    expiresIn: 7,
  }),
  adView({
    adId: 'best',
    reward: 90,
    successRate: 0.95,
    score: 80,
    recommended: true,
    expiresIn: 3,
  }),
  adView({ adId: 'soon', reward: 20, successRate: 0.8, score: 10, expiresIn: 1 }),
]

function ids(wrapper: ReturnType<typeof mount>): string[] {
  return wrapper.findAll('[data-ad-id]').map((el) => el.attributes('data-ad-id') ?? '')
}

describe('AdBoard', () => {
  it('sorts by the advisor score by default and shows counts', () => {
    const wrapper = mount(AdBoard, { props: { ads } })

    expect(ids(wrapper)).toEqual(['best', 'soon', 'rich'])
    expect(wrapper.text()).toContain('3 ads · 2 within your risk budget')
  })

  it('re-sorts by reward, chance or urgency', async () => {
    const wrapper = mount(AdBoard, { props: { ads } })
    const select = wrapper.find('select')

    await select.setValue('reward')
    expect(ids(wrapper)).toEqual(['rich', 'best', 'soon'])

    await select.setValue('successRate')
    expect(ids(wrapper)).toEqual(['best', 'soon', 'rich'])

    await select.setValue('expiresIn')
    expect(ids(wrapper)).toEqual(['soon', 'best', 'rich'])
  })

  it('can hide ads outside the risk budget', async () => {
    const wrapper = mount(AdBoard, { props: { ads } })

    await wrapper.find('input[type="checkbox"]').setValue(true)

    expect(ids(wrapper)).toEqual(['best', 'soon'])
    expect(wrapper.text()).toContain('1 risky ad hidden')
  })

  it('forwards solve and refresh events', async () => {
    const wrapper = mount(AdBoard, { props: { ads } })

    await wrapper.find('[data-ad-id="best"] button').trigger('click')
    await wrapper.find('button[aria-label="Refresh the message board"]').trigger('click')

    expect(wrapper.emitted('solve')).toEqual([['best']])
    expect(wrapper.emitted('refresh')).toHaveLength(1)
  })

  it('explains an empty board', () => {
    expect(mount(AdBoard, { props: { ads: [] } }).text()).toContain('The board is empty')
    expect(mount(AdBoard, { props: { ads: [], loading: true } }).text()).toContain('Loading ads')
  })
})
