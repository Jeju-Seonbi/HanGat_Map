<script setup>
defineProps({ filled: Boolean, size: { type: Number, default: 14 } })
const PATH = 'M12 2.4l2.94 5.96 6.58.96-4.76 4.64 1.12 6.55L12 17.47l-5.88 3.04 1.12-6.55L2.48 9.32l6.58-.96z'
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <path :d="PATH" :fill="filled ? '#FFB020' : 'none'" :stroke="filled ? '#FFB020' : '#cfd6dd'"
      stroke-width="1.6" stroke-linejoin="round" />
  </svg>
</template>
