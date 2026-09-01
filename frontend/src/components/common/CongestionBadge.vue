<script setup lang="ts">import{levelLabel}from'../../data/data';import type{CongestionLevel}from'../../assets/types';defineProps<{level:CongestionLevel}>()</script>
<template>
  <span
    class="congestion"
    :class="level.toLowerCase()"
  >{{ levelLabel[level] }}</span>
</template>
