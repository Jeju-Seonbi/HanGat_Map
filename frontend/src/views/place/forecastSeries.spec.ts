import { describe, expect, it } from 'vitest'
import type { PlaceForecast } from '../../services/PlaceDetailService'
import { buildForecastSeries, isForecastStale } from './forecastSeries'

/** 발표일을 안 주면 창 시작일과 같게 둔다 - 창이 하루 앞서는 경우는 테스트마다 따로 적는다 */
const forecast = (from: string, rates: Array<number | null>, baseDate: string | null = from): PlaceForecast =>
  ({ from, baseDate, rates })

describe('buildForecastSeries - 장소 상세 혼잡 예보 창', () => {
  it('오늘 시작한 예보분은 그대로 편다', () => {
    const days = buildForecastSeries(forecast('2026-09-14', [10, 55, null]), '2026-09-14')

    expect(days.map(d => d.date)).toEqual(['2026-09-14', '2026-09-15', '2026-09-16'])
    expect(days[0]).toMatchObject({ rate: 10, level: 'QUIET', label: '9/14 (월)' })
    expect(days[2]).toMatchObject({ rate: null, level: null })   // 빈 칸은 0이 아니라 null
  })

  it('창이 오늘보다 앞서면 지난 날짜를 버리고 오늘부터 편다', () => {
    const days = buildForecastSeries(forecast('2026-09-11', [1, 2, 3, 4, 5]), '2026-09-13')

    expect(days.map(d => d.date)).toEqual(['2026-09-13', '2026-09-14', '2026-09-15'])
    expect(days[0].rate).toBe(3)   // 날짜와 값이 같이 밀린다 - 값만 자르면 하루씩 어긋난다
  })

  it('예보분이 통째로 과거이거나 예보가 없으면 빈 목록이다', () => {
    expect(buildForecastSeries(forecast('2026-08-01', [1, 2]), '2026-09-13')).toEqual([])
    expect(buildForecastSeries(null, '2026-09-13')).toEqual([])
  })
})

describe('isForecastStale - 갱신 대기 판정', () => {
  it('오늘 발표분이면 창이 어제부터 시작해도 묵은 것이 아니다', () => {
    // 새벽 3시 적재가 받는 창은 늘 어제부터다. 창으로 판정하면 정상 적재에도 매일 갱신 대기가 뜬다
    expect(isForecastStale(forecast('2026-09-15', [10, 20], '2026-09-16'), '2026-09-16')).toBe(false)
  })

  it('어제 발표분이면 묵은 것이다', () => {
    expect(isForecastStale(forecast('2026-09-14', [10, 20], '2026-09-15'), '2026-09-16')).toBe(true)
  })

  it('발표일을 모르는 응답과 예보 없음은 경고하지 않는다', () => {
    expect(isForecastStale(forecast('2026-09-15', [10], null), '2026-09-16')).toBe(false)
    expect(isForecastStale(null, '2026-09-16')).toBe(false)
  })
})
