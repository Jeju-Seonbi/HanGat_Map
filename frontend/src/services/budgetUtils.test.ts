import { describe, expect, it } from 'vitest'
import { toBudgetGaugeState } from './budgetUtils'

describe('Backend budget summary presentation', () => {
  it('keeps no-cost-data distinct from a confirmed zero-won course', () => {
    const state = toBudgetGaugeState({
      has_cost_data: false,
      budget_total: 400000,
      verified_total: 0,
      unknown_count: 0,
    })

    expect(state).toEqual({
      hasCostData: false,
      ratio: 0,
      overBudget: false,
      expectedMin: undefined,
      expectedMax: undefined,
    })
  })

  it('uses Backend totals, usage rate and over-budget decision without recalculation', () => {
    const state = toBudgetGaugeState({
      has_cost_data: true,
      budget_total: 100000,
      verified_total: 16000,
      estimated_total: 120000,
      estimated_min: 80000,
      estimated_max: 120000,
      total_expected: 136000,
      total_expected_min: 96000,
      total_expected_max: 136000,
      remaining_budget: -36000,
      usage_rate: 136,
      over_budget: true,
      unknown_count: 0,
    })

    expect(state).toEqual({
      hasCostData: true,
      ratio: 100,
      overBudget: true,
      expectedMin: 96000,
      expectedMax: 136000,
    })
  })
})
