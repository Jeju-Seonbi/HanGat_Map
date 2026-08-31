import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// backendClient(마이페이지팀 JS)는 목으로 바꾼다 - 위임이 맞게 가는지만 본다
vi.mock('../../api/backendClient.js', () => ({
  apiRequest: vi.fn(),
  getBackendAccessToken: vi.fn(() => 'test-token'),
  getBackendUserId: vi.fn(() => 7),
  reissueAccessToken: vi.fn(),
  BACKEND_BASE_URL: 'http://localhost:8080'
}))

import ReviewApiService from './ReviewApiService'
import { apiRequest, reissueAccessToken } from '../../api/backendClient.js'

/** 값은 2026-08-31 실응답에서 가져왔다 */
const REAL_PAGE = {
  content: [{
    id: 1, userId: 1, rating: 5, congestionReport: 'QUIET',
    content: 'good place', imageUrls: [], createdAt: '2026-08-31T01:37:06.580069'
  }],
  number: 0, size: 6, totalPages: 1, totalElements: 1
}

function mockFetch (handler: (url: string, init?: RequestInit) => unknown) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => ({
    ok: true,
    status: 200,
    json: async () => handler(url, init)
  })))
}

beforeEach(() => vi.clearAllMocks())
afterEach(() => vi.unstubAllGlobals())

describe('후기 목록', () => {
  it('공개 API 로 조회한다 - 토큰 없이', async () => {
    mockFetch(() => ({ success: true, code: 2000, message: '', result: REAL_PAGE }))

    const page = await ReviewApiService.getReviews(24)

    expect(page.content[0].rating).toBe(5)
    expect(page.totalElements).toBe(1)
    const call = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(call[0]).toContain('/places/24/reviews?page=0&size=6')
  })
})

describe('작성·삭제 - 인증 클라이언트 위임', () => {
  it('작성은 auth 요청으로 나간다', async () => {
    await ReviewApiService.create(24, { rating: 4, content: '한줄' })

    expect(apiRequest).toHaveBeenCalledWith('/places/24/reviews',
      expect.objectContaining({ method: 'POST', auth: true }))
  })

  it('삭제도 auth 요청이다', async () => {
    await ReviewApiService.remove(1)

    expect(apiRequest).toHaveBeenCalledWith('/reviews/1',
      expect.objectContaining({ method: 'DELETE', auth: true }))
  })
})

describe('사진 업로드', () => {
  const file = new File([new Uint8Array([1, 2])], 'a.jpg', { type: 'image/jpeg' })

  it('multipart 로 올리고 URL 배열을 받는다', async () => {
    mockFetch(() => ({ success: true, result: ['/uploads/reviews/x.jpg'] }))

    const urls = await ReviewApiService.uploadPhotos([file])

    expect(urls).toEqual(['/uploads/reviews/x.jpg'])
    const init = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as RequestInit
    expect(init.body).toBeInstanceOf(FormData)
    expect((init.headers as Record<string, string>).Authorization).toContain('Bearer')
  })

  it('401 이면 재발급 후 한 번만 재시도한다', async () => {
    let calls = 0
    vi.stubGlobal('fetch', vi.fn(async () => {
      calls++
      return calls === 1
        ? { ok: false, status: 401, json: async () => ({ success: false }) }
        : { ok: true, status: 200, json: async () => ({ success: true, result: ['/uploads/reviews/y.jpg'] }) }
    }))

    const urls = await ReviewApiService.uploadPhotos([file])

    expect(reissueAccessToken).toHaveBeenCalledTimes(1)
    expect(urls).toEqual(['/uploads/reviews/y.jpg'])
  })

  it('서버가 거부하면 메시지를 담아 던진다', async () => {
    mockFetch(() => ({ success: false, message: '후기 사진은 최대 5장까지입니다.' }))

    await expect(ReviewApiService.uploadPhotos([file]))
      .rejects.toThrow('최대 5장')
  })
})
