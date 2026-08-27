export type Transport = 'RENTAL_CAR' | 'PUBLIC_TRANSIT' | 'TAXI' | 'WALK_BIKE'
export type CourseStatus = 'GENERATING' | 'READY' | 'SAVED' | 'FAILED' | 'EXPIRED' | 'DELETED'
export type CourseType = 'USER' | 'SAMPLE'
export type GenerationReason = 'INITIAL' | 'USER_REGENERATE' | 'WEATHER_REPLAN' | 'SAMPLE_BATCH'
export type PreferenceType = 'WANT' | 'AVOID'
export type ItemSource = 'USER_FIXED' | 'AI_RECOMMENDED' | 'REPLACEMENT'
export type CongestionLevel = 'QUIET' | 'NORMAL' | 'CROWDED'
export type CostCategory = 'FOOD' | 'LODGING' | 'TRANSPORT' | 'ACTIVITY' | 'OTHER'
export type AccuracyType = 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN'
export type RecommendationReasonCode = 'CONGESTION' | 'STYLE' | 'GOOD_PRICE' | 'HIDDEN_GEM' | 'ROUTE'
export type WeatherCondition = 'SUNNY' | 'CLOUDY' | 'RAIN' | 'SNOW' | 'STRONG_WIND'
export type IndoorOutdoor = 'INDOOR' | 'OUTDOOR' | 'MIXED'

export interface RegionRef { region_id: number; code: 'EAST'|'WEST'|'SOUTH'|'NORTH'; name: string }
export interface CourseStyle { tag_id: number; code: string; name: string; weight: number }
export interface PlacePreference {
  place_id?: number
  source_code?: 'KAKAO_LOCAL'
  source_place_id?: string
  place_name: string
  address?: string
  road_address?: string
  latitude?: number
  longitude?: number
  category_name?: string
  preference_type: PreferenceType
  fixed_date?: string
  fixed_time?: string
}
export interface KakaoPlaceSearchResult {
  source_code: 'KAKAO_LOCAL'; source_place_id: string; place_name: string
  address?: string; road_address?: string; latitude: number; longitude: number
  phone?: string; place_url?: string; category_name?: string
}
export interface AccommodationInput extends KakaoPlaceSearchResult { region?: RegionRef['code']; image_url?: string }
export interface AccommodationRecommendation extends AccommodationInput { recommendation_reason: string }
export interface CourseCondition {
  start_date: string; end_date: string; people: number; budget_total: number; transport: Transport
  course_regions: RegionRef[]; course_styles: CourseStyle[]; course_place_preferences: PlacePreference[]; accommodation?: AccommodationInput
}
export interface CourseItemCost {
  id: number; course_id: number; course_item_id?: number; category: CostCategory; accuracy_type: AccuracyType
  amount_min?: number; amount_max?: number; currency: 'KRW'; basis_text?: string
}
export interface CourseGap { start_time: string; end_time: string; minutes: number; type: 'FREE_TIME' }
export interface CourseCostSummary {
  verified_amount: number
  estimated_min: number
  estimated_max: number
  unknown_count: number
}
export interface CourseItem {
  id: number; course_id: number; place_id: number; place_name: string; category_name: string; image_url?: string
  source_code?: 'KAKAO_LOCAL'; source_place_id?: string; latitude?: number; longitude?: number
  day_no: number; position: number; visit_date: string; start_time?: string; end_time?: string
  item_source: ItemSource; inbound_distance_m?: number; inbound_travel_minutes?: number
  congestion_rate?: number; congestion_level?: CongestionLevel; recommendation_reason_code?: RecommendationReasonCode
  recommendation_reason?: string; replaced_from_place_id?: number; gap_before?: CourseGap
  weather_condition?: WeatherCondition; temperature?: number; precipitation_probability?: number
  weather_warning?: string; weather_influenced?: boolean
  operating_hours_warning?: boolean; accommodation_influenced?: boolean; costs: CourseItemCost[]
}
export interface CourseDay {
  day_no: number; visit_date: string; items: CourseItem[]
  accommodation_departure_distance_m?: number; accommodation_departure_travel_minutes?: number
  accommodation_return_distance_m?: number; accommodation_return_travel_minutes?: number
}
export interface CourseResult {
  id: number; course_type: CourseType; generation_reason: GenerationReason; status: CourseStatus; title?: string
  start_date: string; end_date: string; people: number; budget_total?: number; transport: Transport
  estimated_cost_min?: number; estimated_cost_max?: number; average_congestion_rate?: number
  cost_summary?: CourseCostSummary; generation_error_code?: string; accommodation?: AccommodationInput; days: CourseDay[]
}
export interface AlternativePlace {
  place_id: number; place_name: string; category_name: string; subcategory_name?: string; image_url?: string
  distance_m: number; congestion_rate?: number; congestion_level?: CongestionLevel
  recommendation_reason: string; replacement_reason: string; radius_km?: 10|20
}
export interface CongestionRescheduleOption {
  visit_date: string
  start_time: string
  end_time: string
  congestion_rate: number
  congestion_level: CongestionLevel
  weather_condition: WeatherCondition
  temperature: number
  precipitation_probability: number
}
export interface SavedCourseSummary {
  course_id: number
  title: string
  start_date: string
  end_date: string
  representative_places: string[]
  average_congestion_rate?: number
  estimated_cost_min?: number
  estimated_cost_max?: number
}
export interface SavedCourseRecord { summary: SavedCourseSummary; course: CourseResult }

export type CourseGenerationItemSource = 'USER_FIXED' | 'AI_RECOMMENDED'

export interface CourseGenerationTourCategory {
  category1: string | null
  category2: string | null
  category3: string | null
}

export interface CourseGenerationCongestionFact {
  date: string | null
  rate: number | null
  level: CongestionLevel | null
}

export interface CourseGenerationWeatherFact {
  forecast_date: string
  forecast_time: string
  temperature: number | null
  precipitation_probability: number | null
  precipitation_type_code: string | null
  sky_condition_code: string | null
  wind_speed: number | null
  humidity: number | null
}

export interface CourseGenerationItem {
  candidate_id: string
  place_name: string
  address: string | null
  latitude: number | null
  longitude: number | null
  image_url: string | null
  tour_category: CourseGenerationTourCategory | null
  region_code: RegionRef['code'] | 'UNKNOWN'
  preference_type: PreferenceType | null
  confirmed_style_hints: string[]
  position: number
  start_time: string | null
  item_source: CourseGenerationItemSource
  recommendation_reason: string | null
  congestion: CourseGenerationCongestionFact[]
  weather: CourseGenerationWeatherFact[] | null
}

export interface CourseGenerationDay {
  day_no: number
  visit_date: string
  items: CourseGenerationItem[]
}

export interface CourseGenerationResponse {
  contract_version: string
  start_date: string
  end_date: string
  days: CourseGenerationDay[]
}
