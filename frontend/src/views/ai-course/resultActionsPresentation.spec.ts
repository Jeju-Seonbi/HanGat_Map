import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync(new URL('./AiCourseView.vue', import.meta.url), 'utf8')
const sharedStyles = readFileSync(new URL('../../assets/styles.css', import.meta.url), 'utf8')
const tokens = readFileSync(new URL('../../assets/styles/tokens.css', import.meta.url), 'utf8')

describe('AI course result actions', () => {
  it('keeps equal responsive action buttons and their existing click behavior', () => {
    expect(source).toContain('class="btn result-map" @click="viewOnMap"')
    expect(source).toContain('class="btn result-save" :disabled="result.status === \'SAVED\' || !canModify" @click="openSave"')
    expect(source).toContain('grid-template-columns: repeat(auto-fit, minmax(140px, 1fr))')
    expect(source).toContain('min-height: 44px')
  })

  it('centers the whole action group vertically only on desktop', () => {
    expect(source).toMatch(/@media \(min-width: 768px\)\s*\{\s*\.result-actions-block\s*\{\s*align-self: center;\s*\}\s*\}/)
    const mobileStyles = source.slice(source.indexOf('@media (max-width: 767px)'))
    expect(mobileStyles).not.toContain('align-self: center')
  })

  it('shows the compact expiry notice beside the top save controls only for READY courses', () => {
    expect(source).toContain('v-if="result.status === \'READY\'" class="temporary-course-notice">미저장 코스는 생성 2시간 후 만료돼요.')
    const bottomActions = source.match(/<div class="result-actions">[\s\S]*?<\/div>/)?.[0]
    expect(bottomActions).not.toContain('temporary-course-notice')
  })

  it.each([
    [320, 1],
    [375, 2],
    [390, 2],
    [430, 2],
  ])('fits the responsive actions at %ipx without horizontal overflow', (viewport, columns) => {
    const availableActionWidth = viewport - 64
    const minimumTwoColumnWidth = (140 * 2) + 10
    expect(availableActionWidth >= minimumTwoColumnWidth ? 2 : 1).toBe(columns)
  })

  it('retains the existing content and safe-area clearance above the mobile navigation', () => {
    expect(sharedStyles).toContain('.course-shell{padding-bottom:72px}')
    expect(tokens).toContain('--mobile-tabbar-h:calc(56px + env(safe-area-inset-bottom, 0px))')
  })
})
