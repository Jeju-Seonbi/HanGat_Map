import type { PlacePreference, PreferenceType } from '../assets/types/course'

export type PreferenceConflict = 'DUPLICATE' | 'OPPOSITE' | undefined

export function findPreferenceConflict(preferences: PlacePreference[], sourcePlaceId: string, preferenceType: PreferenceType): PreferenceConflict {
  const existing = preferences.find(item => item.source_code === 'KAKAO_LOCAL' && item.source_place_id === sourcePlaceId)
  if (!existing) return undefined
  return existing.preference_type === preferenceType ? 'DUPLICATE' : 'OPPOSITE'
}
