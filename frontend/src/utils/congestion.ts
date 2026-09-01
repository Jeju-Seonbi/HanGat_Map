import type { CongestionLevel } from '../assets/types'

// 집중률 -> 등급 변환. 팀 표준 3단계(2026-08-31 통일): 여유 <40 / 보통 <70 / 혼잡 >=70
// 단일 출처는 백엔드 map CongestionLevel.from - 여기와 어긋나면 배지와 저장 스냅숏이 달라진다
export const levelOf = (score: number): CongestionLevel => {
  if (score < 40) return 'QUIET'
  if (score < 70) return 'NORMAL'
  return 'CROWDED'
}
