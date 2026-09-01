<script setup>
/* 후기 사진 확대 보기 — 여러 장이면 좌우로 넘기고, 배경·×·Esc로 닫는다 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const photos = ref([])
const index = ref(0)
const open = computed(() => photos.value.length > 0)
const many = computed(() => photos.value.length > 1)

function show(list, i) { photos.value = list; index.value = i }
function close() { photos.value = [] }
function move(d) { index.value = (index.value + d + photos.value.length) % photos.value.length }

function onKey(e) {
  if (!open.value) return
  if (e.key === 'Escape') close()
  else if (e.key === 'ArrowLeft' && many.value) move(-1)
  else if (e.key === 'ArrowRight' && many.value) move(1)
}
onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))

defineExpose({ show, close })
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="map-lightbox" @click.self="close">
      <button v-if="many" class="lbx-nav prev" aria-label="이전 사진" @click.stop="move(-1)">‹</button>
      <img :src="photos[index]" alt="후기 사진">
      <button v-if="many" class="lbx-nav next" aria-label="다음 사진" @click.stop="move(1)">›</button>
      <button class="lbx-x" aria-label="닫기" @click="close">×</button>
      <div v-if="many" class="lbx-cnt">{{ index + 1 }} / {{ photos.length }}</div>
    </div>
  </Teleport>
</template>

<style scoped>
.map-lightbox{position:fixed;inset:0;z-index:3000;background:rgba(10,16,24,.9);
  display:flex;align-items:center;justify-content:center;padding:44px 60px;cursor:zoom-out}
.map-lightbox img{max-width:100%;max-height:100%;object-fit:contain;border-radius:10px;
  box-shadow:0 18px 60px rgba(0,0,0,.55);cursor:default}
.map-lightbox button{color:#fff;opacity:.8}
.map-lightbox button:hover{opacity:1;background:rgba(255,255,255,.16)}
.lbx-x{position:absolute;top:14px;right:16px;font-size:27px;line-height:1;
  width:40px;height:40px;border-radius:50%}
.lbx-nav{position:absolute;top:50%;transform:translateY(-50%);font-size:32px;line-height:1;
  width:44px;height:66px;border-radius:12px}
.lbx-nav.prev{left:12px}
.lbx-nav.next{right:12px}
.lbx-cnt{position:absolute;bottom:20px;left:50%;transform:translateX(-50%);color:#fff;
  font-size:12.5px;font-weight:700;background:rgba(0,0,0,.5);padding:5px 15px;border-radius:var(--rp)}
@media(max-width:768px){
  .map-lightbox{padding:52px 12px}
  .lbx-nav{width:38px;height:56px;font-size:26px}
  .lbx-nav.prev{left:2px}
  .lbx-nav.next{right:2px}
}
</style>
