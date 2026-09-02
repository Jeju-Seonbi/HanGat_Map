<script setup>
/** 날씨 배지 — 아이콘은 원본 wxIcon()(index.html:512~524)을 그대로 쓴다 */
import { computed } from 'vue'
import { weatherIconSvg } from '../../utils/crowd.js'

const props = defineProps({
  kind: { type: String, required: true },
  t: { type: Number, default: null },
  tmin: { type: Number, default: null },
  size: { type: Number, default: 17 }
})

const icon = computed(() => weatherIconSvg(props.kind, props.size))
</script>

<template>
  <span class="wx">
    <span class="ic" v-html="icon" />
    <b>{{ props.kind }}</b>
    <span v-if="props.t != null" class="tnum tm">
      {{ props.t }}°<template v-if="props.tmin != null"> / {{ props.tmin }}°</template>
    </span>
  </span>
</template>

<style scoped>
/* 원본 .wx (index.html:53~55) */
.wx {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 5px 12px; border-radius: var(--rp);
  background: var(--sky); font-size: 12px; color: var(--tx2); white-space: nowrap;
}
.wx b { color: var(--tx); font-weight: 700; }
.ic { line-height: 0; display: inline-flex; }
.tm { color: var(--tx3); }
</style>
