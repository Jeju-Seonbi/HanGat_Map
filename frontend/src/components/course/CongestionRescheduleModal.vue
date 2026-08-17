<script setup lang="ts">
import type { CongestionRescheduleOption, CourseItem } from '../../assets/types/course'

defineProps<{ item: CourseItem; options: CongestionRescheduleOption[]; loading: boolean }>()
defineEmits<{ close: []; select: [CongestionRescheduleOption] }>()
const dateLabel = (value: string) => new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'short', timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`))
const timeLabel = (value: string) => {
  const hour = Number(value.slice(0, 2))
  return `${hour < 12 ? '오전' : '오후'} ${hour % 12 || 12}:${value.slice(3)}`
}
</script>

<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <section class="course-modal">
      <button class="modal-close" @click="$emit('close')">×</button>
      <span class="eyebrow">더 한산한 시간</span>
      <h2>{{ item.place_name }} 방문 시간 변경</h2>
      <p class="muted">장소는 유지하고 여행 기간 안에서 현재보다 한산한 시간만 보여드려요.</p>
      <p v-if="loading">일정 충돌과 예상 혼잡도를 확인하고 있어요…</p>
      <div v-else class="alt-list">
        <article v-for="option in options" :key="`${option.visit_date}:${option.start_time}`">
          <div><h3>{{ dateLabel(option.visit_date) }} · {{ timeLabel(option.start_time) }}</h3><p>{{ option.start_time }} ~ {{ option.end_time }} · {{ option.congestion_level === 'QUIET' ? '한산' : option.congestion_level === 'CROWDED' ? '혼잡' : '보통' }}</p><small>현재보다 한산한 시간대로 예상돼요.</small></div>
          <button class="btn primary reschedule-select" @click="$emit('select', option)">이 시간으로 변경</button>
        </article>
        <p v-if="!options.length">여행 기간 안에서 충돌 없는 더 한산한 시간을 찾지 못했어요.</p>
      </div>
    </section>
  </div>
</template>

<style scoped>.reschedule-select{white-space:nowrap}</style>
