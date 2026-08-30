/**
 * 혼잡 예보 (MAP-01 핀 색 · MAP-02 날짜 슬라이더).
 *
 * 백엔드 `GET /crowd/forecast`는 장소 전체 × 예보일을 한 번에 준다.
 * 날짜를 밀 때마다 서버를 부르면 슬라이더가 버벅이므로(요건: 0.3초 이내 갱신)
 * 한 번 받아서 장소에 붙여 두고, 이후 날짜 이동은 배열 인덱스 조회로만 처리한다.
 *
 * ⚠️ `days`가 날마다 다르다(실측 21~22일). 화면은 30일 캘린더라
 *    <b>남는 날은 값이 없는 게 정상</b>이고 '정보 없음'으로 표시된다.
 */
import { apiGet } from '../apiClient'
import type { MapPlace } from './MapPlaceService'

export interface CrowdForecast {
  /** true = 백엔드 실데이터, false = 예보 없음(폴백) */
  live: boolean
  /** 첫 예보일 (제주 기준, YYYY-MM-DD). 없으면 null */
  from: string | null
  /** 예보 일수. 화면 30일 중 이 개수만 값이 있다 */
  days: number
  /** placeId → 날짜순 집중률. 인덱스 i = from + i일 */
  values: Record<string, (number | null)[]>
}

/** 백엔드 CrowdForecastResponse (map/model/dto/CrowdForecastResponse.java 와 동일 모양) */
interface BackendForecast {
  from: string | null
  days: number
  values: Record<string, (number | null)[]>
}

export const CrowdService = {
  async getForecast (): Promise<CrowdForecast> {
    try {
      const res = await apiGet<BackendForecast>('/crowd/forecast', 15000)
      return { live: res.days > 0, from: res.from, days: res.days, values: res.values ?? {} }
    } catch {
      // 예보만 실패한 경우 - 장소는 그대로 보이고 혼잡 정보만 '없음'이 된다
      return { live: false, from: null, days: 0, values: {} }
    }
  }
}

/**
 * 받아온 예보를 장소 목록에 붙인다.
 *
 * <p>예보가 있는 장소는 345곳뿐이고 나머지는 `series`가 null로 남는다 -
 * 화면은 그걸 '정보 없음'(회색 핀)으로 그린다. 0으로 채우면 '가장 한산한 곳'으로 뽑혀
 * 사람을 잘못 보내게 된다.
 *
 * <p>오늘이 예보 시작일과 다를 수 있어 <b>offset만큼 앞을 잘라</b> 인덱스를 맞춘다.
 * 이걸 안 하면 화면의 '오늘'과 예보의 '첫날'이 어긋나 날짜가 통째로 밀린다.
 */
export function attachSeries (places: MapPlace[], forecast: CrowdForecast, todayIso: string): void {
  if (!forecast.live || !forecast.from) return

  const offset = daysBetween(forecast.from, todayIso)
  for (const place of places) {
    if (place.id == null) continue
    const series = forecast.values[String(place.id)]
    if (!series) continue
    place.series = offset > 0 ? series.slice(offset) : series
  }
}

/** a → b 사이 일수. 로컬 시간대 영향을 받지 않도록 날짜 문자열만으로 계산한다. */
function daysBetween (a: string, b: string): number {
  const ms = Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)
  return Math.round(ms / 86400000)
}

export default CrowdService
