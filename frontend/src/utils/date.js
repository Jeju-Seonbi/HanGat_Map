/* 예보 시작일 = 접속 시점의 오늘(0시).
   날짜를 고정하지 않으므로 매일 자동으로 갱신된다 — 오늘이 항상 첫 날짜이고 어제 이전은 고를 수 없다 */
export const D0 = (() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d })()

/** 오늘로부터 i일 뒤 */
export const at = i => { const d = new Date(D0); d.setDate(d.getDate() + i); return d }

export const iso = d =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** 8/17 (월) */
export const fmt = d => `${d.getMonth() + 1}/${d.getDate()} (${'일월화수목금토'[d.getDay()]})`

/** 8월 17일 */
export const fmtK = d => `${d.getMonth() + 1}월 ${d.getDate()}일`

/** n일 전 날짜 (샘플 후기의 방문일 표기용) */
export const ago = n => { const d = new Date(D0); d.setDate(d.getDate() - n); return `${d.getMonth() + 1}/${d.getDate()}` }

/** 연·월을 하나의 정수로 — 달력의 이동 가능 범위 비교용 */
export const monthKey = d => d.getFullYear() * 12 + d.getMonth()

/** 예보 범위: 오늘(0) ~ 29일 뒤 */
export const FORECAST_DAYS = 30
