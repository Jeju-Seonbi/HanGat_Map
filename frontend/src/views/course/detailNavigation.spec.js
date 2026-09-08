import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { places } from '../../data/data'
import { sampleCourses } from '../../data/courses'
import { resolveCourseDetail } from './courseDetailModel'

const courseDetailSource = readFileSync(new URL('./CourseDetailView.vue', import.meta.url), 'utf8')

describe('course and place detail navigation', () => {
  it('resolves every stop for the selected course id and rejects unknown ids', () => {
    const seongsan = resolveCourseDetail('sample-seongsan', sampleCourses, places)
    expect(seongsan?.days.map(day => day.places.map(place => place.id))).toEqual([
      ['honinji', 'pyeongdaedang'],
      ['bijarim', 'darangshi'],
    ])
    expect(resolveCourseDetail('demo-course', sampleCourses, places)?.places).toHaveLength(8)
    expect(resolveCourseDetail('no-such-course', sampleCourses, places)).toBeNull()
  })

  it('links a stop only when there is a real place detail to open', () => {
    // 장소 상세 페이지는 백엔드 placeId로만 연다. 실데이터 정류지는 그 id가 있어 링크를 걸고,
    // 목업 정류지는 목업 문자열 id뿐이라 걸지 않는다.
    expect(courseDetailSource).toContain(':to="stop.detailPath"')
    expect(courseDetailSource).toContain('v-if="stop.detailPath"')
    expect(courseDetailSource).toContain('detailPath: item.placeId != null ? `/places/${item.placeId}` : null')
    expect(courseDetailSource).toContain('detailPath: null')
    expect(courseDetailSource).toContain('class="place-detail-link"')
  })
})
