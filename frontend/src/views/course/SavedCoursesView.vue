<script setup lang="ts">
// 저장한 코스 목록 페이지 (담당: 정동현) - 항목 클릭 시 코스 상세(/courses/:courseId)로 이동
// 10개 단위 페이징 - 백엔드 API 계약도 page/size 파라미터(Spring Pageable) 전제
import { computed, ref } from 'vue'
import { usePlaces } from '../../composables/usePlaces'
import { sampleCourses } from '../../data/courses'
import { levelOf } from '../../utils/congestion'
import CongestionBadge from '../../components/common/CongestionBadge.vue'

const { places } = usePlaces()

// 시연용 저장 기록: 같은 코스를 여러 번 저장한 이력으로 13건을 만들어 페이징을 보여준다
const SAVED_DATES = [
  '8월 14일', '8월 13일', '8월 12일', '8월 11일', '8월 10일', '8월 9일', '8월 8일',
  '8월 7일', '8월 6일', '8월 5일', '8월 4일', '8월 3일', '8월 2일',
]
const savedRecords = computed(() =>
  SAVED_DATES.map((savedAt, i) => {
    const course = sampleCourses[i % sampleCourses.length]
    const resolved = course.days
      .flatMap((d) => d.items)
      .flatMap((item) => {
        const place = places.value.find((p) => p.id === item.placeId)
        if (!place) return []
        return [{ name: place.name, score: item.scoreOverride ?? place.score }]
      })
    const avgScore = resolved.length
      ? Math.round(resolved.reduce((sum, r) => sum + r.score, 0) / resolved.length)
      : 0
    return {
      key: `${course.id}-${i}`,
      savedAt,
      course,
      stops: resolved.map((r) => r.name).join(' → '),
      avgLevel: levelOf(avgScore),
    }
  }),
)

const PAGE_SIZE = 10
const page = ref(1)
const totalPages = computed(() =>
  Math.max(1, Math.ceil(savedRecords.value.length / PAGE_SIZE)),
)
const pagedRecords = computed(() =>
  savedRecords.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
)
const goPage = (p: number) => {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>
<template>
  <section class="page">
    <div class="page-head">
      <div>
        <span class="eyebrow">SAVED COURSES</span>
        <h1>저장한 코스</h1>
        <p class="muted">
          총 {{ savedRecords.length }}개 · 시연용 Mock 데이터 · 코스를 누르면 상세로 이동해요
        </p>
      </div>
      <RouterLink
        class="btn primary"
        to="/travel/search"
      >
        새 코스 만들기
      </RouterLink>
    </div>
    <div class="saved-list">
      <RouterLink
        v-for="record in pagedRecords"
        :key="record.key"
        class="saved-card"
        :to="`/courses/${record.course.id}`"
      >
        <div class="saved-main">
          <small class="muted">{{ record.savedAt }} 저장 · {{ record.course.conditionLabel }} · {{ record.course.days.length }}일</small>
          <h3>{{ record.course.title }}</h3>
          <p class="muted">
            {{ record.stops }}
          </p>
        </div>
        <div class="saved-side">
          <CongestionBadge :level="record.avgLevel" />
          <span class="text-link">코스 상세 →</span>
        </div>
      </RouterLink>
    </div>
    <nav
      v-if="totalPages > 1"
      class="pagination"
      aria-label="저장한 코스 페이지 이동"
    >
      <button
        type="button"
        aria-label="이전 페이지"
        :disabled="page === 1"
        @click="goPage(page - 1)"
      >
        ‹
      </button>
      <button
        v-for="p in totalPages"
        :key="p"
        type="button"
        :class="{ active: page === p }"
        :aria-current="page === p ? 'page' : undefined"
        @click="goPage(p)"
      >
        {{ p }}
      </button>
      <button
        type="button"
        aria-label="다음 페이지"
        :disabled="page === totalPages"
        @click="goPage(page + 1)"
      >
        ›
      </button>
    </nav>
  </section>
</template>
<style scoped>
.saved-list {
  display: grid;
  gap: 16px;
}
.saved-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 26px 28px;
  border-radius: 20px;
  background: var(--surface);
  box-shadow: var(--shadow);
  color: inherit;
  transition: transform 0.15s ease;
}
.saved-card:hover {
  transform: translateY(-2px);
}
.saved-main h3 {
  margin: 6px 0;
  font-size: 1.15rem;
}
.saved-main p {
  margin: 0;
  font-size: 0.88rem;
}
.saved-side {
  flex: 0 0 auto;
  display: grid;
  gap: 8px;
  justify-items: end;
}
.pagination {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 36px;
}
.pagination button {
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: var(--muted);
  font-weight: 700;
  cursor: pointer;
}
.pagination button:hover:not(:disabled):not(.active) {
  filter: brightness(0.96);
}
.pagination button.active {
  background: var(--primary);
  color: var(--on-ac);
}
.pagination button:disabled {
  opacity: 0.35;
  cursor: default;
}
@media (max-width: 640px) {
  .saved-card {
    flex-direction: column;
    align-items: flex-start;
  }
  .saved-side {
    justify-items: start;
  }
}
</style>
