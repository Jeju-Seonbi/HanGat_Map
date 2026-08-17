<script setup>
/** 정렬 세그먼트 — 원본 .seg (index.html:109~111) */
defineProps({
  modelValue: { type: String, required: true },
  options: { type: Array, required: true }, // [{key,label}]
  label: { type: String, default: '정렬 기준' }
})
defineEmits(['update:modelValue'])
</script>

<template>
  <div class="seg" role="group" :aria-label="label">
    <button
      v-for="o in options"
      :key="o.key"
      type="button"
      :class="{ on: o.key === modelValue }"
      :aria-pressed="String(o.key === modelValue)"
      @click="$emit('update:modelValue', o.key)"
    >
      {{ o.label }}
    </button>
  </div>
</template>
