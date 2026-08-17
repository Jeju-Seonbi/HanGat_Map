<script setup lang="ts">import type{Place}from'../../assets/types';import CongestionBadge from'../common/CongestionBadge.vue';import PlaceImage from'../common/PlaceImage.vue';defineProps<{place:Place}>()</script>
<template>
  <article class="place-card">
    <PlaceImage
      :src="place.image"
      :alt="place.name"
    /><div class="place-body">
      <div class="eyebrow">
        {{ place.region }} · {{ place.category }}
      </div><h3>{{ place.name }}</h3><p>{{ place.description }}</p><div class="row">
        <CongestionBadge
          :level="place.level"
          :score="place.score"
        /><RouterLink
          class="text-link"
          :to="`/places/${place.id}`"
        >
          자세히 →
        </RouterLink>
      </div>
    </div>
  </article>
</template>
