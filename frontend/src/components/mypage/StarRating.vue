<script setup>
/**
 * 별점.
 * SVG path 와 색(#FFB020 / #cfd6dd)은 원본 index.html:824~826 그대로.
 * editable=true 면 1~5점 입력, false 면 표시 전용.
 */
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Number, default: 0 },
  size: { type: Number, default: 15 },
  editable: { type: Boolean, default: false },
  ariaLabel: { type: String, default: '별점' }
})
const emit = defineEmits(['update:modelValue'])

const PATH = 'M12 2.4l2.94 5.96 6.58.96-4.76 4.64 1.12 6.55L12 17.47l-5.88 3.04 1.12-6.55L2.48 9.32l6.58-.96z'
const stars = [1, 2, 3, 4, 5]
const value = computed(() => props.modelValue || 0)

function pick (n) {
  if (props.editable) emit('update:modelValue', n)
}
</script>

<template>
  <div
    class="stars"
    :role="props.editable ? 'radiogroup' : 'img'"
    :aria-label="props.editable ? props.ariaLabel : `${value}점`"
  >
    <component
      :is="props.editable ? 'button' : 'span'"
      v-for="n in stars"
      :key="n"
      class="s"
      :type="props.editable ? 'button' : undefined"
      :role="props.editable ? 'radio' : undefined"
      :aria-checked="props.editable ? String(n === value) : undefined"
      :aria-label="props.editable ? `${n}점` : undefined"
      @click="pick(n)"
    >
      <svg :width="props.size" :height="props.size" viewBox="0 0 24 24" aria-hidden="true">
        <path
          :d="PATH"
          :fill="n <= value ? '#FFB020' : 'none'"
          :stroke="n <= value ? '#FFB020' : '#cfd6dd'"
          stroke-width="1.6"
          stroke-linejoin="round"
        />
      </svg>
    </component>
  </div>
</template>

<style scoped>
.stars { display: inline-flex; gap: 2px; line-height: 0; }
.s { padding: 1px; line-height: 0; display: inline-flex; }
button.s { border-radius: 4px; }
</style>
