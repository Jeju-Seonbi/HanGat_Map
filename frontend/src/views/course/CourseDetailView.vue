<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import CongestionBadge from '../../components/common/CongestionBadge.vue'
import MapRenderer from '../../components/map/MapRenderer.vue'
import { sampleCourses } from '../../data/courses'
import { levelLabel, places } from '../../data/data'
import { levelOf } from '../../utils/congestion'
import { resolveCourseDetail } from './courseDetailModel'

const route = useRoute()
const editing = ref(false)
const shared = ref(false)

const course = computed(() => resolveCourseDetail(String(route.params.courseId ?? ''), sampleCourses, places))
const averageScore = computed(() => {
  const stops = course.value?.places ?? []
  return stops.length ? Math.round(stops.reduce((sum, place) => sum + place.score, 0) / stops.length) : 0
})
const averageLabel = computed(() => levelLabel[levelOf(averageScore.value)])
const budgetLabel = computed(() => course.value?.budgetLabel.replace(/^검증가 식비 포함\s*/, '') ?? '-')
</script>

<template>
  <section v-if="course" class="page">
    <div class="page-head">
      <div>
        <span class="eyebrow">SAVED COURSE · {{ course.days.length }} DAYS</span>
        <h1>{{ course.title }}</h1>
        <p>{{ course.conditionLabel }}</p>
        <p class="muted course-highlight">{{ course.highlight }}</p>
      </div>
      <div class="actions">
        <button class="btn ghost" @click="shared = true">
          {{ shared ? '링크 복사됨 ✓' : '공유' }}
        </button>
        <button class="btn primary">저장됨 ✓</button>
      </div>
    </div>

    <div class="metrics panel">
      <div><small>예상 비용</small><b>{{ budgetLabel }}</b></div>
      <div><small>평균 혼잡도</small><b>{{ averageScore }} · {{ averageLabel }}</b></div>
      <div><small>방문 장소</small><b>{{ course.places.length }}곳</b></div>
      <div><small>일정</small><b>{{ course.days.length }}일</b></div>
    </div>

    <div class="course-detail-grid">
      <div class="panel course-days">
        <section v-for="day in course.days" :key="day.day" class="course-day-section">
          <div class="row course-day-head">
            <h2>DAY {{ day.day }} · {{ day.label }}</h2>
            <button class="text-link" @click="editing = !editing">
              {{ editing ? '수정 완료' : '일정 수정' }}
            </button>
          </div>

          <div class="simple-timeline">
            <div v-for="place in day.places" :key="`${day.day}-${place.id}`">
              <span>{{ place.time }}</span>
              <img :src="place.image" :alt="place.name">
              <div class="course-stop-content">
                <div class="course-stop-head">
                  <h3>{{ place.name }}</h3>
                  <RouterLink
                    class="place-detail-link"
                    :to="`/places/${place.id}`"
                    :aria-label="`${place.name} 상세 페이지 보기`"
                  >상세 보기</RouterLink>
                </div>
                <p>{{ place.stay }} · {{ place.cost }}</p>
                <CongestionBadge :level="place.level" />
                <div v-if="editing" class="edit-actions">
                  <button>시간 변경</button>
                  <button>장소 교체</button>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>

      <aside>
        <MapRenderer :places="course.places" show-route />
        <p class="route-note">
          장소 간 추천 순서를 나타낸 선이며 실제 도로 경로와 다를 수 있습니다.
        </p>
        <div class="panel compact">
          <h3>코스 정보</h3>
          <dl>
            <dt>일정</dt><dd>{{ course.days.length }}일</dd>
            <dt>장소</dt><dd>{{ course.places.length }}곳</dd>
            <dt>평균 혼잡도</dt><dd>{{ averageScore }} · {{ averageLabel }}</dd>
          </dl>
        </div>
      </aside>
    </div>
  </section>

  <section v-else class="page course-not-found">
    <div class="panel">
      <span class="eyebrow">COURSE NOT FOUND</span>
      <h1>코스를 찾을 수 없어요</h1>
      <p class="muted">저장 목록에서 코스를 다시 선택해 주세요.</p>
      <RouterLink class="btn primary" to="/courses">저장 코스로 돌아가기</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.course-highlight{margin-top:8px}
.course-days{display:grid;gap:32px}
.course-day-section+.course-day-section{padding-top:6px;border-top:1px solid var(--border)}
.course-day-head{margin-bottom:2px}
.course-day-head h2{margin:0}
.course-stop-content{min-width:0}
.course-stop-head{display:flex;align-items:center;justify-content:space-between;gap:12px}
.course-stop-head h3{min-width:0}
.place-detail-link{flex-shrink:0;padding:7px 11px;border:1px solid var(--border);border-radius:10px;
  background:var(--muted);color:var(--primary);font-size:.75rem;font-weight:800;line-height:1}
.place-detail-link:hover{border-color:var(--primary);background:#e7f3ee}
.course-not-found{max-width:720px;text-align:center}
.course-not-found .panel{padding:64px 30px}
.course-not-found .btn{margin-top:20px}
@media(max-width:767px){
  .course-stop-head{align-items:flex-start}
  .place-detail-link{padding:6px 8px}
  .course-day-head{align-items:flex-start}
}
</style>
