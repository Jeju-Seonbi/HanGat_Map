<script setup lang="ts">
import { computed } from 'vue'
import type { CourseCostSummary } from '../../assets/types/course'
import { calculateBudgetOverrun } from '../../services/budgetUtils'

const props = defineProps<{ budget?: number; min?: number; max?: number; summary?: CourseCostSummary }>()
const ratio = computed(() => props.budget && props.max ? Math.min(100, Math.round(props.max / props.budget * 100)) : 0)
const over = computed(() => calculateBudgetOverrun(props.budget, props.max))
const won = (value?: number) => value == null ? '정보 없음' : `${value.toLocaleString()}원`
</script>

<template>
  <section class="budget">
    <span class="budget-kicker">예상 경비</span>
    <h3>{{ won(min) }} ~ {{ won(max) }}</h3>
    <div class="gauge"><i :class="{ over }" :style="{ width: `${ratio}%` }" /></div>
    <div class="budget-row"><span>전체 예산</span><b>{{ won(budget) }}</b></div>
    <dl v-if="summary" class="budget-details">
      <div><dt>검증 비용</dt><dd>{{ won(summary.verified_amount) }}</dd></div>
      <div><dt>추정 비용</dt><dd>{{ won(summary.estimated_min) }} ~ {{ won(summary.estimated_max) }}</dd></div>
      <div><dt>가격 미상</dt><dd>{{ summary.unknown_count }}개 항목</dd></div>
    </dl>
    <p v-if="over" class="course-error">예산을 최대 약 {{ over.toLocaleString() }}원 초과할 수 있어요.</p>
  </section>
</template>

<style scoped>
.budget-details{display:grid;gap:6px;margin:12px 0 0;padding-top:11px;border-top:1px solid var(--course-line)}.budget-details div{display:flex;justify-content:space-between;gap:10px;font-size:.68rem}.budget-details dt{color:var(--course-text-3)}.budget-details dd{margin:0;color:var(--course-text-2);font-weight:700;text-align:right}
</style>
