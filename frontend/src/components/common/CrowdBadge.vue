<script setup>
/**
 * 혼잡 배지.
 * 색 조합은 원본 규칙 그대로: background:var(--{tier}-bg) / color:var(--{tier})
 * 값이 없으면 '한산'으로 취급하지 않고 '정보 없음'을 낸다 (요구사항 MAP_004 예외 조항).
 */
import { computed } from 'vue'
import { tier, tierKo } from '../../utils/crowd.js'

const props = defineProps({
  value: { type: Number, default: null },
  showValue: { type: Boolean, default: false }
})

const t = computed(() => tier(props.value))
const label = computed(() => tierKo(props.value))
</script>

<template>
  <span
    class="bdg"
    :class="t"
    :title="props.value != null ? `집중률 예보 ${props.value}` : '집중률 데이터가 없어요'"
  >
    {{ label }}<span v-if="props.showValue && props.value != null" class="tnum"> {{ props.value }}</span>
  </span>
</template>

<style scoped>
.bdg.calm { background: var(--calm-bg); color: var(--calm); }
.bdg.mid  { background: var(--mid-bg);  color: var(--mid); }
.bdg.busy { background: var(--busy-bg); color: var(--busy); }
.bdg.none { background: var(--surf2);   color: var(--tx3); }
</style>
