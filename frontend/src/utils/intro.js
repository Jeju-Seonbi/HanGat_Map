/**
 * 상세 패널 '소개' 문단 (MAP_008).
 * overview 컬럼은 관광지 소개글과 착한가격·식당 메뉴 문단("대표메뉴: …")이 같이 쓴다 -
 * 메뉴 문단은 메뉴 섹션이 표로 그리므로 소개로는 내지 않는다.
 */
const MENU_PREFIX = '대표메뉴:'

/** 소개로 보여줄 글. 없거나 메뉴 문단이면 null */
export function introOf (overview) {
  const t = (overview ?? '').trim()
  if (!t || t.startsWith(MENU_PREFIX)) return null
  return t
}

/** 2줄로 접었을 때 '더보기'가 필요한 길이인지 - 한글 기준 두 줄 ≈ 70자 */
export const needsMore = text => (text ?? '').length > 70
