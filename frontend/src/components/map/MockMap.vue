<script setup lang="ts">import{places}from'../../data/data';import{ref}from'vue';const selected=ref(places[0].id)</script>
<template>
  <div
    class="mock-map"
    aria-label="제주 혼잡도 모의 지도"
  >
    <div class="map-label">
      JEJU ISLAND <small>Mock map</small>
    </div><button
      v-for="(p,i) in places"
      :key="p.id"
      class="marker"
      :class="[p.level.toLowerCase(),`m${i+1}`,{selected:selected===p.id}]"
      @click="selected=p.id"
    >
      <span>{{ p.score }}</span>{{ p.name }}
    </button><div class="map-legend">
      <span><i class="dot quiet" />여유</span><span><i class="dot normal" />보통</span><span><i class="dot crowded" />혼잡</span>
    </div>
  </div>
</template>
