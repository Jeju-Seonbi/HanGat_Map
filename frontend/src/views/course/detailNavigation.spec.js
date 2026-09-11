import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const courseDetailSource = readFileSync(new URL('./CourseDetailView.vue', import.meta.url), 'utf8')

describe('course detail navigation', () => {
  it('links a stop to the place detail page only when it has a backend placeId', () => {
    expect(courseDetailSource).toContain(':to="stop.detailPath"')
    expect(courseDetailSource).toContain('v-if="stop.detailPath"')
    expect(courseDetailSource).toContain('detailPath: item.placeId != null ? `/places/${item.placeId}` : null')
    expect(courseDetailSource).toContain('class="place-detail-link"')
  })

  it('renders backend courses only - the mock course path is gone', () => {
    // 문자열 id('sample-aewol')로 가짜 코스를 그리던 경로를 걷어냈다. 되살아나면 실패시킨다
    expect(courseDetailSource).not.toContain('sampleCourses')
    expect(courseDetailSource).not.toContain('resolveCourseDetail')
  })
})
