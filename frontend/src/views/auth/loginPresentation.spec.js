import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const loginSource = readFileSync(new URL('./LoginView.vue', import.meta.url), 'utf8')

describe('login presentation', () => {
  it('keeps remembered-email behavior without rendering recent-login history', () => {
    expect(loginSource).toContain('readRecentLogins()')
    expect(loginSource).not.toContain('removeRecentLogin')
    expect(loginSource).not.toContain('fmtRelative')
    expect(loginSource).not.toContain('class="recent"')
    expect(loginSource).not.toContain('>최근 로그인<')
    expect(loginSource).not.toContain('최근 로그인')
    expect(loginSource).not.toContain('readLastProvider')
    expect(loginSource).not.toContain('lastProvider')
  })
})
