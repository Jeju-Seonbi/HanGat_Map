<script setup>
/**
 * 찜한 장소 (요구사항 정의서 MY_006 · MY_007).
 *
 *  MY_006 — 내 찜만 조회 / 카드·목록 형태 / 대표 이미지·장소명·카테고리·주소·평점·운영 상태
 *           날씨·혼잡 표시 / 선택 시 지도 + 상세 / 지도 보기 / 최근·이름·카테고리 정렬 / 빈 상태
 *  MY_007 — 찜 해제 시 목록에서 즉시 제거
 */
import { computed, onMounted, ref, watch } from 'vue'
import CrowdBadge from '../../components/common/CrowdBadge.vue'
import WeatherBadge from '../../components/common/WeatherBadge.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StateBlock from '../../components/common/StateBlock.vue'
import SortSeg from '../../components/mypage/SortSeg.vue'
import PlaceThumb from '../../components/mypage/PlaceThumb.vue'
import StarRating from '../../components/mypage/StarRating.vue'
import MapRenderer from '../../components/map/MapRenderer.vue'
import { listFavorites, removeFavorite, FAVORITE_SORTS } from '../../api/mypage.js'
import { operationStatus } from '../../data/places.js'
import { useUiStore } from '../../stores/ui.js'
import { useApiError } from '../../composables/useApiError.js'
import { won } from '../../utils/format.js'
import { toKakaoFavoritePlaces } from './favoriteMapModel'

const ui = useUiStore()
const toMessage = useApiError()

const sort = ref('recent')
const view = ref('card') // card | list
const showMap = ref(false)
const selectedId = ref(null)

const loading = ref(true)
const error = ref(null)
const data = ref({ items: [], total: 0 })

