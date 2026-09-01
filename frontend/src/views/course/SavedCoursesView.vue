<script setup lang="ts">
// 저장한 코스 목록 페이지 (담당: 정동현) - 항목 클릭 시 코스 상세(/courses/:courseId)로 이동
// 백엔드 GET /courses (JWT) 1순위, 비로그인·통신 실패면 목업으로 화면을 유지하고 라벨을 전환한다
import { onMounted, ref } from 'vue'
import CourseService, { type CourseCard } from '../../services/CourseService'
import CongestionBadge from '../../components/common/CongestionBadge.vue'

const PAGE_SIZE = 10

const cards = ref<CourseCard[]>([])
const ok = ref(true)
const loading = ref(true)
const page = ref(1)          // 화면은 1부터, 백엔드는 0부터
const totalPages = ref(1)
const totalElements = ref(0)

const load = async (target: number) => {
  loading.value = true
  const result = await CourseService.getSavedCourses(target - 1, PAGE_SIZE)
  cards.value = result.cards
  ok.value = result.ok
  totalPages.value = result.totalPages
  totalElements.value = result.totalElements
  page.value = target
  loading.value = false
}

const goPage = (p: number) => {
  if (p < 1 || p > totalPages.value || p === page.value) return
  void load(p)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => load(1))
</script>
<template>
  <section class="page">
    <div class="page-head">
      <div>
        <span class="eyebrow">SAVED COURSES</span>
        <h1>저장한 코스</h1>
        <p class="muted">
          총 {{ totalElements }}개 · 코스를 누르면 상세로 이동해요
        </p>
      </div>
      <RouterLink
        class="btn primary"
        to="/ai-course"
      >
        새 코스 만들기
      </RouterLink>
    </div>
    <p
      v-if="loading"
      class="muted"
    >
      불러오는 중이에요
    </p>
    <div
      v-else-if="!ok"
      class="empty"
    >
      <span>!</span>
      <p>코스를 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요</p>
      <button
        type="button"
        class="btn"
        @click="load(page)"
      >
        다시 시도
      </button>
    </div>
    <div
      v-else-if="!cards.length"
      class="empty"
    >
      <span>+</span>
      <p>아직 저장한 코스가 없어요</p>
      <RouterLink
        class="btn primary"
        to="/ai-course"
      >
        코스 만들러 가기
      </RouterLink>
    </div>
    <div
      v-else
      class="saved-list"
    >
      <RouterLink
        v-for="card in cards"
        :key="card.id"
        class="saved-card"
        :to="`/courses/${card.id}`"
      >
        <div class="saved-main">
          <small class="muted">
            <template v-if="card.savedAtLabel">{{ card.savedAtLabel }} 저장 · </template>{{ card.conditionLabel }}
          </small>
          <h3>{{ card.title }}</h3>
          <p
            v-if="card.stops"
            class="muted"
          >
            {{ card.stops }}
          </p>
        </div>
        <div class="saved-side">
          <CongestionBadge
            v-if="card.level"
            :level="card.level"
          />
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
