import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const placeSource = readFileSync(new URL('./PlaceDetailView.vue', import.meta.url), 'utf8')
const placeCss = readFileSync(new URL('../../assets/place-detail.css', import.meta.url), 'utf8')

describe('place detail presentation', () => {
  it('uses the bright summary-card and decision-first information structure', () => {
    for (const className of [
      'place-breadcrumb',
      'place-hero-card',
      'place-glance',
      'place-content-grid',
      'place-forecast-strip',
      'place-facts-grid',
    ]) {
      expect(placeSource).toContain(`class="${className}`)
    }
  })

  it('keeps the Toss-style place layout responsive and theme-aware', () => {
    expect(placeCss).toContain('.place-hero-card')
    expect(placeCss).toContain('background:var(--surface)')
    expect(placeCss).toContain('color:var(--text)')
    expect(placeCss).toMatch(/@media\s*\(max-width:\s*767px\)/)
    expect(placeCss).not.toMatch(/\.place-hero-card\s*\{[^}]*background:\s*var\(--deep\)/s)
  })
})
