export const MAP_LABEL_MAX_LEVEL = 7

export const shouldShowMapLabels = (level: number) => level <= MAP_LABEL_MAX_LEVEL

export const POI_MARKER_CLASS = {
  food: 'mk-food',
  dine: 'mk-dine',
  cafe: 'mk-cafe',
  cvs: 'mk-cvs',
  stay: 'mk-stay',
  mart: 'mk-mart',
} as const

export type PoiGroup = keyof typeof POI_MARKER_CLASS

/** 업종 레이어 키 - MapCanvas 가 이 순서로 핀을 적용한다 */
export const POI_GROUPS = Object.keys(POI_MARKER_CLASS) as PoiGroup[]

/**
 * 관광지 핀의 모습 - 클래스·지름·z 를 상태(혼잡 단계·선택/코스·필터 안)에서 정한다.
 * 순수 함수로 뺀 이유: MapCanvas 는 `sig` 가 지난번과 같으면 DOM 을 건드리지 않는다(핀 재사용).
 * 그래서 "어떤 상태 조합이 같은 모습인가"를 스펙으로 고정해 둔다.
 */
export function spotPinSpec (tier: string, pick: boolean, on: boolean) {
  const cls = `pn ${tier}${pick ? ' pick' : ''}${on ? '' : ' dim'}`
  const size = pick ? 20 : on ? 15 : 9
  const z = pick ? 400 : on ? 200 : 100
  return { cls, size, z, sig: `${cls}|${size}|${z}` }
}
