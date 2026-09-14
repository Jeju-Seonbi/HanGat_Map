import { describe, expect, it } from 'vitest'
import { buildForecastSeries } from './forecastSeries'

describe('buildForecastSeries - 장소 상세 혼잡 예보 창', () => {
  it('오늘 시작한 예보분은 그대로 편다', () => {
    const days = buildForecastSeries({ from: '2026-09-14', rates: [10, 55, null] }, '2026-09-14')

    expect(days.map(d => d.date)).toEqual(['2026-09-14', '2026-09-15', '2026-09-16'])
    expect(days[0]).toMatchObject({ rate: 10, level: 'QUIET', label: '9/14 (월)' })
    expect(days[2]).toMatchObject({ rate: null, level: null })   // 빈 칸은 0이 아니라 null
  })

  it('배치가 밀려 from이 오늘보다 앞서면 지난 날짜를 버리고 오늘부터 편다', () => {
    const days = buildForecastSeries({ from: '2026-09-11', rates: [1, 2, 3, 4, 5] }, '2026-09-13')

    expect(days.map(d => d.date)).toEqual(['2026-09-13', '2026-09-14', '2026-09-15'])
    expect(days[0].rate).toBe(3)   // 날짜와 값이 같이 밀린다 - 값만 자르면 하루씩 어긋난다
  })

  it('예보분이 통째로 과거이거나 예보가 없으면 빈 목록이다', () => {
    expect(buildForecastSeries({ from: '2026-08-01', rates: [1, 2] }, '2026-09-13')).toEqual([])
    expect(buildForecastSeries(null, '2026-09-13')).toEqual([])
  })
})
