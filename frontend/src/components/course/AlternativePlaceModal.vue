<script setup lang="ts">
import { computed } from 'vue'
import type { AlternativePlace, CourseItem } from '../../assets/types/course'

const props = defineProps<{ item: CourseItem; alternatives: AlternativePlace[]; loading: boolean }>()
defineEmits<{ close: []; select: [AlternativePlace] }>()
const crowded = computed(() => props.item.congestion_level === 'CROWDED')
</script>

<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <section class="course-modal">
      <button class="modal-close" @click="$emit('close')">×</button>
      <span class="eyebrow">장소 대안</span>
      <h2>{{ crowded ? `${item.place_name} 대신 한산한 장소` : `${item.place_name} 대신 다른 장소` }}</h2>
      <p class="muted">{{ crowded ? '같은 세부 카테고리와 예상 혼잡도, 이동 거리를 기준으로 찾았어요.' : '유사 카테고리와 여행 취향, 기존 동선을 함께 고려했어요.' }}</p>
      <p v-if="loading">{{ crowded ? '가까운 한산한 장소를 찾고 있어요…' : '일정에 어울리는 다른 장소를 찾고 있어요…' }}</p>
      <div v-else class="alt-list">
        <p v-if="crowded && alternatives.some(alt => alt.radius_km === 20)" class="course-notice">10km 후보를 먼저 표시하고, 부족한 경우 20km 안의 조금 더 먼 대안을 함께 보여드려요.</p>
        <article v-for="alt in alternatives" :key="alt.place_id">
          <div>
            <span v-if="crowded && alt.radius_km === 20" class="eyebrow">조금 더 먼 대안</span>
            <h3>{{ alt.place_name }}</h3>
            <p>{{ alt.category_name }} · {{ (alt.distance_m / 1000).toFixed(1) }}km · {{ alt.congestion_level === 'QUIET' ? '한산' : alt.congestion_level === 'CROWDED' ? '혼잡' : '보통' }}</p>
            <small>{{ alt.recommendation_reason }}</small>
          </div>
          <button class="btn primary select-alternative" @click="$emit('select', alt)">이곳으로 변경</button>
        </article>
        <p v-if="!alternatives.length">{{ crowded ? '가까운 한산한 대안을 찾지 못했어요.' : '조건에 맞는 다른 장소를 찾지 못했어요.' }}</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.select-alternative{white-space:nowrap}
</style>
