import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

import { MAP_LABEL_MAX_LEVEL, POI_MARKER_CLASS, shouldShowMapLabels } from './mapPresentation'

const mapCss = readFileSync(new URL('../../assets/hangat.css', import.meta.url), 'utf8')
const sharedCss = readFileSync(new URL('../../assets/styles.css', import.meta.url), 'utf8')
const filterPanelSource = readFileSync(new URL('./FilterPanel.vue', import.meta.url), 'utf8')
const kakaoMapSource = readFileSync(new URL('./KakaoMap.vue', import.meta.url), 'utf8')

const mobileCss = mapCss.slice(mapCss.lastIndexOf('@media(max-width:768px)'))

function declarations(selector: string) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return mobileCss.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))?.[1] ?? ''
}

describe('mapPresentation', () => {
  it('상세 지도 수준에서만 장소 이름을 표시한다', () => {
    expect(MAP_LABEL_MAX_LEVEL).toBe(7)
    expect(shouldShowMapLabels(7)).toBe(true)
    expect(shouldShowMapLabels(8)).toBe(false)
  })

  it('각 POI 카테고리에 고유한 마커 클래스를 제공한다', () => {
    expect(POI_MARKER_CLASS).toEqual({
      food: 'mk-food',
      dine: 'mk-dine',
      cafe: 'mk-cafe',
      cvs: 'mk-cvs',
      stay: 'mk-stay',
      mart: 'mk-mart',
    })
  })

  it('축소 상태를 우회해 이름을 표시하는 hover 규칙이 없다', () => {
    expect(mapCss).not.toContain('.pw:hover .poi-label')
  })

  it('모바일 지도에서 캘린더는 위에 있고 검색 필터는 원형 버튼에서 하단 시트로 열린다', () => {
    expect(declarations('.slid')).toContain('top:12px')
    expect(declarations('.slid')).toContain('bottom:auto')
    expect(declarations('.cond')).toContain('top:auto')
    expect(declarations('.cond')).toContain('bottom:0')
    expect(declarations('.cond')).toContain('translateY(')
    expect(declarations('.cond.mobile-open')).toContain('translateY(0)')
    expect(declarations('.filter-fab')).toContain('display:flex')
    expect(mobileCss).not.toContain('.stage.sheet-open .slid')
  })

  it('모바일 검색 버튼과 바텀시트 닫기 버튼이 접근 가능한 상태를 알린다', () => {
    expect(filterPanelSource).toContain('class="filter-fab"')
    expect(filterPanelSource).toContain(':aria-expanded="mobileOpen"')
    expect(filterPanelSource).toContain('aria-controls="mobile-map-filter-sheet"')
    expect(filterPanelSource).toContain('id="mobile-map-filter-sheet"')
    expect(filterPanelSource).toContain('class="mobile-filter-close"')
  })

  it('선택한 찜 장소를 이름 말풍선과 강조 아이콘으로 지도 위에 표시한다', () => {
    expect(kakaoMapSource).toContain("'kakao-selected-place'")
    expect(kakaoMapSource).toContain("'kakao-selected-place__label'")
    expect(kakaoMapSource).toContain("'kakao-selected-place__pin'")
    expect(kakaoMapSource).toContain('map.panTo(selectedPosition)')
    expect(sharedCss).toContain('.kakao-selected-place__label')
    expect(sharedCss).toContain('.kakao-selected-place__pin')
    expect(sharedCss).toMatch(/\.kakao-selected-place__pin\{[^}]*display:block/)
  })
})
