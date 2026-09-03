import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const readSource = relativePath => readFileSync(
  new URL(relativePath, import.meta.url),
  'utf8'
)

const appSource = readSource('../../App.vue')
const defaultLayoutSource = readSource('./DefaultLayout.vue')
const mapLayoutSource = readSource('./MapLayout.vue')
const authLayoutSource = readSource('../auth/AuthLayout.vue')
const routerSource = readSource('../../router/index.js')
const tokensSource = readSource('../../assets/styles/tokens.css')

describe('공통 앱 셸', () => {
  it('데모 푸터를 어떤 라우트에서도 표시하지 않는다', () => {
    expect(defaultLayoutSource).not.toContain('AppFooter')
    expect(routerSource).not.toContain('footer: true')
    expect(existsSync(new URL('../common/AppFooter.vue', import.meta.url))).toBe(false)
  })

  it('모바일 하단 메뉴를 라우트 레이아웃 밖에서 한 번만 렌더링한다', () => {
    expect(appSource).toContain("import MobileTabBar from './components/layout/MobileTabBar.vue'")
    expect(appSource.match(/<MobileTabBar\s*\/>/g)).toHaveLength(1)
    expect(defaultLayoutSource).not.toContain('MobileTabBar')
  })

  it('지도와 회원 화면이 모바일 하단 메뉴 영역을 침범하지 않는다', () => {
    expect(tokensSource).toContain('--mobile-tabbar-h:')
    expect(mapLayoutSource).toContain('100dvh')
    expect(mapLayoutSource).toContain('var(--mobile-tabbar-h)')
    expect(authLayoutSource).toContain('var(--mobile-tabbar-h)')
  })
})
