import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const headerSource = readFileSync(new URL('./AppHeader.vue', import.meta.url), 'utf8')

describe('app header mobile presentation', () => {
  it('keeps the phone-shaped desktop preview action from the shared header', () => {
    expect(headerSource).toContain('openMobilePreview')
    expect(headerSource).toContain('class="mobile-preview-button"')
    expect(headerSource).toContain('aria-label="모바일 화면으로 미리보기"')
    expect(headerSource).toContain('width=390,height=844')
  })

  it('keeps an accessible mobile menu trigger and navigation panel in the top header', () => {
    expect(headerSource).toContain('mobileMenuOpen')
    expect(headerSource).toContain('class="mobile-menu-button"')
    expect(headerSource).toContain(':aria-expanded="mobileMenuOpen"')
    expect(headerSource).toContain('aria-controls="mobile-header-menu"')
    expect(headerSource).toContain('id="mobile-header-menu"')
    expect(headerSource).toContain('class="mobile-header-menu"')
    expect(headerSource).toMatch(/@media \(max-width: 768px\)[\s\S]*\.mobile-menu-button \{ display: inline-flex; \}/)
  })
})
