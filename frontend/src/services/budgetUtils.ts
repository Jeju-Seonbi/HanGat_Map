import type { CourseBudgetSummary } from '../assets/types/course'

export function calculateBudgetOverrun(budget?: number, estimatedMax?: number) {
  if (budget == null || estimatedMax == null || budget <= 0) return 0
  return Math.max(0, estimatedMax - budget)
}

export function toBudgetGaugeState(summary?: CourseBudgetSummary) {
  const hasCostData = summary?.has_cost_data === true
  return {
    hasCostData,
    ratio: hasCostData && summary?.usage_rate != null
      ? Math.min(100, Math.max(0, Math.round(summary.usage_rate)))
      : 0,
    overBudget: hasCostData && summary?.over_budget === true,
    expectedMin: hasCostData ? summary?.total_expected_min : undefined,
    expectedMax: hasCostData ? summary?.total_expected_max : undefined,
  }
}
