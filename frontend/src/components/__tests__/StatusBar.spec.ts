import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusBar from '../StatusBar.vue'
import { gameView } from '@/__tests__/fixtures'

describe('StatusBar', () => {
  it('renders every stat', () => {
    const wrapper = mount(StatusBar, {
      props: {
        game: gameView({ lives: 3, gold: 1500, score: 12345, level: 4, turn: 42, gameId: 'abc' }),
      },
    })

    const text = wrapper.text()
    expect(text).toContain('1 500')
    expect(text).toContain('12 345')
    expect(text).toContain('4')
    expect(text).toContain('42')
    expect(text).toContain('abc')
    expect(wrapper.findAll('.hearts span').filter((s) => s.text() === '♥')).toHaveLength(3)
  })

  it('caps the hearts and flags low lives', () => {
    const many = mount(StatusBar, { props: { game: gameView({ lives: 8 }) } })
    const low = mount(StatusBar, { props: { game: gameView({ lives: 1 }) } })

    expect(many.findAll('.hearts span').filter((s) => s.text() === '♥')).toHaveLength(5)
    expect(many.text()).toContain('+3')
    expect(low.find('.stat--lives').classes()).toContain('is-critical')
  })
})
