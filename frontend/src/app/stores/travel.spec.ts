import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { todayKst, addCalendarDays } from '../../utils/format.js'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetModules()
})

describe('메인 퀵스타트 기본 일정 (담당 정동현)', () => {
  it('오늘부터 2박 3일이다 - 고정 날짜를 두면 지나간 일정이 뜬다', async () => {
    const { useTravelStore } = await import('./travel')

    const { condition } = useTravelStore()

    expect(condition.startDate).toBe(todayKst())
    expect(condition.endDate).toBe(addCalendarDays(todayKst(), 2))
  })

  it('한국 날짜를 쓴다 - 브라우저 시계가 UTC여도 하루 밀리지 않는다', async () => {
    // 한국 2026-09-08 08:00 = UTC 2026-09-07 23:00
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-07T23:00:00Z'))
    try {
      const { useTravelStore } = await import('./travel')

      expect(useTravelStore().condition.startDate).toBe('2026-09-08')
    } finally {
      vi.useRealTimers()
    }
  })
})
