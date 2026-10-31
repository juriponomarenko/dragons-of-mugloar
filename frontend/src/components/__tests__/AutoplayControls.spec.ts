import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AutoplayControls from '../AutoplayControls.vue'

const base = { running: false, busy: false, gameOver: false, delayMs: 500, automatedTurns: 0 }

describe('AutoplayControls', () => {
  it('emits step, run and finish', async () => {
    const wrapper = mount(AutoplayControls, { props: base })
    const buttons = wrapper.findAll('button')

    await buttons[0]!.trigger('click')
    await buttons[1]!.trigger('click')
    await buttons[2]!.trigger('click')

    expect(wrapper.emitted('step')).toHaveLength(1)
    expect(wrapper.emitted('run')).toHaveLength(1)
    expect(wrapper.emitted('finish')).toHaveLength(1)
  })

  it('swaps the run button for stop while running', async () => {
    const wrapper = mount(AutoplayControls, { props: { ...base, running: true, busy: true } })

    expect(wrapper.text()).toContain('Stop')
    expect(wrapper.text()).toContain('Autoplay running')
    await wrapper.find('.btn-danger').trigger('click')
    expect(wrapper.emitted('stop')).toHaveLength(1)
  })

  it('disables actions when busy or the game is over', () => {
    const over = mount(AutoplayControls, { props: { ...base, gameOver: true } })

    expect(over.findAll('button').every((b) => b.attributes('disabled') !== undefined)).toBe(true)
  })

  it('says so on the button while the server is finishing the game', () => {
    const wrapper = mount(AutoplayControls, { props: { ...base, busy: true, finishing: true } })

    expect(wrapper.text()).toContain('Finishing…')
    expect(wrapper.text()).not.toContain('Finish game')
  })

  it('reports delay changes and the advisor turn count', async () => {
    const wrapper = mount(AutoplayControls, { props: { ...base, automatedTurns: 7 } })

    await wrapper.find('input[type="range"]').setValue('900')

    expect(wrapper.emitted('update:delayMs')).toEqual([[900]])
    expect(wrapper.text()).toContain('7 turns played by the advisor')
  })
})
