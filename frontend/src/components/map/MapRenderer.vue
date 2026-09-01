<script setup lang="ts">import{ref}from'vue';import type{Place}from'../../assets/types';import KakaoMap from'./KakaoMap.vue';import MockMap from'./MockMap.vue';withDefaults(defineProps<{places:Place[];selectedId?:string;showRoute?:boolean}>(),{showRoute:false});const emit=defineEmits<{select:[place:Place]}>();const fallback=ref(!import.meta.env.VITE_KAKAO_MAP_APP_KEY)</script>
<template>
  <MockMap v-if="fallback" /><KakaoMap
    v-else
    :places="places"
    :selected-id="selectedId"
    :show-route="showRoute"
    @select="emit('select',$event)"
    @error="fallback=true"
  />
</template>
