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
    // 목업 전용 장소 페이지(/places/:id)를 걷어냈다. 지금 열 수 있는 상세는 지도 패널뿐이고
    // 목업 정류지는 백엔드 id가 없어 그마저도 못 연다 - 그래서 양쪽 다 detailPath가 null이다.
    expect(courseDetailSource).toContain(':to="stop.detailPath"')
    expect(courseDetailSource).toContain('v-if="stop.detailPath"')
    expect(courseDetailSource).not.toContain('/places/')
    expect(courseDetailSource).toContain('class="place-detail-link"')
  })
})
