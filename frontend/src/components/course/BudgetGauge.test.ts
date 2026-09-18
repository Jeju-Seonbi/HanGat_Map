import { describe, expect, it } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import BudgetGauge from './BudgetGauge.vue'

describe('budget rendering', () => {
  it('shows input budget even when there is no cost summary', async () => {
    const html = await renderToString(createSSRApp(BudgetGauge, { budgetTotal: 400000 }))
    expect(html).toContain('400,000원')
    expect(html).toContain('비용 데이터 없음')
  })
})
