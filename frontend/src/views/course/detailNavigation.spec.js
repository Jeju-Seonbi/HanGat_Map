import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { places } from '../../data/data'
import { sampleCourses } from '../../data/courses'
import { resolveCourseDetail } from './courseDetailModel'

const courseDetailSource = readFileSync(new URL('./CourseDetailView.vue', import.meta.url), 'utf8')
const placeDetailCss = readFileSync(new URL('../../assets/place-detail.css', import.meta.url), 'utf8')

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

  it('provides a place detail link for every saved-course stop', () => {
    // 실데이터 연동으로 링크 경로가 뷰모델(stop.detailPath)로 옮겨졌다.
    // 목업 코스는 여전히 /places/:id 를 채우고, 실데이터 일정은 장소 상세가 목업 id
    // 라우팅이라 null 을 넣어 링크를 걸지 않는다 - 두 규칙을 함께 못 박는다.
    expect(courseDetailSource).toContain(':to="stop.detailPath"')
    expect(courseDetailSource).toContain('v-if="stop.detailPath"')
    expect(courseDetailSource).toContain('detailPath: `/places/${place.id}`')
    expect(courseDetailSource).toContain('class="place-detail-link"')
  })

  it('keeps secondary place actions readable on light and dark token surfaces', () => {
    expect(placeDetailCss).toMatch(/\.place-page \.btn\.place-secondary\s*\{[^}]*background:var\(--muted\)[^}]*color:var\(--text\)/s)
    expect(placeDetailCss).toMatch(/\.place-page \.btn\.place-secondary\s*\{[^}]*border:/s)
  })
})
