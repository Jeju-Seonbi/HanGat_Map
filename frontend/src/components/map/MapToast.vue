<script setup>
import { state } from '@/stores/mapStore'
</script>

<template>
  <div class="toast" :class="{ on: !!state.toast }">{{ state.toast }}</div>
</template>
