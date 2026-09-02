<script setup>
/**
 * 장소 대표 이미지 자리 (MY_006).
 *
 * ⚠️ 실제 사진 데이터가 없다. 원본 index.html 에도 장소 사진은 없고,
 *    데이터 모델의 `place_photos` 테이블은 아직 채워지지 않았다.
 *    가짜 사진을 끌어오지 않고, 카테고리 색으로 구분되는 **자리표시자**를 그린다.
 *    실서비스에서는 place_photos.photo_url 로 교체한다.
 */
import { computed } from 'vue'
import { CATEGORY_HUE } from '../../data/places.js'

const props = defineProps({
  category: { type: String, default: '' },
  name: { type: String, default: '' },
  size: { type: String, default: '72px' },
  radius: { type: String, default: '12px' }
})

const hue = computed(() => CATEGORY_HUE[props.category] ?? 210)
/*
  사진 자리를 그라디언트 + 사선 무늬로 채우면 "목업"처럼 보인다.
  단색 톤 블록 하나로 두고, 카테고리 글자는 서비스 타이포 그대로 얹는다.
  사진이 붙었을 때 레이아웃이 변하지 않는 것이 목적이지, 예뻐 보이는 게 목적이 아니다.
*/
const style = computed(() => ({
  width: props.size,
  height: props.size,
  borderRadius: props.radius,
  background: `hsl(${hue.value} 34% 90%)`
}))
</script>

<template>
  <div class="thumb" :style="style" :title="`${props.name} 대표 이미지 (준비 중)`" aria-hidden="true">
    <span class="cat">{{ props.category || '장소' }}</span>
  </div>
</template>

<style scoped>
.thumb {
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  position: relative; overflow: hidden;
}
.cat {
  font-size: 11px; font-weight: 700; letter-spacing: .04em;
  color: var(--tx2);
}
</style>
