<script setup>
/* 업종 필터 아이콘 (MAP_001) */
defineProps({ name: { type: String, required: true } })

const PATHS = {
  spot: '<path d="M3.5 19h17L14 7.5l-3.3 5.4L8.4 10.5 3.5 19Z"/><circle cx="17.5" cy="5.5" r="1.7"/>',
  food: '<circle cx="12" cy="12" r="8.6"/><path d="M8 8.8l2.1 6.4L12 10.6l1.9 4.6L16 8.8M7.6 12.4h8.8"/>',
  dine: '<path d="M6.5 3v6a2.2 2.2 0 0 0 4.4 0V3M8.7 9.2V21"/><path d="M17 3c-1.5 0-2.6 1.7-2.6 3.7S15.5 10.4 17 10.4 19.6 8.7 19.6 6.7 18.5 3 17 3ZM17 10.4V21"/>',
  cafe: '<path d="M4.5 8.5h11.5v5.6a4.2 4.2 0 0 1-4.2 4.2H8.7a4.2 4.2 0 0 1-4.2-4.2V8.5Z"/><path d="M16 9.8h2.1a2.4 2.4 0 0 1 0 4.8H16"/><path d="M7.6 5.4V3.4M11 5.4V3.4M14.4 5.4V3.4"/><path d="M4 21h13"/>',
  cvs: '<path d="M4.5 9.8h15V20h-15z"/><path d="M3.2 9.8 5 4.2h14l1.8 5.6"/><path d="M9.2 20v-5.6h5.6V20"/>',
  stay: '<path d="M3 19.5v-9M3 14.2h18v5.3M21 19.5v-5.3a2.6 2.6 0 0 0-2.6-2.6H10.4v2.6"/><circle cx="7" cy="11.2" r="1.9"/>',
  mart: '<circle cx="9.6" cy="19.4" r="1.5"/><circle cx="17.4" cy="19.4" r="1.5"/><path d="M2.6 3.6h2.7l2.4 11.5h11l1.9-7.7H6.5"/>',
}
</script>

<template>
  <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" v-html="PATHS[name]" />
</template>
