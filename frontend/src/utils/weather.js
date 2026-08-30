import { at, iso } from './date'
import WeatherService from '../services/map/WeatherService'

/* MAP-05: 기상청 실데이터(7일). 로드는 loadPlaces()가 한다 - 범위 밖·실패면 null */
export function wxOf(i) {
  return WeatherService.byDate(iso(at(i)))
}

/* 날씨 아이콘 — 텍스트 글자(☀☁☂)는 브라우저마다 다르게 그려져서 SVG로 직접 그린다 */
const CLOUD = 'M6.4 18.4h11.1a4.6 4.6 0 0 0 .3-9.2A6.6 6.6 0 0 0 5.5 10.3a4.1 4.1 0 0 0 .9 8.1Z'

export function wxIcon(k, z = 26) {
  const o = `<svg class="wxi" viewBox="0 0 24 24" width="${z}" height="${z}" aria-hidden="true">`
  if (k === '맑음') return o + `<circle cx="12" cy="12" r="4.9" fill="#FFAE1A"/>
    <g stroke="#FFAE1A" stroke-width="2.1" stroke-linecap="round">
    <path d="M12 1.7v2.4M12 19.9v2.4M1.7 12h2.4M19.9 12h2.4
    M4.7 4.7l1.7 1.7M17.6 17.6l1.7 1.7M4.7 19.3l1.7-1.7M17.6 6.4l1.7-1.7"/></g></svg>`
  if (k === '구름') return o + `<path d="${CLOUD}" fill="#AFC0D0"/></svg>`
  return o + `<g transform="translate(0,-2.2)"><path d="${CLOUD}" fill="#93A8BC"/></g>
    <g stroke="#3FA0E4" stroke-width="2.2" stroke-linecap="round">
    <path d="M8.2 18.6l-1 3M12 18.6l-1 3M15.8 18.6l-1 3"/></g></svg>`
}
