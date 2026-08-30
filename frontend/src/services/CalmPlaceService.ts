/**
 * 메인 "오늘 한적한 곳" 포스터 캐러셀 (MAIN_001).
 *
 * 1순위: 백엔드 GET /main/calm-places (관광공사 집중률 실측 시드 기반)
 * 폴백: 백엔드가 죽어 있으면 기존 목업(data/data)으로 화면 유지 + live 플래그로 라벨 전환.
 *
 * 실데이터 장소는 아직 프론트에 상세 페이지 데이터가 없으므로 detailId를 null로 둔다
 * - 카드가 링크 없이 렌더된다 (장소 상세 실연동 때 placeId 라우팅으로 교체).
 */
import { apiGet } from './apiClient'
import { levelLabel, places } from '../data/data'

export type CongestionLevelName = 'QUIET' | 'NORMAL' | 'CROWDED'

export interface CalmPlaceCard {
  key: string
  /** /places/:id 상세 이동이 가능한 목업 장소만 값이 있다 */
  detailId: string | null
  name: string
  region: string
  level: CongestionLevelName
  levelLabel: string
  imageUrl: string | null
  reason: string
}

export interface CalmPlaces {
  /** true = 백엔드 실데이터, false = 목업 폴백 */
  live: boolean
  cards: CalmPlaceCard[]
}

/** 백엔드 CalmPlaceResponse (domain/main/model/CalmPlaceResponse.java 와 동일 모양) */
interface BackendCalmPlace {
  placeId: number
  name: string
  regionLabel: string
  categoryLabel: string
  imageUrl: string | null
  rate: number
  level: CongestionLevelName
  levelLabel: string
  reason: string
}

export const CalmPlaceService = {
  async getCalmPlaces (limit = 7): Promise<CalmPlaces> {
    try {
      const rows = await apiGet<BackendCalmPlace[]>(`/main/calm-places?limit=${limit}`)
      return {
        live: true,
        cards: rows.map(row => ({
          key: `live-${row.placeId}`,
          detailId: null,
          name: row.name,
          region: row.regionLabel,
          level: row.level,
          levelLabel: row.levelLabel,
          imageUrl: row.imageUrl,
          reason: row.reason
        }))
      }
    } catch {
      return { live: false, cards: sampleCards(limit) }
    }
  }
}

/** 목업 폴백 - 기존 캐러셀과 동일 규칙 (관광지만, 혼잡 제외, 집중률 오름차순) */
function sampleCards (limit: number): CalmPlaceCard[] {
  return [...places]
    .filter(p => p.categoryCode !== 'RESTAURANT' && (p.level === 'QUIET' || p.level === 'NORMAL'))
    .sort((a, b) => a.score - b.score)
    .slice(0, limit)
    .map(p => ({
      key: p.id,
      detailId: p.id,
      name: p.name,
      region: p.region,
      level: p.level as CongestionLevelName,
      levelLabel: levelLabel[p.level],
      imageUrl: p.imageUrl ?? p.image ?? null,
      reason: p.level === 'QUIET' ? '이 날짜 혼잡 예보가 여유예요' : '인기 명소보다 한산한 편이에요'
    }))
}

export default CalmPlaceService
