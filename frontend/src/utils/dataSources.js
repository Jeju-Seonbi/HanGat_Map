/**
 * 지도 화면 하단 출처 표기 (MAP_001) - 공공데이터 이용조건이자 심사 확인 항목.
 *
 * 켜진 레이어 기준으로 그 화면에 실제로 보이는 원천만, 기관명만 적는다(괄호 설명은 줄이 길어져 뺌 - 2026-09-11).
 * 관광지·식당·숙소 핀과 혼잡 색은 항상 있으니 관광공사·기상청은 고정,
 * 착한가격(행안부)·카페/편의점/마트(소상공인시장진흥공단)는 그 레이어가 켜졌을 때만.
 */
const KTO = '한국관광공사'
const KMA = '기상청'
const MOIS = '행정안전부'
const SBIZ = '소상공인시장진흥공단'

/** @param {Record<string, number|boolean>} layers state.L 모양 */
export function sourcesFor (layers) {
  const on = k => !!layers?.[k]
  const parts = [KTO]
  if (on('food')) parts.push(MOIS)
  if (on('cafe') || on('cvs') || on('mart')) parts.push(SBIZ)
  parts.push(KMA)
  return parts
}

export function sourceLine (layers) {
  return `출처 ${sourcesFor(layers).join(' · ')}`
}
