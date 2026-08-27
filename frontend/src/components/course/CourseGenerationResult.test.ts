import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { describe, expect, it } from 'vitest'
import {
  courseConditionFixture,
  courseGenerationResponseFixture,
} from '../../test/courseGenerationFixtures'
import CourseGenerationResult from './CourseGenerationResult.vue'

describe('CourseGenerationResult', () => {
  it('renders days, Korean facts and reason while keeping missing weather/congestion explicit', async () => {
    const html = await renderToString(createSSRApp(CourseGenerationResult, {
      result: courseGenerationResponseFixture(),
      condition: courseConditionFixture(),
      loading: false,
    }))

    expect(html).toContain('DAY 1')
    expect(html).toContain('성산일출봉')
    expect(html).toContain('09:00')
    expect(html).toContain('사용자가 고정한 일정과 동부 동선을 함께 고려했어요.')
    expect(html).toContain('혼잡 정보 없음')
    expect(html).not.toContain('기온 정보 없음')
  })
})
