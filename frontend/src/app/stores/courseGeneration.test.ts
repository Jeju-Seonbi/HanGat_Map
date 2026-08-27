import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CourseApiError } from '../../services/courseApiService'
import {
  courseConditionFixture,
  courseGenerationResponseFixture,
} from '../../test/courseGenerationFixtures'
import { useCourseGenerationStore } from './courseGeneration'

describe('courseGeneration store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('tracks loading, stores the response, and blocks duplicate submissions', async () => {
    let release!: () => void
    const pending = new Promise<void>(resolve => { release = resolve })
    const generator = {
      createCourse: vi.fn(async () => {
        await pending
        return courseGenerationResponseFixture()
      }),
    }
    const store = useCourseGenerationStore()

    const first = store.generate(courseConditionFixture(), generator)
    const duplicate = store.generate(courseConditionFixture(), generator)
    expect(store.loading).toBe(true)
    expect(generator.createCourse).toHaveBeenCalledTimes(1)
    expect(await duplicate).toBeUndefined()

    release()
    await first
    expect(store.loading).toBe(false)
    expect(store.error).toBe('')
    expect(store.result?.days[0].items[0].place_name).toBe('성산일출봉')
  })

  it('exposes a safe user message and always clears loading after failure', async () => {
    const store = useCourseGenerationStore()
    const generator = {
      createCourse: vi.fn(async () => {
        throw new CourseApiError('internal provider text', 'HTTP', 503)
      }),
    }

    await store.generate(courseConditionFixture(), generator)

    expect(store.loading).toBe(false)
    expect(store.result).toBeUndefined()
    expect(store.error).toBe('AI 코스를 생성하지 못했어요. 잠시 후 다시 시도해 주세요.')
    expect(store.error).not.toContain('provider')
  })
})
