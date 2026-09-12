import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

import { MAP_LABEL_MAX_LEVEL, POI_GROUPS, POI_MARKER_CLASS, SPOT_ICON_GROUPS, shouldShowMapLabels, spotIconGroup, spotPinSpec } from './mapPresentation'

const mapCss = readFileSync(new URL('../../assets/styles/hangat.css', import.meta.url), 'utf8')
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

  it('업종 레이어 키는 마커 클래스 표와 같은 순서다', () => {
    expect(POI_GROUPS).toEqual(['food', 'dine', 'cafe', 'cvs', 'stay', 'mart'])
  })

  describe('spotPinSpec - 관광지 핀 모습(핀 재사용의 변경 감지 기준)', () => {
    it('선택·코스 정류지는 24px 최상단, 필터 안은 18px, 필터 밖은 9px 흐림 (2026-09-12 20/15 → 24/18)', () => {
      expect(spotPinSpec('busy', true, true)).toMatchObject({ cls: 'pn busy ic-mt pick', size: 24, z: 400 })
      expect(spotPinSpec('calm', false, true)).toMatchObject({ cls: 'pn calm ic-mt', size: 18, z: 200 })
      expect(spotPinSpec('mid', false, false)).toMatchObject({ cls: 'pn mid ic-mt dim', size: 9, z: 100 })
    })

    it('아이콘 묶음이 클래스에 들어가고 서명에도 반영된다', () => {
      expect(spotPinSpec('calm', false, true, 'sea').cls).toBe('pn calm ic-sea')
      expect(spotPinSpec('calm', false, true, 'sea').sig).not.toBe(spotPinSpec('calm', false, true, 'mt').sig)
    })

    it('혼잡 단계만 달라져도 서명이 달라진다 - 날짜 이동 때 색이 갱신되는 근거', () => {
      expect(spotPinSpec('calm', false, true).sig).not.toBe(spotPinSpec('busy', false, true).sig)
      expect(spotPinSpec('calm', false, true).sig).toBe(spotPinSpec('calm', false, true).sig)
    })
  })

  it('핀 래퍼는 크기가 고정이고 풀 핀은 레이어 안 절대배치, 숨김·이름표 숨김은 클래스로 - 핀 재사용 전제', () => {
    expect(mapCss).toMatch(/\.pw\{[^}]*width:20px;height:20px/)
    expect(mapCss).toContain('#map .pw .lb-t.off{display:none}')
    // 카페·편의점·숙소·마트는 이름표 없음(2026-09-12), 선택 핀 이름표는 예외
    expect(mapCss).toContain('#map .pw:has(.mk-cafe,.mk-cvs,.mk-stay,.mk-mart) .lb-t:not(.sel-on){display:none}')
    expect(mapCss).toMatch(/\.pl-layer\{[^}]*width:0;height:0/)
    expect(mapCss).toMatch(/\.pw\.pl\{position:absolute;margin:-10px 0 0 -10px/)
    expect(mapCss).toContain('.pw.hid{display:none}')
  })

  describe('spotIconGroup - 관광지 아이콘 7묶음(관광공사 분류 코드 앞자리)', () => {
    it('앞 4자리 우선, 없으면 앞 2자리, 모르면 산 - 미분류가 없다', () => {
      expect(spotIconGroup('NA010100')).toBe('mt')       // 산·오름
      expect(spotIconGroup('NA020700')).toBe('sea')      // 항구
      expect(spotIconGroup('NA040700')).toBe('park')     // 수목원
      expect(spotIconGroup('VE030100')).toBe('park')     // 시민공원
      expect(spotIconGroup('VE070100')).toBe('culture')  // 박물관
      expect(spotIconGroup('VE990000')).toBe('culture')  // 처음 보는 문화시설 하위
      expect(spotIconGroup('HS030100')).toBe('history')  // 불교
      expect(spotIconGroup('LS010700')).toBe('leisure')  // 승마
      expect(spotIconGroup('EX030300')).toBe('play')     // 체험농장
      expect(spotIconGroup('VE020100')).toBe('play')     // 테마파크
      expect(spotIconGroup('EV010200')).toBe('play')     // 축제
      expect(spotIconGroup('AC050100')).toBe('play')     // 야영장
      expect(spotIconGroup(null)).toBe('mt')
      expect(spotIconGroup('ZZ000000')).toBe('mt')
    })

    it('산을 뺀 6묶음은 CSS 에 마스크 아이콘 규칙이 있다(산은 .pn 기본값)', () => {
      expect(SPOT_ICON_GROUPS).toHaveLength(7)
      for (const g of SPOT_ICON_GROUPS.filter(g => g !== 'mt')) {
        expect(mapCss).toMatch(new RegExp(`\\.pn\\.ic-${g}\\{--ico:url\\("data:image/svg\\+xml,`))
      }
    })
  })

  it('핀 아이콘: 관광지(.pn)와 업종 마커 전부 마스크 아이콘이 있고, 흐린 9px 핀엔 없다', () => {
    expect(mapCss).toContain('.pn.dim::after{display:none}')
    for (const cls of ['pn', ...Object.values(POI_MARKER_CLASS)]) {
      expect(mapCss).toMatch(new RegExp(`\\.${cls}\\{--ico:url\\("data:image/svg\\+xml,`))
    }
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
