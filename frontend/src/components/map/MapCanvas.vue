<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { loadKakaoMap } from '@/composables/useKakaoLoader'
import { mapBridge } from '@/composables/mapBridge'
import { state, inFilter, inRegion } from '@/stores/mapStore'
import { SPOTS, FOOD, DINE, CAFE, CVS, STAY, MART } from '@/data/placesMap'
import { crowd, tier } from '@/utils/crowd'
import { cssVar } from '@/utils/geo'
import { POI_MARKER_CLASS, shouldShowMapLabels } from './mapPresentation'

const emit = defineEmits(['select', 'blank-click'])

const el = ref(null)
const failed = ref('')
const origin = location.origin
let map = null
/* 오버레이는 반응형일 필요가 없어 ref 바깥에 둔다 */
const OV = { spot: [], food: [], dine: [], cafe: [], cvs: [], stay: [], mart: [], route: [], num: [] }

const LL = (lat, lng) => new kakao.maps.LatLng(lat, lng)

function clearOverlays() {
  for (const k in OV) { OV[k].forEach(o => o.setMap(null)); OV[k].length = 0 }
}

function syncLabelVisibility() {
  if (!map || !el.value) return
  el.value.classList.toggle('labels-visible', shouldShowMapLabels(map.getLevel()))
}

const onZoomChanged = () => syncLabelVisibility()

/** 핀 하나 = CustomOverlay 하나 (라벨은 같은 오버레이 안에 겹쳐 넣는다) */
function addPin(group, lat, lng, html, onClick, z) {
  const node = document.createElement('div')
  node.className = 'pw'
  node.innerHTML = html
  const ov = new kakao.maps.CustomOverlay({
    position: LL(lat, lng), content: node,
    yAnchor: 0.5, xAnchor: 0.5, zIndex: z || 0, clickable: !!onClick,
  })
  ov.setMap(map)
  OV[group].push(ov)
  if (onClick) node.addEventListener('click', e => { e.stopPropagation(); onClick() })
}

function draw() {
  if (!map) return
  clearOverlays()
  const { di, sel, course, courseDay, L } = state
  const inCourse = n => course && course.stops.some(s => s.o && s.o.n === n)

  if (L.spot) SPOTS.forEach(s => {
    const c = crowd(s, di), on = inFilter(s), t = tier(c)
    const pick = inCourse(s.n) || (sel && sel.n === s.n)
    const sz = pick ? 20 : on ? 15 : 9
    const cls = `pn ${L.crowd ? t : 'calm'}${pick ? ' pick' : ''}${on ? '' : ' dim'}`
    addPin('spot', s.y, s.x,
      `${on ? `<div class="lb-t">${s.n}</div>` : ''}
       <div class="${cls}" style="width:${sz}px;height:${sz}px"></div>`,
      () => emit('select', s), pick ? 400 : on ? 200 : 100)
  })

  const poi = (g, list) => {
    if (!L[g]) return
    list.filter(inRegion).forEach(f => addPin(g, f.y, f.x,
      `<div class="lb-t poi-label">${f.n}</div><div class="poi-marker ${POI_MARKER_CLASS[g]}"></div>`, null, 60))
  }
  poi('food', FOOD)
  poi('dine', DINE)
  poi('cafe', CAFE)
  poi('cvs', CVS)
  poi('stay', STAY)
  poi('mart', MART)

  if (course) {
    /* MAP_006: 일차 전환 시 해당 일차 경로만 강조 (번호는 일차 내 방문 순서) */
    const g = {}
    course.stops.filter(s => s.o).forEach(s => (g[s.d] = g[s.d] || []).push(s))
    Object.keys(g).forEach(d => {
      const pts = g[d].map(s => [s.o.y, s.o.x])
      const on = courseDay === 'all' || +courseDay === +d
      if (pts.length > 1) {
        const line = new kakao.maps.Polyline({
          path: pts.map(p => LL(p[0], p[1])),
          strokeWeight: on ? 4 : 2, strokeColor: cssVar('ac'),
          strokeOpacity: on ? 0.9 : 0.25, strokeStyle: 'shortdash',
        })
        line.setMap(map)
        OV.route.push(line)
      }
      if (on) pts.forEach((p, i) => addPin('num', p[0], p[1], `<div class="mk-num">${i + 1}</div>`, null, 600))
    })
  }
}

/** 좌측 카드·우측 패널에 가리지 않도록 여백을 주고 맞춘다 (화면이 좁으면 비율로 축소) */
function fitPoints(pts, minLevel) {
  if (!map || !pts?.length) return
  const W = el.value.clientWidth, H = el.value.clientHeight
  if (!W || !H) return
  if (pts.length === 1) {
    map.setCenter(LL(pts[0][0], pts[0][1]))
    if (map.getLevel() > (minLevel || 6)) map.setLevel(minLevel || 6)
    return
  }
  const b = new kakao.maps.LatLngBounds()
  pts.forEach(p => b.extend(LL(p[0], p[1])))
  const left = Math.min(340, Math.round(W * 0.26)), right = Math.min(380, Math.round(W * 0.28))
  const top = Math.min(90, Math.round(H * 0.14)), bottom = Math.min(130, Math.round(H * 0.18))
  map.setBounds(b, top, right, bottom, left)
}

function fitRegion() {
  if (!map) return
  const ss = SPOTS.filter(s => state.F.reg === '전체' || s.r === state.F.reg)
  if (!ss.length) return
  if (state.F.reg === '전체') { map.setCenter(LL(33.383, 126.55)); map.setLevel(10); return }
  fitPoints(ss.map(s => [s.y, s.x]))
}

const onResize = () => map && map.relayout()

onMounted(async () => {
  try {
    await loadKakaoMap()
  } catch (e) {
    failed.value = e.message
    return
  }
  map = new kakao.maps.Map(el.value, { center: LL(33.383, 126.55), level: 10 })
  map.setMinLevel(1)
  map.setMaxLevel(13)
  map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.BOTTOMRIGHT)
  kakao.maps.event.addListener(map, 'click', () => emit('blank-click'))
  kakao.maps.event.addListener(map, 'zoom_changed', onZoomChanged)
  addEventListener('resize', onResize)

  Object.assign(mapBridge, {
    ready: true,
    panTo: (lat, lng) => map.panTo(LL(lat, lng)),
    zoomTo: lv => { if (map.getLevel() > lv) map.setLevel(lv) },
    fitRegion, fitPoints,
    relayout: onResize,
  })
  fitRegion()
  syncLabelVisibility()
  draw()
})

onBeforeUnmount(() => {
  removeEventListener('resize', onResize)
  if (map) kakao.maps.event.removeListener(map, 'zoom_changed', onZoomChanged)
  clearOverlays()
  mapBridge.ready = false
})

/* 상태가 바뀌면 다시 그린다. 지도 자체는 새로 만들지 않는다 */
watch(() => [state.di, state.sel, state.course, state.courseDay, state.F.reg, state.F.cat,
  ...Object.values(state.L)], draw, { deep: true })
</script>

<template>
  <div id="map" ref="el">
    <div v-if="failed" class="map-fail">
      지도를 불러오지 못했어요.
      <span>{{ failed }}<br>
        카카오 개발자 콘솔에 <b>{{ origin }}</b> 도메인이 등록됐는지,
        제품 설정 &gt; 카카오맵이 켜져 있는지 확인해 주세요.</span>
    </div>
  </div>
</template>
