<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'

defineProps({
  title: { type: String, default: '' },
  labelledBy: { type: String, default: 'modal-title' }
})
const emit = defineEmits(['close'])
const box = ref(null)

function onKey (e) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => {
  document.addEventListener('keydown', onKey)
  document.body.style.overflow = 'hidden'
  box.value?.focus()
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="scrim" @click.self="emit('close')">
    <div ref="box" class="modal thin" role="dialog" aria-modal="true" :aria-labelledby="labelledBy" tabindex="-1">
      <h4 v-if="title" :id="labelledBy">{{ title }}</h4>
      <slot />
    </div>
  </div>
</template>
