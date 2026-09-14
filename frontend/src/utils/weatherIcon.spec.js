import { describe, expect, it } from 'vitest'
import { wxIcon } from './weather'

/* 최종점검 #39 - 그라데이션 id 가 문서 전역이라 아이콘마다 달라야 한다. 같으면 맨 앞 정의(상세 카드)가 숨을 때 다른 아이콘도 색을 잃는다 */
describe('wxIcon 그라데이션 id', () => {
  const ids = svg => [...svg.matchAll(/id="([^"]+)"/g)].map(m => m[1])
  const refs = svg => [...svg.matchAll(/url\(#([^)]+)\)/g)].map(m => m[1])

  it('아이콘 두 개는 서로 다른 id 를 쓴다', () => {
    const a = ids(wxIcon('맑음')), b = ids(wxIcon('맑음'))
    expect(a).toHaveLength(3)
    expect(a.some(id => b.includes(id))).toBe(false)
  })

  it('맑음·구름·비 전부 자기 <defs> 안의 id 만 참조한다', () => {
    for (const k of ['맑음', '구름', '비']) {
      const svg = wxIcon(k)
      const own = ids(svg)
      expect(refs(svg).length).toBeGreaterThan(0)
      for (const r of refs(svg)) expect(own).toContain(r)
    }
  })
})
