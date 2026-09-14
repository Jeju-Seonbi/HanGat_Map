import type { CongestionLevel } from '../../assets/types'
import type { PlaceForecast } from '../../services/PlaceDetailService'
import { levelOf } from '../../utils/congestion'
import { addCalendarDays, fmt } from '../../utils/format.js'

export interface ForecastDay {
  date: string
  rate: number | null
  level: CongestionLevel | null
  label: string
}

/**
 * 예보 창(from부터 하루씩)을 날짜 행으로 펴고 오늘 이전은 버린다.
 *
 * 혼잡 배치가 하루 이상 밀리면 from이 오늘보다 앞선다. 지난 날짜까지 "예보"로 그리면
 * 축의 첫 날짜가 오늘과 어긋나 화면이 며칠 전에 멈춘 것처럼 보인다(9/12 사용성 피드백).
 * 빈 칸은 null로 둔다 - 0으로 바꾸면 '정보 없음'이 '한산'이 된다.
 */
export function buildForecastSeries (rows: PlaceForecast | null, today: string): ForecastDay[] {
  if (!rows) return []
  return rows.rates
    .map((rate, index) => {
      const date = addCalendarDays(rows.from, index)
      return { date, rate: rate ?? null, level: levelOf(rate), label: fmt(date) }
    })
    .filter(day => day.date >= today)
}
