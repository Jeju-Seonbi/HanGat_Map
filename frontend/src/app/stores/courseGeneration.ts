import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { CourseCondition, CourseGenerationResponse } from '../../assets/types/course'
import { CourseApiError, courseApiService } from '../../services/courseApiService'

type CourseGenerator = Pick<typeof courseApiService, 'createCourse'>

export function courseGenerationErrorMessage(error: unknown) {
  if (!(error instanceof CourseApiError)) return '코스를 생성하지 못했어요. 다시 시도해 주세요.'
  if (error.failure === 'TIMEOUT') return '코스 생성 시간이 길어지고 있어요. 잠시 후 다시 시도해 주세요.'
  if (error.failure === 'NETWORK') return '서버에 연결하지 못했어요. 네트워크 상태를 확인해 주세요.'
  if (error.failure === 'INVALID_RESPONSE') return '생성된 코스 정보를 불러오지 못했어요. 다시 시도해 주세요.'
  return error.status != null && error.status < 500
    ? '입력한 여행 조건을 확인해 주세요.'
    : 'AI 코스를 생성하지 못했어요. 잠시 후 다시 시도해 주세요.'
}

export const useCourseGenerationStore = defineStore('courseGeneration', () => {
  const result = ref<CourseGenerationResponse>()
  const loading = ref(false)
  const error = ref('')

  async function generate(condition: CourseCondition, generator: CourseGenerator = courseApiService) {
    if (loading.value) return result.value
    loading.value = true
    error.value = ''
    try {
      result.value = await generator.createCourse(condition)
      return result.value
    } catch (failure) {
      error.value = courseGenerationErrorMessage(failure)
      return undefined
    } finally {
      loading.value = false
    }
  }

  function clearResult() {
    result.value = undefined
    error.value = ''
  }

  return { result, loading, error, generate, clearResult }
})
