<script setup lang="ts">
import { reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import CourseConditionForm from '../../components/course/CourseConditionForm.vue'
import CourseGenerationResult from '../../components/course/CourseGenerationResult.vue'
import { useCourseGenerationStore } from '../../app/stores/courseGeneration'
import type { CourseCondition } from '../../assets/types/course'

const now = new Date()
const later = new Date(now)
later.setDate(now.getDate() + 2)
const iso = (date: Date) => date.toISOString().slice(0, 10)

const condition = reactive<CourseCondition>({
  start_date: iso(now),
  end_date: iso(later),
  people: 2,
  budget_total: 400000,
  transport: 'RENTAL_CAR',
  course_regions: [],
  course_styles: [],
  course_place_preferences: [],
})

const generationStore = useCourseGenerationStore()
const { result, loading, error } = storeToRefs(generationStore)
const editing = ref(true)

async function generate(next: CourseCondition) {
  Object.assign(condition, JSON.parse(JSON.stringify(next)) as CourseCondition)
  const generated = await generationStore.generate(condition)
  if (!generated) return
  editing.value = false
  requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

function editCondition() {
  generationStore.clearResult()
  editing.value = true
}
</script>

<template>
  <div class="ai-course-page">
    <header class="course-page-header">
      <div>
        <span>AI 코스 만들기</span>
        <h1>{{ loading ? '제주 여행을 구성하고 있어요' : editing ? '나만의 제주 여행' : '추천 코스' }}</h1>
        <p>{{ loading ? '선택한 조건을 바탕으로 잠시만 기다려 주세요.' : editing ? '여행 조건을 선택하면 혼잡도와 동선을 고려해 코스를 추천해드려요.' : '선택한 조건과 예상 혼잡도를 반영한 제주 여행 일정이에요.' }}</p>
      </div>
    </header>

    <section v-if="loading" class="course-shell generation-state" aria-live="polite">
      <div class="generation-spinner" aria-hidden="true" />
      <span class="result-label">AI 코스 생성 중</span>
      <h2>여행 조건을 분석하고 있어요.</h2>
      <p>혼잡도와 이동 동선을 고려해 코스를 만들고 있어요.</p>
    </section>

    <section v-else-if="editing" class="course-shell">
      <CourseConditionForm :initial="condition" :loading="loading" @submit="generate" />
      <p v-if="error" class="course-error" role="alert">
        {{ error }} <button class="text-link" :disabled="loading" @click="generate(condition)">다시 시도</button>
      </p>
    </section>

    <CourseGenerationResult
      v-else-if="result"
      :result="result"
      :condition="condition"
      :loading="loading"
      @edit="editCondition"
      @regenerate="generate(condition)"
    />
  </div>
</template>
