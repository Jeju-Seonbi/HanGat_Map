import type { CongestionLevel } from '../assets/types'

// 집중률 → 등급 변환 (여유 <40 / 보통 40~69 / 혼잡 70~79 / 매우 혼잡 ≥80)
// 백엔드 공통 코어 CongestionService와 동일 임계값을 유지한다 (data/data.ts의 level도 이 기준)
export const levelOf = (score: number): CongestionLevel => {
  if (score < 40) return 'RELAXED'
  if (score < 70) return 'MODERATE'
  if (score < 80) return 'CROWDED'
  return 'VERY_CROWDED'
}
