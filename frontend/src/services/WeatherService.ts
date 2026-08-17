/**
 * 메인 화면의 "제주 일주일 날씨".
 *
 * ⚠️ 이 파일은 전달본에 없었다 (통합 2026-08-17).
 *    views/home/HomeView.vue가 `WeatherService.getWeeklyForecast()`와 `DailyWeather`를
 *    import 하는데 정동현님 폴더에도 AI코스 폴더에도 services/WeatherService.ts 가 없어
 *    빌드가 실패했다. 화면이 기대하는 모양 그대로 채워 넣었다.
 *
 * 값은 **예측이 아니라 결정적 샘플**이다. 새로 지어내지 않고
 * 이미 프로젝트에 있는 utils/crowd.js 의 weatherOn() 을 쓴다 —
 * 원본 index.html:526~528 의 수식을 그대로 옮긴 함수이고,
 * 지도·마이페이지의 날씨 배지도 같은 값을 본다. 화면끼리 날씨가 어긋나지 않는다.
 *
 * 실서비스에서는 기상청 단기·중기예보로 이 파일만 교체하면 된다.
 */
/* utils/crowd.js · utils/date.js 는 JS 다. tsconfig 의 allowJs 로 해석되고
   checkJs:false 라 반환값은 any 로 잡힌다 — 아래 DailyWeather 로 모양을 고정한다. */
import { weatherOn } from '../utils/crowd.js'
import { at, fmt } from '../utils/date.js'

export interface DailyWeather {
  /** 8/17 (월) */
  day: string
  /** 화면에는 aria-hidden 으로 들어간다 — 뜻은 description 이 전달한다 */
  icon: string
  temperature: number
  description: string
}

const ICON: Record<string, string> = {
  맑음: '☀️',
  구름: '☁️',
  비: '🌧️'
}

const FORECAST_DAYS = 7

export const WeatherService = {
  /** 오늘부터 7일. weatherOn() 이 날짜만 보고 값을 내므로 매일 자동으로 밀린다 */
  async getWeeklyForecast (): Promise<DailyWeather[]> {
    return Array.from({ length: FORECAST_DAYS }, (_, i) => {
      const date = at(i)
      const w = weatherOn(date)
      return {
        day: fmt(date),
        icon: ICON[w.k] ?? '☁️',
        temperature: w.t,
        description: `${w.k} · 최저 ${w.tmin}°`
      }
    })
  }
}

export default WeatherService
