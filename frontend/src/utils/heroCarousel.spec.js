import { describe, expect, it } from 'vitest'
import { nextHeroIndex } from './heroCarousel.js'

describe('nextHeroIndex', () => {
  it('마지막 사진 다음에는 첫 번째 사진으로 돌아간다', () => {
    expect(nextHeroIndex(3, 4)).toBe(0)
  })

  it('마지막 사진 전까지는 다음 사진으로 이동한다', () => {
    expect(nextHeroIndex(1, 4)).toBe(2)
  })
})
