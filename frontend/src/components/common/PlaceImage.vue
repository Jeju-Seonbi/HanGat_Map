<script setup lang="ts">import{useFallbackImage}from'../../utils/imageFallback';defineProps<{src:string;alt:string;eager?:boolean}>()</script>
<template>
  <img
    :src="src"
    :alt="alt"
    :loading="eager?'eager':'lazy'"
    class="place-image"
    @error="useFallbackImage"
  >
</template>