async function fetchList () {
  loading.value = data.value.items.length === 0
  error.value = null
  try {
    data.value = await listFavorites({ sort: sort.value })
    if (selectedId.value && !data.value.items.some(i => i.placeId === selectedId.value)) {
      selectedId.value = null
    }
  } catch (e) {
    const msg = toMessage(e)
    if (msg) error.value = msg
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)
watch(sort, fetchList)

const selected = computed(() => data.value.items.find(i => i.placeId === selectedId.value) || null)
const favoriteMapPlaces = computed(() => toKakaoFavoritePlaces(data.value.items))

function select (placeId) {
  selectedId.value = selectedId.value === placeId ? null : placeId
  if (selectedId.value) showMap.value = true
}

async function unfavorite (item) {
  try {
    await removeFavorite(item.placeId)
    // 즉시 반영 (MY_007)
    data.value = {
      ...data.value,
      items: data.value.items.filter(i => i.placeId !== item.placeId),
      total: data.value.total - 1
    }
    if (selectedId.value === item.placeId) selectedId.value = null
    ui.toast(`${item.name} 찜을 해제했어요`)
  } catch (e) {
    const msg = toMessage(e)
    if (msg) ui.toast(msg)
  }
}

const status = item => operationStatus(item)
</script>

<template>
  <section>
    <div class="bar-top">
      <div class="sect">찜한 장소 <span class="cnt tnum">{{ data.total }}</span></div>
      <SortSeg v-model="sort" :options="FAVORITE_SORTS" label="찜 정렬 기준" />
    </div>

    <div v-if="!loading && !error && data.items.length" class="tools">
      <div class="seg small">
        <button :class="{ on: view === 'card' }" @click="view = 'card'">카드</button>
        <button :class="{ on: view === 'list' }" @click="view = 'list'">목록</button>
      </div>
      <button class="btn2" :class="{ primary: showMap }" @click="showMap = !showMap">
        {{ showMap ? '지도 닫기' : '지도 보기' }}
      </button>
    </div>

    <StateBlock :loading="loading" :error="error" :rows="3" @retry="fetchList" />

    <template v-if="!loading && !error">
      <EmptyState
        v-if="!data.items.length"
        title="찜한 장소가 없어요"
        hint="지도에서 마음에 드는 장소를 열고 하트를 누르면 여기에 모여요."
        action-label="지도에서 찾아보기"
        action-to="/map"
      />

      <template v-else>
        <!-- 지도 + 상세 (MY_006: 선택하면 지도에서 보여주고 옆에 상세도 띄운다) -->
        <div v-if="showMap" class="mapwrap" :class="{ withDetail: !!selected }">
          <div class="favorites-kakao-map">
            <MapRenderer :places="favoriteMapPlaces" :selected-id="selectedId"
              @select="place => select(place.id)" />
          </div>

          <aside v-if="selected" class="detail fl">
            <div class="dh">
              <div>
                <h3>{{ selected.name }}</h3>
                <p class="note">{{ selected.category }} · {{ selected.region }}</p>
              </div>
              <button class="x" aria-label="상세 닫기" @click="selectedId = null">×</button>
            </div>
            <div class="dbadges">
              <CrowdBadge :value="selected.crowd" show-value />
              <WeatherBadge :kind="selected.weather.kind" :t="selected.weather.t" />
            </div>
            <dl class="dmeta">
              <div><dt>주소</dt><dd>{{ selected.addr }}</dd></div>
              <div><dt>운영</dt><dd>{{ status(selected).label }}</dd></div>
              <div><dt>입장료</dt><dd>{{ selected.fee ? `${won(selected.fee)}원` : '무료' }}</dd></div>
              <div><dt>편의</dt>
                <dd>
                  <span class="am" :class="{ no: !selected.park }">주차</span>
                  <span class="am" :class="{ no: !selected.toilet }">화장실</span>
                  <span v-if="selected.indoor" class="am">실내</span>
                </dd>
              </div>
            </dl>
            <div class="dacts">
              <RouterLink class="btn2 primary" :to="{ name: 'map', query: { place: selected.placeId } }">
                지도에서 열기
              </RouterLink>
              <button class="btn2 danger" @click="unfavorite(selected)">찜 해제</button>
            </div>
          </aside>
        </div>

        <!-- 카드 보기 -->
        <ul v-if="view === 'card'" class="cards">
          <li v-for="p in data.items" :key="p.placeId">
            <article class="card hoverable" :class="{ sel: p.placeId === selectedId }">
              <button class="hit" :aria-label="`${p.name} 상세 보기`" @click="select(p.placeId)">
                <PlaceThumb :category="p.category" :name="p.name" size="100%" radius="12px" class="th" />
                <div class="cbody">
                  <div class="cname">{{ p.name }}</div>
                  <p class="note addr">{{ p.category }} · {{ p.addr }}</p>
                  <div class="crow">
                    <template v-if="p.rating != null">
                      <StarRating :model-value="Math.round(p.rating)" :size="12" />
                      <span class="score tnum">{{ p.rating }}</span>
                      <span class="note tnum">({{ p.reviewCount }})</span>
                    </template>
                    <span v-else class="note">평점 없음</span>
                    <span class="op" :class="status(p).code.toLowerCase()">{{ status(p).label }}</span>
                  </div>
                  <div class="cbadges">
                    <CrowdBadge :value="p.crowd" />
                    <WeatherBadge :kind="p.weather.kind" :t="p.weather.t" :size="15" />
                  </div>
                </div>
              </button>
              <button class="unfav" :aria-label="`${p.name} 찜 해제`" @click="unfavorite(p)">♥</button>
            </article>
          </li>
        </ul>

        <!-- 목록 보기 (원본 .row 구조) -->
        <ul v-else class="rows">
          <li v-for="p in data.items" :key="p.placeId">
            <button class="row" :class="{ sel: p.placeId === selectedId }" @click="select(p.placeId)">
              <span class="rpin" :class="p.crowdTier" aria-hidden="true" />
              <span class="rinfo">
                <span class="rn">{{ p.name }}</span>
                <span class="rs">{{ p.category }} · {{ p.addr }}</span>
              </span>
              <CrowdBadge :value="p.crowd" />
            </button>
            <button class="unfav row-un" :aria-label="`${p.name} 찜 해제`" @click="unfavorite(p)">♥</button>
          </li>
        </ul>

      </template>
    </template>
  </section>
</template>

<style scoped>
.bar-top { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.bar-top .sect { margin: 0; flex: 1; }
.cnt { color: var(--tx3); font-weight: 700; }

.tools { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.seg.small { width: 150px; }
.tools .btn2 { padding: 8px 14px; }

.mapwrap { display: grid; grid-template-columns: 1fr; gap: 10px; margin-bottom: 14px; }
.mapwrap.withDetail { grid-template-columns: 1fr 300px; }
.favorites-kakao-map {
  height: 340px; overflow: hidden; border: 1px solid var(--line);
  border-radius: 14px; background: var(--surf2);
}
.favorites-kakao-map :deep(.kakao-map),
.favorites-kakao-map :deep(.mock-map) { width: 100%; height: 100%; min-height: 0; border-radius: 14px; }

.detail { padding: 16px; align-self: start; }
.dh { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 10px; }
.dh h3 { font-size: 16px; font-weight: 800; letter-spacing: -.03em; }
.dh .x { color: var(--tx3); font-size: 19px; line-height: 1; padding: 3px 7px; border-radius: 9px; margin-left: auto; }
.dh .x:hover { background: var(--surf2); }
.dbadges { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 10px; }
.dmeta > div { display: flex; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--line); }
.dmeta > div:last-child { border: none; }
.dmeta dt { font-size: 11.5px; color: var(--tx3); font-weight: 600; width: 44px; flex-shrink: 0; }
.dmeta dd { font-size: 12px; font-weight: 600; flex: 1; }
.am {
  display: inline-block; font-size: 11px; font-weight: 600; padding: 4px 10px;
  border-radius: var(--rp); background: var(--surf2); color: var(--tx2); margin: 0 4px 4px 0;
}
.am.no { opacity: .4; text-decoration: line-through; }
.dacts { display: flex; gap: 7px; margin-top: 12px; }
.dacts > * { flex: 1; text-align: center; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(224px, 1fr)); gap: 10px; }
.cards .card { padding: 10px; position: relative; }
.cards .card.sel { border-color: var(--ac); }
.hit { display: block; width: 100%; text-align: left; }
.th { width: 100% !important; height: 96px !important; }
.cbody { padding: 10px 4px 2px; }
.cname { font-size: 14px; font-weight: 800; letter-spacing: -.02em; }
.addr { margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.crow { display: flex; align-items: center; gap: 5px; margin-top: 7px; flex-wrap: wrap; }
.score { font-size: 12px; font-weight: 800; }
.op { font-size: 10.5px; font-weight: 700; margin-left: auto; color: var(--tx3); }
.op.open { color: var(--calm); }
.op.closed { color: var(--busy); }
.cbadges { display: flex; gap: 5px; margin-top: 9px; flex-wrap: wrap; }

.unfav {
  position: absolute; top: 16px; right: 16px;
  width: 30px; height: 30px; border-radius: 50%;
  background: rgba(255, 255, 255, .92); color: var(--pink);
  font-size: 15px; line-height: 1; box-shadow: var(--sh);
}
.unfav:hover { background: var(--pink-bg); }

.rows { display: flex; flex-direction: column; gap: 6px; }
.rows li { display: flex; align-items: center; gap: 4px; }
.row {
  flex: 1; min-width: 0; display: flex; align-items: center; gap: 10px;
  padding: 9px 11px; border-radius: 12px;
  background: var(--surf); border: 1px solid var(--line); text-align: left;
  transition: box-shadow .12s, transform .12s;
}
.row:hover { transform: translateX(2px); box-shadow: var(--sh); }
.row.sel { border-color: var(--ac); }
.rinfo { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.rn { font-size: 13.5px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rs { font-size: 11px; color: var(--tx3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.row-un { position: static; box-shadow: none; background: var(--surf2); flex-shrink: 0; }

.foot { margin-top: 12px; }

@media (max-width: 900px) {
  .mapwrap.withDetail { grid-template-columns: 1fr; }
}
</style>
