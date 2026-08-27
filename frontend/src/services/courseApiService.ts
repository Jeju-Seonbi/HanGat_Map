import type { CourseCondition, CourseGenerationResponse } from '../assets/types/course'

export type CourseApiFailure = 'HTTP' | 'NETWORK' | 'TIMEOUT' | 'INVALID_RESPONSE'

export class CourseApiError extends Error {
  constructor(
    message: string,
    public readonly failure: CourseApiFailure,
    public readonly status?: number,
  ) {
    super(message)
    this.name = 'CourseApiError'
  }
}

export function courseGenerationErrorMessage(error: unknown) {
  if (!(error instanceof CourseApiError)) return '코스를 생성하지 못했어요. 다시 시도해 주세요.'
  if (error.failure === 'TIMEOUT') return '코스 생성 시간이 길어지고 있어요. 잠시 후 다시 시도해 주세요.'
  if (error.failure === 'NETWORK') return '서버에 연결하지 못했어요. 네트워크 상태를 확인해 주세요.'
  if (error.failure === 'INVALID_RESPONSE') return '생성된 코스 정보를 불러오지 못했어요. 다시 시도해 주세요.'
  return error.status != null && error.status < 500
    ? '입력한 여행 조건을 확인해 주세요.'
    : 'AI 코스를 생성하지 못했어요. 잠시 후 다시 시도해 주세요.'
}

type CourseRequestPayload = Pick<
  CourseCondition,
  | 'start_date'
  | 'end_date'
  | 'people'
  | 'budget_total'
  | 'transport'
  | 'course_regions'
  | 'course_styles'
  | 'course_place_preferences'
  | 'accommodation'
>

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null

const isGenerationItem = (value: unknown) => {
  if (!isRecord(value)) return false
  return typeof value.candidate_id === 'string'
    && typeof value.place_name === 'string'
    && typeof value.position === 'number'
    && (typeof value.start_time === 'string' || value.start_time === null)
    && Array.isArray(value.confirmed_style_hints)
    && Array.isArray(value.congestion)
    && (value.weather === null || Array.isArray(value.weather))
}

export function isCourseGenerationResponse(value: unknown): value is CourseGenerationResponse {
  if (!isRecord(value)
      || typeof value.contract_version !== 'string'
      || typeof value.start_date !== 'string'
      || typeof value.end_date !== 'string'
      || !Array.isArray(value.days)) {
    return false
  }
  return value.days.every(day => isRecord(day)
    && typeof day.day_no === 'number'
    && typeof day.visit_date === 'string'
    && Array.isArray(day.items)
    && day.items.every(isGenerationItem))
}

export function toCourseRequestPayload(condition: CourseCondition): CourseRequestPayload {
  return {
    start_date: condition.start_date,
    end_date: condition.end_date,
    people: condition.people,
    budget_total: condition.budget_total,
    transport: condition.transport,
    course_regions: condition.course_regions,
    course_styles: condition.course_styles,
    course_place_preferences: condition.course_place_preferences,
    ...(condition.accommodation ? { accommodation: condition.accommodation } : {}),
  }
}

export class CourseApiClient {
  constructor(
    private readonly fetcher: typeof fetch = fetch,
    private readonly endpoint = '/courses',
  ) {}

  async createCourse(condition: CourseCondition, timeoutMs = 75_000): Promise<CourseGenerationResponse> {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    try {
      const response = await this.fetcher(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(toCourseRequestPayload(condition)),
        signal: controller.signal,
      })
      if (!response.ok) {
        throw new CourseApiError(
          response.status >= 500
            ? 'AI 코스 생성 서버가 요청을 처리하지 못했어요.'
            : '입력한 여행 조건을 확인해 주세요.',
          'HTTP',
          response.status,
        )
      }

      let body: unknown
      try {
        body = await response.json()
      } catch {
        throw new CourseApiError('코스 생성 응답 형식을 확인하지 못했어요.', 'INVALID_RESPONSE')
      }
      if (!isCourseGenerationResponse(body)) {
        throw new CourseApiError('코스 생성 응답 형식을 확인하지 못했어요.', 'INVALID_RESPONSE')
      }
      return body
    } catch (error) {
      if (error instanceof CourseApiError) throw error
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new CourseApiError('코스 생성 요청 시간이 초과됐어요.', 'TIMEOUT')
      }
      throw new CourseApiError('코스 생성 서버에 연결하지 못했어요.', 'NETWORK')
    } finally {
      clearTimeout(timer)
    }
  }
}

export const courseApiService = new CourseApiClient()
