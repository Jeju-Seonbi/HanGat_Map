import type { Place } from '../../assets/types'
import { levelOf } from '../../utils/congestion'

export interface FavoriteMapItem {
  placeId: string
  name: string
  category: string
  region: string
  addr: string
  x: number
  y: number
  crowd: number
  fee: number
}

export function toKakaoFavoritePlaces(items: FavoriteMapItem[]): Place[] {
  return items.map(item => {
    const score = Math.round(Number(item.crowd) || 0)
    return {
      id: item.placeId,
      name: item.name,
      region: item.region,
      category: item.category,
      address: item.addr,
      latitude: item.y,
      longitude: item.x,
      description: `${item.category} · ${item.region}`,
      score,
      level: levelOf(score),
      time: '',
      stay: '',
      cost: item.fee ? `${item.fee.toLocaleString('ko-KR')}원` : '무료',
      image: '',
      tags: [],
    }
  })
}
