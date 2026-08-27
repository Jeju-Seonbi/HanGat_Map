<script setup lang="ts">
import { computed } from 'vue'
import type {
  CourseCondition,
  CourseGenerationItem,
  CourseGenerationResponse,
  CongestionLevel,
} from '../../assets/types/course'
import {
  categoryCode,
  congestionForVisit,
  formatCourseTime,
  weatherForVisit,
} from '../../services/courseGenerationPresentation'

const props = defineProps<{
  result: CourseGenerationResponse
  condition: CourseCondition
  loading: boolean
}>()
defineEmits<{ edit: []; regenerate: [] }>()

const transportLabel = {
  RENTAL_CAR: '렌터카',
  PUBLIC_TRANSIT: '대중교통',
  TAXI: '택시',
  WALK_BIKE: '도보·자전거',
}
const congestionLabel: Record<CongestionLevel, string> = {
  QUIET: '쾌적',
  NORMAL: '보통',
  CROWDED: '혼잡',
}
const tripDays = computed(() => props.result.days.length)
const tripNights = computed(() => Math.max(0, tripDays.value - 1))
const visitCount = computed(() => props.result.days.reduce(
  (count, day) => count + day.items.length, 0))
const regionSummary = computed(() => props.condition.course_regions
  .map(region => region.name).join(' · ') || '전체')
const styleSummary = computed(() => props.condition.course_styles
  .map(style => style.name).join(' · '))

const formatDate = (value: string) => new Intl.DateTimeFormat('ko-KR', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
  timeZone: 'UTC',
}).format(new Date(`${value}T00:00:00Z`))

const formatShortDate = (value: string) => new Intl.DateTimeFormat('ko-KR', {
  month: 'numeric',
  day: 'numeric',
  timeZone: 'UTC',
}).format(new Date(`${value}T00:00:00Z`))

const congestion = (item: CourseGenerationItem, visitDate: string) =>
  congestionForVisit(item, visitDate)
const weather = (item: CourseGenerationItem, visitDate: string) =>
  weatherForVisit(item, visitDate)
</script>

<template>
  <section class="course-shell result-shell">
    <header class="course-result-head">
      <div class="result-heading-copy">
        <span class="result-label">추천 코스</span>
        <h2>{{ tripNights }}박 {{ tripDays }}일 제주 여행</h2>
        <p>{{ formatShortDate(result.start_date) }} ~ {{ formatShortDate(result.end_date) }} · {{ condition.people }}명 · {{ transportLabel[condition.transport] }}</p>
        <div class="result-condition-tags">
          <span>{{ regionSummary }}</span>
          <span v-if="styleSummary">{{ styleSummary }}</span>
        </div>
      </div>
    </header>

    <div class="course-result-grid">
      <main>
        <section v-for="day in result.days" :key="day.day_no" class="course-day">
          <header><b>DAY {{ day.day_no }}</b><span>{{ formatDate(day.visit_date) }}</span></header>
          <div class="day-timeline">
            <article v-for="item in day.items" :key="item.candidate_id" class="course-item">
              <div class="item-time"><b>{{ formatCourseTime(item.start_time) }}</b></div>
              <img :src="item.image_url || '/images/placeholder.svg'" :alt="item.place_name">
              <div class="item-copy">
                <div class="item-head">
                  <div><small>{{ item.region_code }} · {{ categoryCode(item) }}</small><h3>{{ item.place_name }}</h3></div>
                  <span
                    v-if="congestion(item, day.visit_date)?.level"
                    class="level"
                    :class="congestion(item, day.visit_date)!.level!.toLowerCase()"
                  >{{ congestionLabel[congestion(item, day.visit_date)!.level!] }}</span>
                  <span v-else class="level unknown">혼잡 정보 없음</span>
                </div>
                <div class="badges">
                  <span>{{ item.item_source === 'USER_FIXED' ? '사용자 지정' : 'AI 추천' }}</span>
                  <span v-for="hint in item.confirmed_style_hints" :key="hint">{{ hint }}</span>
                </div>
                <p v-if="item.address" class="item-address">{{ item.address }}</p>
                <p v-if="weather(item, day.visit_date)" class="item-weather">
                  {{ weather(item, day.visit_date)!.temperature ?? '기온 정보 없음' }}<template v-if="weather(item, day.visit_date)!.temperature != null">℃</template>
                  <template v-if="weather(item, day.visit_date)!.precipitation_probability != null"> · 강수 {{ weather(item, day.visit_date)!.precipitation_probability }}%</template>
                </p>
                <p class="item-reason">{{ item.recommendation_reason || '추천 이유를 준비 중이에요.' }}</p>
              </div>
            </article>
          </div>
        </section>
      </main>

      <aside class="course-side">
        <section class="course-summary-card">
          <span class="summary-kicker">TRIP SUMMARY</span>
          <h3>코스 요약</h3>
          <dl>
            <dt>여행 일정</dt><dd>{{ tripNights }}박 {{ tripDays }}일</dd>
            <dt>방문 장소</dt><dd>{{ visitCount }}곳</dd>
            <dt>전체 예산</dt><dd>{{ condition.budget_total.toLocaleString() }}원</dd>
            <dt>이동수단</dt><dd>{{ transportLabel[condition.transport] }}</dd>
            <dt>숙소</dt><dd>{{ condition.accommodation?.place_name ?? '미정' }}</dd>
          </dl>
        </section>
      </aside>
    </div>

    <div class="result-actions">
      <button class="btn" @click="$emit('edit')">조건 수정</button>
      <button class="btn primary" :disabled="loading" @click="$emit('regenerate')">다른 코스 만들기</button>
    </div>
  </section>
</template>

<style scoped>
.item-address{margin:7px 0 0;color:var(--course-text-2);font-size:.75rem}.item-weather{margin:7px 0 0;color:var(--course-text-2);font-size:.75rem;font-weight:700}@media(max-width:767px){.course-result-grid{display:flex;flex-direction:column}.course-side{display:contents}.course-result-grid>main{order:2;min-width:0;width:100%}.course-summary-card{order:3;min-width:0;width:100%}}
</style>
