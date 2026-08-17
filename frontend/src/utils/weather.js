import { at, iso } from './date'
import { hash } from './crowd'

/* 기상청 단기예보(3일)·중기예보(10일) 자리 — 지금은 샘플 */
export function wxOf(i) {
  const d = iso(at(i)), h = hash('wx' + d) % 100, t = 28 + (hash('t' + d) % 6)
  const w = h < 52 ? { k: '맑음', rain: 0 } : h < 80 ? { k: '구름', rain: 0 } : { k: '비', rain: 1 }
  return { ...w, t: w.rain ? t - 3 : t, tmin: (w.rain ? t - 3 : t) - 4 }
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
