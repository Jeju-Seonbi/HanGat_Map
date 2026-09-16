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
 * 관광공사 API는 새벽 3시 적재 시각에 아직 어제부터 시작하는 창을 준다. 그래서 from은 정상 적재에도
 * 늘 어제다. 지난 날짜까지 "예보"로 그리면 축의 첫 날짜가 오늘과 어긋나 화면이 며칠 전에 멈춘 것처럼
 * 보인다(9/12 사용성 피드백). 빈 칸은 null로 둔다 - 0으로 바꾸면 '정보 없음'이 '한산'이 된다.
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

/**
 * 오늘 발표분을 아직 못 받았는지. <b>창 시작일(from)이 아니라 발표일(baseDate)로 본다.</b>
 *
 * from으로 판정하면 정상 적재에도 매일 '갱신 대기'가 뜬다 - 새벽 배치가 받는 창이 늘 어제부터라서다.
 * 발표일이 없는 응답(옛 백엔드)은 묵었다고 단정하지 않는다 - 모르는 것을 경고로 바꾸지 않는다.
 */
export function isForecastStale (rows: PlaceForecast | null, today: string): boolean {
  return rows?.baseDate != null && rows.baseDate < today
}
