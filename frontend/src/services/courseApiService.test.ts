import { describe, expect, it, vi } from 'vitest'
import {
  courseConditionFixture,
  courseGenerationResponseFixture,
} from '../test/courseGenerationFixtures'
import {
  CourseApiClient,
  CourseApiError,
  isCourseGenerationResponse,
  toCourseRequestPayload,
} from './courseApiService'

describe('CourseApiClient', () => {
  it('posts only the backend CourseRequestDto snake_case contract', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify(courseGenerationResponseFixture()), {
      status: 200,
      headers: { 'Content-Type': 'application/json; charset=UTF-8' },
    })) as unknown as typeof fetch
    const client = new CourseApiClient(fetcher)

    const result = await client.createCourse(courseConditionFixture())

    expect(result.days[0].items[0].place_name).toBe('성산일출봉')
    expect(fetcher).toHaveBeenCalledTimes(1)
    const [url, init] = (fetcher as unknown as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(url).toBe('/courses')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual(toCourseRequestPayload(courseConditionFixture()))
    expect(JSON.parse(init.body as string)).not.toHaveProperty('id')
    expect(JSON.parse(init.body as string)).not.toHaveProperty('mock')
  })

  it('accepts null weather and missing congestion facts without inventing values', () => {
    const body = courseGenerationResponseFixture()
    expect(isCourseGenerationResponse(body)).toBe(true)
    expect(body.days[0].items[0].weather).toBeNull()
    expect(body.days[0].items[0].congestion).toEqual([])
  })

  it('maps server, network, timeout, and malformed responses safely', async () => {
    const serverClient = new CourseApiClient(
      vi.fn(async () => new Response('upstream secret detail', { status: 503 })) as unknown as typeof fetch)
    await expect(serverClient.createCourse(courseConditionFixture())).rejects.toMatchObject({
      failure: 'HTTP', status: 503,
    })

    const networkClient = new CourseApiClient(
      vi.fn(async () => { throw new TypeError('connection details') }) as unknown as typeof fetch)
    await expect(networkClient.createCourse(courseConditionFixture())).rejects.toMatchObject({ failure: 'NETWORK' })

    const timeoutClient = new CourseApiClient(((input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    })) as typeof fetch)
    await expect(timeoutClient.createCourse(courseConditionFixture(), 1)).rejects.toMatchObject({ failure: 'TIMEOUT' })

    const invalidClient = new CourseApiClient(
      vi.fn(async () => new Response('{', { status: 200 })) as unknown as typeof fetch)
    const invalidResponse = invalidClient.createCourse(courseConditionFixture())
    await expect(invalidResponse).rejects.toBeInstanceOf(CourseApiError)
    await expect(invalidResponse).rejects.toMatchObject({
      failure: 'INVALID_RESPONSE',
    })
  })
})
