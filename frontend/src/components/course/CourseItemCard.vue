<script setup lang="ts">
import type { CourseItem, Transport } from '../../assets/types/course'

const props = defineProps<{ item: CourseItem; transport: Transport }>()
defineEmits<{ alternative: [CourseItem]; reschedule: [CourseItem] }>()

const level = { QUIET: '한산', NORMAL: '보통', CROWDED: '혼잡' }
const accuracy = { VERIFIED: '검증가', ESTIMATED: '추정', UNKNOWN: '가격 정보 없음' }
const weather = {
  SUNNY: ['☀', '맑음'], CLOUDY: ['☁', '흐림'], RAIN: ['🌧', '비'], SNOW: ['🌨', '눈'], STRONG_WIND: ['💨', '강풍'],
} as const
const travelMode = { RENTAL_CAR: '차량', PUBLIC_TRANSIT: '대중교통', TAXI: '택시', WALK_BIKE: '도보·자전거' }
const distance = (metres?: number) => metres ? metres >= 1000 ? `${(metres / 1000).toFixed(1)}km` : `${metres}m` : ''
const gapDuration = (minutes: number) => minutes % 60
  ? `약 ${Math.floor(minutes / 60)}시간 ${minutes % 60}분`
  : `약 ${minutes / 60}시간`
const costLabel = (cost: CourseItem['costs'][number]) => {
  if (cost.accuracy_type === 'UNKNOWN') return accuracy.UNKNOWN
  if (cost.amount_min == null && cost.amount_max == null) return accuracy[cost.accuracy_type]
  const amount = cost.amount_min === cost.amount_max
    ? `${(cost.amount_min ?? cost.amount_max ?? 0).toLocaleString()}원`
    : `${(cost.amount_min ?? 0).toLocaleString()}~${(cost.amount_max ?? 0).toLocaleString()}원`
  return `${amount} · ${accuracy[cost.accuracy_type]}`
}
</script>

<template>
  <div v-if="item.gap_before" class="travel-line">
    <span>↓</span> 자유시간 · {{ gapDuration(item.gap_before.minutes) }}
  </div>
  <div v-if="item.inbound_travel_minutes" class="travel-line">
    <span>↓</span> {{ travelMode[props.transport] }} {{ item.inbound_travel_minutes }}분 · {{ distance(item.inbound_distance_m) }}
  </div>
  <article class="course-item">
    <div class="item-time"><b>{{ item.start_time || '시간 미정' }}</b><small v-if="item.end_time">~ {{ item.end_time }}</small></div>
    <img :src="item.image_url || '/images/placeholder.svg'" :alt="item.place_name">
    <div class="item-copy">
      <div class="item-head">
        <div><small>{{ item.category_name }}</small><h3>{{ item.place_name }}</h3></div>
        <span v-if="item.congestion_level" class="level" :class="item.congestion_level.toLowerCase()">{{ level[item.congestion_level] }}</span>
        <span v-else class="level unknown">혼잡 정보 없음</span>
      </div>
      <div class="badges">
        <span v-if="item.item_source === 'USER_FIXED'">사용자 지정</span>
        <span v-else-if="item.item_source === 'AI_RECOMMENDED'">AI 추천</span>
        <span v-else-if="item.item_source === 'REPLACEMENT'">대체 추천</span>
        <span v-for="cost in item.costs" :key="cost.id" class="cost-badge">{{ costLabel(cost) }}</span>
      </div>
      <p v-if="item.weather_condition" class="item-weather">{{ weather[item.weather_condition][0] }} {{ weather[item.weather_condition][1] }} {{ item.temperature }}℃<template v-if="item.weather_condition === 'RAIN' || item.weather_condition === 'SNOW'"> · 강수 {{ item.precipitation_probability }}%</template></p>
      <p class="item-reason">{{ item.recommendation_reason || '추천 이유를 준비 중이에요.' }}</p>
      <p v-if="item.weather_warning" class="fixed-warning">{{ item.weather_warning }}</p>
      <p v-if="item.operating_hours_warning" class="fixed-warning">선택한 방문 시간이 일반 운영시간과 다를 수 있어요.</p>
      <p v-if="item.item_source === 'USER_FIXED' && item.congestion_level === 'CROWDED'" class="fixed-warning">사용자 지정 일정이에요. 해당 시간대는 혼잡할 것으로 예상돼요.</p>
      <button v-if="item.item_source !== 'USER_FIXED'" class="btn small alternative-button" @click="$emit('alternative', item)">{{ item.congestion_level === 'CROWDED' ? '한산한 대안 보기' : '다른 장소 보기' }}</button>
      <button v-if="item.congestion_level === 'CROWDED'" class="btn small alternative-button reschedule-button" @click="$emit('reschedule', item)"><span class="reschedule-label-desktop">이 장소를 더 한산한 시간으로 옮기기</span><span class="reschedule-label-mobile">한산한 시간 찾기</span></button>
    </div>
  </article>
</template>

<style scoped>
.alternative-button{margin-right:6px;white-space:nowrap}.item-weather{margin:7px 0 0;color:var(--course-text-2);font-size:.7rem;font-weight:700}.reschedule-label-mobile{display:none}@media(max-width:767px){.alternative-button{width:100%;max-width:100%;margin-right:0;white-space:nowrap}.reschedule-label-desktop{display:none}.reschedule-label-mobile{display:inline}}
</style>
