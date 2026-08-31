import type { CourseCondition, CourseResult } from '../assets/types/course'

const STORAGE_KEY = 'hangat.pending-course-claim.v1'

export interface PendingCourseClaim {
  course: CourseResult
  condition: CourseCondition
  title: string
}

export function storePendingCourseClaim(
  course: CourseResult,
  condition: CourseCondition,
  title: string,
): void {
  if (!course.claim_token || !course.claim_expires_at) {
    throw new Error('저장할 코스의 인증 정보가 없습니다.')
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ course, condition, title }))
}

export function takePendingCourseClaim(): PendingCourseClaim | null {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  sessionStorage.removeItem(STORAGE_KEY)

  try {
    const value = JSON.parse(raw) as PendingCourseClaim
    if (!value?.course?.claim_token || !value.course.claim_expires_at
      || !value.condition || !value.title?.trim()) {
      return null
    }
    if (Date.parse(value.course.claim_expires_at) <= Date.now()) {
      return null
    }
    return value
  } catch {
    return null
  }
}

export function clearPendingCourseClaim(): void {
  sessionStorage.removeItem(STORAGE_KEY)
}
