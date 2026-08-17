export function calculateBudgetOverrun(budget?: number, estimatedMax?: number) {
  if (budget == null || estimatedMax == null || budget <= 0) return 0
  return Math.max(0, estimatedMax - budget)
}
