<script setup lang="ts">
import { computed } from 'vue'
import type { CourseBudgetSummary } from '../../assets/types/course'
import { toBudgetGaugeState } from '../../services/budgetUtils'

const props = defineProps<{ summary?: CourseBudgetSummary; budgetTotal?: number | null }>()
const state = computed(() => toBudgetGaugeState(props.summary))
const won = (value?: number) => value == null ? '정보 없음' : `${value.toLocaleString()}원`
const expected = computed(() => {
  const { expectedMin, expectedMax } = state.value
  if (expectedMin == null || expectedMax == null) return '정보 없음'
  return expectedMin === expectedMax
    ? won(expectedMax)
    : `${expectedMin.toLocaleString()} ~ ${expectedMax.toLocaleString()}원`
})
</script>

<template>
  <section class="budget">
    <span class="budget-kicker">예상 경비</span>
    <h3>{{ state.hasCostData ? expected : '비용 데이터 없음' }}</h3>
    <div v-if="state.hasCostData" class="gauge"><i :class="{ over: state.overBudget }" :style="{ width: `${state.ratio}%` }" /></div>
    <div class="budget-row"><span>전체 예산</span><b>{{ won(summary?.budget_total ?? budgetTotal ?? undefined) }}</b></div>
    <dl v-if="state.hasCostData && summary" class="budget-details">
      <div><dt>산정 가능한 예상 비용</dt><dd>{{ expected }}</dd></div>
      <div><dt>확인된 비용</dt><dd>{{ won(summary.verified_total) }}</dd></div>
      <div><dt>추정 비용</dt><dd>{{ won(summary.estimated_min) }} ~ {{ won(summary.estimated_max) }}</dd></div>
      <div><dt>남은 예산</dt><dd>{{ won(summary.remaining_budget) }}</dd></div>
      <div><dt>예산 사용률</dt><dd>{{ summary.usage_rate == null ? '정보 없음' : `${summary.usage_rate}%` }}</dd></div>
      <div v-if="summary.unknown_count > 0"><dt>가격 미상</dt><dd>{{ summary.unknown_count }}개 항목</dd></div>
    </dl>
    <p v-else class="budget-no-data">검증되거나 명시적 규칙으로 산정된 비용이 아직 없어요.</p>
    <p v-if="state.hasCostData" class="budget-no-data">대표메뉴는 1인 1메뉴 기준 추정입니다. 가격 미상 항목과 미산정 숙박·교통비는 합계·남은 예산에 반영되지 않아요.</p>
    <p v-if="state.overBudget && summary?.remaining_budget != null" class="course-error">예산을 최대 약 {{ Math.abs(summary.remaining_budget).toLocaleString() }}원 초과할 수 있어요.</p>
  </section>
</template>

<style scoped>
.budget-details{display:grid;gap:6px;margin:12px 0 0;padding-top:11px;border-top:1px solid var(--course-line)}.budget-details div{display:flex;justify-content:space-between;gap:10px;font-size:.68rem}.budget-details dt{color:var(--course-text-3)}.budget-details dd{margin:0;color:var(--course-text-2);font-weight:700;text-align:right}.budget-no-data{margin:12px 0 0;color:var(--course-text-3);font-size:.72rem;line-height:1.5}
</style>
