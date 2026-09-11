import { describe, expect, it } from 'vitest'
import { sourceLine, sourcesFor } from './dataSources'

describe('map data source line (MAP_001)', () => {
  it('always names KTO and KMA - tourist pins, crowd colours and weather are always on screen', () => {
    expect(sourcesFor({ spot: 1 })).toEqual(['한국관광공사', '기상청'])
  })

  it('adds MOIS only when the good-price layer is on', () => {
    expect(sourcesFor({ spot: 1, food: 1 })).toContain('행정안전부')
    expect(sourcesFor({ spot: 1, food: 0 })).not.toContain('행정안전부')
  })

  it('adds the small-business agency once for any of cafe / convenience / mart', () => {
    for (const k of ['cafe', 'cvs', 'mart']) {
      const parts = sourcesFor({ spot: 1, [k]: 1 })
      expect(parts.filter(p => p.startsWith('소상공인시장진흥공단'))).toHaveLength(1)
    }
    expect(sourcesFor({ spot: 1, cafe: 1, cvs: 1, mart: 1 }).filter(p => p.startsWith('소상공인'))).toHaveLength(1)
  })

  it('renders one short line', () => {
    expect(sourceLine({ spot: 1, food: 1, cafe: 1 }))
      .toBe('출처 한국관광공사 · 행정안전부 · 소상공인시장진흥공단 · 기상청')
  })
})
