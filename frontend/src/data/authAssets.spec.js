import { existsSync, readFileSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { AUTH_HERO_IMAGES } from './authHeroImages.js'

const publicPath = relativePath => fileURLToPath(
  new URL(`../../public/${relativePath}`, import.meta.url)
)

describe('인증 화면 정적 이미지', () => {
  it('로그인 슬라이드는 WebP 네 장이며 전체 용량이 2.5MB 이하이다', () => {
    expect(AUTH_HERO_IMAGES).toHaveLength(4)

    const totalBytes = AUTH_HERO_IMAGES.reduce((total, source) => {
      expect(source).toMatch(/^\/login-slides\/.+\.webp$/)
      const imagePath = publicPath(source.slice(1))
      expect(existsSync(imagePath)).toBe(true)
      return total + statSync(imagePath).size
    }, 0)

    expect(totalBytes).toBeLessThanOrEqual(2_500_000)
  })

  it('사이트 아이콘은 ICO와 터치 아이콘을 제공한다', () => {
    const icoPath = publicPath('favicon.ico')
    const pngPath = publicPath('favicon-32x32.png')
    const touchPath = publicPath('apple-touch-icon.png')

    expect(existsSync(icoPath)).toBe(true)
    expect(existsSync(pngPath)).toBe(true)
    expect(existsSync(touchPath)).toBe(true)

    const icoHeader = readFileSync(icoPath).subarray(0, 4)
    expect([...icoHeader]).toEqual([0, 0, 1, 0])

    const indexHtml = readFileSync(
      fileURLToPath(new URL('../../index.html', import.meta.url)),
      'utf8'
    )
    expect(indexHtml).toContain('href="/favicon.ico"')
    expect(indexHtml).toContain('href="/apple-touch-icon.png"')
  })
})
