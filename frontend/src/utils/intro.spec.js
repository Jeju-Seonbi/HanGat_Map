import { describe, expect, it } from 'vitest'
import { introOf, needsMore } from './intro'

describe('introOf (MAP_008)', () => {
  it('returns the KTO overview text trimmed', () => {
    expect(introOf('  표선면 가시리는 제주도 내에서 …  ')).toBe('표선면 가시리는 제주도 내에서 …')
  })

  it('never shows a menu paragraph as an introduction', () => {
    expect(introOf('대표메뉴: 순대국밥 9,000원 · 해물순두부 9,000원')).toBeNull()
  })

  it('is null when there is nothing to say', () => {
    expect(introOf(null)).toBeNull()
    expect(introOf('   ')).toBeNull()
  })

  it('asks for a 더보기 button only when two lines are not enough', () => {
    expect(needsMore('짧은 소개')).toBe(false)
    expect(needsMore('가'.repeat(71))).toBe(true)
  })
})
